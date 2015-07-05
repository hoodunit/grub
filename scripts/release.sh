#!/bin/sh
echo "Building and deploying app"
CURRENT_DIR="`pwd`/`dirname $0`"
$CURRENT_DIR/build.sh && $CURRENT_DIR/deploy.sh
