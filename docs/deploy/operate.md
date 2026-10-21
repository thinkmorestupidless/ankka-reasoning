---
title: Operate the service
description: Rebuild the graph database from the topic, measure how long a record takes to reach the graph, know what withdrawing text removes and when, and read the service's failures.
kind: guide
related: [deploy/deploy-on-ankka.md, deploy/live.md, concepts/architecture.md, build/withdraw-text.md, build/golden-set.md, reference/configuration.md]
---

# Operate the service

The service keeps its records in its own database and publishes a copy to the graph. Operating it is
mostly knowing which of the two a symptom belongs to, and that the copy can always be made again.

## What is kept where

| Store | Holds | If it is lost |
|---|---|---|
| the service's Postgres database | every record; the only place a rule is checked against; and ankka's own records of the blueprint, its versions and its runs | the records are lost; back it up as any ankka service's database |
| the delta topic `reasoning-graph` | the latest delta for every node and edge, compacted | the graph can no longer be rebuilt, though the records are intact |
| the graph database, Neo4j | a copy of the topic | nothing: rebuild it |

The service never writes to the graph database and creates nothing in it. The graph sink is its only
writer, and keeps no state of its own: the topic is where it starts from.

## Rebuild the graph database

The graph database can be emptied and filled again from the topic alone. The service is not involved
and keeps taking writes throughout; answers are unavailable or partial until the sink has caught up.

On a laptop, `just rebuild` does it:

```bash
just rebuild
```

It stops the sink, deletes every node, raises the sink's version by one, and starts the sink again. A
sink at a new version reads the topic from its start. The version is kept in `.env` as
`REASONING_SINK_VERSION`, which `docker-compose.yml` passes to the sink as `ANKKA_GRAPH_SINK_VERSION`:

```text
rebuild: the sink reads the topic again at version 2
```

With the launch example recorded, the graph held its 12 nodes and 18 edges again within three seconds,
and the answer to `/answers/belief` asked before and after was the same, byte for byte.

On a cluster the steps are the same: empty the database, or point the sink at an empty one, and apply
`deploy/graph-sink.json` again with `ANKKA_GRAPH_SINK_VERSION` one higher. ankka's
[Fill a graph store](https://docs.ankka.cloud/deploy/graph-sink/#rebuild-the-store-from-the-topic)
describes it.

A rebuilt graph holds the latest state of every element and nothing older, so text withdrawn before
the rebuild is not in it. The process layer is rebuilt with the rest: after the golden set's four
runs, the rebuilt graph answered what a run wrote, and the comparison of the two holders with the
acts behind each, the same as before.

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

`just measure-golden-set` times the appraiser's four runs over [the golden set](../build/golden-set.md)
the same way: each run, each record it wrote from the run's end to the graph holding the record and
its act, and the whole set to the two holders compared from the graph. It needs a service with a
model; with the scripted one the build uses, nine in ten records were in the graph with their acts
within 1.6 seconds of their run's end and the whole set took 8.5 seconds. It exits `1` past five
seconds for nine in ten or two minutes for the whole.

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
| writes succeed, `/graph/wait` reports `caughtUp: false` | the sink: its logs, and whether it can reach Kafka and Neo4j; on a cluster `ankka services get reasoning-graph-sink` shows how far behind it is and which delta it is failing on |
| `/answers` routes answer `503` with `graph.unavailable` | whether the service can reach Neo4j; a read gives up after five seconds |
| answers are missing recent records | the sink is behind; `/graph/wait` on the record says what is missing |
| the service logs `no broker is set (ANKKA_KAFKA_BOOTSTRAP_SERVERS), so the graph is not published` once at start | no broker is configured; records are held and nothing is published |
| a run ends `failed` and its `reason` names a key | `ANTHROPIC_API_KEY` or `TYPESAFE_API_KEY` is not set for the service; [Live, against a real model](live.md) |
| a run ends `failed` and its `reason` names a claim | the critique found a claim that does not derive from a document of the tranche; the records before the verdict are held with their acts, and the belief was not revised |
| a run has ended and `GET /process/runs/{run}` lists fewer acts than it wrote | the acts' view is a moment behind; read it again |

With no broker set the service still starts, holds records and returns them, and `/graph/wait`
answers `503`. With no graph database set, or one that cannot be reached, every route that reads it
answers `503` and every other route is unaffected.
