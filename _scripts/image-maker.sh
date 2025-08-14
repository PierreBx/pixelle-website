#!/usr/bin/env bash
set -euo pipefail

# =========================
#  WORKING DIRECTORY
# =========================
# Default to ~/Téléchargements/_pixelle_images; override with: WORK_DIR=/path ./script.sh
WORK_DIR="${WORK_DIR:-$HOME/Téléchargements/_pixelle_images}"
RESULTS_DIR="_results"

# =========================
#  CONFIG
# =========================
MAX_W="${MAX_W:-1600}"    # Max width for in-body images (no upscaling)
JPG_Q="${JPG_Q:-85}"      # JPEG quality
WEBP_Q="${WEBP_Q:-80}"    # WebP quality
PREVIEW_W=1200            # Preview target width
PREVIEW_H=630             # Preview target height

LC_ALL=C  # stable sorting

# =========================
#  Pick ImageMagick binary
# =========================
if command -v magick >/dev/null 2>&1; then
  IM="magick"
elif command -v convert >/dev/null 2>&1; then
  IM="convert"
else
  echo "Error: ImageMagick not found. Install with: sudo apt install imagemagick"
  exit 1
fi

has_webp_support() {
  if [[ "$IM" == "magick" ]]; then
    magick -list format | grep -q '^WEBP'
  else
    convert -list format | grep -q '^WEBP'
  fi
}

# =========================
#  Helpers
# =========================
slugify() {
  tr '[:upper:]' '[:lower:]' \
  | sed -E 's/[[:space:]]+/-/g' \
  | tr -cd 'a-z0-9\-_'
}

has_alpha() {
  local f="$1"
  local ch
  ch="$($IM "$f" -format "%[channels]" info: 2>/dev/null || echo "")"
  if [[ "$ch" == *a* || "$ch" == *A* ]]; then echo 1; else echo 0; fi
}

# =========================
#  Ensure work dir exists and enter it
# =========================
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"
mkdir -p "$RESULTS_DIR"

shopt -s nullglob

# =========================
#  1) Ask for PREFIX
# =========================
read -r -p "Enter a prefix for output filenames (e.g., 'rome-trip-2025'): " PREFIX
PREFIX="$(printf '%s' "$PREFIX" | slugify)"
if [[ -z "$PREFIX" ]]; then
  echo "A non-empty prefix is required."
  exit 1
fi

# =========================
#  2) Process the newest preview_* image
# =========================
preview_src=""
# Collect candidates
mapfile -d '' PREVIEW_CANDIDATES < <(find . -maxdepth 1 -type f \
  \( -iname 'preview_*.jpg' -o -iname 'preview_*.jpeg' -o -iname 'preview_*.png' -o -iname 'preview_*.webp' \) -print0)

if (( ${#PREVIEW_CANDIDATES[@]} > 0 )); then
  newest_ts=0
  newest_path=""
  for f in "${PREVIEW_CANDIDATES[@]}"; do
    # Strip leading "./" for nicer output but keep full path for stat
    ts=$(stat -c %Y "$f" 2>/dev/null || stat -f %m "$f")
    if (( ts > newest_ts )); then
      newest_ts=$ts
      newest_path="$f"
    fi
  done
  preview_src="$newest_path"
fi

if [[ -n "$preview_src" ]]; then
  echo "Found preview image: $preview_src"
  outjpg="$RESULTS_DIR/${PREFIX}_preview.jpg"
  outwebp="$RESULTS_DIR/${PREFIX}_preview.webp"

  "$IM" "$preview_src" -auto-orient -colorspace sRGB \
    -gravity center -resize ${PREVIEW_W}x${PREVIEW_H}^ -extent ${PREVIEW_W}x${PREVIEW_H} \
    -strip -interlace Plane -quality "$JPG_Q" "$outjpg"

  if has_webp_support; then
    "$IM" "$outjpg" -quality "$WEBP_Q" -define webp:method=6 "$outwebp" || true
  fi

  echo "Preview outputs:"
  echo " - $outjpg"
  [[ -f "$outwebp" ]] && echo " - $outwebp"
else
  echo "No file starting with 'preview_' found. Skipping preview step."
fi

# =========================
#  3) Process all other images for post body
# =========================
mapfile -d '' ALL_IMAGES < <(find . -maxdepth 1 -type f \
  \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' \) \
  ! -iname 'preview_*' -print0)

if (( ${#ALL_IMAGES[@]} == 0 )); then
  echo "No other images found to process."
  echo "Results dir: $WORK_DIR/$RESULTS_DIR"
  exit 0
fi

# Sort by name (stable numbering)
mapfile -t SORTED_IMAGES < <(printf '%s\n' "${ALL_IMAGES[@]}" | sort -V)

idx=1
for src in "${SORTED_IMAGES[@]}"; do
  [[ -f "$src" ]] || continue

  printf -v NUM "%02d" "$idx"
  base_out="$RESULTS_DIR/${PREFIX}_${NUM}"

  # JPEG (general-purpose)
  out_jpg="${base_out}.jpg"
  "$IM" "$src" -auto-orient -colorspace sRGB -strip -filter Lanczos \
    -resize "${MAX_W}x>" -sampling-factor 4:2:0 -interlace Plane \
    -quality "$JPG_Q" "$out_jpg"

  # WebP (usually smallest)
  if has_webp_support; then
    out_webp="${base_out}.webp"
    "$IM" "$src" -auto-orient -colorspace sRGB -strip -filter Lanczos \
      -resize "${MAX_W}x>" -quality "$WEBP_Q" -define webp:method=6 "$out_webp"
  fi

  # PNG only if source has alpha
  if [[ "$(has_alpha "$src")" == "1" ]]; then
    out_png="${base_out}.png"
    "$IM" "$src" -auto-orient -colorspace sRGB -strip -filter Lanczos \
      -resize "${MAX_W}x>" -define png:compression-level=9 \
      -define png:compression-filter=5 "$out_png"
  fi

  echo "Body image #$NUM from: $src"
  echo " - ${out_jpg}"
  [[ -n "${out_webp:-}" && -f "${out_webp:-}" ]] && echo " - ${out_webp}"
  [[ -n "${out_png:-}"  && -f "${out_png:-}"  ]] && echo " - ${out_png}"

  ((idx++))
done

echo "All done. Results in: $WORK_DIR/$RESULTS_DIR"
