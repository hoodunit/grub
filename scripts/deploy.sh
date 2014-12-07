#!/bin/bash

BUILD_NAME=`ls -t target/builds | head -1`
#scp target/builds/$BUILD_NAME $GRUB_USER@$GRUB_SERVER:$GRUB_STAGING_DIR/$BUILD_NAME
ssh $GRUB_USER@$GRUB_SERVER "cp $GRUB_STAGING_DIR/$BUILD_NAME $GRUB_RELEASE_DIR/$BUILD_NAME && killall java && run_server.sh"
