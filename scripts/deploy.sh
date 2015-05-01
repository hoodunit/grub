#!/bin/bash

BUILD_NAME=`ls -t target/builds | head -1`
scp target/builds/$BUILD_NAME $GRUB_USER@$GRUB_SERVER:$GRUB_RELEASE_DIR/$BUILD_NAME
