# Introduction

This repo contains cron jobs running along side with [Leonardo](https://github.com/databiosphere/leonardo)

# Cron Jobs
[This doc](https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes) explains what processes belong in this repo vs what belongs in [Leonardo](https://github.com/databiosphere/leonardo)

## resource-validator
[Design Doc](https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/737542150/2020-08-25+Proposal+for+Resource+Validator+Cron+Job)

This job updates Google resources to match Leonardo database status.

## zombie-monitor

This job updates Leonardo database to match Google resource status.

# Running Locally

## Setting environment variables

export LEONARDO_DB_USER=???

export LEONARDO_DB_PASSWORD=???

export LEONARDO_PATH_TO_CREDENTIAL=???

export REPORT_DESTINATION_BUCKET=<your test google bucket>

Note: You can get this info from `leonardo/config` directory. To setup `leonardo/config` directory, follow [this](https://github.com/broadinstitute/firecloud-develop#quick-start---how-do-i-set-up-my-configs)

## Run cloud-sql container (if you're connecting to dev leonardo DB instance)
```
docker run \
  -v <your local path to leonardo repo>/leonardo/config/sqlproxy-service-account.json:/config \
  -p 127.0.0.1:3306:3306 \
  gcr.io/cloudsql-docker/gce-proxy:1.16 /cloud_sql_proxy \
  -instances=<mysql instance you'd like to connect> -credential_file=/config
```

## Run a job
```
sbt <project>/run --help
```

e.g. `sbt resourceValidator/run --dryRun --all`