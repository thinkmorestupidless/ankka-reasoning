# Contract: the graph vocabulary

Every kind of node and edge the service publishes, as `ankka.graph-delta.v1` deltas on the topic
named in [deployment.md](deployment.md). A reader of the graph database needs this page and nothing
else. The same content is served, as JSON, at `GET /graph/vocabulary` ([http-api.md](http-api.md)).
What the service holds behind each kind is in [../data-model.md](../data-model.md).

## Rules that hold for every element

- **V1.** A node's `id` is `<kind>:<identifier>`, and its labels are `Element` (the sink's) and the
  one label below. An edge's `id` is `<TYPE>|<from id>|<to id>`.
- **V2.** Every node and edge is published by exactly one kind of record, named below, and by the
  one record it belongs to.
- **V3.** An edge runs from the node of the record that stated it to the node of a record that was
  held before it, so `recordedAt` never rises along an edge. Nor does `dated`, except into a
  `Holder` or a `Source`, which are dated when they were registered. An edge's type and endpoints
  never change.
- **V4.** A node is published whole each time. The only node that ever loses properties is one
  whose text is withdrawn.
- **V5.** Nothing is ever tombstoned.
- **V6.** A time is a whole number of milliseconds since the epoch. `dated` is the record's date,
  `recordedAt` the time the service accepted it. Every node has both.
- **V7.** `id`, `_version` and `_deleted` are the sink's. `_version` is the record's revision, or
  for a belief revision the sequence number of its event.
- **V8.** An edge's endpoint may be a placeholder (`Element`, `id`, `_version = -1`) until the
  record that owns it is published. A reader that needs every element of a record waits for it
  ([http-api.md](http-api.md), `POST /graph/wait`).

## Belief layer

### Nodes

| Label | `id` | Properties (besides `dated`, `recordedAt`) | Published by |
|---|---|---|---|
| `Question` | `question:<question>` | `statement` | question |
| `Hypothesis` | `hypothesis:<question>/<hypothesis>` | `statement` | question |
| `Holder` | `holder:<holder>` | `kind`, `name` | holder |
| `Source` | `source:<source>` | `name` | source |
| `Evidence` | `evidence:<digest>` | `locator`, `excerpt`, `author`?, `publishedAt`?, `withdrawn`, `withdrawnAt`? | evidence |
| `Claim` | `claim:<claim>` | `statement`, `withdrawn`, `withdrawnAt`? | claim |
| `BeliefRevision` | `revision:<revision>` | `n`, `probability` | belief |

`?` marks a property that may be absent. `withdrawn` is always present on `Evidence` and `Claim`,
`false` until the text is withdrawn. A withdrawn `Evidence` has no `locator`, `excerpt` or
`author`; a withdrawn `Claim` has no `statement`. The note and the writer of a withdrawal are not
published.

### Edges

| Type | From | To | Properties | Published by |
|---|---|---|---|---|
| `ANSWERS` | `Hypothesis` | `Question` | | question |
| `FROM_SOURCE` | `Evidence` | `Source` | | evidence |
| `STATED_BY` | `Claim` | `Holder` | | claim |
| `DERIVES_FROM` | `Claim` | `Evidence` | | claim |
| `SUPPORTS` | `Claim` | `Hypothesis` | | claim |
| `CONTRADICTS` | `Claim` | `Hypothesis` | | claim |
| `REVISES` | `Claim` | `Claim` | | claim (the later one) |
| `HELD_BY` | `BeliefRevision` | `Holder` | | belief |
| `BELIEF_IN` | `BeliefRevision` | `Hypothesis` | | belief |
| `RESTS_ON` | `BeliefRevision` | `Claim` | `weight`? | belief |
| `FOLLOWS` | `BeliefRevision` | `BeliefRevision` | | belief |

A revision's probability is on its node, since a revision is in exactly one hypothesis.

## Market layer

Adds the holder kind `market` and the kinds below. Every edge it adds leaves one of its own nodes.

### Nodes

| Label | `id` | Properties (besides `dated`, `recordedAt`) | Published by |
|---|---|---|---|
| `Market` | `market:<market>` | `venue`, `resolutionCriteria`, `closesAt` | market |
| `Resolution` | `resolution:<market>/<resolution>` | `authority`, `void`, `outcome`? | market |

A market's price observations are `BeliefRevision` nodes of the holder it speaks as, published by
that holder's beliefs like any other.

### Edges

| Type | From | To | Properties | Published by |
|---|---|---|---|---|
| `ABOUT` | `Market` | `Question` | | market |
| `OFFERS` | `Market` | `Hypothesis` | `outcome` | market |
| `SPEAKS_AS` | `Market` | `Holder` | | market |
| `RESOLUTION_OF` | `Resolution` | `Market` | | market |
| `RESOLVES_TO` | `Resolution` | `Hypothesis` | `outcome` | market |
| `ON_EVIDENCE` | `Resolution` | `Evidence` | | market |
| `REVISES_RESOLUTION` | `Resolution` | `Resolution` | | market |

A void resolution has no `RESOLVES_TO` edge.

## Reading it

The trace from a belief revision to its sources:

```cypher
MATCH (r:BeliefRevision {id: $revision})-[o:RESTS_ON]->(c:Claim)-[:DERIVES_FROM]->(e:Evidence)-[:FROM_SOURCE]->(s:Source)
RETURN r, o.weight, c, e, s
```

A claim as it stood at a date `$asOf`, recorded by `$by`: it counts when `c.dated <= $asOf` and
`c.recordedAt <= $by`, and it is a revised claim when some `(later:Claim)-[:REVISES]->(c)` counts
too. A belief's revision then is the one with the greatest `n` that counts.

## Properties a test holds

- Every element published for the seeded set is of a kind on this page, with only the properties
  listed for it and each of the type listed.
- No kind on this page is published by two kinds of record.
- The belief layer's tables name no label or type of the market layer, and every scenario that
  names no market passes with the market layer's components not registered.
- No path in the graph returns to the node it started from.
