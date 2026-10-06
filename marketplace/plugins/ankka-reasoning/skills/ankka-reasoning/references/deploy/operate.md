# Operate the service

> Rebuild the graph database from the topic, measure how long a record takes to reach the graph, know what withdrawing text removes and when, and read the service's failures.

Source: https://reasoning.ankka.cloud/deploy/operate/
The service keeps its records in its own database and publishes a copy to the graph. Operating it is
mostly knowing which of the two a symptom belongs to, and that the copy can always be made again.

## What is kept where

| Store | Holds | If it is lost |
|---|---|---|
| the service's Postgres database | every record; the only place a rule is checked against | the records are lost; back it up as any ankka service's database |
| the delta topic `reasoning-graph` | the latest delta for every node and edge, compacted | the graph can no longer be rebuilt, though the records are intact |
| the graph database, Neo4j | a copy of the topic | nothing: rebuild it |

The service never writes to the graph database and creates nothing in it. The merge sink is its only
writer.

## Rebuild the graph database

The graph database can be emptied and filled again from the topic alone. The service is not involved
and keeps taking writes throughout; answers are unavailable or partial until the sink has caught up.

On a laptop, `just rebuild` does it:

```bash
just rebuild
```

It stops the sink, deletes every node, resets the sink's consumer group to the start of the topic, and
starts the sink:

```bash
docker compose stop sink
docker compose exec -T neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (n) CALL (n) { DETACH DELETE n } IN TRANSACTIONS OF 10000 ROWS"
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
  --group reasoning-graph.graph.in --reset-offsets --to-earliest --all-topics --execute
docker compose start sink
```

With 3,752 nodes and 11,350 edges on a laptop the graph was whole again in about six seconds, and an
explanation asked before and after was the same, byte for byte.

On a cluster the steps are ankka-flow's, on its page
[Rebuild a graph](https://flow.ankka.cloud/deploy/rebuild-a-graph/): scale the sink to zero, empty the
database, `flow reset reasoning-graph --streamlet graph`, scale it up.

A rebuilt graph holds the latest state of every element and nothing older, so text withdrawn before
the rebuild is not in it.

## How long a record takes to reach the graph

A record is in the graph a few seconds after its write is answered. The floor is a poll: the graph
consumers learn of a changed record by asking the database once a second.

`just measure` records the seeded set of 555 records under identifiers of its own, times each from its
`201` to the graph holding its node and edges, then times an explanation over a question grown to a
thousand records:

```bash
just measure
```

```text
run mtmhfv9 against http://localhost:9000
time to the graph over 555 records: median 2419 ms, nine in ten within 3778 ms, slowest 4376 ms (target: five seconds, nine in ten)
time to explain a belief's change over a question of 1002 records, asked 20 times: median 14 ms, slowest 46 ms (target: one second)
```

Those figures are from a laptop with everything in Docker, under a burst of all 555 writes in about
fifteen seconds. The command exits `1` when nine in ten records take longer than five seconds or an
explanation takes longer than one, so it can be a check after a change.

A writer that must read its own write from the graph does not sleep; it asks `POST /graph/wait`.

## What withdrawing text removes, and when

Withdrawn text is gone from the service's database at once, from the graph database when the sink has
applied the new delta, and from the topic when the broker has compacted the element's key, which the
topic's `max.compaction.lag.ms` bounds at one day as deployed.

It is not removed from a backup of the database or the broker taken before the withdrawal, nor from
Postgres's own storage until that row is next vacuumed, which no query through the service or the
database can read. A deployment that must meet a deadline for erasure sets its backup retention and
the topic's compaction bound to fit it. [Withdraw text](../build/withdraw-text.md) is the request.

## Stewards

A steward may withdraw the text of any record. Set `REASONING_STEWARDS` to the writers who may, as
whole writer strings separated by commas, and restart: a setting is read once at start.

```bash
REASONING_STEWARDS=service:compliance/steward
```

## Reading failures

Each failure belongs to one store, and a write is affected only by the first.

| Symptom | Where to look |
|---|---|
| writes fail or time out | the service and its database: nothing else is on a write's path |
| writes succeed, `/graph/wait` reports `caughtUp: false` | the sink: its logs, and whether it can reach Kafka and Neo4j |
| `/answers` routes answer `503` with `graph.unavailable` | whether the service can reach Neo4j; a read gives up after five seconds |
| answers are missing recent records | the sink is behind; `/graph/wait` on the record says what is missing |
| the service logs `no broker is set (ANKKA_KAFKA_BOOTSTRAP_SERVERS), so the graph is not published` once at start | no broker is configured; records are held and nothing is published |

With no broker set the service still starts, holds records and returns them, and `/graph/wait`
answers `503`. With no graph database set, or one that cannot be reached, every route that reads it
answers `503` and every other route is unaffected.
