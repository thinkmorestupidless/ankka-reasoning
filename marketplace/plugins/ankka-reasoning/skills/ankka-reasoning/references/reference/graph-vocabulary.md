# Graph vocabulary

> Look up every kind of node and edge the graph can hold, with its identifier, its properties, its layer and the kind of record that publishes it, and the query that traces a belief to its sources.

Source: https://reasoning.ankka.cloud/reference/graph-vocabulary/
The vocabulary is the list of every kind of node and edge the service publishes. A reader of the
graph database needs this page and nothing else: nothing is in the graph that is not named here.
The service publishes each element as an `ankka.graph-delta.v1` delta on its delta topic, and
[ankka-flow](https://flow.ankka.cloud/)'s merge sink writes it to the graph database. The same list
is served as JSON by `GET /graph/vocabulary`, described on the [HTTP API](http-api.md) page.

## What holds for every element

- **A node's `id` is `<kind>:<identifier>`.** A claim `launch.pending` is the node
  `claim:launch.pending`. A node carries two labels: `Element`, which the sink gives every node,
  and the one label of its kind.
- **An edge's `id` is `<TYPE>|<from id>|<to id>`.** An edge's type and its two ends never change.
- **Every kind is published by one kind of record**, and every element by the one record it belongs
  to. No node or edge has two writers.
- **An edge runs from the record that stated the link to a record held before it.** `recordedAt`
  never rises along an edge. Nor does `dated`, except into a `Holder` or a `Source`, which are dated
  when they were registered. No path returns to the node it started from.
- **Every node has `dated` and `recordedAt`.** Each is a whole number of milliseconds since the
  epoch: `dated` is the record's own date and `recordedAt` the time the service accepted it. Edges
  carry neither.
- **`id`, `_version` and `_deleted` are the sink's.** `_version` is the record's revision, or for a
  belief revision the sequence number of its event. Nothing is ever deleted from the graph, so
  `_deleted` is not set.
- **A node is published whole each time.** The only node that ever loses properties is one whose
  text is withdrawn.
- **An edge's far end may be a placeholder for a moment.** It has the label `Element`, an `id` and
  `_version = -1`, until the record that owns it is published. A reader that needs every element
  of a record waits for it with `POST /graph/wait`.

A property's type is one of four: text, number, whole number, or flag (true or false). The tables
mark a property that may be absent with `?`.

## Belief layer

The belief layer names the holder kinds `agent`, `person` and `model`.

### Nodes

| Label | `id` | Properties, besides `dated` and `recordedAt` | Published by |
|---|---|---|---|
| `Question` | `question:<question>` | `statement` text | question |
| `Hypothesis` | `hypothesis:<question>/<hypothesis>` | `statement` text | question |
| `Holder` | `holder:<holder>` | `kind` text, `name` text | holder |
| `Source` | `source:<source>` | `name` text | source |
| `Evidence` | `evidence:<digest>` | `locator`? text, `excerpt`? text, `author`? text, `publishedAt`? whole number, `withdrawn` flag, `withdrawnAt`? whole number | evidence |
| `Claim` | `claim:<claim>` | `statement`? text, `withdrawn` flag, `withdrawnAt`? whole number | claim |
| `BeliefRevision` | `revision:<revision>` | `n` whole number, `probability` number | belief |

`withdrawn` is always present on `Evidence` and `Claim`, and `false` until the text is withdrawn.
Withdrawn `Evidence` has no `locator`, `excerpt` or `author`, and a withdrawn `Claim` has no
`statement`; each then has `withdrawnAt`. The note and the writer of a withdrawal are not
published, and neither is the writer of any record. A revision's probability is on its node,
because a revision is in exactly one hypothesis. `n` is the revision's place in its belief's line,
from 1.

### Edges

| Type | From | To | Properties | Published by |
|---|---|---|---|---|
| `ANSWERS` | `Hypothesis` | `Question` | | question |
| `FROM_SOURCE` | `Evidence` | `Source` | | evidence |
| `STATED_BY` | `Claim` | `Holder` | | claim |
| `DERIVES_FROM` | `Claim` | `Evidence` | | claim |
| `SUPPORTS` | `Claim` | `Hypothesis` | | claim |
| `CONTRADICTS` | `Claim` | `Hypothesis` | | claim |
| `REVISES` | `Claim` | `Claim` | | claim, the later one |
| `HELD_BY` | `BeliefRevision` | `Holder` | | belief |
| `BELIEF_IN` | `BeliefRevision` | `Hypothesis` | | belief |
| `RESTS_ON` | `BeliefRevision` | `Claim` | `weight`? number | belief |
| `FOLLOWS` | `BeliefRevision` | `BeliefRevision` | | belief |

## Market layer

The market layer adds the holder kind `market` and the kinds in the two tables here. Every edge it
adds leaves one of its own nodes, so it changes nothing in the belief layer: a graph read with the
belief layer's tables alone is still complete for what they name.

### Nodes

| Label | `id` | Properties, besides `dated` and `recordedAt` | Published by |
|---|---|---|---|
| `Market` | `market:<market>` | `venue` text, `resolutionCriteria` text, `closesAt` whole number | market |
| `Resolution` | `resolution:<market>/<resolution>` | `authority` text, `void` flag, `outcome`? text | market |

A market's price observations are `BeliefRevision` nodes of the holder it speaks as,
`holder:market.<market>`, published by that holder's beliefs like any other revision.

### Edges

| Type | From | To | Properties | Published by |
|---|---|---|---|---|
| `ABOUT` | `Market` | `Question` | | market |
| `OFFERS` | `Market` | `Hypothesis` | `outcome` text | market |
| `SPEAKS_AS` | `Market` | `Holder` | | market |
| `RESOLUTION_OF` | `Resolution` | `Market` | | market |
| `RESOLVES_TO` | `Resolution` | `Hypothesis` | `outcome` text | market |
| `ON_EVIDENCE` | `Resolution` | `Evidence` | | market |
| `REVISES_RESOLUTION` | `Resolution` | `Resolution` | | market |

A void resolution has no `outcome` and no `RESOLVES_TO` edge.

## The trace from a belief to its sources

One walk reaches the claims a belief revision rests on, the evidence each derives from, and where
the evidence came from:

```cypher
MATCH (r:BeliefRevision {id: $revision})-[o:RESTS_ON]->(c:Claim)-[:DERIVES_FROM]->(e:Evidence)-[:FROM_SOURCE]->(s:Source)
RETURN r, o.weight, c, e, s
```

With `$revision` set to `revision:agent-a.launch.2` in the launch example it returns one row: the
revision at 0.61, the claim "the approval barrier has gone", the evidence "Notice 1187: Product Y
is approved for sale." and the source "the regulator".

## Reading as of a time

A record counts at a date `$asOf`, as recorded by `$by`, when both hold:

```cypher
c.dated <= $asOf AND c.recordedAt <= $by
```

Both are milliseconds since the epoch. A claim is a revised claim at that time when some
`(later:Claim)-[:REVISES]->(c)` counts too. A belief's revision at that time is the one with the
greatest `n` that counts, which is well defined because a revision is neither dated nor recorded
before the one it follows. [Read the graph](../build/read-the-graph.md) works through both.
