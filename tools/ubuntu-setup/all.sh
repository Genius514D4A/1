#!/bin/bash
set -e
set -x

echo "*** installing basics"
"$SCRIPTDIR/misc.sh"

echo "*** installing python dependences"
"$SCRIPTDIR/pip.sh"

echo "*** installing java"
"$SCRIPTDIR/java8.sh"

echo "*** install scala"
"$SCRIPTDIR/scala.sh"

echo "*** installing docker"
"$SCRIPTDIR/docker.sh"

echo "*** installing ansible"
"$SCRIPTDIR/ansible.sh"

