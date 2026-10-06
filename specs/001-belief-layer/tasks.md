# Tasks: the belief layer

**Input**: Design documents from `/specs/001-belief-layer/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: included. The spec's acceptance scenarios are the living features under `features/`, and
every one runs as a test (research R12). A story's feature suites are written first and seen to
fail; a claim in research.md's "Verify first" is settled by a test that would show it false, before
anything is built on it.

**Organization**: by user story, in the spec's priority order. US1 (record the reasoning) is the
MVP and US2 (the graph) is what makes it visible; US3 is the acceptance example of the feature.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 to US7 from spec.md
- Paths are repository-relative. `.../` abbreviates `src/main/scala/reasoning/` under the module
  named, and `test/` abbreviates `modules/service/src/test/scala/reasoning/`.

## Path Conventions

Five sbt modules under `modules/` (`graph`, `belief`, `market`, `service`, `seed`), the living
features at `features/`, seed data at `seed/`, deployment files at `deploy/` and `compose/`. See
plan.md *Project Structure*. Rule names are those in contracts/refusals.md; routes are those in
contracts/http-api.md; labels and edge types are those in contracts/graph-vocabulary.md.

---

## Phase 1: Setup (the build, and the things beside the service)

**Purpose**: a repository that compiles, starts an empty service and runs its tests.

- [ ] T001 Create `project/build.properties` (sbt 1.12.15), `project/plugins.sbt` (sbt-native-packager 1.11.7, sbt-scalafmt 2.6.2, sbt-dynver 5.1.1) and `project/Dependencies.scala` holding every version: Scala 3.9.0, ankka 0.10.0 (`sdk`, `runtime`, `http`, `auth-oidc`, `testkit`), neo4j-java-driver 5.28.5, munit 1.3.6, testcontainers 1.21.4 (`kafka`, `neo4j`), logback 1.6.3, and the images `apache/kafka:3.9.1`, `neo4j:5.26-community`, `ghcr.io/thinkmorestupidless/ankka-flow-sidecar:0.3.0`
- [ ] T002 Create `build.sbt` with the modules `graph`, `belief` (depends on `graph`), `market` (depends on `belief`), `service` (depends on `belief`, `market`), `seed` (depends on none): common settings copied from `../satisfactory/build.sbt` (`-deprecation -feature -Wunused:all -Wvalue-discard`, `--release 21`, `Test / fork := true`, `Test / parallelExecution := false`, `Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)`); `service` with `JavaAppPackaging`, `DockerPlugin`, port 9000, `publish / skip`; the `schema` task (ankka's DDL from the runtime jar into `target/ddl`) and `deployDescriptors` task; the system properties `reasoning.kafka.image`, `reasoning.neo4j.image`, `reasoning.sink.image` set from `Dependencies` and forwarded in `Test / javaOptions`; `service / Test / baseDirectory` set to the repository root so `features/` resolves
- [ ] T003 [P] Copy `.scalafmt.conf` from `../ankka/.scalafmt.conf` and `.githooks/pre-commit` from `../satisfactory/.githooks/pre-commit`; extend `.gitignore` with `.bloop/`, `project/project/`, `project/target/`, `*.log`, `.env`
- [ ] T004 [P] Create `Justfile`, one command per recipe: `test`, `run` (`sbt service/run`), `up` (`sbt schema && docker compose up -d`), `down`, `seed name url="http://localhost:9000"` (`sbt "seed/run seed/{{name}}.json {{url}}"`), `measure`, `rebuild`, `images`, `descriptors`, `hooks`, `features` (the speckit-bdd checker command from `CLAUDE.md`)
- [ ] T005 [P] Create `docker-compose.yml` and `compose/` exactly as contracts/deployment.md "On a laptop": `postgres` mounting `./target/ddl`; `kafka` with an external listener on 9094 and topic auto-creation off; a one-shot `topic` service that creates `reasoning-graph` with 3 partitions, `cleanup.policy=compact` and `max.compaction.lag.ms=86400000`; `neo4j` with password `reasoning-local-password`; `sink` from the sidecar image with `compose/flow-graph/streamlet.conf` (stage `neo4j-merge-sink`, inlet topic `reasoning-graph`, group `reasoning-graph.graph.in`), `compose/flow-graph/descriptor.json` (the built-in's, copied from `../ankka-flow` at tag `v0.3.0`, `protocol/fixtures/builtin/neo4j-merge-sink.json`) and `compose/neo4j-secret/{uri,username,password,database}`
- [ ] T006 [P] Create `.github/workflows/ci.yml` on pull requests, ubuntu-24.04: a `build` job (`sbt scalafmtCheckAll scalafmtSbtCheck compile test`) and a `features` job (the speckit-bdd checker through `uvx`)
- [ ] T007 Settle V9: resolve the build and confirm that `ankka-testkit_3-0.10.0.jar` contains `com/thinkmorestupidless/ankka/testkit/GherkinSuite.class` and that `ankka-auth-oidc_3` 0.10.0 resolves; start a `## Found during implementation` section in `specs/001-belief-layer/research.md` and record the outcome, pinning a later ankka release there and in `project/Dependencies.scala` if either is missing
- [ ] T008 Create `modules/service/.../service/Settings.scala` and `modules/service/src/main/resources/application.conf` (`ankka.service.name = "reasoning"` and a `reasoning` block with every key and environment override in contracts/deployment.md "Settings"), and `modules/service/.../service/Main.scala` that starts a service with an empty component list, `ProjectionRuntime.fromEnv()` and an HTTP server; add `test/ServiceStartsSuite.scala` starting it under `AnkkaTestKit`

---

## Phase 2: Foundational (what every story builds on)

**Purpose**: the vocabulary types, identifiers, refusals, writers and the test fixture.

**⚠️ CRITICAL**: no story can start until this phase is complete.

- [ ] T009 [P] Create `modules/graph/.../graph/Vocabulary.scala`: `Layer`, `PropertyType` (text, whole number, number, flag), `Property(name, type, optional)`, `NodeKind(label, layer, properties, publishedBy)`, `EdgeKind(edgeType, layer, from, to, properties, publishedBy)`, `Vocabulary(nodes, edges, holderKinds)` with `++`, lookups by label and type, and `publishers` reporting any kind with two; with `modules/graph/src/test/scala/reasoning/graph/VocabularySuite.scala`
- [ ] T010 [P] Create `modules/graph/.../graph/Elements.scala`: node and edge ids by rule V1 of contracts/graph-vocabulary.md, the element key (`node:<id>`, `edge:<id>`), and builders that turn a `NodeKind` or `EdgeKind` plus values into ankka's `GraphElement`, adding `dated` and `recordedAt` to every node and refusing a property the kind does not name or of the wrong type; with `ElementsSuite.scala` beside the vocabulary suite
- [ ] T011 [P] Create `modules/belief/.../belief/domain/Ids.scala` (the identifier format of research R11; `HypothesisRef` parsed from `<question>/<hypothesis>`), `domain/Refusal.scala` (`Refusal(rule, status, error, names)` and the exception that carries one), `domain/Recorded.scala` (`Writer`, instants as whole milliseconds with an ISO-8601 JSON codec, the `dated`/`recordedAt`/`writer` fields) and `application/Clock.scala` (a trait, a system clock and a settable one for tests); with suites under `modules/belief/src/test/scala/reasoning/belief/domain/`
- [ ] T012 [P] Create `modules/belief/.../belief/api/Replies.scala`: a `handle { … }` wrapper that turns a `Refusal` into the JSON body and status of contracts/refusals.md "The body", maps `CommandError` codes to statuses, and answers `201` for a new record and `200` for a repeat (rule H1); with `RepliesSuite.scala`
- [ ] T013 Settle V5, test first: create `test/WritersSuite.scala` asserting the four rows of contracts/deployment.md "Writers" (distinct `asCaller` services give distinct writers; the gateway with no token is `401`; a local caller is `local`), then `modules/service/.../service/Writers.scala` with the one `Acl.Authenticate` decision that makes it pass, deferring to `ankka-auth-oidc` for a bearer token; record V5's outcome in research.md
- [ ] T014 Settle V4 and V7: create `test/ServiceFixture.scala` (the service's components under `AnkkaTestKit` with `ProjectionRuntime.withBroker` over an in-memory broker, `HttpServer.at("127.0.0.1", 0)`, the settable clock starting at 2026-12-01T00:00:00Z so that every date a scenario uses is in the past, an HTTP client that can call as any writer) and `test/FeaturesPathSuite.scala` asserting that `features/` is found from the forked test JVM and holds the thirteen feature files; record both outcomes
- [ ] T015 [P] Settle V8: `test/EntityIdSuite.scala` round-tripping a key value entity whose id contains `~`, `.`, `-`, `_` and 64 hex characters, and one whose id is 302 characters long (the longest a belief's can be), through `AnkkaTestKit`; record the outcome and, if a character or the length is refused, change the separators or the limits in data-model.md and research R8/R11 before any entity is written

**Checkpoint**: an empty service starts, a refusal has a shape, a caller is a writer.

---

## Phase 3: User Story 1 — Record the reasoning about a question (Priority: P1) 🎯 MVP

**Goal**: questions, holders, sources, evidence, claims and beliefs written and read back over
HTTP, with every rule that refuses a bad record.

**Independent Test**: quickstart tier 2. With no broker and no graph database, the launch example
is recorded and read back, sent twice with the same counts, and each rule's breaking case is
refused by name.

### Tests for User Story 1 (write first; they fail until the story is built)

- [ ] T016 [US1] Create `test/steps/RecordSteps.scala` (the Given/When/Then of `features/record/` except withdrawal, speaking to the service only through `ServiceFixture`'s HTTP client; a refusal step asserts the `rule` and the `names` the scenario says it names) and one suite per file, each a `GherkinSuite` over a single feature file mixing in those steps: `test/QuestionsFeatures.scala`, `HoldersAndSourcesFeatures.scala`, `EvidenceFeatures.scala`, `ClaimsFeatures.scala`, `BeliefsFeatures.scala`

### Implementation for User Story 1

- [ ] T017 [P] [US1] Question: `modules/belief/.../belief/domain/Question.scala` (question and hypothesis as in data-model.md, with codecs) and `application/QuestionEntity.scala` (key value entity `question`: `open`, `add-hypothesis`, `get`; the same record again is answered with the held one, a different one is `id.held-by-another`); with `QuestionEntitySuite.scala` on `KeyValueEntityTestKit`
- [ ] T018 [P] [US1] Holder: `domain/Holder.scala` and `application/HolderEntity.scala` (key value entity `holder`: `register` making the sending writer speak for it, `add-writer` accepted only from a writer who speaks for it, `get`); with `HolderEntitySuite.scala`
- [ ] T019 [P] [US1] Source: `domain/Source.scala` and `application/SourceEntity.scala` (key value entity `source`: `register`, `get`); with `SourceEntitySuite.scala`
- [ ] T020 [P] [US1] Evidence: `domain/Evidence.scala` (the digest of research R11 as its identifier) and `application/EvidenceEntity.scala` (key value entity `evidence`: `record`, `get`); with `EvidenceEntitySuite.scala` showing the same source, locator and excerpt give one identifier
- [ ] T021 [P] [US1] Claim: `domain/Claim.scala` and `application/ClaimEntity.scala` (key value entity `claim`: `state`, `get`), and `application/ClaimRows.scala` (a view over the claim entity's state, one row per claim holding identifiers and dates only, never the statement, queried by the claim it revises; research R17); with `ClaimEntitySuite.scala`
- [ ] T022 [P] [US1] Belief: `domain/Belief.scala` (revision, `restsOn`, weights) and `application/BeliefEntity.scala` (event sourced entity `belief`, id `<holder>~<question>~<hypothesis>`, state `current` and `count`, event `RevisionStated`, command `state-revision` enforcing every "decided by the entity" rule in data-model.md including `unlessUnchanged`); with `BeliefEntitySuite.scala` on `EventSourcedTestKit` covering the three transitions drawn there
- [ ] T023 [P] [US1] Revision read records: `application/RevisionRecordEntity.scala` (key value entity `belief-revision`, written once) and `application/RevisionRecords.scala` (a consumer over the belief entity's events that writes one per revision with its `n` and `sequence`; research R8); with suites on `KeyValueEntityTestKit` and `ConsumerTestKit`
- [ ] T024 [US1] Create `modules/belief/.../belief/domain/Rules.scala`: every rule under "Any record", "Questions and holders", "Evidence", "Claims" and "Beliefs" in contracts/refusals.md as a pure function from already-read facts to a `Refusal` or nothing; with `RulesSuite.scala` breaking each rule once and asserting its name and `names`
- [ ] T025 [US1] Create `modules/belief/.../belief/application/Recorder.scala` (research R3): for each kind of write, read the records it names by id through the component client, apply `Rules`, default `dated` and stamp `recordedAt` from the `Clock`, carry the writer, send the one command; it is constructed with a `BeliefConfig` (the holder kinds it accepts, the excerpt limit, the stewards) and reads no settings itself; for a belief revision, look up the read record first for repeat-safety and write it straight after the command (R8); with `RecorderSuite.scala` over a stubbed transport
- [ ] T026 [US1] Create the endpoints of contracts/http-api.md "Belief layer: writes and read-back" in `modules/belief/.../belief/api/`: `QuestionsEndpoint.scala`, `HoldersEndpoint.scala`, `SourcesEndpoint.scala`, `EvidenceEndpoint.scala`, `ClaimsEndpoint.scala` (with `revisedBy` from `ClaimRows`), `BeliefsEndpoint.scala` (including `GET /beliefs/line`, which walks `follows` from the head a page at a time); all through `Replies.handle`, none with more than two path parameters
- [ ] T027 [US1] Create `modules/belief/.../belief/BeliefLayer.scala` (the layer's holder kinds `agent`, `person` and `model`, the `BeliefConfig` value the service fills from its `Settings`, the component list and the endpoints) and register it in `modules/service/.../service/Main.scala` behind the `Writers` ACL, handing the `Recorder` the holder kinds of every registered layer
- [ ] T028 [P] [US1] Create `seed/launch-example.json` (the launch example of `GLOSSARY.md`, record by record, dated 4 and 11 May 2026, with the identifiers quickstart.md uses: question `launch` with hypotheses `yes` and `no`, holder `agent-a`, sources `company-x-filings` and `regulator`, claims `launch.pending` and `launch.barrier-gone`, revisions `agent-a.launch.1` and `agent-a.launch.2`) and `modules/seed/.../seed/Seed.scala` (posts a file's records in order to a base URL, stops on a refusal with its body); with `test/SeedSuite.scala` sending the file twice to `ServiceFixture` and asserting the second pass creates nothing
- [ ] T029 [US1] Make the five feature suites of T016 pass; then try quickstart tier 2's "to see it can fail" once and restore it

**Checkpoint**: US1 is complete and testable on its own. This is the MVP.

---

## Phase 4: User Story 2 — The reasoning is a graph anyone can follow (Priority: P1)

**Goal**: every record a node, every link an edge, in Neo4j through the real sink; a declared
vocabulary; a wait; a rebuild.

**Independent Test**: quickstart tier 3, `*PublicationFeatures *VocabularyFeatures`: walk from the
current belief revision to the regulator source in the graph database; empty and rebuild it and
compare.

### Tests for User Story 2

- [ ] T030 [US2] Settle V3, test first: create `test/GraphFixture.scala` (one per test JVM: a container network; Kafka from `reasoning.kafka.image` with an internal listener for containers and a mapped one for the host; the topic created compacted with the settings of contracts/deployment.md; Neo4j from `reasoning.neo4j.image`; the sidecar from `reasoning.sink.image` configured as the sink alone from generated `streamlet.conf`, descriptor and credential files; helpers to stop and start the sink under a new consumer group, to empty Neo4j, to pause Neo4j, and to re-produce every record on the topic) and `test/SinkSmokeSuite.scala` producing one hand-written delta and finding its node in Neo4j; record V3's outcome
- [ ] T031 [US2] Create `test/steps/GraphSteps.scala` and the suites `test/PublicationFeatures.scala` and `test/VocabularyFeatures.scala` over `features/graph/`, on `GraphFixture` with the launch example seeded through `Seed`; list the scenario *nothing is published that the vocabulary does not name* in `ranElsewhere` until T052 binds its market step

### Implementation for User Story 2

- [ ] T032 [P] [US2] Add the belief layer's vocabulary to `modules/belief/.../belief/BeliefLayer.scala`: the seven node kinds and eleven edge kinds of contracts/graph-vocabulary.md "Belief layer", each with its publishing kind of record, beside the holder kinds T027 declared; with `BeliefVocabularySuite.scala` asserting it equals the contract's tables and that no kind has two publishers
- [ ] T033 [P] [US2] Create the key value records' graph consumers in `modules/belief/.../belief/application/graph/`: `QuestionGraph.scala` (the question, its hypotheses and their `ANSWERS` edges), `HolderGraph.scala`, `SourceGraph.scala`, `EvidenceGraph.scala` (`FROM_SOURCE`), `ClaimGraph.scala` (`STATED_BY`, `DERIVES_FROM`, `SUPPORTS`, `CONTRADICTS`, `REVISES`), each over `ChangeSource.stateOf`, each built only through `Elements`, each exposing the function from a record to its elements; with suites on `ConsumerTestKit.graph` asserting keys, versions, properties and edges
- [ ] T034 [P] [US2] Create `modules/belief/.../belief/application/graph/BeliefGraph.scala` over `ChangeSource.eventsOf(BeliefEntity)`: the revision's node and its `HELD_BY`, `BELIEF_IN`, `RESTS_ON` (with `weight`) and `FOLLOWS` edges at the event's sequence number; with `BeliefGraphSuite.scala`
- [ ] T035 [US2] Register the graph consumers in `BeliefLayer.scala` only when a broker is configured, on the topic the service passes in from its `Settings`, logging once when there is none (rule D1); extend `ServiceStartsSuite` to show the service starts both ways
- [ ] T036 [US2] Create `modules/graph/.../graph/GraphReader.scala` (the port: `versionsOf(element keys)`, and one method per answer added by later stories) and `modules/graph/.../graph/Neo4jGraph.scala` (the driver opened from a connection value the service passes in, since `graph` cannot see the service's `Settings`; closed on shutdown, `unavailable` when not configured or not reachable; rule D2); with `Neo4jGraphSuite.scala` against the Neo4j container, which also settles V11 by running a query from a virtual thread
- [ ] T037 [US2] Create `modules/service/.../service/GraphEndpoint.scala`: `POST /graph/wait` (research R10: read the record's entity for its version, compute its element keys with the function its consumer exposes, poll `versionsOf` until every node and edge is at that version or the limit passes) and `GET /graph/vocabulary` (the composed vocabulary as JSON)
- [ ] T038 [US2] Create `deploy/pipeline/blueprint.conf` and `deploy/pipeline/kind.conf` per contracts/deployment.md "The pipeline"; settle V6 by running `flow verify` from an ankka-flow `v0.3.0` checkout and correct the topic setting's spelling in the blueprint and in contracts/deployment.md; record the outcome
- [ ] T039 [US2] Make `PublicationFeatures` and `VocabularyFeatures` pass (the rebuild scenario stops the sink, empties Neo4j and starts the sink under a new group; the "cannot be reached" scenarios pause Neo4j); settle V10 with `test/LatencySuite.scala` recording 100 records' time to the graph and asserting nine in ten under five seconds; add `test/VocabularyQuerySuite.scala`, which reads the trace query out of contracts/graph-vocabulary.md "Reading it" and runs it as written against the launch example, expecting the claim, evidence and source behind the current revision (SC-009)

**Checkpoint**: the launch example is in Neo4j and can be walked by hand (quickstart tier 4's first half).

---

## Phase 5: User Story 3 — Ask why a belief changed (Priority: P1)

**Goal**: the explanation of a belief change and the case for and against a hypothesis, made of
records only.

**Independent Test**: `*BeliefChangeFeatures *CaseFeatures`. For the launch example the answer names
the regulator's evidence, the claim derived from it and the claim it revised, each with its source
(SC-001).

### Tests for User Story 3

- [ ] T040 [US3] Create `test/steps/AnswerSteps.scala` (including a check, applied to every answer a step receives, that each string in it is a property of a held record, an identifier or a field name of contracts/http-api.md) and the suites `test/BeliefChangeFeatures.scala` and `test/CaseFeatures.scala` over `features/explain/belief-change.feature` and `case.feature`

### Implementation for User Story 3

- [ ] T041 [P] [US3] Create `modules/graph/.../graph/Answers.scala`: the shared answer shapes of contracts/http-api.md (`support`, evidence with its source, `side`) with JSON codecs, each field a record's property or identifier
- [ ] T042 [US3] Add to `GraphReader` and `Neo4jGraph` the Cypher for a belief's revision with what it rests on, for two revisions of one belief, for the claims taking a stance on a hypothesis, and for the beliefs about a question whose current revision rests on a revised claim; each query starts from a node matched on its `id` (research R9); with cases in `Neo4jGraphSuite.scala`
- [ ] T043 [US3] Create `modules/belief/.../belief/answers/BeliefChange.scala` (newly, no longer and still rested on with both weights; evidence observed between the two revisions; refusing revisions of two beliefs with `answer.revisions.of-two-beliefs`), `answers/Case.scala` (claims by stance, revised claims shown apart, the holders whose current revisions rest on each) and `answers/RestingOnRevised.scala`
- [ ] T044 [US3] Create `modules/belief/.../belief/api/AnswersEndpoint.scala` with `/answers/belief-change`, `/answers/belief`, `/answers/case` and `/answers/resting-on-revised`, answering `503` with `graph.unavailable` when the graph database cannot be read (rule H5); register it in `BeliefLayer.scala`
- [ ] T045 [US3] Make `BeliefChangeFeatures` and `CaseFeatures` pass

**Checkpoint**: the acceptance example of the feature works end to end.

---

## Phase 6: User Story 4 — Ask what was believed at a past time (Priority: P2)

**Goal**: every answer as of a date, and as recorded by a time; what was learned after a time.

**Independent Test**: `*AsOfFeatures`. As of 6 May the answer is 0.38 resting on the first claim,
with the regulator's evidence and the second claim absent.

- [ ] T046 [US4] Create the suite `test/AsOfFeatures.scala` over `features/explain/as-of.feature`, with steps that set the fixture's clock so a record can be dated one day and recorded on another
- [ ] T047 [US4] Give every answer query in `Neo4jGraph` the two optional bounds of research R9 (`dated <= asOf`, `recordedAt <= asRecordedBy`, a claim revised only by a claim that counts, a belief's revision the last that counts) and read `asOf` and `asRecordedBy` on every `/answers` route in `AnswersEndpoint.scala`
- [ ] T048 [P] [US4] Create `modules/belief/.../belief/answers/Learned.scala` and the route `/answers/learned` (evidence, claims and revisions about a question dated later than a time, oldest first, each with the time it was recorded)
- [ ] T049 [US4] Make `AsOfFeatures` pass; add `test/AsOfPropertySuite.scala` asking every answer of the launch example at each day of May and asserting none holds a record dated later, and that an answer as recorded by a time is unchanged after a back-dated record is entered (SC-006)

**Checkpoint**: history is readable both ways.

---

## Phase 7: User Story 5 — See where two holders disagree, a market among them (Priority: P2)

**Goal**: comparison of two holders' beliefs; the market layer above the belief layer, with price
observations as the market's belief revisions.

**Independent Test**: `*DisagreementFeatures *MarketsFeatures`. A market at 0.48 compared with the
holder at 0.61 gives the difference 0.13, the holder's claims, and the market stating no reasons.

### Tests for User Story 5

- [ ] T050 [US5] Create `test/steps/MarketSteps.scala` and the suites `test/DisagreementFeatures.scala` and `test/MarketsFeatures.scala` over `features/explain/disagreement.feature` and `features/market/markets.feature`

### Implementation for User Story 5

- [ ] T051 [P] [US5] Create `modules/belief/.../belief/answers/Comparison.scala`, its Cypher in `Neo4jGraph` and the route `/answers/comparison` (both probabilities, the difference, claims both rest on with each weight, claims only one rests on, `statesNoReasons`; refusing beliefs in two hypotheses with `answer.beliefs.in-two-hypotheses`)
- [ ] T052 [P] [US5] Create the market layer's records in `modules/market/.../market/`: `domain/Market.scala`, `domain/Rules.scala` (the `market.*` rules for opening and for price observations in contracts/refusals.md, with `RulesSuite.scala`), `application/MarketEntity.scala` (key value entity `market`: `open`, `get`), `application/MarketGraph.scala` (the `Market` node and its `ABOUT`, `OFFERS` and `SPEAKS_AS` edges) and `MarketLayer.scala` (its vocabulary, adding the holder kind `market`, and its components); with entity, consumer and vocabulary suites; then bind the market step of *nothing is published that the vocabulary does not name* and remove it from `ranElsewhere` in `VocabularyFeatures.scala`
- [ ] T053 [US5] Create `modules/market/.../market/application/MarketRecorder.scala` (research R18): opening a market registers the holder `market.<id>` and then creates the market; a price observation becomes a revision of that holder's belief, sent through the belief layer's `Recorder` with `unlessUnchanged` and the derived identifier of data-model.md; with `MarketRecorderSuite.scala`
- [ ] T054 [US5] Create `modules/market/.../market/api/MarketsEndpoint.scala` with `POST /markets`, `GET /markets/{market}` and `POST /markets/{market}/price-observations`, and register `MarketLayer` in `modules/service/.../service/Main.scala`
- [ ] T055 [US5] Make `DisagreementFeatures` and `MarketsFeatures` pass; add `test/BeliefOnlySuite.scala` running the record, graph and explain feature suites against a service with `MarketLayer` not registered, every scenario that names no market passing (SC-008)

**Checkpoint**: a layer sits on the belief layer and the belief layer does not know.

---

## Phase 8: User Story 7 — Take text out of a record without breaking the reasoning (Priority: P2)

**Goal**: the text of evidence or of a claim withdrawn for good, the record and its links kept.

**Independent Test**: `*WithdrawalFeatures *ErasureSuite`. After the regulator's evidence is
withdrawn there is no excerpt in the service, in Neo4j or in a rebuilt Neo4j, and the explanation
still holds with the evidence shown as withdrawn.

### Tests for User Story 7

- [ ] T056 [US7] Settle V1, test first: create `test/ErasureSuite.scala` that records evidence with a marker string in its excerpt, withdraws it, and searches every row of the service's database for the marker's bytes; it fails until T058 and its outcome is recorded
- [ ] T057 [US7] Create `test/steps/WithdrawalSteps.scala` and the suite `test/WithdrawalFeatures.scala` over `features/record/withdrawal.feature` on `GraphFixture`, with a steward configured through `REASONING_STEWARDS`

### Implementation for User Story 7

- [ ] T058 [P] [US7] Add `withdraw` to `EvidenceEntity.scala` and `ClaimEntity.scala`: the state written again without `locator`, `excerpt` and `author`, or without `statement`, with the `Withdrawal` of data-model.md set; a second withdrawal answered with the record; extend both entity suites
- [ ] T059 [US7] Add the withdrawal rules to `domain/Rules.scala` and `application/Recorder.scala` (`withdrawal.note.none`; `withdrawal.writer.may-not` unless the writer recorded the evidence, speaks for the claim's holder, or is one of the stewards in `BeliefConfig`) and the routes `POST /evidence/{evidence}/withdrawal` and `POST /claims/{claim}/withdrawal`; recording withdrawn evidence again answers with the withdrawn record (FR-004)
- [ ] T060 [US7] Make `EvidenceGraph.scala` and `ClaimGraph.scala` publish a withdrawn record's node without its text, with `withdrawn` and `withdrawnAt`, and make every answer shape in `Answers.scala` carry `withdrawal` and omit the text; this settles V2 through the scenarios *withdrawn text is gone from the graph* and *a graph database rebuilt after a withdrawal does not hold the withdrawn text*
- [ ] T061 [US7] Create `test/CompactionErasureSuite.scala`: on a topic with compaction lags of a second, withdraw a record, wait for the broker to compact its key, read the topic from the start and assert no record holds the marker (FR-036)
- [ ] T062 [US7] Make `WithdrawalFeatures` and `ErasureSuite` pass

**Checkpoint**: the one exception to nothing being edited is proven where it matters.

---

## Phase 9: User Story 6 — Resolve a market on evidence (Priority: P3)

**Goal**: a resolution as a record, why a market was resolved, and what each holder believed then.

**Independent Test**: `*ResolutionFeatures`. The launch example's market resolved to YES answers
with its outcome, authority, evidence and source, and lists each holder's last earlier revision.

- [ ] T063 [US6] Create the suite `test/ResolutionFeatures.scala` over `features/market/resolution.feature`, extending `MarketSteps.scala`
- [ ] T064 [P] [US6] Add the resolution to `modules/market/.../market/domain/Market.scala`, its rules to `domain/Rules.scala` (the `market.resolution.*` rules and `market.outcome.not-offered`), `resolve` to `application/MarketEntity.scala` (appended to the market's state; `revises` must be the current resolution) and to `MarketRecorder.scala` (evidence held and dated no later; the writer speaks for the market's holder); extend the suites
- [ ] T065 [P] [US6] Add the `Resolution` node and the `RESOLUTION_OF`, `RESOLVES_TO`, `ON_EVIDENCE` and `REVISES_RESOLUTION` edges to `MarketLayer.scala`'s vocabulary and to `MarketGraph.scala`; extend their suites
- [ ] T066 [US6] Create `modules/market/.../market/answers/WhyResolved.scala` and `answers/BeliefsAtResolution.scala` with their Cypher in `Neo4jGraph`, and add `POST /markets/{market}/resolutions`, `GET /markets/{market}/resolution` and `GET /markets/{market}/beliefs-at-resolution` to `MarketsEndpoint.scala`
- [ ] T067 [US6] Make `ResolutionFeatures` pass

**Checkpoint**: every user story is complete.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: the seeded set, the measurements, deployment, and closing what planning left open.

- [ ] T068 Create `modules/seed/.../seed/Generate.scala`, a deterministic generator, and commit its output `seed/ten-questions.json`: ten questions with at least 500 records between them, several holders with different beliefs, revised claims, back-dated history, three markets with price observations and one resolved; every name made up
- [ ] T069 Create `test/SeededSetSuite.scala` on `GraphFixture` over `seed/ten-questions.json`: every belief revision that rests on a claim reaches a source (SC-002); the whole file sent twice leaves the record counts and the graph unchanged (SC-003); no path returns to its start (SC-004); every answer is identical after the graph database is emptied and rebuilt (SC-005); no answer as of a time holds a later record, and none as recorded by a time changes after a back-dated record is entered (SC-006); withdrawn text is in no answer and no rebuilt graph (SC-011); every node and edge in the graph is of a kind the vocabulary names, with only its properties (FR-017)
- [ ] T070 [P] Create `modules/seed/.../seed/Measure.scala` and the `just measure` recipe: the time from a record's `201` to its wait ending, for every record of the seeded set, and the time to answer `/answers/belief-change` on a question grown to 1,000 records; record both under `## Measurements` in research.md against SC-007
- [ ] T071 [P] Create `deploy/service.json` per contracts/deployment.md "The service descriptor" and `test/DeploymentSuite.scala` decoding it with ankka's descriptor rules (`ankka-controlplane-api` as a test dependency in `project/Dependencies.scala`)
- [ ] T072 [P] Create `notes/ankka-requests.md` from research.md "Requests", one section per request with what it would remove from this repository
- [ ] T073 Run quickstart tiers 4 and 5 by hand, tier 5 on kind beside ankka and ankka-flow, and record what was run and seen at the end of `specs/001-belief-layer/quickstart.md`
- [ ] T074 Close the V-list: under `## Found during implementation` in research.md, a table of V1 to V11 with each outcome (passed, not verified, settled differently) and anything else implementation found
- [ ] T075 Update `README.md` (status, how to run it, "What is here now"), `CLAUDE.md` (a Commands section with the sbt and just commands that now exist) and `../ankka-brain/repos/ankka-reasoning.md` (stage, the ankka and ankka-flow pins, the open questions)
- [ ] T076 Tick quickstart.md's reviewer's checklist: `sbt scalafmtCheckAll test` from a clean checkout, `just features`, each "to see it can fail" tried once

---

## Dependencies & Execution Order

- **Setup (Phase 1)** then **Foundational (Phase 2)** block everything.
- **US1** needs only the foundation.
- **US2** needs US1's records to publish.
- **US3** needs US2 (the graph and the fixture).
- **US4** needs US3's answers, which it extends.
- **US5** needs US2 for its reads and US1 for its writes; its comparison (T051) also needs US3's answer shapes. It does not need US4.
- **US7** needs US2 and US3, since it changes what they publish and show, and US4, since one of its scenarios reads as recorded by an earlier time. It does not need US5 or US6.
- **US6** needs US5 (the market).
- **Polish** needs every story.

Within a story: the feature suite first, then entities and rules, then the recorder, then
endpoints, then the suite made to pass.

```text
Setup → Foundational → US1 → US2 → US3 ─┬→ US4 → US7
                                        └→ US5 → US6
```

## Parallel Execution Examples

- Phase 1: T003, T004, T005 and T006 together, once T001 and T002 exist.
- Phase 2: T009, T010, T011, T012 and T015 together.
- US1: the six record kinds, T017 to T023, together; T028 beside T024 to T027.
- US2: T032, T033 and T034 together, once T031 exists.
- After US3: US4 and US5 can be taken by two people at once, then US7 and US6; all four touch `Neo4jGraph.scala` and `Answers.scala`, so merge in phase order.
- Polish: T070, T071 and T072 together.

## Implementation Strategy

**MVP**: Phases 1 to 3. A service that holds the launch example, refuses what breaks a rule, and is
proven by the record features with nothing else running.

**First thing worth showing**: add Phase 4. The launch example in Neo4j, walked by hand.

**The feature's point**: add Phase 5. "Why did the belief go from 0.38 to 0.61?" answered with
records.

Then US4 and US5 in either order, US7 after US4, US6 after US5, and the polish phase last. Stop at any
checkpoint: each is a working increment with its scenarios passing.

A "Verify first" task that fails is a finding, not a failure. Record it in research.md, change the
decision it rested on there, and only then go on.
