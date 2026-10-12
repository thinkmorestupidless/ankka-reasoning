# Implementation Plan: The process layer

**Branch**: `002-process-layer` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-process-layer/spec.md`

## Summary

A run of a blueprint writes reasoning records as the holder its input names, through three tools
that call the belief layer's one write path; every record it writes is held with an act naming the
run, the step, the worker, the version and the holder; blueprint versions, runs, run endings and
acts are published as a process layer of the graph, above the belief layer, which changes in
nothing. An appraiser blueprint, carried by the service, reads a tranche of documents, states a
claim per document, has the claims judged, and revises a belief; run over a golden set of two
tranches for two holders from two versions, it produces the records the three questions are
answered from, with acts attached.

Technically: ankka's blueprints hosted by this service (R4), pinned to the first release after
`0.11.0` (R1); the graph kept by ankka's graph sink with ankka-contrib's Neo4j store in place of
ankka-flow, done first (R2); a sixth sbt module `process` (R3) with four key value entities, two
consumers over ankka's blueprint and run events and a view (R6); tools that check everything, write
the record, then the act (R8); the critique as a judge step per claim and a handler that fails the
run naming the claim (R12, R13); the model and the judgment scripted from the golden set offline
and real in one skipped-without-keys suite (R14, R15); every lower answer offered again with acts
by one more walk (R18).

Planning found four things the spec had assumed otherwise or left to the plan:

- **A tool is not told its worker, and cannot fail its run.** ankka's `RunRef` carries the run,
  step, blueprint and version; the act's worker is read from the version's own data (R9). A tool's
  error returns to the model; the run is failed by a call step's handler, which is where the
  critique's verdict lives (R12). Requests 14 and 16.
- **The holder is the run's input, not the worker's registration.** A worker is data with no
  room for a holder, and FR-010 already puts the holder in the input. The blueprint's registration
  names the holders it may be run for, and every worker of a run speaks for the run's holder
  (R10). The features' steps stay true; the spec's FR-003 wording is read this way.
- **The evidence tool takes a document, not provenance.** A tool takes three parameters; the
  run holds the tranche, and a document is what evidence is recorded from. Provenance is on the
  record, as FR-001 asks, and comes from the document (R11). Request 15.
- **"A model key" is two keys.** ankka's text model is Anthropic's and its judgment is Jev's; the
  live suite is skipped without either (R15). Request 17.

## Technical Context

**Language/Version**: Scala 3.9.0 on JDK 21, sbt 1.12.15, as before.

**Primary Dependencies**: `ankka-sdk`, `ankka-runtime`, `ankka-http`, `ankka-agent`,
`ankka-auth-oidc` at the first release after `0.11.0` carrying blueprints (R1, V1);
`neo4j-java-driver` 5.28.5; jsoniter through ankka's `Codecs`. Beside the service:
`ghcr.io/thinkmorestupidless/ankka-graph-sink-neo4j` from ankka-contrib (R2), Kafka 3.9, Neo4j 5.26.
Live only: Anthropic's API and TypeSafe AI's Jev through ankka's providers.

**Storage**: the service's Postgres, provisioned by ankka: four more key value entities, ankka's
own blueprint and run entities. The graph database is a copy, as before.

**Testing**: munit; `KeyValueEntityTestKit`, `ConsumerTestKit.graph` and `AnkkaTestKit` with
`TestModelProvider` and `TestJudgmentProvider` and the `Scripted` helpers of ankka's testkit;
`GherkinSuite` over one file each; testcontainers for Kafka, Neo4j and the contrib sink image.
Docker from tier 1.

**Target Platform**: an ankka installation on Kubernetes with the contrib sink deployed as a
service of the project; a laptop with Docker for everything short of that.

**Project Type**: web service (an ankka application hosting blueprints) with a seeding client.

**Performance Goals**: SC-006, the golden set's four runs, the graph caught up and the four answers
within two minutes on a laptop with a scripted model; SC-009, a record a run writes in the graph
with its act within five seconds, nine in ten; SC-007, the live suite's direction right nine runs
in ten.

**Constraints**: the seven rules of `CLAUDE.md`; no cross-entity transaction (the act is written
after its record, R8); at most three parameters per tool (R11); no worker in `RunRef` (R9); a tool
cannot fail a run (R12); the belief layer and the market layer change in nothing (FR-005, SC-008);
every feature passes with no model key and no network (FR-013).

**Scale/Scope**: the golden set's eight documents, four runs, about forty records and acts; 30
scenarios under `features/process/` and two under `features/graph/`; 16 functional requirements;
4 kinds of node and 5 of edge added.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is the unfilled template, so the gate is this repository's
`CLAUDE.md` rules and the family's principles in `../ankka-brain/principles.md`.

| Rule or principle | Status | How the design honours it |
|---|---|---|
| Nothing is edited | pass | A run and its ending are two records, each held once (clarified; R6). An act is held once. A blueprint version is held once; a changed blueprint is a new version. Nothing of the process layer is withdrawn. |
| Rules are checked on the write side | pass | A tool checks the run, the version, the worker and the holder before writing, from ankka's records and the service's own entities (R7 to R10), and the belief layer's rules run as for any writer (R5). Nothing reads the graph or a view to decide. |
| A link has one owner | pass | A run publishes `OF`; a run ending `ENDED`; an act `IN`, `WROTE` and `AS` (contracts/graph-vocabulary.md). The vocabulary test fails on two publishers. |
| Links point backwards | pass | The version is held before the run, the run before its ending and before any act, the record before its act (R7, R8); each check is on the write side, and `dated` never rises along an edge: an act is dated as its record, a run ending not before its run. |
| The belief layer names nothing above it | pass | `process` is a module that depends on `market` and `belief`, not the reverse (R3); the lower layers' records, consumers, answers and routes are untouched; the answers with acts are the process layer's own (R18); SC-008 is a suite. |
| Answers are records | pass | `/process/answers/…` is the lower answer, byte for byte, and acts, runs, endings and versions read back from the graph (R18). A run ending's `reason` is ankka's record, not text written for the occasion. |
| Missing platform features are requests | pass | Four new requests (14 to 17) and two carried over; three ways round are named in Complexity Tracking beside the request that removes each. Nothing reaches into ankka's tables or the sink's internals. |
| No BSL on the path | pass | As before; the contrib store and the Neo4j driver are Apache-2.0. |
| Effects are inert data | pass | Entities return effects; the time and the writer arrive in the command, read once by the tool as the endpoint reads them (R19). |
| Explicit registration | pass | One `components` list; the appraiser's registry is built once in `Main`. |
| Be your own customer | pass | ankka's blueprints, test providers and graph sink as released; ankka-contrib's store as released. |
| Only a tag publishes | pass | Unchanged; `ankka-reasoning-process` joins the release's libraries. |
| The honest not-implemented list | pass | The spec's "Not in this feature" and the window of R8 go on the limitations page. |
| Same conventions across repos | pass | Unchanged. |

**Violations to justify**: none. Three things grow beyond the simplest design and are in
Complexity Tracking.

**Re-check after Phase 1**: unchanged. The data model keeps one writer per element, the act is a
record of its own with every check before the write, and the contracts expose no way to change a
record.

## Project Structure

### Documentation (this feature)

```text
specs/002-process-layer/
├── plan.md              # this file
├── research.md          # R1–R19, fourteen things to verify first, requests 14–17
├── data-model.md        # the four process records, the act rows, the golden set's files and the run input
├── quickstart.md        # tiers 0 to 6: the sink moved, components, the service, the graph, a laptop, kind, live
├── contracts/
│   ├── http-api.md          # /process routes: runs, versions, acts, answers with acts, the wait's new kinds
│   ├── blueprint.md         # the three tools and the appraiser as data, its handlers, question and instruction sets
│   ├── graph-vocabulary.md  # the process layer's nodes, edges, trace queries, properties a test holds
│   ├── refusals.md          # every new rule, and the failed run's reasons
│   └── deployment.md        # the sink in place of the pipeline, the keys, the holders, the golden set's path
├── checklists/requirements.md
└── tasks.md             # written by /speckit-tasks
```

### Source Code (repository root)

```text
build.sbt                      # six modules; process between market and service; golden-set/ as process's resources
project/Dependencies.scala     # V.ankka → the blueprints release; ankka("agent"); V.sinkImage → the contrib image
golden-set/                    # question.json, may.json, june.json, seed.json (R17)
modules/
├── graph/                     # unchanged but Neo4jGraph's scaladoc (the sink is ankka's)
├── belief/                    # unchanged
├── market/                    # unchanged
├── process/                   # new; depends on market
│   └── src/main/scala/reasoning/process/
│       ├── domain/            # BlueprintVersion, Run, RunEnding, Act, RunStatus, ActIds; ProcessRules; Same
│       ├── application/       # the four entities, ActRows, BlueprintVersions, RunFollower, ProcessRecorder (R6, R7)
│       │   └── graph/         # ProcessGraphOf, the four graph consumers, On(topic)
│       ├── tools/             # RunResolver (run, version, worker, holder; R7–R10), RecordEvidence, StateClaim, ReviseBelief (R8, R11)
│       ├── appraiser/         # Appraiser (the blueprint as data), Instructions (v1, v2), handlers, the judgment question, GoldenSet (reads the files)
│       ├── answers/           # ProcessAnswers: the lower answers with acts, and what a run wrote (R18)
│       ├── api/               # /process/runs, /process/acts, /process/appraiser, /process/answers, /process/markets
│       ├── ProcessVocabulary.scala
│       └── ProcessLayer.scala # vocabulary, components, graphComponents, recorder, registry(ctx), endpoints, published
├── service/
│   ├── src/main/scala/reasoning/service/
│   │   ├── Main.scala         # + AgentRuntime.descriptors, the AgentRuntime extension, the process layer, holders and sources at start
│   │   ├── Settings.scala     # + appraiser holders, golden-set path
│   │   ├── Providers.scala    # Anthropic and Jev from the environment, or a provider that says which key is missing (D10)
│   │   ├── Writers.scala      # + the blueprint: form, never derived from a caller
│   │   └── GraphEndpoint.scala    # + four wait kinds, the process layer in the vocabulary
│   └── src/test/scala/reasoning/
│       ├── ProcessFeatures.scala      # ToolsFeatures, RunsFeatures, GoldenSetFeatures, LiveFeatures (skipped without keys)
│       ├── LiveAppraiserSuite.scala   # tier 6 (R15)
│       ├── steps/ProcessFeatureSuite.scala   # the process steps; ScriptedAppraiser (R14)
│       └── support/ServiceFixture.scala, GraphFixture.scala   # the test providers; the contrib sink container (R2)
├── seed/                      # + reads golden-set/seed.json; `Measure` gains the golden-set timing
docker-compose.yml             # sink: the contrib image from environment variables; compose/flow-graph and neo4j-secret go
compose/rebuild.sh             # raises ANKKA_GRAPH_SINK_VERSION
deploy/graph-sink.json         # the sink's descriptor; deploy/pipeline/ goes
docs/                          # + concepts/process-layer, build/run-a-blueprint, build/the-appraiser, build/golden-set, deploy/live; the sink pages rewritten
notes/ankka-requests.md        # 8, 9, 13 answered or moot; 14–17 added
.github/workflows/ci.yml       # the sidecar build steps go
```

**Structure Decision**: one repository, six sbt modules. `process` is a library like the three
beneath it, published as `ankka-reasoning-process`, so that the layering stays a fact of the build
(R3). The appraiser lives in it as the layer's worked example. The golden set is files at the root
packaged as the module's resources, so one set serves the suites, the service, the pages and the
live run (R17).

## Slices, in the order they are built

Each slice ends with its scenarios passing and is a demonstrable increment.

| # | Slice | Stories | Scenarios it turns on | Quickstart tier |
|---|---|---|---|---|
| 0 | The sink moved: contrib image in the suites, compose, deploy and CI; ankka pinned to the blueprints release; `ankka-agent` on the path; F1/F2 of spec 001 closed | | every scenario of spec 001, unchanged | 0 |
| 1 | The process module: records, entities, consumers over ankka's events, the vocabulary, graph consumers, the wait's kinds; the service hosting blueprints with an empty registry | US2 (part) | `features/graph/vocabulary.feature` (the two changed), *a blueprint version is in the graph when it is registered* | 1, 3 |
| 2 | The three tools, the run resolver and the act; a minimal blueprint in the test registry that calls them; `POST /process/runs` | US1 | `features/process/tools.feature` | 1, 2 |
| 3 | The golden set's files and the appraiser as data; the scripted appraiser; both runs for one holder end completed | US3 (part) | the first three of `golden-set.feature`, *the golden set runs with no model key and no network* | 2 |
| 4 | Runs, endings and acts in the graph; what a run wrote; the answers with acts | US2 | `features/process/runs.feature` | 3 |
| 5 | The critique that fails; version 2 and the second holder; the four answers; the market resolved afterwards | US3 | the rest of `golden-set.feature` | 3 |
| 6 | The live suite and the providers from the environment | US4 | `features/process/live.feature` | 6 |
| 7 | The laptop walkthrough, `just seed golden-set`, `just measure-golden-set`, the pages, the skill, the kind walkthrough, the requests file, the measurements | | SC-006, SC-007, SC-009 | 4, 5 |

Slice 0 is the prerequisite the spec names and is worth a pull request of its own. Slice 3 is the
first thing worth showing: a blueprint reading documents into the graph. Slice 5 is the acceptance
example of the whole feature.

## Complexity Tracking

| What grows | Why needed | Simpler alternative rejected because |
|---|---|---|
| A tool reads the blueprint version to learn its worker (R9) | `RunRef` carries no worker and the act must name one | A tool per worker: the registry grows with every worker. Request 14 removes the lookup. |
| `record_evidence` takes a document id and reads provenance from the run's tranche (R11) | A tool takes three parameters, and provenance is five fields | A JSON-string parameter: the model fills it worse and the schema says nothing. Request 15 lets the tool take the route's fields. |
| A call step after the judge step to fail the run naming the claim (R12, R13) | A tool cannot fail its run and a judgment names no claim; ankka's `critique` loop re-drafts after the belief would already be revised | `Step.critique(...)` with a judgment: the drafter writes before the verdict and the reason names the question. Request 16 lets the verdict fail the run where it is found. |
| The run is written by the tool and by a consumer, both from ankka's record (R7) | An act must link to a held run, and a consumer may lag the tool's call | The tool waiting for the consumer: a wait on a projection on the write side, which spec 001 R3 forbids. Nothing to ask of ankka: the two writes are one record. |
