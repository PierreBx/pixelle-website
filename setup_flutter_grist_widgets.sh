#!/bin/bash

# Script to setup flutter_grist_widgets project
# Run this script in your desired parent directory

echo "Setting up flutter_grist_widgets project..."

# Clone the repository
if [ ! -d "flutter_grist_widgets" ]; then
    git clone https://github.com/PierreBx/flutter_grist_widgets.git
    cd flutter_grist_widgets
else
    cd flutter_grist_widgets
fi

# The git bundle is available at:
# You'll need to download it separately and run:
# git bundle unbundle /path/to/flutter_grist_widgets.bundle
# git checkout -b main
# git push -u origin main

echo "✅ Repository cloned"
echo ""
echo "Next steps:"
echo "1. Download the git bundle from the cloud environment"
echo "2. Or manually create the files using the provided file contents"
echo ""
echo "Alternative: I can provide you with all file contents to copy/paste"
