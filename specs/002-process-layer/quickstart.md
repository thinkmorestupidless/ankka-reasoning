# Quickstart: validating the process layer

Five tiers, as spec 001's [quickstart.md](../001-belief-layer/quickstart.md), each proving more
with more running; a sixth needs model keys. Docker is required from tier 1 here, because ankka's
test kit runs Postgres for a blueprint's runs. The routes are in
[contracts/http-api.md](contracts/http-api.md), the appraiser in
[contracts/blueprint.md](contracts/blueprint.md), the settings in
[contracts/deployment.md](contracts/deployment.md).

## Prerequisites

JDK 21, sbt, Docker, `just`. An ankka release carrying blueprints, pinned in
`project/Dependencies.scala` (research R1). For tier 6, `ANTHROPIC_API_KEY` and `TYPESAFE_API_KEY`.
For tier 5, a kind cluster with ankka installed and the `ankka` command.

## Tier 0 — the sink moved (several minutes)

```bash
sbt 'service/testOnly *SinkSmokeSuite *PublicationFeatures *SeededSetSuite'
```

The graph suites against ankka-contrib's sink image in place of ankka-flow's sidecar (R2), with
nothing else of this feature built.

Proves: every scenario of spec 001 passes under the new sink; `just rebuild` empties and refills
the graph by raising the sink's version (V2, V3).

To see it can fail: set `V.sinkImage` to a tag that does not exist, and every graph suite fails
to start its container.

## Tier 1 — components alone (a minute)

```bash
sbt process/test
```

Each process entity through its unit test kit; the appraiser registered against ankka's check
(V4); the three tools called through their handlers inside `RunContext.within(...)` and outside it;
the vocabulary's properties with the process layer composed and without it.

Proves: a record a tool writes is held with its act, and not at all when the act's checks fail
(R8); a tool in no run writes nothing; the process layer's edges leave its own nodes (FR-005);
`belief` and `market` compile and pass with no `process` module on their path (SC-008).

To see it can fail: give an `Act` node a property the vocabulary does not name, and the vocabulary
suite goes red.

## Tier 2 — the whole service, no graph (a few minutes)

```bash
sbt 'service/testOnly *ToolsFeatures'
```

The service under `AnkkaTestKit` with the test model and judgment providers, driven over HTTP by
`features/process/tools.feature`: runs started by `POST /process/runs`, the model scripted from the
golden set (R14).

Proves: US1. Every rule in [contracts/refusals.md](contracts/refusals.md) is broken once and names
itself; a run sent twice writes each record once (V12); evidence an earlier run recorded keeps its
act.

To see it can fail: remove the check that the blueprint speaks for the input's holder, and *a
record stated as a holder the blueprint does not speak for is refused* goes red.

## Tier 3 — the service, Kafka, the sink and Neo4j (several minutes)

```bash
sbt 'service/testOnly *RunsFeatures *GoldenSetFeatures *VocabularyFeatures *ProcessVocabularyQuerySuite'
```

Testcontainers start Kafka, Neo4j and the contrib sink image once for the run. `runs.feature`,
`golden-set.feature` and the changed `vocabulary.feature` run against them.

Proves: US2 and US3. A belief revision traces to its act, run and version (FR-007); an emptied and
rebuilt graph holds the process layer (FR-008); the four runs give the four answers the golden set
says (SC-001 to SC-004); the market resolved afterwards lists each holder's belief with its version
(FR-015). `ProcessVocabularyQuerySuite` runs the Cypher printed on the vocabulary page as written.

To see it can fail: publish `IN` from the run instead of the act, and *every kind of edge in the
process layer runs from a node of the process layer* goes red; script the judgment to answer `no`
for the approval document, and *the appraiser run over the June tranche revises the belief* goes
red with the run failed naming the claim.

## Tier 4 — on a laptop, by hand

```bash
just up                      # Postgres, Kafka with the compacted topic, Neo4j, the contrib sink
just run                     # the service on :9000; without model keys every run fails at its first model call
just seed golden-set         # the question, its sources, the market
```

With keys set in the environment (or skip to the scripted run below):

```bash
curl -s localhost:9000/process/runs -d '{"blueprint":"appraiser","runId":"appraiser-a-may","input":{"tranche":"may","holder":"appraiser-a","question":"launch","observedAt":"2026-05-31T09:00:00Z"}}'
curl -s localhost:9000/process/runs/appraiser-a-may          # until "ending" appears
curl -s localhost:9000/process/runs -d '{"blueprint":"appraiser","runId":"appraiser-a-june","input":{"tranche":"june","holder":"appraiser-a","question":"launch","follows":"appraiser-a-may.yes","observedAt":"2026-06-30T09:00:00Z"}}'
curl -s localhost:9000/graph/wait -d '{"kind":"revision","id":"appraiser-a-june.yes"}'
curl -s 'localhost:9000/process/answers/belief-change?from=appraiser-a-may.yes&to=appraiser-a-june.yes'
```

Expect a probability below one half then above it; the three June claims newly rested on, the
approval's among them, each with its evidence and source, and the five May claims no longer rested
on; and in `acts` the approval claim's act naming the step `read`, the worker `reader`, the run
`appraiser-a-june` and version 1. The June tranche is observed on 30 June because its rumour was
published on 20 June at 22:00, and evidence observed before it was published is refused.

Then version 2 for the second holder, and the comparison:

```bash
curl -s localhost:9000/process/appraiser/versions -d '{"instructions":"v2"}'
# the two runs again for appraiser-b, then:
curl -s 'localhost:9000/process/answers/comparison?hypothesis=launch/yes&a=appraiser-a&b=appraiser-b'
```

Expect `both` empty, each holder's three June claims under `onlyA` and `onlyB` (a claim is one
holder's; the two share the evidence, not the claims), `difference` the gap the two instruction
sets make, and in `acts` version 1 behind one holder's revision and version 2 behind the other's.

Read the trace straight from the graph database:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (r:BeliefRevision {id:'revision:appraiser-a-june.yes'})<-[:WROTE]-(a:Act)-[:IN]->(run:Run)-[:OF]->(v:BlueprintVersion) RETURN a.step, a.worker, run.id, v.version"
```

Then the rebuild and the measurement:

```bash
just rebuild                 # empty Neo4j, raise the sink's version, let it refill
just measure-golden-set      # the four runs with a scripted model, timed: records to the graph with their acts (SC-009), the whole set (SC-006)
```

Proves: SC-006 (two minutes on a laptop), SC-009 (five seconds, nine in ten).

## Tier 5 — beside ankka on kind

```bash
ankka projects create reasoning
ankka projects topics set reasoning-graph --partitions 3 --cleanup compact -p reasoning
ankka projects secrets set reasoning-graph username=neo4j password=- -p reasoning
just images && just descriptors
ankka services apply -f target/deploy/graph-sink.json --project reasoning
ankka services apply -f target/deploy/service.json --project reasoning
```

Then the tier 4 commands against the gateway, with a token (people are writers from this ankka
release, spec 001 F1 closed).

Proves: the contrib sink is what a cluster runs; the descriptors are what a cluster accepts; a run
through the cluster's model keys, set as project secrets, reaches the graph.

## Tier 6 — the live suite

```bash
ANTHROPIC_API_KEY=… TYPESAFE_API_KEY=… sbt 'service/testOnly *LiveAppraiserSuite'
```

Without the keys the suite is skipped (tier 2 shows it skipped, not failed). With them, the
appraiser runs for one holder over both tranches against the real providers.

Proves: US4, each run once. SC-007's nine in ten: run it ten times and record the count below.

## Reviewer's checklist

- [x] Tiers 0 to 3 pass from a clean checkout with `sbt test`; `LiveAppraiserSuite` is reported skipped (2026-10-21, below).
- [x] Every scenario under `features/` is run by exactly one suite; none is ignored (`just features`: 0 findings in 155 scenarios; `sbt test` reports 0 ignored).
- [x] Each "to see it can fail" above was tried once and went red (research V-outcomes and F-findings of the phase that built each).
- [x] Tier 4's answers match the golden set's expected readings, value for value: under the scripted model in `GoldenSetFeatures` and `DocsCaptureSuite`, whose replies are on the pages; by hand on a laptop, not run (research F-ac).
- [x] The measurements for SC-006 and SC-009 are recorded at the end of `research.md`; SC-007's ten live runs are recorded there as not run, no key being available.
- [x] Every item in `research.md`'s "Verify first" has an outcome beside it.
- [x] `notes/ankka-requests.md` marks 8, 9 and 13 answered or moot and holds 14 to 17.

## What was run

- 2026-10-18, laptop (Apple silicon, Docker), after Phase 1: `sbt clean test` ran 218 tests in 4 min
  41 s; 216 passed. Every scenario of spec 001 passes against ankka 0.12.0 and ankka-contrib's sink.
  The two that fail are the scenarios of `features/graph/vocabulary.feature` this spec changed to
  name the process layer, on unbound steps, as planned until T026 binds them. `scalafmtCheckAll`
  clean. On the laptop: `just up` with the contrib sink, the launch example seeded, `just rebuild`
  to version 2, the same answer (research V3).
- 2026-10-20, laptop (Apple silicon, Docker), after Phase 6, tier 2 for the live suite:
  `sbt 'service/testOnly *LiveAppraiserSuite *LiveFeatures'` with no key in the environment
  reports 1 passed, 3 skipped, 0 failed: the suite's test and the feature's two scenarios with a
  key are skipped, and the scenario without a key, which runs the suite with an empty environment,
  passes. Tier 6 with the keys, ten times for SC-007: not yet run.
- 2026-10-21, laptop (Apple silicon, Docker), Phase 7. Tier 4 by hand was not run: `just up` binds
  Kafka on 9094 and Neo4j on 7687, which another project's kind cluster held that day (research
  F-ac). In its place `DocsCaptureSuite`, with `REASONING_CAPTURE_DIR` set, ran tier 4's requests
  against the graph fixture's service with the scripted model and wrote every reply the new pages
  show; `GoldenSetMeasureSuite` ran `measure-golden-set` the same way (SC-006 8.5 s, SC-009 nine
  in ten within 1.6 s; research Measurements); `ServiceStartsSuite` started a run with no key and
  read its ending, `failed` naming `ANTHROPIC_API_KEY`. Tier 5 was not run: the `kind-ankka`
  cluster was down. Tier 6 with the keys: not run, no key available. `just features`: 0 findings in
  155 scenarios and 2 specs. `just docs` built every page. `sbt clean scalafmtCheckAll test`
  ran 373 tests in 14 min 26 s: 362 passed, 4 skipped (the live suite's test, the live feature's
  two keyed scenarios, the docs capture), 7 failed, every one a thirty-second `/graph/wait` after a
  rebuild answered by Pekko HTTP's twenty-second request timeout (research F-ae); the three suites
  passed alone, a second full run failed one the same way, and after the timeout was raised
  `sbt scalafmtCheckAll test` passed 369 with the same 4 skipped in 7 min 33 s.
