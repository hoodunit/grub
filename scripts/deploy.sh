#!/bin/sh

BUILD_NAME=`ls -t target/builds | head -1`
SERVER_URL=$GRUB_USER@$GRUB_SERVER:$GRUB_RELEASE_DIR/$BUILD_NAME
echo "Deploying to $SERVER_URL"
scp target/builds/$BUILD_NAME $SERVER_URL
