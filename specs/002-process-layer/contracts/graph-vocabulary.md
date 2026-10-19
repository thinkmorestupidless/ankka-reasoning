# Contract: the graph vocabulary, process layer

What the process layer adds to the vocabulary of spec 001's
[graph-vocabulary.md](../../001-belief-layer/contracts/graph-vocabulary.md). Rules V1 to V8 there
hold for every element below. The belief layer's and the market layer's tables are unchanged. The
same content is served at `GET /graph/vocabulary` under the layer `process`.

## Nodes

| Label | `id` | Properties (besides `dated`, `recordedAt`) | Published by |
|---|---|---|---|
| `BlueprintVersion` | `blueprint-version:<blueprint>@<n>` | `blueprint`, `version`, `digest` | blueprint version |
| `Run` | `run:<run id>` | `blueprint`, `version`, `startedBy` | run |
| `RunEnding` | `run-ending:<run id>` | `status`, `reason`? | run ending |
| `Act` | `act:<56 hex>` | `step`, `worker`, `recordKind` (`evidence`, `claim` or `revision`) | act |

`dated` is: when the version was registered; when the run started; when the run ended; the written
record's `dated`. A run with no `RunEnding` is running.

## Edges

| Type | From | To | Properties | Published by |
|---|---|---|---|---|
| `OF` | `Run` | `BlueprintVersion` | | run |
| `ENDED` | `RunEnding` | `Run` | | run ending |
| `IN` | `Act` | `Run` | | act |
| `WROTE` | `Act` | `Evidence`, `Claim` or `BeliefRevision`, exactly one; served as `Evidence\|Claim\|BeliefRevision` | | act |
| `AS` | `Act` | `Holder` | | act |

Every edge the layer adds leaves one of its own nodes (the spec's FR-005; the vocabulary scenario
*every kind of edge in the process layer runs from a node of the process layer*). The step and the
worker are on the `Act` node, not on `IN`: the spec's link table puts them on the link and FR-005 on
the node; one place is enough and a node's properties are what a reader of an act sees first.

## Reading it

From a belief revision to the run and the version that wrote it (FR-007):

```cypher
MATCH (r:BeliefRevision {id: $revision})<-[:WROTE]-(a:Act)-[:IN]->(run:Run)-[:OF]->(v:BlueprintVersion)
OPTIONAL MATCH (e:RunEnding)-[:ENDED]->(run)
RETURN a, run, e, v
```

Everything a run wrote:

```cypher
MATCH (a:Act)-[:IN]->(:Run {id: $run})
MATCH (a)-[:WROTE]->(x)
RETURN a, labels(x), x
```

The answers with acts (research R18) run the lower answer's walk and then one walk from the
records found to their acts, runs, endings and versions.

## Properties a test holds

- Every element the golden set's four runs publish is of a kind on this page or spec 001's, with only
  the properties listed and each of the type listed.
- Every `Act` has exactly one `WROTE` edge, one `IN` and one `AS`; every `Run` has one `OF`; a
  `RunEnding` has one `ENDED`.
- Every record the runs wrote has exactly one `Act` pointing at it (SC-005).
- No kind on this page is published by two kinds of record; no label or type of this page appears
  in the belief layer's or the market layer's tables; every scenario of those layers passes with the
  process layer's components not registered (SC-008).
- No path returns to the node it started from.
