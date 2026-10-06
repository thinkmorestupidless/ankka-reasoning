# Implementation Plan: The belief layer

**Branch**: `001-belief-layer` | **Date**: 2026-10-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-belief-layer/spec.md`

## Summary

A service that holds the reasoning about a question as records that are never edited, publishes
each record as a node and each link as an edge for a graph database, and answers from that graph
why a belief changed, what the case for a hypothesis is, what was believed at a past time and where
two holders differ. A market layer sits above the belief layer and changes nothing in it.

Technically: an ankka application in Scala 3 against ankka 0.10.0, in five sbt modules whose
dependency direction makes the layering a fact of the build (R2). Every record that carries text a
writer supplied is a key value entity; only a belief, which is a line of numbers and identifiers,
is event sourced (R1). The rules that span records are checked by the application before a command
is sent, which is sound here because nothing a rule depends on can ever stop being true (R3).
Graph consumers publish `ankka.graph-delta.v1` deltas to one compacted topic; ankka-flow 0.3.0's
built-in merge sink applies them to Neo4j (R6, R7). Answers are Cypher walks that start from a node
found by its id (R9). The living features run as tests through ankka's `GherkinSuite` (R12).

Planning found five things the spec had assumed otherwise or left open:

- **An event's text can never be erased.** ankka never removes events from a journal, and a
  deleted entity's snapshot keeps its last state. A key value entity's row is overwritten in place.
  So the spec's "records are ankka entities" becomes: key value entities for everything with text,
  an event sourced entity for the belief line. The README and `CLAUDE.md` said "event-sourced
  record"; they are corrected.
- **A node's version in the graph says nothing of its edges.** Deltas for a node and for its edges
  land on different partitions and commit in different transactions. FR-020's wait is therefore for
  the record's node *and every edge it stated*, and the scenario is reworded.
- **Someone has to be able to withdraw text when its writer is gone.** FR-034 gains a steward: a
  writer the deployment names, who may withdraw the text of any record. One scenario and one
  glossary term are added.
- **A handler has no clock and does not know its caller.** The time a record was recorded and the
  writer who sent it are read once by the endpoint and carried in the command (R4, R5).
- **A view reads one source into one row per entity**, so a belief's revisions cannot be listed
  from a view. Each revision is also kept as a small read record, and a line is read by walking
  back from its head (R8).

## Technical Context

**Language/Version**: Scala 3.9.0 on JDK 21, sbt 1.12.15, as every JVM repository in the family.

**Primary Dependencies**: `ankka-sdk`, `ankka-runtime`, `ankka-http`, `ankka-auth-oidc` 0.10.0;
`neo4j-java-driver` 5.28.5 (the version ankka-flow's sink uses); jsoniter-scala 2.40.1 through
ankka's `Codecs`. Beside the service: ankka-flow 0.3.0 (`ankka-flow-sidecar` image, built-in
`neo4j-merge-sink`), Kafka 3.9, Neo4j 5.26.

**Storage**: the service's Postgres, provisioned by ankka (key value entities' `durable_state`, the
belief line's `event_journal`). The graph database is a copy and is never the source of a decision.

**Testing**: munit 1.3.6. `KeyValueEntityTestKit`, `EventSourcedTestKit` and `ConsumerTestKit.graph`
for single components; `AnkkaTestKit` for the whole service; ankka-testkit's `GherkinSuite` for the
scenarios under `features/`; testcontainers 1.21.4 for Kafka, Neo4j and the sink's own image.

**Target Platform**: an ankka installation on Kubernetes, beside an ankka-flow pipeline. A laptop
with Docker for everything short of that.

**Project Type**: web service (an ankka application) with a small seeding client.

**Performance Goals**: SC-007. A record is in the graph within five seconds of being recorded, nine
times in ten; an explanation over a question with 1,000 records is answered within one second.

**Constraints**: nothing is edited (text withdrawal excepted); no decision is read from the graph
database; one writer per graph element; at most two path parameters on a route; no clock and no
caller in a command context; no cross-entity transaction.

**Scale/Scope**: tens of questions, thousands of records, a market's price observed a few times an
hour. 119 scenarios, 37 functional requirements, 9 kinds of node and 18 kinds of edge.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is the unfilled template, so the gate is this repository's
`CLAUDE.md` rules and the family's principles in `../ankka-brain/principles.md`.

| Rule or principle | Status | How the design honours it |
|---|---|---|
| Nothing is edited | pass | Every entity's handlers accept a record once and refuse a different one under the same identifier. The only later change is withdrawing text (R1) or appending (a hypothesis, a writer, a resolution, a belief revision). |
| Rules are checked on the write side | pass | The application reads the referenced entities by id, strongly consistently, before a command is sent (R3). No rule reads Neo4j or a view. |
| A link has one owner | pass | Each entity's graph consumer publishes its own node and only the edges that leave it (R6). The vocabulary records the publishing kind of record for every kind, and a test fails on two. |
| Links point backwards | pass | A record names only records already held and dated no later (FR-010, FR-011), enforced before the command. Edge endpoints and types never change, which the sink's merge needs. |
| The belief layer names nothing above it | pass | `belief` is an sbt module that `market` depends on and not the reverse, so a market word in the belief layer does not compile (R2). |
| Answers are records | pass | Every answer type is assembled from nodes and edges read back from the graph; no field is free text the service wrote (R9). |
| Missing platform features are requests | pass | Seven gaps are recorded as requests (research, "Requests"). One is worked round, the read record per belief revision (R8), and is named in Complexity Tracking beside request 5, which would remove it. Nothing reaches into ankka's tables or the sink's internals. |
| No BSL on the path | pass | Pekko through ankka; Neo4j Community and its Apache-2.0 driver; Kafka. |
| Effects are inert data | pass | Handlers return effects; time and identity arrive in the command, so handlers stay pure (R4). |
| Explicit registration | pass | One `components` list in the service module is the whole inventory. |
| Be your own customer | pass | The application uses ankka and ankka-flow as released, by version. |
| Only a tag publishes | pass | sbt-dynver; an image on a `v*` tag only. Nothing is published in this feature. |
| The honest not-implemented list | pass | The spec's "Not in this feature", carried into `docs/` when a docs tree exists. |
| Same conventions across repos | pass | Justfile, `.scalafmt.conf`, the pre-commit hook, serialised tests, spec-kit and speckit-bdd as in the siblings. |

**Violations to justify**: none. Two things grow beyond the simplest design and are in Complexity
Tracking.

**Re-check after Phase 1**: unchanged. The data model keeps one writer per element and the
contracts expose no way to change a record.

## Project Structure

### Documentation (this feature)

```text
specs/001-belief-layer/
├── plan.md              # this file
├── research.md          # R1–R18: decisions and what each rests on; eleven things to verify first; requests
├── data-model.md        # the nine kinds of record, their fields, rules and identifiers
├── quickstart.md        # five tiers, from unit suites to a pipeline beside ankka on kind
├── contracts/
│   ├── http-api.md          # every route: writes, read-back, answers, the graph wait
│   ├── refusals.md          # every rule a write can break: its name, status and what it names
│   ├── graph-vocabulary.md  # node and edge kinds, properties, element ids, layers, who publishes
│   └── deployment.md        # environment, topic, pipeline, writers and stewards
├── checklists/requirements.md
└── tasks.md             # written by /speckit-tasks
```

### Source Code (repository root)

```text
build.sbt                      # five modules; ankka and every other version from project/Dependencies.scala
project/
├── Dependencies.scala         # the one place a version is written
├── build.properties           # sbt 1.12.15
└── plugins.sbt                # sbt-native-packager, sbt-scalafmt, sbt-dynver
modules/
├── graph/                     # no ankka dependency but ankka-sdk's graph elements
│   └── src/main/scala/reasoning/graph/
│       ├── Vocabulary.scala   # NodeKind, EdgeKind, Layer, Property; a vocabulary is a value
│       ├── Elements.scala     # builds a GraphElement from a kind, so nothing undeclared is built
│       ├── GraphReader.scala  # the port answers are written against
│       ├── Neo4jGraph.scala   # the driver, the Cypher, the wait
│       └── Answers.scala      # the shapes shared by both layers' answers
├── belief/                    # depends on graph
│   └── src/main/scala/reasoning/belief/
│       ├── domain/            # Question, Holder, Source, Evidence, Claim, Belief; rules; Refusal
│       ├── application/       # the entities, their graph consumers, Recorder, RevisionRecords, ClaimRows
│       ├── answers/           # BeliefChange, Case, AsOf, Learned, Comparison, RestingOnRevised
│       ├── api/               # endpoints under /questions /holders /sources /evidence /claims /beliefs /answers
│       └── BeliefLayer.scala  # its vocabulary and its component list
├── market/                    # depends on belief and graph
│   └── src/main/scala/reasoning/market/
│       ├── domain/            # Market, Outcome, Resolution; rules
│       ├── application/       # MarketEntity, its graph consumer, MarketRecorder
│       ├── answers/           # WhyResolved, BeliefsAtResolution
│       ├── api/               # endpoint under /markets
│       └── MarketLayer.scala  # its vocabulary (adds the holder kind "market") and components
├── service/                   # depends on belief and market
│   ├── src/main/scala/reasoning/service/
│   │   ├── Main.scala         # the inventory, the extensions, start
│   │   ├── Settings.scala     # application.conf with an environment override per key
│   │   ├── Writers.scala      # the ACL: who the caller is, as a writer
│   │   └── GraphEndpoint.scala    # /graph/wait and /graph/vocabulary
│   └── src/test/scala/reasoning/  # the feature suites and their steps; the graph fixture
└── seed/                      # depends on nothing of ours: an HTTP client
    └── src/main/scala/reasoning/seed/   # reads seed/*.json, posts it; the first writer
features/                      # the living features (exists)
seed/                          # the launch example and ten questions, as JSON
deploy/
├── service.json               # the service descriptor template
└── pipeline/blueprint.conf    # the sink-only pipeline and its compacted topic
docker-compose.yml             # Postgres, Kafka, Neo4j and the sink's sidecar
compose/                       # the sidecar's streamlet.conf, descriptor and credential files
notes/ankka-requests.md        # what this application would like from ankka and ankka-flow
Justfile  .scalafmt.conf  .githooks/pre-commit  .github/workflows/ci.yml
```

**Structure Decision**: one repository, five sbt modules. `graph`, `belief` and `market` are
libraries; `service` is the one deployable; `seed` is a client that proves the HTTP contract from
outside. The split exists so that FR-018 is enforced by the compiler: `belief` cannot name anything
in `market`, and `graph` knows neither. Package root `reasoning`. Inside a layer the split is the
one satisfactory uses: `domain` (values and rules, no ankka), `application` (components), `api`
(endpoints).

## Slices, in the order they are built

Each slice ends with its scenarios passing and is a demonstrable increment. A feature suite is
added when its area is implemented, so the build is never green on unbound steps.

| # | Slice | Stories | Scenarios it turns on | Quickstart tier |
|---|---|---|---|---|
| 0 | The build: modules, pins, compose, hooks, CI, an empty service that starts | | none | 1 |
| 1 | Belief-layer records and their rules, written and read back over HTTP; writers | US1 | `features/record/` except withdrawal | 1, 2 |
| 2 | The vocabulary, graph consumers, the topic and the sink; wait; rebuild | US2 | `features/graph/` | 3 |
| 3 | Why a belief changed; the case for and against | US3 | `features/explain/belief-change`, `case` | 3 |
| 4 | As of a date and as recorded by a time; what was learned | US4 | `features/explain/as-of` | 3 |
| 5 | Comparison; markets and price observations | US5 | `features/explain/disagreement`, `features/market/markets` | 3 |
| 6 | Withdrawing text, in the service and in the graph | US7 | `features/record/withdrawal` | 3 |
| 7 | Resolutions and what was believed at resolution | US6 | `features/market/resolution` | 3 |
| 8 | The seeded ten questions; the measurements; the laptop and kind walkthroughs | | SC-002, SC-003, SC-005, SC-007 | 4, 5 |

Slices 1 and 2 are the first thing worth showing: the launch example recorded and visible in Neo4j.
Slice 3 is the acceptance example of the whole feature.

## Complexity Tracking

| What grows | Why needed | Simpler alternative rejected because |
|---|---|---|
| Two kinds of entity (key value for records, event sourced for the belief line) | Text must be erasable, which only a key value entity's row is; a belief's revisions must form one line under concurrent writers and can be very many, which only an entity with events and a small state gives | All event sourced: withdrawn text would stay in the journal for good. All key value: a belief's line would be one ever-growing state |
| A read record per belief revision beside the belief's events (R8) | FR-014 asks for a line's revisions from the service alone, and a view cannot list them | A view row per belief holding every revision grows without bound; the graph database cannot be the answer because it is not "the service alone" |
| Five sbt modules for a small service | The layering rule is otherwise only a convention | One module with packages: a market word in the belief layer would compile, and the rule would rest on review |
