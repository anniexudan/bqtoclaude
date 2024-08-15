# BigQuery Remote Function bridge for Claude API

This sample automates the creation of a GCP CloudFunction that can be registered as a remote function for BQ to interact with Claude API.




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