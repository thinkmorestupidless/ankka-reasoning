# Run it on your machine

> Start the ankka-reasoning service on a laptop with Postgres, Kafka, the merge sink and Neo4j beside it, and check that a record reaches the graph.

Source: https://reasoning.ankka.cloud/get-started/run-locally/
This page takes a clone of the repository to a running service on `localhost:9000`, with everything a
cluster would put beside it running in Docker. It takes a few minutes, most of it the first build.

## What you need

JDK 21, [sbt](https://www.scala-sbt.org/), Docker and [`just`](https://just.systems/). ankka itself is
a library dependency and comes from Maven Central; nothing of ankka has to be installed.

One image has to be built by hand. The graph is filled by ankka-flow's merge sink, whose released
image `ghcr.io/thinkmorestupidless/ankka-flow-sidecar:0.3.0` cannot be pulled without a credential.
Build it from the tag it was released from and give it that name:

```bash
git clone --branch v0.3.0 https://github.com/thinkmorestupidless/ankka-flow
cd ankka-flow
sbt sidecar/Docker/publishLocal
docker tag ankka-flow-sidecar:0.3.0 ghcr.io/thinkmorestupidless/ankka-flow-sidecar:0.3.0
```

## Start what stands in for a cluster

`just up` writes ankka's database schema where Postgres will load it, then starts four containers:
Postgres for the service's records, Kafka with the delta topic created compacted, Neo4j, and the merge
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
`cleanup.policy=compact`, as the pipeline's operator does in a cluster. The topic must exist before the
service first publishes, because a topic that came into being on first use would not be compacted, and
compaction is what lets withdrawn text leave the log.

| Container | Reached at |
|---|---|
| `postgres` | `localhost:5432` |
| `kafka` | `localhost:9094` |
| `neo4j` | `http://localhost:7474`, `bolt://localhost:7687`, user `neo4j`, password `reasoning-local-password` |
| `sink` | metrics on `localhost:2051` |

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
changed. [Operate the service](../deploy/operate.md) rebuilds the graph database from the topic and
measures how long a record takes to reach it.
