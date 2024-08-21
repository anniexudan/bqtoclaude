# BigQuery Remote Function bridge for Claude API

This sample solution automates the creation of a GCP Cloud Function that can be registered as a remote function for BQ to interact with Claude API. To have all the necessary pieces together terraform is used to provision the infrastructure. Among the components this solution implements we can find:
* [Micronaut](https://micronaut.io) based implementation for the logic of the CloudFunction
* GCP Storage buckets to stage all the cloud function resources
* IAM permissions to grant the needed access for the cloud function and the BigQuery service account in charge to call the deployed cloud function
* BigQuery connection and the routing that wraps the cloud function remote call

To setup the solution run the `setup.sh` script, on a project with the right level of permissions, if a service account impersonation is needed to run terraform code (preferred model) setting the `GOOGLE_IMPERSONATE_SERVICE_ACCOUNT` to the service account mail will indicate it to terraform.

## Example BigQuery usage


``` SQL
-- "functions" dataset was provided as parameter in the setup for this BigQuery routine.
select msg, functions.claude_messages(msg) as response
from
  UNNEST([
    'describe what a color is',
    'summarize the events happening in the movie Interstellar'
  ]) as msg
```

## Infrastructure Cleanup

In case of needed to cleanup the resources used to setup this example remote function on BigQuery, running the `destroy.sh` script will take care of tearing down those resources created by `terraform`.