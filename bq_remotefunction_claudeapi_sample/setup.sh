#!/bin/bash
set -eu

if [ "$#" -ne 2 ]
  then
    echo "Usage : sh setup.sh <gcp project> <state-bucket-name>"
    exit -1
fi

STATE_BUCKET=$2
PROJECT=$1

pushd "bqclaude-remotefunction"

mvn clean install

popd

pushd "infra"

terraform init \
 -backend-config="bucket=$STATE_BUCKET" \
 -backend-config="prefix=terraform/state/bqclauderf" \
 && terraform apply     \
  -var="project=${PROJECT}"

popd