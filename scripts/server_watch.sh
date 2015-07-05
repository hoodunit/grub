#!/bin/sh
# Helper script for simple deployments.
#
# Runs the latest JAR file from the given directory.
# When a new file is added to the directory, the process
# is killed and the most recent file is run.
# If the script is killed the running processes will also
# be killed.
trap "trap - TERM && echo 'Caught SIGTERM, sending SIGTERM to process group' && kill -- -$$" INT TERM EXIT
while true; do
  LATEST_BUILD=`ls -r $GRUB_RELEASE_DIR | head -1 | tr -d '\n'`
  echo "Starting server package $LATEST_BUILD"
  java -jar $GRUB_RELEASE_DIR/$LATEST_BUILD prod &
  PID=$!
  echo "Watching server directory for new files, restarting server on change"
  inotifywait -e close_write -r $GRUB_RELEASE_DIR
  echo "Restarting server"
  kill $PID
done
