# Tasks: the process layer

**Input**: Design documents from `/specs/002-process-layer/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: included. The spec's acceptance scenarios are the living features under
`features/process/` and the two changed in `features/graph/vocabulary.feature`, and every one runs
as a test (spec 001 R12). A story's feature suite is written first and seen to fail on unbound
steps; a claim in research.md's "Verify first" (V1 to V14) is settled by the first task that would
show it false, and its outcome written beside it.

**Organization**: by user story, in the spec's priority order, after two phases that are not a
story: the sink moved and the build pinned (the prerequisite the spec names, research R1 and R2),
and the process module's records and projection (research R3 to R7), which every story writes
through. US1 (a run writes as a holder) is the MVP; US2 (runs in the graph) makes it visible; US3
(the golden set) is the acceptance example; US4 (live) is optional and skipped without keys.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 to US4 from spec.md
- Paths are repository-relative. `process/.../` abbreviates
  `modules/process/src/main/scala/reasoning/process/`, `service/.../`
  `modules/service/src/main/scala/reasoning/service/`, and `test/`
  `modules/service/src/test/scala/reasoning/`.

## Path Conventions

Six sbt modules under `modules/` (`graph`, `belief`, `market`, `process`, `service`, `seed`), the
living features at `features/`, the golden set at `golden-set/`, seed data at `seed/`, deployment
files at `deploy/` and `compose/`. See plan.md *Project Structure*. Rule names are those in
contracts/refusals.md; routes those in contracts/http-api.md; labels and edge types those in
contracts/graph-vocabulary.md; the blueprint and tools those in contracts/blueprint.md.

---

## Phase 1: Setup (the sink moved, the build pinned)

**Purpose**: every scenario of spec 001 passes against ankka-contrib's sink and the ankka release
that carries blueprints, with nothing of this feature built. Worth a pull request of its own.

- [x] T001 **Blocked until the ankka release carrying blueprints is on Maven Central.** In `project/Dependencies.scala` set `V.ankka` to that release (before it exists, prove Phases 1 and 2 locally with `sbt -Dankka.version=<publishLocal of ankka main>` and push nothing that changes the pin, so a clean checkout and CI keep resolving), add `ankkaAgent = ankka("agent")`, and replace `V.sinkImage` with `ghcr.io/thinkmorestupidless/ankka-graph-sink-neo4j:0.1.0`; record V1 in research.md "Found during implementation"
- [x] T002 In `build.sbt` add `ankkaAgent` to `belief`'s and `service`'s dependencies (the test providers come with it), keep the `-Dreasoning.sink.image` forwarding, and remove nothing else yet
- [x] T003 Rewrite `test/support/GraphFixture.scala` `startSink()` to run `image("reasoning.sink.image")` with the environment `ANKKA_GRAPH_SINK_TOPIC=reasoning-graph`, `ANKKA_GRAPH_SINK_VERSION=<N, one higher per start>`, `NEO4J_URI=neo4j://neo4j:7687`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`, `NEO4J_DATABASE=neo4j`, `ANKKA_KAFKA_BOOTSTRAP_SERVERS=kafka:19092`, and no mounted files; drop the generated `streamlet.conf`, the descriptor copy and the credential files; keep `stopSink`, `emptyGraph`, `pauseGraph`, `sinkLogs`
- [x] T004 Run `sbt 'service/testOnly *SinkSmokeSuite *PublicationFeatures *WithdrawalFeatures *CompactionErasureSuite'` and record V2 (the image pulls anonymously; `Element`, `id`, `_version`, `_deleted`, placeholders at `-1` are as `Neo4jGraph.versionsOf` reads them); fix `test/GraphFeatureSuite.sinkHasDrained` and `MarketFeatureSuite.SinkOwn` if the store's own properties differ
- [x] T005 [P] Replace the `sink` service in `docker-compose.yml` with the contrib image configured by environment (`REASONING_SINK_IMAGE` override kept; `ANKKA_GRAPH_SINK_VERSION` from `REASONING_SINK_VERSION`, default 1), delete `compose/flow-graph/` and `compose/neo4j-secret/`, and rewrite `compose/rebuild.sh` to stop the sink, empty Neo4j in batches, and start the sink with `REASONING_SINK_VERSION` one higher (written to `.env`); record V3 after `just up`, `just seed launch-example`, `just rebuild`
- [x] T006 [P] Replace `deploy/pipeline/` with `deploy/graph-sink.json` as contracts/deployment.md "The sink" (image from `${SINK_IMAGE}`, `"http": false`, `"database": "none"`, the six variables, the secret `reasoning-graph`); extend the `deployDescriptors` task in `build.sbt` to render it with `V.sinkImage`; update `DeploymentSuite` to check both descriptors' variable names
- [x] T007 [P] In `.github/workflows/ci.yml` remove the ankka-flow checkout and the two sidecar build steps; nothing else changes
- [x] T008 Replace `FeatureSuite.directoryOf` in `test/steps/FeatureSuite.scala` with the release's one-file `GherkinSuite` and delete `target/feature-suites` handling; in `service/.../Writers.scala` identify a gateway caller as `person:<subject>@<issuer>` through `ankka-auth-oidc` as spec 001 R5's second row; record V14 and close F1 and F2 in `specs/001-belief-layer/research.md` with one line each
- [x] T009 Run `sbt clean scalafmtAll test` and record the result (every suite of spec 001, under the new sink and release) as the first line of quickstart.md "What was run"; record V10 (whether the kit shares one Postgres)

**Checkpoint**: `sbt test` green with no ankka-flow anywhere in the tree but `notes/` and `specs/001-belief-layer/`.

---

## Phase 2: Foundational (the process module and its projection)

**Purpose**: the layer's records exist, are published to the graph with their vocabulary, and the
service hosts ankka's blueprints with an empty registry. No story can be built before this.

- [x] T010 Add the `process` module to `build.sbt`: depends on `market`; published as `ankka-reasoning-process`; `Compile / unmanagedResourceDirectories += (ThisBuild / baseDirectory).value / "golden-set"`; `service` depends on `process`; the common settings as the others
- [x] T011 [P] Create `process/.../domain/Records.scala` with `BlueprintVersion(id, blueprint, version, digest, dated, recordedAt, writer)`, `Run(id, blueprint, version, startedBy, dated, recordedAt, writer)`, `RunEnding(run, status: RunStatus, reason, dated, recordedAt, writer)`, `Act(id, run, step, worker, blueprint, version, record: RecordRef(kind, id), holder, dated, recordedAt, writer)`, `enum RunStatus { completed, failed, cancelled }` with `fromAnkka(status)` (`running` and `waiting-for-decision` give `None`), and jsoniter codecs as data-model.md
- [x] T012 [P] Create `process/.../domain/Ids.scala`: `Ids.version(blueprint, n) = s"$blueprint@$n"`; `Ids.act(run, kind, id)` = `"act:" + first 56 hex of SHA-256 of s"$run\n$kind\n$id\n"`; `Ids.claim(run, statement)` = `s"$run." + 16 hex of SHA-256 of the statement`; `Ids.revision(run, hypothesisId)`; `Ids.RunIdLimit = 47`; with `process/src/test/scala/reasoning/process/IdsSuite.scala` round-tripping lengths against spec 001's 64-character limit
- [x] T013 [P] Create `process/.../domain/ProcessRules.scala` with pure rules returning `Option[Refusal]`: `runIdFormat` (`run.id.too-long`, `schedule:` prefix), `runNotHeld`, `versionNotHeld`, `actWorkerUnknown`, `actRecordNotHeld`, `documentNotInTranche`, `restFormat`, `toolCalledInNoRun`, `endingNotBeforeRun`; and `process/.../domain/Same.scala` for the four records (compare what ankka supplied; a repeat is equal content)
- [x] T014 Create `process/.../application/Entities.scala`: `BlueprintVersionEntity` (`blueprint-version`), `RunEntityRecord` (`run`; named so as not to clash with ankka's `RunEntity`), `RunEndingEntity` (`run-ending`), `ActEntity` (`act`), each a `KeyValueEntity[Slot[A]]` with `put`/`get`/`version` as the belief layer's, repeat by `Same`, `id.held-by-another` otherwise; unit suites in `process/src/test/scala/reasoning/process/EntitiesSuite.scala` through `KeyValueEntityTestKit`
- [x] T015 Create `process/.../application/ActRows.scala`: view `act-rows` over `ChangeSource.stateOf(ActEntity)`, row `ActRow(id, run, recordKind, recordId, holder, dated)`, queries `byRun(run)` and `byRecord(kind, id)`
- [x] T016 Create `process/.../application/ProcessRecorder.scala`: `holdVersion(BlueprintVersion)`, `holdRun(Run)` (checks the version is held: `blueprint-version.not-held`), `holdEnding(RunEnding)` (checks the run is held and `dated` not before it), `holdAct(Act)` (checks run, version, holder spoken for by `blueprint:<name>`, record held; answers the earlier act when one is held for the record by another run); every read of an entity by id, never of a view; `published(kind, id): Option[(Vector[Element], Long)]` for the four kinds
- [x] T017 Create `process/.../application/Followers.scala`: `BlueprintVersions`, a consumer over `ChangeSource.eventsOf(BlueprintEntity)` holding a `BlueprintVersion` on `VersionRegistered` (writer the service's own, from `ProcessConfig.serviceWriter`); `RunFollower`, a consumer over `ChangeSource.eventsOf(RunEntity)` holding a `Run` on `Started` (after `holdVersion` from `agents.blueprints.versions`) and a `RunEnding` on `Ended` with `RunStatus.fromAnkka`; both through `ProcessRecorder`; `ConsumerTestKit` suites feeding each event in `process/src/test/.../FollowersSuite.scala`; record V7
- [x] T018 [P] Create `process/.../ProcessVocabulary.scala`: layer `process`; nodes `BlueprintVersion` (`blueprint-version`), `Run` (`run`), `RunEnding` (`run-ending`), `Act` (`act`) with the properties of contracts/graph-vocabulary.md; edges `OF`, `ENDED`, `IN`, `WROTE`, `AS` with `publishedBy`; no holder kind; and `process/src/test/.../ProcessVocabularySuite.scala` asserting every edge's `from` is a label of this layer, no label or type of this page is in `BeliefVocabulary` or `MarketVocabulary`, and `(BeliefLayer.vocabulary ++ MarketLayer.vocabulary ++ ProcessLayer.vocabulary).problems` is empty
- [x] T019 Create `process/.../application/graph/ProcessGraphOf.scala` (one pure function per record: the node with `dated`/`recordedAt` and the edges that leave it, `WROTE` to `evidence:<digest>`, `claim:<id>` or `revision:<id>` through `BeliefVocabulary`'s ids) and `process/.../application/graph/Consumers.scala` (`blueprint-version-graph`, `run-graph`, `run-ending-graph`, `act-graph` over `stateOf` each entity, inside `On(topic)`); `ConsumerTestKit.graph` suites in `process/src/test/.../ProcessGraphSuite.scala` asserting every element is in the vocabulary
- [x] T020 Create `process/.../ProcessLayer.scala`: `vocabulary`, `components` (the four entities, the view, the two followers), `graphComponents(topic)`, `recorder(clients, clock, config)`, `published`, `registry(ctx): BlueprintRegistry` (empty until US1), `endpoints(...)` (empty until US1); `ProcessConfig(serviceWriter, appraiserHolders, goldenSetDir: Option[Path])`
- [x] T021 In `service/.../Main.scala` compose the process layer: `vocabulary` adds `ProcessLayer.vocabulary`; `components` adds `ProcessLayer.components`, `AgentRuntime.descriptors` and, when publishing, `ProcessLayer.graphComponents(topic)`; `extensions` adds `AgentRuntime.withDefaultModel(Providers.model).withJudgments(Providers.judgments).withBlueprints(ProcessLayer.registry)`; `endpoints` adds `ProcessLayer.endpoints` and passes `ProcessLayer.published` to `GraphEndpoint`; `Settings.scala` gains `appraiserHolders` (`REASONING_APPRAISER_HOLDERS`) and `goldenSet` (`REASONING_GOLDEN_SET`), and `modules/service/src/main/resources/application.conf` the two keys. *Done without the two settings*, which nothing reads before T028 and T034 and which the configuration page would list: they are added there
- [x] T022 [P] Create `service/.../Providers.scala`: `model` is `AnthropicProvider.fromEnv()` when `ANTHROPIC_API_KEY` is set, else a `ModelProvider` whose every call fails with `the model key ANTHROPIC_API_KEY is not set`; `judgments` likewise over `JevProvider.fromEnv()` and `TYPESAFE_API_KEY`; `ServiceStartsSuite` asserts the service starts with neither (deployment D10)
- [x] T023 [P] In `service/.../Writers.scala` add `Writers.blueprint(name) = s"blueprint:$name"` and a test in `test/WritersSuite.scala` that no `Caller` derives a `blueprint:` writer
- [x] T024 In `service/.../GraphEndpoint.scala` add the wait kinds `blueprint-version`, `run`, `run-ending` and `act` through `ProcessLayer.published`, and serve the process layer in `GET /graph/vocabulary`
- [x] T025 In `test/support/ServiceFixture.scala` and `GraphFixture.scala` build the `AgentRuntime` with a shared `TestModelProvider` and `TestJudgmentProvider` exposed as `ServiceFixture.model` and `.judge`, reset in `FeatureSuite.beforeEach`; the registry from `ProcessLayer.registry`; record V9
- [x] T026 Write `test/steps/ProcessFeatureSuite.scala` extending `MarketFeatureSuite` with the shared steps of the three process features' Backgrounds (`the launch question with its hypotheses`, `a scripted model`, `the appraiser, registered at blueprint version 1, whose worker "reader" speaks for the holder "appraiser-a"`, `the graph has caught up`) and `test/ProcessFeatures.scala` declaring `ToolsFeatures`, `RunsFeatures`, `GoldenSetFeatures` over the three files, each failing on unbound steps; change `features/graph/vocabulary.feature`'s steps in `MarketFeatureSuite`: *the belief layer names nothing of the market layer or the process layer* and *every kind of edge in the process layer runs from a node of the process layer* pass from `GET /graph/vocabulary`. *Done as:* the vocabulary's steps. `ProcessFeatureSuite`, its Background's steps and each process suite are written in the task that binds that suite's steps (T037 `ToolsFeatures`, T041 `RunsFeatures`, T046 `GoldenSetFeatures`), so that no suite fails on an unbound step and the pull request is green

**Checkpoint**: `sbt process/test` green; `VocabularyFeatures` green with the two changed scenarios; the service starts hosting blueprints with none registered; `sbt test` green.

---

## Phase 3: User Story 1 - A run writes reasoning records as a holder (Priority: P1) 🎯 MVP

**Goal**: three tools a blueprint names write belief-layer records as the run's holder, each with
its act; the appraiser is registered and can be run over HTTP with a scripted model.

**Independent Test**: `sbt 'service/testOnly *ToolsFeatures'` (quickstart tier 2) and
`sbt process/test` (tier 1): every scenario of `features/process/tools.feature`.

- [x] T027 [P] [US1] Create `golden-set/question.json`, `golden-set/may.json`, `golden-set/june.json` and `golden-set/seed.json` as data-model.md "The golden set": the launch question, the sources, the market; five May documents (`filing`, `notice`, `trade-press`, `analyst`, `supplier-call`) and three June (`approval`, `launch-date`, `rumour`), each a few hundred made-up words with its `expectedReading` and the `v1`/`v2` weights; the weighted readings giving `0.35`/`0.72` (v1) and `0.40`/`0.66` (v2)
- [x] T028 [P] [US1] Create `process/.../appraiser/GoldenSet.scala`: reads the three files from `ProcessConfig.goldenSetDir` or the classpath; `question`, `tranche(name): Option[Tranche]`, `document(tranche, id)`; `Document`, `Tranche`, `ExpectedReading` with codecs; `process/src/test/.../GoldenSetSuite.scala` asserts eight documents, their kinds, and that each tranche's weighted readings fall on the right side of one half for both instruction sets
- [x] T029 [US1] Create `process/.../tools/RunResolver.scala`: from `RunContext.current` (else `tool.called-in-no-run`), `agents.runs.get(runId)` cached per run id, `agents.blueprints.version(blueprint, n)` cached per version; `holdVersion` then `holdRun` through `ProcessRecorder` if not held (R7); the step's single worker from the version's data (`act.worker.unknown` otherwise, R9); the holder and `follows`, `observedAt`, `tranche` from the run's input (R10); `Resolved(run, version, step, worker, input)`; record V6
- [x] T030 [US1] Create `process/.../tools/Tools.scala` with the three `FunctionTool`s of contracts/blueprint.md: `record_evidence(document, excerpt, author?)` reading provenance from the tranche (`document.not-in-tranche`), `state_claim(statement, derivesFrom, stances)` parsing `<hypothesis> supports|contradicts`, `revise_belief(hypothesis, probability, restsOn)` parsing `<claim>[@<weight>]` (`belief.rest.format`); each: resolve (T029), write through the belief `Recorder` as `blueprint:<name>` dated by `observedAt` or the clock, then `holdAct` (R8), answering `{record, act}`; a `Refused` becomes the tool's error with `rule`, `error`, `names`; record V5
- [x] T031 [US1] Create `process/src/test/.../ToolsSuite.scala` (tier 1): each tool inside `RunContext.within(RunRef(...))` against a kit holding the belief entities and a stub `RunCalls`/`BlueprintCalls`: a record is held with its act; nothing is held when the act's checks fail (an unknown run, a step with no single worker); a tool outside any run writes nothing; a repeat writes nothing twice
- [x] T032 [US1] Create `process/.../appraiser/Instructions.scala` (`v1`, `v2`: the reader's and the reviser's instructions as contracts/blueprint.md, the reviser's differing on weights) and `process/.../appraiser/Appraiser.scala`: `blueprint(instructions): Blueprint` with the five steps, two workers and shapes of contracts/blueprint.md; the handlers `load-tranche` and `critique-verdict`; the yes/no question `claim-derives-from-tranche`; `Appraiser.registry(ctx, config): BlueprintRegistry` with the tools, handlers, question and `carrying(blueprint(Instructions.v1))`
- [x] T033 [US1] Create `process/src/test/.../AppraiserSuite.scala`: `BlueprintCheck` accepts `Appraiser.blueprint(v1)` and `(v2)` with no problems, the two digests differ, and a judge step over `Each` is accepted; record V4
- [x] T034 [US1] In `process/.../ProcessLayer.scala` make `registry(ctx)` the appraiser's, and in `service/.../Main.scala` register at start the appraiser's holders (`appraiserHolders`, kind `agent`, writer `blueprint:appraiser`, a repeat after the first) and the golden set's sources, through the belief `Recorder`
- [x] T035 [US1] Create `process/.../api/RunsEndpoint.scala` (`POST /process/runs` checking `blueprint.not-held`, `run.id.too-long`, `run.input.invalid`, the holder spoken for, then `agents.runs.start`, `409 run.id.held-by-another` on `Conflict`, answering the run as held; `GET /process/runs/{run}` with `ending?` and `acts` from `ActRows`; `GET /process/runs/{run}/acts`) and `process/.../api/ActsEndpoint.scala` (`GET /process/acts/{act}`, `GET /process/acts?record=`), both with `Replies.handle`; wire into `ProcessLayer.endpoints`
- [x] T036 [US1] Create `test/steps/ScriptedAppraiser.scala` (R14): installs on `ServiceFixture.model` the standing rules that drive a run from the golden set (`Scripted.forStep`, `itemOf`, `afterTool`; `call("record_evidence", …)`, `call("state_claim", …)` with the expected reading, `answer(...)`; for `revise`, `call("revise_belief", …)` with the instruction set's probability and weights), and on `ServiceFixture.judge` `always(Answers.yesNo("claim-derives-from-tranche", 0.95))`; `withClaimFrom(evidenceId)` and `judgeNo(claimId)` as overrides a scenario installs; record V13
- [x] T037 [US1] Bind every step of `features/process/tools.feature` in `test/steps/ProcessFeatureSuite.scala`: a run started by `POST /process/runs` and awaited by `GET /process/runs/{run}` until `ending`; the outline's five rows through `ScriptedAppraiser` overrides (a source not registered, a claim from no evidence, a stance on another question's hypothesis, a rest on another holder's claim, a revision dated before its claim via `observedAt`) each refused naming the rule in the run's reason and nothing held; *a run sent twice* by posting the same body again and counting records (V12); *evidence an earlier run recorded* by two runs over one document; *a tool called in no run* bound to a direct call of `Tools.recordEvidence`'s handler outside any `RunContext` against the fixture's recorder, as a scenario may run against one component
- [x] T038 [US1] Run `sbt 'service/testOnly *ToolsFeatures' process/test`, all green; `just features` no findings; record V6, V12 and V13 outcomes

**Checkpoint**: a blueprint's run writes evidence, claims and a belief revision as its holder, each with an act, refused exactly as a writer is; MVP demonstrable with `POST /process/runs` and `GET /process/runs/{run}`.

---

## Phase 4: User Story 2 - Runs and blueprint versions are in the graph (Priority: P1)

**Goal**: every blueprint version, run, run ending and act is a node with its edges; a belief
revision traces to its act, run and version; every lower answer is offered again with acts.

**Independent Test**: `sbt 'service/testOnly *RunsFeatures *VocabularyFeatures *ProcessVocabularyQuerySuite'`
(quickstart tier 3): every scenario of `features/process/runs.feature`.

- [x] T039 [P] [US2] Create `process/.../answers/ProcessAnswers.scala` (R18): `withActs[A](lower: => A, ids: A => Seq[RecordRef]): WithActs[A]` running the one Cypher walk of contracts/graph-vocabulary.md over the records' node ids; `belief`, `beliefChange`, `caseFor`, `learned`, `comparison`, `restingOnRevised`, `whyResolved`, `beliefsAtResolution` each wrapping `BeliefAnswers`/`MarketAnswers`; `run(runId, at): RunWrote(run, ending?, wrote: [{act, record}])` (`record.not-held` when absent); shapes `ActView`, `RunView`, `RunEndingView`, `VersionView`, `WithActs(answer, acts)` in `process/.../answers/Shapes.scala`
- [x] T040 [P] [US2] Create `process/.../api/ProcessAnswersEndpoint.scala`: `/process/answers/{belief-change,belief,case,learned,comparison,resting-on-revised,run}` and `/process/markets/{market}/{resolution,beliefs-at-resolution}` with `asOf`/`asRecordedBy`, `answer` serialised with the lower layer's codecs byte for byte; wire into `ProcessLayer.endpoints`
- [x] T041 [US2] Bind every step of `features/process/runs.feature` in `test/steps/ProcessFeatureSuite.scala`: the run under a named run id over the May tranche "which has ended"; nodes and edges read with `graphRows`; *an act links…* walking from each act; *a belief revision is traced…* through `GET /process/answers/belief` with acts; *a reader lists every record a run wrote* through `/process/answers/run`; *a writer waits…* through `POST /graph/wait` for the evidence and then for its act, asserting the wait's `missing` covers the act's edges; *an emptied graph database is rebuilt…* through `GraphFixture.emptyGraph` and `startSink` at version N+1, comparing node and edge counts and `/process/answers/run` before and after, and asserting the topic's end did not move
- [x] T042 [US2] Create `test/ProcessVocabularyQuerySuite.scala` running the two Cypher queries printed in `specs/002-process-layer/contracts/graph-vocabulary.md` as written over a completed run, and `test/ProcessGraphPropertiesSuite.scala` asserting the page's "Properties a test holds": one `WROTE`/`IN`/`AS` per act, one `OF` per run, one `ENDED` per ending, one act per record the run wrote (SC-005), no element outside the vocabulary, no cycle
- [x] T043 [US2] Extend `BeliefOnlySuite` (or add `test/NoProcessSuite.scala`): every scenario of `features/record/`, `features/explain/` and `features/market/` passes with `ProcessLayer` not composed (SC-008), the fixture started with `process = false`
- [x] T044 [US2] Run `sbt 'service/testOnly *RunsFeatures *VocabularyFeatures *ProcessVocabularyQuerySuite *ProcessGraphPropertiesSuite *NoProcessSuite'`, all green; record V7 and V11 outcomes

**Checkpoint**: a belief revision walks to its act, run, ending and version in Neo4j and in one answer; an emptied graph refills with the process layer.

---

## Phase 5: User Story 3 - The golden set: the appraiser answers the three questions (Priority: P1)

**Goal**: the appraiser over both tranches for two holders from two versions, a failed critique
that revises nothing, the four answers, the market resolved afterwards, all offline.

**Independent Test**: `sbt 'service/testOnly *GoldenSetFeatures'` (quickstart tier 3): every
scenario of `features/process/golden-set.feature`, with no model key and no network.

- [ ] T045 [US3] Create `process/.../api/AppraiserEndpoint.scala`: `POST /process/appraiser/versions {instructions}` (`appraiser.instructions.unknown`; `agents.blueprints.register(Appraiser.blueprint(set))`, `201` when `isNew` else `200`), `GET /process/appraiser/versions`, `GET /process/appraiser/versions/{n}` with ankka's canonical blueprint; wire into `ProcessLayer.endpoints`
- [ ] T046 [US3] In `test/steps/ProcessFeatureSuite.scala` bind the golden set's steps: `the golden set` (the question and market from `golden-set/seed.json` under the scenario's prefix, holders registered), `a scripted model that answers from the expected readings`, `the appraiser is run for <holder> over the <month> tranche[, following …]` moving the clock to 20 May or 20 June first (R19), `the appraiser registered again with different instructions, at blueprint version 2` through `POST /process/appraiser/versions {instructions: "v2"}`, the counts of evidence, claims and revisions with acts, the probabilities, the stances against each document's expected reading
- [ ] T047 [US3] Bind *a run whose critique fails revises no belief, and what it wrote stays held*: `ScriptedAppraiser.withClaimFrom(<evidence a writer recorded from no document>)` for one reader item and `judgeNo(<that claim>)`; assert the run's ending is `failed` with a reason naming the claim, the claims held with acts, no belief for the holder; record V11
- [ ] T048 [US3] Bind the four answers and the resolution through `/process/answers/…` and `/process/markets/…`: *the case for the hypothesis…* (every claim's evidence from a June document, each with act and run), *as of a day between the tranches…* (`asOf` 1 June on `/process/answers/belief`; `/process/answers/learned` after 1 June holds exactly the June run's records), *the explanation of the change…* (`/process/answers/belief-change` newly rested on names the approval document's claim, its evidence and source, and the act and June run), *the two holders rest on the same claims with different weights* (`/process/answers/comparison`: `both` with two weights, `onlyA`/`onlyB` empty), *the market resolved afterwards…* (a writer resolves on the approval evidence, then `/process/markets/{market}/beliefs-at-resolution` shows each holder's last revision with version 1 and 2 in `acts`); *the golden set runs with no model key and no network* asserting `sys.env` lacks both keys in the test JVM and both runs completed; record V8
- [ ] T049 [US3] Run `sbt 'service/testOnly *GoldenSetFeatures'`, all green, and time the suite's four-run scenario for SC-006 (two minutes on a laptop); write the first row of research.md "Measurements"

**Checkpoint**: the three questions answered from records a process wrote, with the acts, runs and versions beside each record.

---

## Phase 6: User Story 4 - The same appraiser against a real model (Priority: P2)

**Goal**: the same blueprint over the same golden set against Anthropic and Jev, asserting
direction, the critique passing and every record with its act; skipped without keys.

**Independent Test**: `ANTHROPIC_API_KEY=… TYPESAFE_API_KEY=… sbt 'service/testOnly *LiveAppraiserSuite'`
(quickstart tier 6); without the keys, `sbt 'service/testOnly *LiveAppraiserSuite *LiveFeatures'`
reports skipped, not failed.

- [ ] T050 [US4] Create `test/support/LiveFixture.scala`: the service as `ServiceFixture` but with `AgentRuntime.withDefaultModel(AnthropicProvider.fromEnv()).withJudgments(JevProvider.fromEnv())`, started lazily and only when both keys are set
- [ ] T051 [US4] Create `test/LiveAppraiserSuite.scala`: `assume` on both keys; the golden set seeded; the appraiser for `appraiser-a` over May then June; asserts the first revision below `0.5` and the second above, both endings `completed`, every record of both runs with an act (through `/process/runs/{run}/acts` against `/process/answers/run`); on a failed critique the message names the document whose reading the model got wrong (from the claim's evidence)
- [ ] T052 [US4] Create `LiveFeatures` in `test/ProcessFeatures.scala` over `features/process/live.feature` with steps in `test/steps/LiveFeatureSuite.scala`: *the live suite is skipped, not failed, when there is no model key* bound by running the munit suite programmatically with the keys unset and asserting its events are skips; the two real-model scenarios `assume` the keys and delegate to the same assertions as T051
- [ ] T053 [US4] Run the live suite ten times with keys and record SC-007 (direction right, critique passed, every record with its act, in N of 10) in research.md "Measurements"; run `sbt 'service/testOnly *LiveAppraiserSuite *LiveFeatures'` without keys and record it skipped

**Checkpoint**: the blueprint's instructions and tools make sense to a real model; the build never needs a key.

---

## Phase 7: Polish & cross-cutting (the laptop, the cluster, the pages, the requests)

- [ ] T054 [P] In `modules/seed/src/main/scala/reasoning/seed/Seed.scala` accept `golden-set/seed.json` (`just seed golden-set`), and in `Measure.scala` add `measure-golden-set`: the four runs with `observedAt`, each record timed to the graph with its act (SC-009), and the whole set timed (SC-006); add the recipe to `Justfile`
- [ ] T055 [P] Run quickstart tier 4 on a laptop (`just up`, `just run` without keys, `just seed golden-set`, the four runs with keys or through the scripted suite's `measure-golden-set`, the answers, the Cypher trace, `just rebuild`); record what was seen in quickstart.md "What was run" and the SC-006 and SC-009 rows in research.md "Measurements"
- [ ] T056 [P] Write the pages: `docs/concepts/process-layer.md` (acts, runs, endings, versions; the window of R8), `docs/build/run-a-blueprint.md` (the three tools, `POST /process/runs`, a run's acts), `docs/build/the-appraiser.md` (the blueprint as data, the two instruction sets, the critique), `docs/build/golden-set.md` (the files, the expected readings, the four answers with real replies), `docs/deploy/live.md` (the two keys, the live suite); add each to `mkdocs.yml` nav and `extra.docs.facets.layers` (`process`), and to `tools/docs/skill/ankka-reasoning/SKILL.md` `pages:`
- [ ] T057 [P] Rewrite the sink pages for ankka's graph sink and ankka-contrib's store: `docs/index.md`, `docs/concepts/architecture.md` (and `docs/assets/diagrams/architecture.svg`), `docs/concepts/layers.md` (the process layer), `docs/get-started/run-locally.md`, `docs/build/read-the-graph.md`, `docs/build/record-reasoning.md` (the launch example's sources are the golden set's, registered at start, so their replies are `200` with the service as writer; F-m), `docs/reference/graph-vocabulary.md` (the process layer's tables), `docs/reference/http-api.md` (the `/process` routes), `docs/reference/refusals.md`, `docs/reference/limitations.md` (the R8 window; no tool fails a run; three parameters; the two keys), `docs/deploy/deploy-on-ankka.md` (D13 order, the sink descriptor), `docs/deploy/operate.md` (rebuild by version); `README.md` and `CLAUDE.md` (six modules, the sink, the pins, the new commands)
- [ ] T058 Run `just docs-sync` and `just docs`; commit what renders under `marketplace/` and `docs/reference/configuration.md`
- [ ] T059 [P] Update `notes/ankka-requests.md`: 8 and 9 answered or moot (the release; ankka-contrib's public image), 13 answered by ankka's blueprints, 6 restated against ankka's graph sink, and 14 to 17 written out as research.md "Requests" with what each would remove; update `../ankka-brain/repos/ankka-reasoning.md` (pins, modules, the sink, the process layer) and `../ankka-brain/roadmap.md`'s ankka-reasoning lines
- [ ] T060 Run quickstart tier 5 on the local kind installation with ankka at the pinned release: the project, the topic, the secret, the sink descriptor, the service, a run through the gateway with a person's token; record it in quickstart.md "What was run"
- [ ] T061 Fill research.md "Found during implementation" with every V1 to V14 outcome and whatever else building it changed; tick the reviewer's checklist in quickstart.md; run `sbt clean scalafmtCheckAll test` and `just features` one last time and record both

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: first; the spec's prerequisite. T001 before everything; T003 before T004; T005 to T007 parallel; T008 after T001; T009 last.
- **Foundational (Phase 2)**: after Phase 1. T010 first; T011 to T013 parallel; T014 to T017 in order; T018 and T019 parallel after T011; T020 after T014 to T019; T021 after T020; T022 and T023 parallel; T024 after T021; T025 after T021; T026 after T025.
- **US1 (Phase 3)**: after Phase 2. T027 and T028 parallel; T029 then T030 then T031; T032 after T028 and T030; T033 after T032; T034 after T032; T035 after T029; T036 after T032; T037 after T035 and T036; T038 last.
- **US2 (Phase 4)**: after US1 (its scenarios run the appraiser). T039 and T040 parallel; T041 after T040; T042 and T043 after T041; T044 last.
- **US3 (Phase 5)**: after US2 (its answers are the answers with acts). T045 first; T046 to T048 in order; T049 last.
- **US4 (Phase 6)**: after US3. T050 then T051 then T052; T053 last.
- **Polish (Phase 7)**: after US3; T060 after T057; T061 last.

### User Story Dependencies

- **US1**: the MVP; needs only Phases 1 and 2.
- **US2**: runs the appraiser of US1 and reads its acts.
- **US3**: the acceptance example; needs the answers with acts of US2.
- **US4**: the same blueprint and golden set as US3, against real providers.

### Parallel Opportunities

- Phase 1: T005, T006, T007 together once T001 and T002 are in.
- Phase 2: T011, T012, T013 together; T018 and T019 together; T022 and T023 together.
- Phase 3: T027 and T028 together, then T029; T033 and T034 and T035 together after T032.
- Phase 4: T039 and T040 together; T042 and T043 together.
- Phase 7: T054 to T057 and T059 together.

---

## Parallel Example: Phase 2

```bash
# After T010:
Task: "Create process/.../domain/Records.scala"       # T011
Task: "Create process/.../domain/Ids.scala"           # T012
Task: "Create process/.../domain/ProcessRules.scala"  # T013
# After T011:
Task: "Create process/.../ProcessVocabulary.scala"    # T018
Task: "Create process/.../application/graph/*"        # T019
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1: the sink moved and the build pinned; `sbt test` green on spec 001. Open a pull request.
2. Phase 2: the process module, hosting blueprints, the vocabulary and the graph consumers.
3. Phase 3: the tools, the appraiser, the scripted model, `POST /process/runs`.
4. **Stop and validate**: `sbt 'service/testOnly *ToolsFeatures' process/test`; a run over HTTP writes records with acts.

### Incremental Delivery

1. US2: the layer visible in Neo4j and in answers with acts.
2. US3: the golden set's four answers and the resolution; the feature's proof.
3. US4: the live suite, when keys are at hand.
4. Phase 7: the laptop and cluster walkthroughs, the pages, the requests, the measurements.

---

## Notes

- Every feature file is run by exactly one suite; `LiveFeatures` and `LiveAppraiserSuite` are skipped, never failed, without keys.
- A test never names an image or an ankka version by a literal: `Dependencies.scala` and the forwarded properties.
- A step body takes at most four values (spec 001 F10) unless the release lifts it; the process steps are written to that.
- Commit after each task or logical group, "Tasks: T0NN" in the message; Phase 1 as its own pull request.
