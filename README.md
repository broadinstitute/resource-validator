[![Build Status](https://github.com/broadinstitute/leonardo-cron-jobs/workflows/Unit%20Tests/badge.svg)](https://github.com/broadinstitute/leonardo-cron-jobs/actions) 
[![codecov](https://codecov.io/gh/broadinstitute/leonardo-cron-jobs/branch/master/graph/badge.svg)](https://codecov.io/gh/broadinstitute/leonardo-cron-jobs)

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

Note: You can get this info from `leonardo/config` directory. To setup `leonardo/config` directory, follow [this](https://github.com/broadinstitute/firecloud-develop#quick-start---how-do-i-set-up-my-configs)

## Run cloud-sql container (if you're connecting to dev leonardo DB instance)
```
docker run \
  -v <your local path to leonardo repo>/leonardo/config/sqlproxy-service-account.json:/config \
  -p 127.0.0.1:3306:3306 \
  gcr.io/cloudsql-docker/gce-proxy:1.16 /cloud_sql_proxy \
  -instances=<mysql instance you'd like to connect> -credential_file=/config
```
.
## Set up configuration file
Copy `application.conf.example` under each project in dir `[project]/src/main/resources` as `application.conf`. Replace values properly.

## Run a job
```
sbt <project>/run --help
```

e.g. `sbt "resourceValidator/run --dryRun --all"`

Run unit tests (excluding unit tests require database access):
`testOnly -- -l cronJobs.dbTest`

Run all unit tests require database access:
`testOnly -- -n cronJobs.dbTest`

## Contributing

1. Run these unit tests locally before making a PR:
- `com.broadinstitute.dsp.zombieMonitor.DbReaderSpec` 
- `com.broadinstitute.dsp.resourceValidator.DbReaderSpec`

   These are not run in CI, so you have to make sure you run them manually before merging any PRs. Instructions on running these can be found in the respective `DbReaderSpec` files.

2. Once your PR is approved you can merge it and a new PR will be automatically created in [terra-helm](https://github.com/broadinstitute/terra-helm). 

3. Get this terra-helm PR merged (you can merge it yourself) and another automatic commit will bump leonardo's chart version. This will trigger another automatic commit 
in [terra-helmfile](https://github.com/broadinstitute/terra-helmfile). Note that this commit will only auto-bump `dev` and `perf`, and will be auto-merged.

4. Once the terra-helmfile PR is auto-merged, go to [argo](https://ap-argocd.dsp-devops.broadinstitute.org/applications) (you need to be on VPN to access argo), and click the `SYNC APPS` button on the left upper corner. Select these boxes to sync:
 - `leonardo dev` 
 - `leonardo perf`
 - `prune`
    
    This will sync leonardo's deployment to match [terra-helmfile](https://github.com/broadinstitute/terra-helmfile) repo.
    the chartVersion bump and sync for other environments will happen automatically when there is a Terra monolith release.

