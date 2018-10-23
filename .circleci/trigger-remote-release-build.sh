#!/usr/bin/env bash


if [[ ! -n "$CIRCLE_USER_TOKEN" ]]; then
    echo "You must export CIRCLE_USER_TOKEN environment variable before run." 1>&2
    exit 1
fi    

if [[ ! -n "$1"  ||  ! -n "$2" ]]; then
        echo "Both release version (argument 1) and next version (argument 2) should be informed" 1>&2
        exit 1
fi



RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

release_version=$1
next_version=$2
if [[ -z $3 ]]; then
  dryrun=true
else
  dryrun=false
fi 

echo -e "Releasing version ${RED} $release_version ${NC} and preparing next version ${GREEN} $next_version ${NC}"

template_parameter='{"build_parameters": {"RELEASE_VERSION": "%s", "NEXT_VERSION": "%s", "C8TECH_RELEASE_DRYRUN": "%s"}}'
json_parameter=$(printf "$template_parameter" "$release_version" "$next_version", "$dryrun")

curl \
  --header "Content-Type: application/json" \
  --data "$json_parameter" \
  --request POST \
                https://circleci.com/api/v1.1/project/github/cvgaviao/osgi-repository-maven-plugin/tree/master?circle-token=$CIRCLE_USER_TOKEN

  