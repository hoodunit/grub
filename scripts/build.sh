#!/bin/bash

echo "# Removing old builds"
rm -rf target
rm resources/public/js/grub.js
rm resources/public/js/grub.min.js
rm -rf resources/public/js/out
echo ""
echo "$ lein cljx"
lein cljx &&
echo "" &&
echo "$ lein cljsbuild once dev" &&
lein cljsbuild once dev &&
echo "" &&
echo "$ lein cljsbuild once prod" &&
lein cljsbuild once prod &&
echo "" &&
echo "$ lein uberjar" &&
lein uberjar &&
mkdir target/builds
BUILD_NAME=grub-`date "+%F-%H%M"`-`git rev-parse HEAD | cut -c -10`.jar &&
mv target/grub-standalone.jar target/builds/$BUILD_NAME &&
echo "Built package target/builds/$BUILD_NAME"
