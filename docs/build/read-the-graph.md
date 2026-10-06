---
title: Read the graph
description: Query the graph database directly with Cypher, using the vocabulary's labels, identifiers and edges, filter by either of a record's two times, and know when the graph has caught up with a write.
kind: guide
layers: [belief, market]
related: [reference/graph-vocabulary.md, build/ask-for-explanations.md, concepts/architecture.md, concepts/layers.md]
---

# Read the graph

Every record the service holds is published to a graph database, and that database is open to any
reader with a Cypher client. The routes on [Ask for explanations](ask-for-explanations.md) are six
queries the service runs for a caller; a question they do not answer is a query a reader writes. This
guide shows what is in the graph and how to ask it, against Neo4j as
[Run it on your machine](../get-started/run-locally.md) starts it:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password "<query>"
```

Every query on this page was run that way. The records are the question `launch` with two holders and
a market, as [Record reasoning](record-reasoning.md) and [Markets and resolutions](markets.md) leave
them.

## The service never writes to the graph

The graph database is for reading. The service publishes each record as graph deltas to a Kafka topic,
ankka-flow's merge sink applies them to the graph database, and the service connects to the database
only to read. It creates nothing there, and no rule about whether a record may be held is ever decided
from the graph. Two things follow for a reader:

- The graph is a moment behind the service. A record is held as soon as its write is answered and is in
  the graph a second or so later.
- The graph can be emptied and rebuilt from the topic at any time, and holds the same nodes and edges
  afterwards. Nothing a reader adds to it survives that, so a reader does not write to it either.

## A node is a record

Every record is a node with two labels: `Element`, which every node the sink writes has, and the kind
of record. The node's `id` is the kind's prefix, a colon and the record's identifier.

| Record | Label | `id` |
|---|---|---|
| question | `Question` | `question:<question>` |
| hypothesis | `Hypothesis` | `hypothesis:<question>/<hypothesis>` |
| holder | `Holder` | `holder:<holder>` |
| source | `Source` | `source:<source>` |
| evidence | `Evidence` | `evidence:<digest>` |
| claim | `Claim` | `claim:<claim>` |
| belief revision | `BeliefRevision` | `revision:<revision>` |
| market | `Market` | `market:<market>` |
| resolution | `Resolution` | `resolution:<market>/<resolution>` |

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (r:BeliefRevision {id: 'revision:agent-a.launch.2'}) RETURN properties(r)"
```

```text
properties(r)
{dated: 1778490000000, id: "revision:agent-a.launch.2", _version: 2, probability: 0.61, recordedAt: 1791288678567, n: 2}
```

A node carries the record's own properties (`probability` and `n` for a revision), the two times every
record has, `dated` and `recordedAt`, and two properties the sink owns, `id` and `_version`. Who wrote
a record is not published. [Graph vocabulary](../reference/graph-vocabulary.md) lists the properties of
every kind.

The two times are whole milliseconds since the epoch, so that they compare as numbers. Cypher turns
them into instants:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (r:Element {id: 'revision:agent-a.launch.2'}) RETURN labels(r), r.probability, r.n, datetime({epochMillis: r.dated}) AS dated, datetime({epochMillis: r.recordedAt}) AS recordedAt, r._version"
```

```text
labels(r), r.probability, r.n, dated, recordedAt, r._version
["Element", "BeliefRevision"], 0.61, 2, 2026-05-11T09:00Z, 2026-10-06T12:11:18.567Z, 2
```

## Start a query from a node's id

A query that starts from one record matches it as `(:Element {id: ...})`. The sink creates one
constraint, that `id` is unique among `Element` nodes, and the index behind it is the only index on a
property:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "SHOW CONSTRAINTS YIELD name, type, labelsOrTypes, properties"
```

```text
name, type, labelsOrTypes, properties
"element_id", "UNIQUENESS", ["Element"], ["id"]
```

Matching on `Element` finds the node through that index. Matching the same `id` on the kind's label
alone, `(:BeliefRevision {id: ...})`, reads every node of that kind and filters them. The result is the
same and on a small graph so is the time; on a large one it is not. Each of the service's own answers
is a walk that starts from a node matched on `Element` and its `id`.

## An edge is a link one record stated

Every link between two records is an edge from the record that stated it to the record it names, which
was held first. Edges therefore always point backwards in time, and the graph has no cycles.

| From | Edge | To |
|---|---|---|
| `Hypothesis` | `ANSWERS` | `Question` |
| `Evidence` | `FROM_SOURCE` | `Source` |
| `Claim` | `STATED_BY` | `Holder` |
| `Claim` | `DERIVES_FROM` | `Evidence` |
| `Claim` | `SUPPORTS`, `CONTRADICTS` | `Hypothesis` |
| `Claim` | `REVISES` | `Claim` |
| `BeliefRevision` | `HELD_BY` | `Holder` |
| `BeliefRevision` | `BELIEF_IN` | `Hypothesis` |
| `BeliefRevision` | `RESTS_ON` | `Claim` |
| `BeliefRevision` | `FOLLOWS` | `BeliefRevision` |
| `Market` | `ABOUT` | `Question` |
| `Market` | `OFFERS` | `Hypothesis` |
| `Market` | `SPEAKS_AS` | `Holder` |
| `Resolution` | `RESOLUTION_OF` | `Market` |
| `Resolution` | `RESOLVES_TO` | `Hypothesis` |
| `Resolution` | `ON_EVIDENCE` | `Evidence` |
| `Resolution` | `REVISES_RESOLUTION` | `Resolution` |

Three kinds of edge carry a property: `RESTS_ON` the `weight` the holder gave the claim, when it gave
one, and `OFFERS` and `RESOLVES_TO` the `outcome` name.

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (r:Element {id: 'revision:agent-b.launch.1'})-[o:RESTS_ON]->(c:Claim) RETURN c.id, o.weight, properties(o)"
```

```text
c.id, o.weight, properties(o)
"claim:launch.tooling", 0.6, {weight: 0.6, _version: 1, id: "RESTS_ON|revision:agent-b.launch.1|claim:launch.tooling"}
"claim:launch.barrier-gone", 0.4, {weight: 0.4, _version: 1, id: "RESTS_ON|revision:agent-b.launch.1|claim:launch.barrier-gone"}
```

An edge has an `id` and a `_version` of its own, the sink's as on a node. An edge is published by the
record it leaves, and by no other, so an edge never changes unless that record does.

## Trace a belief to its sources

The trace from a belief revision to where its reasons came from is one path: the claims it rests on,
the evidence each derives from, and the source of each piece.

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (r:Element {id: 'revision:agent-a.launch.2'})-[:RESTS_ON]->(c:Claim)-[:DERIVES_FROM]->(e:Evidence)-[:FROM_SOURCE]->(s:Source) RETURN c.statement, e.excerpt, s.name"
```

```text
c.statement, e.excerpt, s.name
"the approval barrier has gone", "Notice 1187: Product Y is approved for sale.", "the regulator"
```

Every revision that rests on a claim reaches a source this way, because a claim cannot be stated
without evidence and evidence cannot be recorded without a source. A revision that rests on nothing, a
market's price for one, returns no rows.

## Find what is current

Nothing in the graph is marked current, because marking it would mean changing a node when a later
record arrives. A belief's current revision is the one no revision follows, and a claim is a revised
claim when another claim revises it. Each holder's current belief in `launch/yes`:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (y:Element {id: 'hypothesis:launch/yes'})<-[:BELIEF_IN]-(r:BeliefRevision)-[:HELD_BY]->(h:Holder) WHERE NOT EXISTS { (:BeliefRevision)-[:FOLLOWS]->(r) } RETURN h.id, h.kind, r.probability ORDER BY r.probability DESC"
```

```text
h.id, h.kind, r.probability
"holder:agent-a", "agent", 0.61
"holder:market.launch-market", "market", 0.55
"holder:agent-b", "agent", 0.43
```

The market is one of the holders, of kind `market`, and its price is a revision like the others. Every
current belief that rests on a claim since revised:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (later:Claim)-[:REVISES]->(c:Claim)<-[:RESTS_ON]-(r:BeliefRevision)-[:HELD_BY]->(h:Holder) WHERE NOT EXISTS { (:BeliefRevision)-[:FOLLOWS]->(r) } RETURN h.id, c.id, later.id"
```

```text
h.id, c.id, later.id
"holder:agent-b", "claim:launch.tooling", "claim:launch.tooling-ordered"
```

## Ask about a past time

The graph is read as it stood at a past time by filtering on the two times each node carries. A record
counts when `dated` is at or before the time asked about, and "current" becomes "the last that counts".
agent-a's belief as of 6 May:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (y:Element {id: 'hypothesis:launch/yes'})<-[:BELIEF_IN]-(r:BeliefRevision)-[:HELD_BY]->(:Element {id: 'holder:agent-a'}) WHERE r.dated <= datetime('2026-05-06T00:00:00Z').epochMillis RETURN r.id, r.probability ORDER BY r.n DESC LIMIT 1"
```

```text
r.id, r.probability
"revision:agent-a.launch.1", 0.38
```

Filtering on `recordedAt` asks what had been entered by a time, whatever date it carries. The claims
about `launch/yes` dated by 9 May and entered by 12:12:00 on 6 October:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (c:Claim)-[:SUPPORTS|CONTRADICTS]->(:Element {id: 'hypothesis:launch/yes'}) WHERE c.dated <= datetime('2026-05-09T00:00:00Z').epochMillis AND c.recordedAt <= datetime('2026-10-06T12:12:00Z').epochMillis RETURN c.id, c.statement ORDER BY c.dated"
```

```text
c.id, c.statement
"claim:launch.pending", "approval is pending and the launch date is uncertain"
```

agent-b's claim `launch.tooling` is dated 8 May and so counts by date, but it was entered at 12:12:44,
and the second filter leaves it out.

A query about the past applies the filter to every record it walks through, not only the one it starts
from: a claim is a revised claim at a time only if the claim that revises it counts at that time. Along
any edge the record that states the link was recorded no earlier than the record it names, and dated
no earlier too, with one exception. A holder and a source are registered and not stated, so they carry
the day they were registered, which may be later than the evidence or claim that names them. Leave
`Holder` and `Source` nodes out of a filter on `dated`.

## Versions, and nodes not yet filled in

`_version` is the revision of the record that an element was last published at, and it is how the sink
applies deltas in any order: a delta older than what the graph holds is passed over. A record that
never changes stays at 1. A claim whose text has been withdrawn was published twice:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (c:Element {id: 'claim:launch.rumour'}) RETURN c._version, c.withdrawn"
```

```text
c._version, c.withdrawn
2, TRUE
```

An edge can reach the sink before the node it points to. The sink then creates that node as a
placeholder, with its `id` and `_version = -1` and nothing else, and fills it in when the node's own
delta arrives. A placeholder is not a record yet: it has no kind label's properties and no times. In a
graph that has caught up there are none:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (n:Element) WHERE n._version = -1 RETURN count(n) AS placeholders"
```

```text
placeholders
0
```

A query that must not see half-arrived records filters on `_version >= 0`, or waits.

## Wait for the graph to hold a record

`POST /graph/wait` ends when the graph holds a record's node and every edge the record stated, at the
record's version or later, or when the limit passes:

```bash
curl -s localhost:9000/graph/wait -d '{ "kind": "claim", "id": "launch.tooling-ordered" }'
```

```json
{"caughtUp":true,"waitedMs":3123,"missing":[]}
```

`kind` is `question`, `holder`, `source`, `evidence`, `claim`, `revision` or `market`; a price
observation is waited for as the revision it became, and a resolution as its market. `limitMs` is 5,000
when left out and at most the deployment's `REASONING_WAIT_LIMIT_MAX_MS`. Passing the limit answers
`200` with `caughtUp: false` and the element keys the graph lacks, a placeholder counting as lacking:

```bash
curl -s localhost:9000/graph/wait -d '{ "kind": "source", "id": "weekly-digest", "limitMs": 0 }'
```

```json
{"caughtUp":false,"waitedMs":39,"missing":["node:source:weekly-digest"]}
```

A record that is not held answers `404` with the rule `record.not-held`, and a service with no broker
or no graph database answers `503`.

## Read the vocabulary from the service

`GET /graph/vocabulary` answers with every kind of node and edge that can be in the graph, by layer,
with the properties of each and the kind of record that publishes it. It is the same vocabulary the
service builds its elements through, so it cannot name something the graph will not hold or miss
something it will.

```bash
curl -s localhost:9000/graph/vocabulary | python3 -m json.tool
```

This reply is an excerpt: one kind of node and one kind of edge from the `belief` layer.

```json
{
    "label": "Claim",
    "id": "claim:<identifier>",
    "properties": [
        {
            "name": "statement",
            "type": "text",
            "optional": true
        },
        {
            "name": "withdrawn",
            "type": "flag",
            "optional": false
        },
        {
            "name": "withdrawnAt",
            "type": "whole number",
            "optional": true
        }
    ],
    "publishedBy": "claim"
}
```

```json
{
    "type": "RESTS_ON",
    "from": "BeliefRevision",
    "to": "Claim",
    "properties": [
        {
            "name": "weight",
            "type": "number",
            "optional": true
        }
    ],
    "publishedBy": "belief"
}
```

The reply has two layers, `belief` and `market`, and each lists its `nodes`, its `edges` and the
`holderKinds` it adds. A claim's `statement` is optional because a claim whose text has been withdrawn
has none: [Withdraw text](withdraw-text.md). Why the vocabulary is layered is on
[Layers and the vocabulary](../concepts/layers.md).
