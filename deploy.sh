#!/bin/bash

git branch -D deploy
git checkout -b deploy
lein cljsbuild once prod
git add --force public/js/grub.min.js
git commit -m "Add client code for Heroku deployment"
git push --force heroku deploy:master
git checkout master
