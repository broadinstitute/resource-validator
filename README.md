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

## Copy `application.conf.example` under each project as `application.conf`. Replace values properly

## Run a job
```
sbt <project>/run --help
```

e.g. `sbt "resourceValidator/run --dryRun --all"`

## Contributing

Currently, `com.broadinstitute.dsp.zombieMonitor.DbReaderSpec` and `com.broadinstitute.dsp.resourceValidator.DbReaderSpec` are not run in CI, hence make sure you run them manually before merging any PRs.

Once a PR is merged, there will be a PR created in [terra-helm](https://github.com/broadinstitute/terra-helm) (WIP). 
Get this PR merged, and another automatic commit will bump leonardo's chart version. This will trigger another automatic commit 
in [terra-helmfile](https://github.com/broadinstitute/terra-helmfile), note this commit will only auto bump `dev` and `perf`,
create another PR for bumping all other environments when you're ready (similar to [this](https://github.com/broadinstitute/terra-helmfile/pull/390)). Once all these PRs are merged, 
go to [argo](https://ap-argocd.dsp-devops.broadinstitute.org/applications) (you need to be on VPN to access argo), and click `SYNC APPS` button on the left upper corner for all leonardo deploys (select `PRUNE` option as well).
you can also choose to `sync` individual environments if you choose to), this will sync leonardo's deployment to match [terra-helmfile](https://github.com/broadinstitute/terra-helmfile) repo.
