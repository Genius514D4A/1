#!/bin/bash
#
# use the command line interface to install Slack package.
#
: ${WHISK_SYSTEM_AUTH:?"WHISK_SYSTEM_AUTH must be set and non-empty"}
AUTH_KEY=$WHISK_SYSTEM_AUTH

SCRIPTDIR="$(cd $(dirname "$0")/ && pwd)"
source "$SCRIPTDIR/util.sh"
cd "$SCRIPTDIR/../bin"

echo Installing Slack package.

createPackage slack \
    -a description "Package which contains actions to interact with the Slack messaging service"

waitForAll

install slack/post.js slack/post \
    -a description 'Posts a message to Slack' \
    -a parameters '[ {"name":"username", "required":true}, {"name":"text", "required":true}, {"name":"url", "required":true, "bindTime":true},{"name":"channel", "required":true} ]' \
    -a sampleInput '{"username":"whisk", "text":"Hello whisk!", "channel":"myChannel", "url": "https://hooks.slack.com/services/XYZ/ABCDEFG/12345678"}'

waitForAll

echo Slack package ERRORS = $ERRORS
exit $ERRORS
