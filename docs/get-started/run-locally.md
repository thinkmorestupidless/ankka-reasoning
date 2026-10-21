---
title: Run it on your machine
description: Start the ankka-reasoning service on a laptop with Postgres, Kafka, the graph sink and Neo4j beside it, and check that a record reaches the graph.
kind: tutorial
related: [get-started/first-explanation.md, build/golden-set.md, deploy/live.md, reference/configuration.md, deploy/operate.md]
---

# Run it on your machine

This page takes a clone of the repository to a running service on `localhost:9000`, with everything a
cluster would put beside it running in Docker. It takes a few minutes, most of it the first build.

## What you need

JDK 21, [sbt](https://www.scala-sbt.org/), Docker and [`just`](https://just.systems/). ankka itself is
a library dependency and comes from Maven Central; nothing of ankka has to be installed.

The graph is filled by ankka's graph sink, from ankka-contrib's image
`ghcr.io/thinkmorestupidless/ankka-graph-sink-neo4j:0.1.0`, which Docker pulls on first use. It is
built for `linux/amd64`; on Apple silicon Docker runs it under emulation.

## Start what stands in for a cluster

`just up` writes ankka's database schema where Postgres will load it, then starts four containers:
Postgres for the service's records, Kafka with the delta topic created compacted, Neo4j, and the graph
sink that copies the topic into Neo4j.

```bash
git clone https://github.com/thinkmorestupidless/ankka-reasoning
cd ankka-reasoning
just up
```

```text
 Container ankka-reasoning-postgres-1 Started
 Container ankka-reasoning-kafka-1 Healthy
 Container ankka-reasoning-topic-1 Exited
 Container ankka-reasoning-neo4j-1 Started
 Container ankka-reasoning-sink-1 Started
```

The `topic` container runs once and exits: it creates the topic `reasoning-graph` with
`cleanup.policy=compact`, as a project declares it in a cluster. The topic must exist before the
service first publishes, because a topic that came into being on first use would not be compacted, and
compaction is what lets withdrawn text leave the log.

| Container | Reached at |
|---|---|
| `postgres` | `localhost:5432` |
| `kafka` | `localhost:9094` |
| `neo4j` | `http://localhost:7474`, `bolt://localhost:7687`, user `neo4j`, password `reasoning-local-password` |
| `sink` | no port; its environment in `docker-compose.yml` names the topic and Neo4j, as its descriptor does in a cluster |

Where port 5432 is already taken, set `REASONING_POSTGRES_PORT` for both `just up` and `just run`:

```bash
export REASONING_POSTGRES_PORT=5433
```

## Start the service

`just run` starts the service on port 9000 against those containers. It runs on the host, so a change
to the code is a restart and not an image build.

```bash
just run
```

The service is up when it has started its graph consumers, one per kind of record:

```text
INFO  o.a.p.p.ProjectionBehavior$ - Starting projection [ProjectionId(ankka-consumer-claim-graph, 0-255)]
```

As it starts it also registers the appraiser, the blueprint it carries, with ankka's agent runtime,
and the holders and sources the appraiser writes with. A run of the appraiser needs a model and a
judgment, from `ANTHROPIC_API_KEY` and `TYPESAFE_API_KEY` in the environment of `just run`; without
them everything else works and a run fails at its first model call, naming the key.
[Live, against a real model](../deploy/live.md) has both.

## Check it

The vocabulary route needs nothing recorded and answers as soon as the service is up:

```bash
curl -s localhost:9000/graph/vocabulary | python3 -m json.tool | head -8
```

```json
{
    "layers": [
        {
            "name": "belief",
            "nodes": [
                {
                    "label": "Question",
                    "id": "question:<identifier>",
```

Register a source and wait for the graph to hold it. The wait ends when Neo4j has the record's node
and every edge it stated:

```bash
curl -s localhost:9000/sources -d '{"id":"a-source","name":"a source"}'
curl -s localhost:9000/graph/wait -d '{"kind":"source","id":"a-source"}'
```

```json
{"caughtUp":true,"waitedMs":1573,"missing":[]}
```

`caughtUp` is `true` within a few seconds. If it is `false`, the record is still held by the service
and `missing` names what the graph lacks; the usual cause is that the `sink` container is not running,
which `docker compose logs sink` will show.

Outside a cluster every caller is taken to be the writer `local`, so nothing here needs a credential.
[Writers, holders and stewards](../concepts/writers-and-holders.md) says who a caller is in a cluster.

## Stop it

Stop the service with Ctrl-C. `just down` stops the containers and removes their volumes, so the next
`just up` starts with nothing recorded.

```bash
just down
```

## Where to go from here

[Your first explanation](first-explanation.md) records the launch example and asks why a belief
changed. [The golden set](../build/golden-set.md) has the appraiser read eight documents and asks
the same question with the runs behind the answer. [Operate the service](../deploy/operate.md)
rebuilds the graph database from the topic and measures how long a record takes to reach it.
