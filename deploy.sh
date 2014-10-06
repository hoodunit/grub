#!/bin/bash

git pull
lein cljx
lein cljsbuild once prod
lein run prod
