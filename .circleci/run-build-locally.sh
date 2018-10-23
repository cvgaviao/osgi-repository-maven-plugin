#!/usr/bin/env bash


if [[ ! -n "$CIRCLE_USER_TOKEN" ]]; then
    echo "You must export CIRCLE_USER_TOKEN environment variable before run." 1>&2
    exit 1
fi    

if [[ ! -n "$1" ]]; then
        echo "Commit hash (argument 1) should be informed" 1>&2
        exit 1
fi

echo "building from commit " $1 

curl --user ${CIRCLE_USER_TOKEN}: \
    --request POST \
    --form revision=$1\
    --form config=@config.yml \
    --form notify=false \
        https://circleci.com/api/v1.1/project/github/cvgaviao/osgi-repository-maven-plugin/tree/master
        
        