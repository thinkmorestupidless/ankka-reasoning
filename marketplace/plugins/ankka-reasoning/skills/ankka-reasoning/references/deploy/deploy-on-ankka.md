# Deploy on ankka

> Deploy the graph sink that fills the graph database, then the ankka-reasoning service, to an ankka installation with Kafka and Neo4j, let people write through the gateway, and know which of these steps is unproven.

Source: https://reasoning.ankka.cloud/deploy/deploy-on-ankka/
ankka-reasoning deploys as two services in one [ankka](https://docs.ankka.cloud/) project: `reasoning`,
the service, and `reasoning-graph-sink`, ankka's
[graph sink](https://docs.ankka.cloud/deploy/graph-sink/) from ankka-contrib's image
`ghcr.io/thinkmorestupidless/ankka-graph-sink-neo4j`, which reads the delta topic into Neo4j. The
topic is declared on the project, compacted, before anything publishes to it.

The order on this page is: the project, the topic, the secret, the sink, the service. These steps
have not been run on an installation, local or cloud; the descriptors decode under ankka's own rules,
and the same image and variables run beside the service on a laptop, as
[Run it on your machine](../get-started/run-locally.md) shows.

## What the installation must have

- An ankka installation, with the `ankka` CLI logged in to it.
- A Kafka broker the project's services can reach, as the installation provides.
- Neo4j 5.26 or later, reachable from the project.

## Create the project and declare the topic

```bash
ankka projects create reasoning -O <organization>
ankka projects topics set reasoning-graph --partitions 3 --cleanup compact --max-compaction-lag 1d -p reasoning
```

The topic is declared compacted so that the broker keeps the latest delta under every element's key,
which is what lets the graph be rebuilt from it and lets [withdrawn text](../build/withdraw-text.md)
leave the log. A topic that came into being on first use would not be compacted. The maximum
compaction lag, one day, is the longest a superseded delta, and the text it carried, may stay in the
log; left out, it is `none`, and nothing bounds it.

## Create the secret

The sink writes to Neo4j and the service reads it, both with credentials from the project secret
`reasoning-graph`. The service only ever reads, so where the two can have different users, give the
service's one that can do nothing else.

```bash
ankka projects secrets set reasoning-graph username=<neo4j user> password=- -p reasoning
```

`password=-` reads the value from standard input, so it is not left in the shell's history.

## Get the image and the descriptors

Each release publishes the service's image to `ghcr.io/thinkmorestupidless/ankka-reasoning:<version>`
and attaches two files to its page on GitHub: `ankka-reasoning-<version>-service.json`, the service
descriptor naming that image and the ankka version it was built against, and
`ankka-reasoning-<version>-graph-sink.json`, the sink's descriptor naming the sink image the release
was tested with. The broker and the graph database's address are left in both as placeholders, for the
deployment to fill in.

To build them from a checkout:

```bash
just images
REASONING_DEPLOY_KAFKA=<broker host:port> REASONING_DEPLOY_NEO4J_URI=<bolt address> just descriptors
```

`just images` publishes the image `ankka-reasoning:<version>` to the local Docker daemon; set
`DOCKER_REPOSITORY` to prefix it with a registry, and push it wherever the cluster pulls from.
`just descriptors` writes `target/deploy/service.json` and `target/deploy/graph-sink.json` with the
images, the ankka version, the broker and the graph database's address filled in. A value that is not
set is left in the file as a placeholder and the command says so.

## Apply the sink

The sink's descriptor, before it is filled in:

```json
{
  "name": "reasoning-graph-sink",
  "service": {
    "image": "${SINK_IMAGE}",
    "http": false,
    "database": "none",
    "env": [
      { "name": "ANKKA_GRAPH_SINK_TOPIC", "value": "reasoning-graph" },
      { "name": "ANKKA_GRAPH_SINK_VERSION", "value": "1" },
      { "name": "NEO4J_URI", "value": "${NEO4J_URI}" },
      { "name": "NEO4J_USERNAME", "secretKeyRef": { "name": "reasoning-graph", "key": "username" } },
      { "name": "NEO4J_PASSWORD", "secretKeyRef": { "name": "reasoning-graph", "key": "password" } },
      { "name": "NEO4J_DATABASE", "value": "neo4j" }
    ]
  }
}
```

```bash
ankka services apply -f ankka-reasoning-<version>-graph-sink.json -p reasoning
ankka services get reasoning-graph-sink -p reasoning
```

The sink serves no HTTP and keeps no state of its own, so nothing is provisioned for it: the topic is
where it starts from. Its status shows how far behind the topic it is and, while a delta is being
refused, which. Raising `ANKKA_GRAPH_SINK_VERSION` by one and applying it again reads the topic from
its start; [Operate the service](operate.md) uses that to rebuild the graph database.

## Apply the service

The service's descriptor, before it is filled in:

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

```bash
ankka services apply -f ankka-reasoning-<version>-service.json -p reasoning
ankka services list -p reasoning
```

It needs nothing else set. Its database and its HTTP port are ankka's, supplied by the platform.
[Configuration](../reference/configuration.md) lists every setting, among them `REASONING_STEWARDS`,
the writers who may withdraw the text of any record.

## Let people write through the gateway

With nothing more set, the service's writers are the other services of the installation and the
machines registered on its organizations, and a caller through the gateway is refused with `401`. To
let people write, list the identity providers whose tokens the service accepts, in the service
descriptor's `env`:

```json
{ "name": "ANKKA_AUTH_ISSUERS", "value": "staff" },
{ "name": "ANKKA_AUTH_STAFF_ISSUER", "value": "https://auth.example.com/realms/staff" },
{ "name": "ANKKA_AUTH_STAFF_JWKS_URL", "value": "https://auth.example.com/realms/staff/protocol/openid-connect/certs" },
{ "name": "ANKKA_AUTH_STAFF_AUDIENCE", "value": "reasoning" }
```

and expose the service with `ankka services expose`. A person whose token that issuer signed is the
writer `person:<subject>@staff`. A list with a mistake in it stops the service from starting, naming
every problem. [Writers, holders and stewards](../concepts/writers-and-holders.md) says who each
caller is taken to be.

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

The first answers with the source and the writer it was held as. A wait that answers `caughtUp: true`
has crossed the service, the installation's Kafka, the sink and Neo4j.

## What each failure looks like

| Symptom | Cause |
|---|---|
| every write through the gateway answers `401` | the service lists no issuer, or the token is not one a listed issuer signed for the audience; call from another ankka service, or see [Let people write through the gateway](#let-people-write-through-the-gateway) |
| the service does not start and names the issuers | `ANKKA_AUTH_ISSUERS` or one of its issuer's variables is missing or malformed |
| `/graph/wait` answers `503` with `graph.unavailable` | no broker or no graph database is set for the service |
| `/graph/wait` answers `caughtUp: false` and names what is missing | the sink is not running, is behind, or cannot reach Neo4j; `ankka services get reasoning-graph-sink` says which. The record is held all the same |
| every `/answers` route answers `503` | the service cannot reach Neo4j with the address and credentials it was given |
| withdrawn text stays in the log past a day | the topic was not declared compacted before the service first published |
