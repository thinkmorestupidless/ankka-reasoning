# Deploy on ankka

> Deploy the pipeline that fills the graph database, then the ankka-reasoning service, to an ankka installation with ankka-flow, Kafka and Neo4j, and know which of these steps is unproven.

Source: https://reasoning.ankka.cloud/deploy/deploy-on-ankka/
ankka-reasoning deploys as two things: an [ankka-flow](https://flow.ankka.cloud/) pipeline that is the
Neo4j merge sink alone, and one [ankka](https://docs.ankka.cloud/) service, `reasoning`. The pipeline
goes first, because it creates the topic the service publishes to.

These steps have not been run end to end against a cluster. The blueprint is accepted by `flow verify`
and the service descriptor passes the same validation `ankka services apply` applies, both checked by
the repository's tests, but no deployment through them has been made. The steps are the ones ankka and
ankka-flow document for any service and any pipeline; treat the first deployment as a test of this
page. [Limitations](../reference/limitations.md) says the same.

## What the installation must have

- An ankka installation, with the `ankka` CLI logged in to it.
- ankka-flow [installed](https://flow.ankka.cloud/deploy/install/) on the same cluster, with the
  `flow` CLI.
- A Kafka broker both can reach.
- Neo4j 5.26 or later, and a Kubernetes Secret holding its connection, as the
  [merge sink](https://flow.ankka.cloud/build/graph-sink/) requires.

The service's writers in a cluster are other ankka services in the installation. A caller through the
gateway is refused with `401`, so there is nothing to gain from exposing the service yet;
[Writers, holders and stewards](../concepts/writers-and-holders.md) says why.

## Deploy the pipeline first

The blueprint is
[`deploy/pipeline/blueprint.conf`](https://github.com/thinkmorestupidless/ankka-reasoning/blob/main/deploy/pipeline/blueprint.conf):
one streamlet, the built-in merge sink, reading one topic the pipeline owns.

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
      topic { max.compaction.lag.ms = 86400000 }
    }
  }
}
```

The topic is the pipeline's own so that ankka-flow's operator creates it, compacted, before anything
is published. ankka creates no topics, and a topic that came into being on first use would not be
compacted. `max.compaction.lag.ms` is the longest a superseded delta may stay in the log, which is the
bound on how long [withdrawn text](../build/withdraw-text.md) can remain on the topic: one day.

The sink reaches Neo4j through a Secret named in deploy-time configuration. For a local cluster where
ankka-flow's own `just neo4j-up` created the Secret `neo4j-local`, that file is
[`deploy/pipeline/kind.conf`](https://github.com/thinkmorestupidless/ankka-reasoning/blob/main/deploy/pipeline/kind.conf):

```hocon
flow.streamlets.graph.config { secret = neo4j-local }
```

Verify, then generate the resource and apply it into the namespace of the project the service will
run in:

```bash
flow verify deploy/pipeline/blueprint.conf --conf deploy/pipeline/kind.conf
flow generate deploy/pipeline/blueprint.conf --conf deploy/pipeline/kind.conf -n ankka-reasoning \
  | kubectl apply -f -
```

The sink's consumer group is `reasoning-graph.graph.in`.

## Get the image and the descriptor

Each release publishes the service's image to `ghcr.io/thinkmorestupidless/ankka-reasoning:<version>`
and attaches two files to its page on GitHub: `ankka-reasoning-<version>-service.json`, the service
descriptor naming that image and the ankka version it was built against, and
`ankka-reasoning-<version>-blueprint.conf`, the pipeline's blueprint. No version has been released
yet, so for now both are built from the repository.

To build them from a checkout:

```bash
just images
REASONING_DEPLOY_KAFKA=<broker host:port> REASONING_DEPLOY_NEO4J_URI=<bolt address> just descriptors
```

`just images` publishes the image `ankka-reasoning:<version>` to the local Docker daemon; set
`DOCKER_REPOSITORY` to prefix it with a registry, and push it wherever the cluster pulls from.
`just descriptors` writes `target/deploy/service.json` with the image, the ankka version the service
was built against, the broker and the graph database's address filled in. A value that is not set is
left in the file as a placeholder and the command says so; the descriptor a release attaches has the
broker and the graph database left that way, for the deployment to fill in.

The descriptor, before it is filled in:

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

## Create the secret and deploy the service

The service reads the graph database with credentials from a Secret, `reasoning-graph`, in the
project's namespace. It only ever reads, so give it a Neo4j user that can do nothing else.

```bash
ankka projects create reasoning -O <organization>
kubectl -n ankka-reasoning create secret generic reasoning-graph \
  --from-literal=username=<neo4j user> \
  --from-literal=password=<its password>
ankka services apply -f target/deploy/service.json -p reasoning
```

The service needs nothing else set. Its database and its HTTP port are ankka's, supplied by the
platform. [Configuration](../reference/configuration.md) lists every setting, among them
`REASONING_STEWARDS`, the writers who may withdraw the text of any record.

## Check it from another service

Another ankka service in the installation reaches it by name, as ankka documents under
[Calling services](https://docs.ankka.cloud/build/calling-services/), and is the writer
`service:<its project>/<its name>`. The first thing to ask is the vocabulary, which needs nothing
recorded:

```bash
curl -s http://reasoning.ankka-reasoning.svc.cluster.local/graph/vocabulary
```

Then register a source and wait for it, as [Run it on your machine](../get-started/run-locally.md)
does. A wait that answers `caughtUp: true` has crossed the service, Kafka, the sink and Neo4j.

## What each failure looks like

| Symptom | Cause |
|---|---|
| every write answers `401` | the caller came through the gateway; call from another ankka service |
| `/graph/wait` answers `503` with `graph.unavailable` | no broker or no graph database is set for the service |
| `/graph/wait` answers `caughtUp: false` and names what is missing | the sink is not running, or cannot reach Neo4j; the record is held all the same |
| every `/answers` route answers `503` | the service cannot reach Neo4j with the address and credentials it was given |
| the operator reports the topic is not compacted | the topic existed before the pipeline was deployed; delete it and let the pipeline create it |
