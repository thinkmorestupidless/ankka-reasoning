# Quickstart: validating the belief layer

Five tiers, each proving more with more running. Docker is required from tier 2. The routes are in
[contracts/http-api.md](contracts/http-api.md) and the settings in
[contracts/deployment.md](contracts/deployment.md). What was run and seen is recorded at the end.

## Prerequisites

JDK 21, sbt, Docker, `just`. For tier 5: a kind cluster with ankka and ankka-flow installed from
their own checkouts, and the `ankka` and `flow` commands.

## Tier 1 — components alone (seconds)

```bash
sbt graph/test belief/test market/test
```

Each entity through its unit testkit, each graph consumer through `ConsumerTestKit.graph`, the
rules as plain functions, and the vocabulary's three properties.

Proves: a record is accepted once and a different one refused; a belief's revisions form one line;
every element a consumer builds is in the vocabulary (FR-017); the belief layer compiles and passes
with no market module on its path (FR-018, SC-008).

To see it can fail: add a property to a consumer that the vocabulary does not name, and the
vocabulary suite goes red.

## Tier 2 — the whole service, no graph (about two minutes)

```bash
sbt 'service/testOnly *QuestionsFeatures *HoldersAndSourcesFeatures *EvidenceFeatures *ClaimsFeatures *BeliefsFeatures'
```

The service under `AnkkaTestKit` with an in-memory broker, driven over HTTP by the scenarios in
`features/record/` other than withdrawal.

Proves: US1. Every rule in
[contracts/refusals.md](contracts/refusals.md) is broken once and names itself (FR-013); every
write sent twice is one record (SC-003); a writer who does not speak for a holder is refused
(SC-010).

To see it can fail: remove the check that a claim's evidence is held, and *a claim that names
something not held is refused* goes red.

## Tier 3 — the service, Kafka, the sink and Neo4j (several minutes)

```bash
sbt 'service/testOnly *PublicationFeatures *VocabularyFeatures *BeliefChangeFeatures *CaseFeatures *AsOfFeatures *DisagreementFeatures *MarketsFeatures *WithdrawalFeatures *ResolutionFeatures *SeededSetSuite *AsOfPropertySuite *VocabularyQuerySuite *CompactionErasureSuite *ErasureSuite'
```

Testcontainers start Kafka, Neo4j and the released sink image once for the run. The scenarios in
`features/graph/`, `features/explain/` and `features/market/`, and the graph scenarios of
withdrawal, run against them.

Proves: US2 to US7. The launch example's explanation names the regulator's evidence, the claim
derived from it and the claim it revised, and nothing else (SC-001). An emptied and rebuilt graph
gives the same answers (SC-005). No answer as of a time holds a later record, and no answer as
recorded by a time changes after a back-dated record is entered (SC-006). Withdrawn text is in no
answer and not in a rebuilt graph (SC-011).

To see it can fail: publish an edge from the wrong consumer and *every edge runs from a record to
one held before it* goes red; stop the sink container and the wait scenarios report the limit
passed.

## Tier 4 — on a laptop, by hand

```bash
just up                      # Postgres, Kafka with the compacted topic, Neo4j, the sink
just run                     # the service on :9000
just seed launch-example     # the two pieces of evidence, two claims, one belief, two revisions
```

Wait for the second revision to be in the graph, then ask why the belief changed:

```bash
curl -s localhost:9000/graph/wait -d '{"kind":"revision","id":"agent-a.launch.2"}'
curl -s 'localhost:9000/answers/belief-change?from=agent-a.launch.1&to=agent-a.launch.2'
```

Expect `0.38` and `0.61`, the claim "the approval barrier has gone" newly rested on with the
regulator's evidence and source, and the earlier claim no longer rested on and revised by it.

Read the same trace straight from the graph database:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (r:BeliefRevision {id:'revision:agent-a.launch.2'})-[:RESTS_ON]->(c:Claim)-[:DERIVES_FROM]->(e:Evidence)-[:FROM_SOURCE]->(s:Source) RETURN c.statement, e.excerpt, s.name"
```

Then the seeded set and the measurements:

```bash
just seed ten-questions      # ten questions, 555 records
just measure                 # the set again under its own names, each record timed to the graph; then a question of 1,000 records, timed to its answer
just rebuild                 # stop the sink, empty Neo4j, reset the group, start the sink
```

Proves: SC-002 (every belief revision that rests on a claim reaches a source), SC-007 (five seconds
to the graph nine times in ten; one second for an explanation over 1,000 records). The Cypher
above is the vocabulary page's own trace query, which tier 3 runs as written (SC-009).

## Tier 5 — beside ankka and ankka-flow on kind

With ankka's and ankka-flow's local clusters up and Neo4j installed by ankka-flow's `just
neo4j-up`:

```bash
flow verify deploy/pipeline/blueprint.conf --conf deploy/pipeline/kind.conf
flow generate deploy/pipeline/blueprint.conf --conf deploy/pipeline/kind.conf -n ankka-reasoning -o pipeline.yaml && kubectl apply -f pipeline.yaml
just images && REASONING_DEPLOY_KAFKA=<broker> REASONING_DEPLOY_NEO4J_URI=<bolt address> just descriptors
ankka services apply -f target/deploy/service.json --project reasoning
just seed launch-example https://reasoning-reasoning.127.0.0.1.sslip.io:8443
```

The pipeline first, so the operator creates the topic compacted. Then the same two `curl` commands
as tier 4 against the gateway, with a token.

Proves: the descriptor and the blueprint are what a cluster accepts; a person is identified by a
token and a service by its certificate (R5); the sink the operator runs is the one tier 3 tested.

## Reviewer's checklist

- [x] Tier 1 to 3 pass from a clean checkout with `sbt test`.
- [x] Every scenario under `features/` is run by exactly one suite; none is ignored.
- [x] Each "to see it can fail" above was tried once and went red.
- [x] Tier 4's explanation matches the launch example in the glossary, value for value.
- [x] The measurements for SC-007 are recorded at the end of `research.md`.
- [x] Every item in `research.md`'s "Verify first" has an outcome beside it.

## What was run, 2026-10-06

**Tiers 1 to 3.** `sbt clean scalafmtCheckAll test`, on a laptop with the sink's image built from
ankka-flow's `v0.3.0` tag (research F8): 266 tests in 43 suites, none failed or ignored, in about four and a half minutes. Each feature file is run by one suite; the five
under `features/record` other than withdrawal are run a second time against a service with no
market layer (`BeliefOnlySuite`). `just features` reports no findings in 119 scenarios.

**Each "to see it can fail".**

| Changed | Went red |
|---|---|
| a property the vocabulary does not name, added to a claim's node | `GraphSuite`, twice, with `UndeclaredElement` |
| the check that a claim's evidence is held, removed | *a claim that names something not held is refused*, and nothing else |
| a claim's `REVISES` edge published the wrong way round | *every edge runs from a record to one held before it* and *every record is in the graph with the links it stated* |
| the sink stopped, on the laptop stack | a wait of six seconds for evidence recorded meanwhile answered `caughtUp: false`, naming the node and the edge it lacked; the record was held all the while, and the wait ended in under two seconds once the sink was started again |

**Tier 4.** `REASONING_POSTGRES_PORT=5433 just up` (this laptop's 5432 was taken), the staged
service on `:9000`, then:

| Step | Seen |
|---|---|
| `just seed launch-example` | 10 sent, 10 new |
| `/graph/wait` for `agent-a.launch.2` | caught up after 1.9 s |
| `/answers/belief-change` | 0.38 and 0.61; newly rested on "the approval barrier has gone" with "Notice 1187: Product Y is approved for sale." from the regulator; no longer rested on "approval is pending and the launch date is uncertain", revised by `launch.barrier-gone`; the notice observed between |
| the trace in `cypher-shell` | one row: the claim, the notice's excerpt, "the regulator" |
| `just seed ten-questions` | 557 sent, 555 new, the 2 withdrawals answered `200` |
| `just measure` | in research.md, Measurements |
| `just rebuild` | 3,752 nodes and 11,350 edges back in about six seconds; an explanation asked before and after was the same, byte for byte |

**Tier 5.** Run on 2026-10-07 against the local kind installation (ankka and ankka-flow at their
`main` of the day, `ankka-flow-sidecar:latest`), with the image and descriptor of release 0.1.1,
through a kubeconfig holding only that cluster's context.

| Step | Seen |
|---|---|
| `just neo4j-up` in ankka-flow | Neo4j in `neo4j`, the Secret `neo4j-local` in `shop`; copied into `ankka-reasoning` |
| `ankka projects create reasoning` | created; no namespace until the first service is applied (F18) |
| `ankka services apply` with the release's descriptor | `ankka-reasoning` namespace created; pod `Init:0/1` until its secret existed |
| `flow verify` / `flow generate` | verified; piping `generate` to `kubectl` refused (F19), `-o pipeline.yaml` applied; `aflow` Ready in 25 s, sink pod running |
| the secret `reasoning-graph` | service Ready within a minute; every graph consumer started against `kafka.kafka.svc:9092` |
| the topic | created by the pipeline with `cleanup.policy=compact`, `max.compaction.lag.ms=86400000`, 3 partitions |
| a port-forward with plain HTTP, then TLS without a client certificate | empty reply, then a TLS alert: every port is mutual TLS |
| `ankka services expose`, then `curl` through the gateway | `401`, `error="a caller through the gateway cannot be identified as a writer yet"` (F1, as designed) |
| the same calls with the shopping cart sample's service certificate | writer `service:shoppingcart/cart`; a source `201`; `/graph/wait` caught up in 3.2 s |
| the launch example, record by record, as that writer | 10 of 10 `201`; `/graph/wait` for `agent-a.launch.2` caught up in 2.5 s |
| `/answers/belief-change` | 0.38 and 0.61, the barrier-gone claim with the regulator's notice newly rested on, the pending claim no longer rested on and revised: the same as tier 4 |
| `/answers/belief` as of 6 May | 0.38, resting on `launch.pending` |
| the trace query in the cluster's `cypher-shell` | one row: the claim, the notice's excerpt, "the regulator" |

Not run: a cloud installation, and a writer that is a person (F1).
