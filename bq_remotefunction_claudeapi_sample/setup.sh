#!/bin/bash
set -eu

if [ "$#" -ne 3 ]
  then
    echo "Usage : sh setup.sh <gcp project> <state-bucket-name> <bq dataset for routine>"
    exit -1
fi

BQ_DATASET=$3
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
  -var="project=${PROJECT}" \
  -var="routine_dataset=${BQ_DATASET}"

popd