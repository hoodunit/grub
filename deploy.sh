#!/bin/bash

rm -rf target
git pull
lein cljx
lein cljsbuild once prod
lein run prod
