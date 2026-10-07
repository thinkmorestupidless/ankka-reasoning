---
title: Deploy on ankka
description: Deploy the pipeline that fills the graph database, then the ankka-reasoning service, to an ankka installation with ankka-flow, Kafka and Neo4j, and know which of these steps is unproven.
kind: guide
related: [concepts/architecture.md, deploy/operate.md, reference/configuration.md, reference/limitations.md]
---

# Deploy on ankka

ankka-reasoning deploys as two things: an [ankka-flow](https://flow.ankka.cloud/) pipeline that is the
Neo4j merge sink alone, and one [ankka](https://docs.ankka.cloud/) service, `reasoning`. The pipeline
has to exist before the service publishes anything, because it creates the topic the service publishes
to, compacted.

The order on this page is the one that works: the project, then the service, then the pipeline, then
the secrets. The service goes before the pipeline because ankka creates a project's namespace when the
first service is applied to it, and the pipeline and the secrets go in that namespace; the service's
pod waits for its secret and publishes nothing until it starts. These steps were run against a local
kind installation of ankka and ankka-flow, with the image and descriptor of release 0.1.1, and the
launch example recorded and explained through them. They have not been run against a cloud
installation.

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

## Create the project and apply the service

```bash
ankka projects create reasoning -O <organization>
ankka services apply -f ankka-reasoning-<version>-service.json -p reasoning
```

The descriptor is the one attached to the release, with the broker and the graph database's address
written in: [Get the image and the descriptor](#get-the-image-and-the-descriptor) says how. Applying
it creates the namespace `ankka-reasoning`. The pod does not start yet: its secret does not exist.

## Deploy the pipeline

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

Verify, then generate the resource into a file and apply it into the project's namespace:

```bash
flow verify deploy/pipeline/blueprint.conf --conf deploy/pipeline/kind.conf
flow generate deploy/pipeline/blueprint.conf --conf deploy/pipeline/kind.conf -n ankka-reasoning -o pipeline.yaml
kubectl apply -f pipeline.yaml
kubectl -n ankka-reasoning get aflow
```

```text
NAME              PIPELINE          PHASE   AGE
reasoning-graph   reasoning-graph   Ready   25s
```

The file, not a pipe: `flow` 0.4.0 prints its note about the topic on standard output when it writes
the resource there too, and `kubectl` then refuses the stream for an unknown field `note`. The sink's
consumer group is `reasoning-graph.graph.in`.

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

## Create the secrets

The sink reaches Neo4j through the Secret the deploy-time configuration names, which has to be in the
project's namespace too: on a local cluster, a copy of `neo4j-local` from the namespace ankka-flow
created it in. The service reads the graph database with credentials from a Secret of its own,
`reasoning-graph`. It only ever reads, so give it a Neo4j user that can do nothing else.

```bash
kubectl -n ankka-reasoning create secret generic reasoning-graph \
  --from-literal=username=<neo4j user> \
  --from-literal=password=<its password>
ankka services list -p reasoning
```

```text
NAME       STATUS  INSTANCES  GEN  IMAGE                                              HOSTNAME
reasoning  Ready   1/1        1    ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.1  -
```

The service is Ready about a minute after its secret exists. It needs nothing else set. Its database and its HTTP port are ankka's, supplied by the
platform. [Configuration](../reference/configuration.md) lists every setting, among them
`REASONING_STEWARDS`, the writers who may withdraw the text of any record.

## Check it from another service

Every port in an ankka installation is mutual TLS, so the service can be reached only by the gateway
or by another ankka workload presenting the certificate the installation issued it. Another ankka
service reaches it at `https://reasoning.ankka-reasoning.svc.cluster.local:9000`, as ankka documents
under [Calling services](https://docs.ankka.cloud/build/calling-services/), and is the writer
`service:<its project>/<its name>`: the identity in its certificate, which nothing in a request can
claim. A `curl` from inside another service's pod, with that pod's service certificate:

```bash
curl -s --cacert ca.crt --cert tls.crt --key tls.key \
  https://reasoning.ankka-reasoning.svc.cluster.local:9000/sources -d '{"id":"cluster-source","name":"a source"}'
curl -s --cacert ca.crt --cert tls.crt --key tls.key \
  https://reasoning.ankka-reasoning.svc.cluster.local:9000/graph/wait -d '{"kind":"source","id":"cluster-source"}'
```

```json
{"id":"cluster-source","name":"a source","dated":"2026-10-07T06:21:00.009Z","recordedAt":"2026-10-07T06:21:00.009Z","writer":"service:shoppingcart/cart"}
{"caughtUp":true,"waitedMs":3198,"missing":[]}
```

A wait that answers `caughtUp: true` has crossed the service, the installation's Kafka, the sink and
Neo4j. Exposed with `ankka services expose`, the service answers a caller through the gateway with
`401`, because a person's token is not verified yet.

## What each failure looks like

| Symptom | Cause |
|---|---|
| every write answers `401` | the caller came through the gateway; call from another ankka service |
| `/graph/wait` answers `503` with `graph.unavailable` | no broker or no graph database is set for the service |
| `/graph/wait` answers `caughtUp: false` and names what is missing | the sink is not running, or cannot reach Neo4j; the record is held all the same |
| every `/answers` route answers `503` | the service cannot reach Neo4j with the address and credentials it was given |
| the operator reports the topic is not compacted | the topic existed before the pipeline was deployed; delete it and let the pipeline create it |
