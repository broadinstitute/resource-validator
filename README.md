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
export LEONARDO_DB_USER=???

export LEONARDO_DB_PASSWORD=???

export LEONARDO_PATH_TO_CREDENTIAL=??