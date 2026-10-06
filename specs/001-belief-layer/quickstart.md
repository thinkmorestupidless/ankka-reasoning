# Quickstart: validating the belief layer

Five tiers, each proving more with more running. Docker is required from tier 2. The routes are in
[contracts/http-api.md](contracts/http-api.md) and the settings in
[contracts/deployment.md](contracts/deployment.md). Nothing here exists until the slices in
[plan.md](plan.md) are built; this is what each will be checked against.

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
sbt 'service/testOnly *PublicationFeatures *VocabularyFeatures *BeliefChangeFeatures *CaseFeatures *AsOfFeatures *DisagreementFeatures *MarketsFeatures *WithdrawalFeatures *ResolutionFeatures *SeededSetSuite'
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
curl -s 'localhost:9000/answers/belief-change?holder=agent-a&hypothesis=launch/yes&from=agent-a.launch.1&to=agent-a.launch.2'
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
just seed ten-questions      # ten questions, at least 500 records
just measure                 # time to graph for each record; time to answer for the largest question
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
flow generate deploy/pipeline/blueprint.conf --conf deploy/pipeline/kind.conf -n reasoning | kubectl apply -f -
just images && just descriptors
ankka services apply -f target/deploy/service.json --project reasoning
just seed launch-example https://reasoning-reasoning.127.0.0.1.sslip.io:8443
```

The pipeline first, so the operator creates the topic compacted. Then the same two `curl` commands
as tier 4 against the gateway, with a token.

Proves: the descriptor and the blueprint are what a cluster accepts; a person is identified by a
token and a service by its certificate (R5); the sink the operator runs is the one tier 3 tested.

## Reviewer's checklist

- [ ] Tier 1 to 3 pass from a clean checkout with `sbt test`.
- [ ] Every scenario under `features/` is run by exactly one suite; none is ignored.
- [ ] Each "to see it can fail" above was tried once and went red.
- [ ] Tier 4's explanation matches the launch example in the glossary, value for value.
- [ ] The measurements for SC-007 are recorded at the end of `research.md`.
- [ ] Every item in `research.md`'s "Verify first" has an outcome beside it.
