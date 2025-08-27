#!/bin/bash

PIXL_LOCAL_URL=http://0.0.0.0:4000/pixelle/

green_text() {
    echo -e "\e[32m$1\e[0m"
}

clear

echo -n cleaning posts and media...
# shellcheck disable=SC1072
rm ./_posts/*.md 2> /dev/null
rm -rf ./assets/media/* 2> /dev/null
green_text done!

#echo -n creating assets and includes files...
#cp -ru ./_data/media ./assets/
#cp -ru ./_data/media ./_includes/
#green_text done!

#echo creating posts from yaml database...
#groovy ./_scripts/create_posts.groovy

#echo creating vocabulary entries...
#groovy ./_scripts/create-voc-posts.groovy

echo creating posts from Grist Blog table...
groovy ./_scripts/create-grist-posts.groovy


echo "end of run.sh successfully!"

echo "waiting 1 sec before launching site"
sleep 1 && google-chrome --new-tab $PIXL_LOCAL_URL

