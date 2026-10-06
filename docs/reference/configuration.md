---
title: Configuration
description: Every setting of the ankka-reasoning service, the environment variable that overrides each, its default, and the settings that belong to ankka.
kind: reference
related: [deploy/deploy-on-ankka.md, deploy/operate.md, concepts/writers-and-holders.md]
---

# Configuration

The service reads its settings once at start, from keys under `reasoning` in its `application.conf`,
each with an environment variable that overrides it. Changing a setting is a restart.

## The service's own settings

<!-- generated:start configuration -->
| Variable | Configuration key | Default | Applies in |
|---|---|---|---|
| `REASONING_GRAPH_TOPIC` | `reasoning.graph-topic` | `"reasoning-graph"` | service |
| `REASONING_NEO4J_URI` | `reasoning.neo4j.uri` | `""` | service |
| `REASONING_NEO4J_USERNAME` | `reasoning.neo4j.username` | `""` | service |
| `REASONING_NEO4J_PASSWORD` | `reasoning.neo4j.password` | `""` | service |
| `REASONING_NEO4J_DATABASE` | `reasoning.neo4j.database` | `"neo4j"` | service |
| `REASONING_EXCERPT_LIMIT` | `reasoning.excerpt-limit` | `2000` | service |
| `REASONING_WAIT_LIMIT_MAX_MS` | `reasoning.wait-limit-max-ms` | `30000` | service |
| `REASONING_STEWARDS` | `reasoning.stewards` | `""` | service |

Settings with no environment variable, overridable in the service's own `application.conf`:

| Configuration key | Default | Applies in |
|---|---|---|
<!-- generated:end configuration -->

`REASONING_GRAPH_TOPIC` names the delta topic the graph is published to. It has to be the topic the
pipeline declares, which is `reasoning-graph` in the blueprint shipped with the repository.

`REASONING_NEO4J_URI` is the Bolt address of the graph database the answers read, and
`REASONING_NEO4J_USERNAME` and `REASONING_NEO4J_PASSWORD` its credentials, which in a deployment come
from a secret. With no address set, every route that reads the graph answers `503` and every other
route is unaffected. `REASONING_NEO4J_DATABASE` names the database within it.

`REASONING_EXCERPT_LIMIT` is the longest excerpt a piece of evidence may carry, in characters. A longer
one is refused with `text.length`.

`REASONING_WAIT_LIMIT_MAX_MS` is the longest a `POST /graph/wait` may be asked to wait, in
milliseconds. A request for longer waits this long.

`REASONING_STEWARDS` lists the writers who may withdraw the text of any record, as whole writer
strings separated by commas, for example `service:compliance/steward`. It is empty by default, so no
writer is a steward.

## Settings that are ankka's

The service is an ankka service, and these are read by ankka's runtime. ankka's own
[configuration reference](https://docs.ankka.cloud/reference/configuration/) describes them in full.

| Variable | What it means here |
|---|---|
| `ANKKA_KAFKA_BOOTSTRAP_SERVERS` | the broker the graph is published to; with none set the service holds and returns records and publishes nothing |
| `ANKKA_DB_HOST`, `ANKKA_DB_PORT`, `ANKKA_DB_NAME`, `ANKKA_DB_USER`, `ANKKA_DB_PASSWORD` | the service's Postgres database; set by the platform in a deployment |
| `ANKKA_HTTP_PORT` | the port the service listens on, `9000` by default; set by the platform in a deployment |

## One setting fixed in the service

The service asks its database what has changed once a second:
`pekko.persistence.r2dbc.refresh-interval = 1s` in its `application.conf`, where Pekko's default is
three seconds. A graph consumer over a key value entity's state is fed by this poll alone, so it is
the floor under how long a record takes to reach the graph. It has no environment variable.

## On a laptop

`just up` and `just run` use the values in `docker-compose.yml` and the `Justfile`. One variable is
theirs and not the service's: `REASONING_POSTGRES_PORT` moves the Postgres container, and the service
with it, off port 5432 where that is taken.
