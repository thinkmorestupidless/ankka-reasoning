# Contract: deployment

What the service needs around it: its settings, the topic, the pipeline that fills the graph
database, and who a caller is taken to be. The routes are in [http-api.md](http-api.md); what is
published to the topic is in [graph-vocabulary.md](graph-vocabulary.md).

## Settings

Each is a key under `reasoning` in `application.conf` with an environment override.

| Variable | Default | Meaning |
|---|---|---|
| `ANKKA_KAFKA_BOOTSTRAP_SERVERS` | none | ankka's own. The broker the graph is published to |
| `REASONING_GRAPH_TOPIC` | `reasoning-graph` | The delta topic |
| `REASONING_NEO4J_URI` | none | Bolt address of the graph database the answers read |
| `REASONING_NEO4J_USERNAME`, `REASONING_NEO4J_PASSWORD` | none | Its credentials, from a project secret |
| `REASONING_NEO4J_DATABASE` | `neo4j` | |
| `REASONING_EXCERPT_LIMIT` | `2000` | The longest excerpt, in characters |
| `REASONING_WAIT_LIMIT_MAX_MS` | `30000` | The longest a `/graph/wait` may be asked to wait |
| `REASONING_STEWARDS` | empty | Writers, comma separated, who may withdraw the text of any record |
| `ANKKA_AUTH_ISSUERS` and `ANKKA_AUTH_<NAME>_*` | none | ankka's own. The issuers whose tokens identify a person |

- **D1.** With no broker set, the service starts, holds and returns records, and publishes nothing.
  It says so once in its log. `/graph/wait` then answers `503`.
- **D2.** With no graph database set, or one that cannot be reached, every route that reads it
  answers `503` and every other route is unaffected.
- **D3.** The service never writes to the graph database and creates nothing in it.
- **D4.** A setting is read once at start. Changing one is a restart.

## Writers

| The caller is | The writer is |
|---|---|
| another ankka service, by its certificate | `service:<project>/<name>` |
| the gateway, with a bearer token an accepted issuer signed | `person:<subject>@<issuer name>` |
| the gateway, with no token or one not accepted | refused, `401` |
| anything, when the service runs outside a cluster | `local` |

- **D5.** The writer is decided by the service from the connection and the token. Nothing in a
  request body or header names it.
- **D6.** Outside a cluster every caller is `local`, so "speaks for" refuses nobody there. That is
  for a laptop only.
- **D7.** A steward is matched on the whole writer string.

## The topic

| | |
|---|---|
| Name | `REASONING_GRAPH_TOPIC` |
| Records | `ankka.graph-delta.v1`, keyed by element key (`node:<id>`, `edge:<id>`) |
| Cleanup | `compact` |
| Partitions | 3 |
| `max.compaction.lag.ms` | `86400000` (one day) |

- **D8.** The topic is created by the pipeline, compacted, before the service first publishes. A
  topic that came into being uncompacted is reported by the operator and has to be recreated.
- **D9.** One day is the longest a withdrawn record's earlier delta may stay in the log. The
  service's own row is overwritten when the withdrawal is accepted, and the graph database's node
  when the sink applies the new delta.

## The pipeline

`deploy/pipeline/blueprint.conf`, for ankka-flow 0.3.0:

```hocon
blueprint {
  name = reasoning-graph
  streamlets {
    graph = builtin/neo4j-merge-sink
  }
  topics {
    reasoning-graph {
      topic.name = "reasoning-graph"
      consumers  = [graph.in]
      partitions = 3
      replicas   = 1
      consumer-config { auto.offset.reset = earliest }
      topic-config { max.compaction.lag.ms = 86400000 }
    }
  }
}
```

How a blueprint spells a topic setting is to be confirmed against ankka-flow 0.3.0 (V6 in
[../research.md](../research.md)); `topic-config` above stands for it. The sink reaches the graph
database through a Secret named in deploy-time configuration, as for any merge sink. Its consumer group is `reasoning-graph.graph.in`. Neo4j is 5.26 or later.

## The service descriptor

`deploy/service.json`:

```json
{
  "name": "reasoning",
  "service": {
    "image": "${IMAGE}",
    "runtime": "${ANKKA_VERSION}",
    "env": [
      { "name": "ANKKA_KAFKA_BOOTSTRAP_SERVERS", "value": "${KAFKA}" },
      { "name": "REASONING_NEO4J_URI", "value": "${NEO4J_URI}" },
      { "name": "REASONING_NEO4J_USERNAME", "secretKeyRef": { "name": "reasoning-graph", "key": "username" } },
      { "name": "REASONING_NEO4J_PASSWORD", "secretKeyRef": { "name": "reasoning-graph", "key": "password" } }
    ],
    "resources": { "instanceType": "small", "autoscaling": { "minInstances": 1 } }
  }
}
```

## On a laptop

`docker-compose.yml` runs what a cluster would supply:

| Service | Image | Reached at |
|---|---|---|
| `postgres` | `postgres:17-alpine`, with ankka's DDL from `target/ddl` | `localhost:5432` |
| `kafka` | `apache/kafka:3.9.1`, topics not created on first use | `localhost:9094` |
| `topic` | the Kafka image, run once: creates the topic compacted | |
| `neo4j` | `neo4j:5.26-community` | `localhost:7474`, `bolt://localhost:7687` |
| `sink` | `ghcr.io/thinkmorestupidless/ankka-flow-sidecar:0.3.0`, configured from `compose/flow-graph` and `compose/neo4j-secret` | metrics on `localhost:2051` |

## Rebuilding the graph database

On a cluster, as ankka-flow documents: scale the sink to zero, empty the database, `flow reset
reasoning-graph --streamlet graph`, scale it up. On a laptop: stop `sink`, empty the database,
reset the group `reasoning-graph.graph.in` to the earliest offset, start `sink`. The service is not
involved either way.

## What "gone" covers

Withdrawn text is gone from the service's row at once, from the graph database when the sink has
applied the delta, and from the topic within the compaction bound. It is not removed from a backup
of the database or the broker taken before the withdrawal, nor from Postgres's own storage until
its next vacuum of that row, which no query through the service or the database can read. A deployment that must meet a deadline for erasure sets its backup
retention accordingly.

## Properties a test holds

- The service starts and passes every scenario under `features/record` other than withdrawal with
  neither a broker nor a graph database set.
- With the graph database stopped, a write is accepted in the time it takes with it running.
- After a withdrawal, the service's database holds no row containing the withdrawn text, and a
  graph database rebuilt from the topic holds none of it.
- `deploy/service.json` and `deploy/pipeline/blueprint.conf` are valid: the descriptor decodes
  under ankka's rules and `flow verify` accepts the blueprint.
