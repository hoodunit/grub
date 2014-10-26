#!/bin/bash

rm -rf target
git pull
rm -rf dev
lein cljx
git checkout dev
lein cljsbuild once prod
lein run prod
