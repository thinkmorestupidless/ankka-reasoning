# Requests to ankka, ankka-flow and ankka-contrib

What this application would like from the platform it is built on. Each says what is done here
today and what the request would remove. None blocks the belief layer or the process layer; the
reasons are in [`specs/001-belief-layer/research.md`](../specs/001-belief-layer/research.md) and
[`specs/002-process-layer/research.md`](../specs/002-process-layer/research.md), by the number in
brackets (R for a decision, F or V for what building it found).

Nothing here reaches into the platform's tables or internals to get round a gap. Where a gap could
not wait, the way round is in this repository's own code and named below.

## To ankka

### 1. Erase part of an event sourced entity's history (R1)

**Today.** ankka never removes an event. Text in an event is permanent, so every record that
carries text a writer supplied is a key value entity, whose one row is overwritten when the text is
withdrawn. Only a belief's line, which carries numbers and identifiers, is event sourced.

**Asked.** A way to erase an event's payload, or to keep part of an event outside the journal where
it can be erased.

**Would remove.** The rule that decides a record's kind of entity by whether it holds text, and the
`Recorder`'s work of keeping history for records that would naturally be events.

### 2. A clock in `CommandContext` (R4)

**Today.** The endpoint reads the time once (`Clock`) and carries it in every command, so that a
record's `recordedAt` is decided in one place and a test can set it.

**Asked.** The time of the command in `CommandContext`, settable in the test kits. Another
application in the family has asked for the same.

**Would remove.** `Clock` and the time field of every command.

### 3. The caller's identity in `CommandContext` (R5)

**Today.** The endpoint decides who the writer is and passes it in the command. An entity trusts
the endpoint for it.

**Asked.** The caller, as the platform identified it, in `CommandContext`.

**Would remove.** The writer field of every command, and the assumption that every route sets it.

### 4. More than two path parameters on a route (R15)

**Today.** Routes are shaped to need at most two, and a hypothesis is named in the query as
`<question>/<hypothesis>`.

**Asked.** Any number of path parameters. Another application in the family has asked for the same.

**Would remove.** Nothing in code; it would let the routes read as the records nest.

### 5. A view keyed by something other than its source entity's id, or multi-source views (R8)

**Today.** A view has one row per source entity. A belief is one entity with many revisions, so a
revision is found by its id only because each is also written to a read record of its own
(`RevisionRecordEntity`, fed by the `RevisionRecords` consumer).

**Asked.** A view with a row per event, or the multi-source views ankka has specified.

**Would remove.** `RevisionRecordEntity` and `RevisionRecords`.

### 10. A setting of ankka's own for how often a state consumer polls (F14)

**Today.** A graph consumer over a key value entity's state is fed by a poll every three seconds,
which is the floor under how long a record takes to reach the graph. The service sets Pekko's
`pekko.persistence.r2dbc.refresh-interval` to one second in its own `application.conf`, as ankka's
control plane does for itself.

**Asked.** A documented ankka setting for it with a shorter default, or state changes pushed as
events are.

**Would remove.** The Pekko key from this service's configuration.

### 11. A step body of more than four values in `GherkinSuite` (F10)

**Today.** A step that names five things is written with one fixed in its expression.

**Asked.** Step bodies of at least six values.

**Would remove.** Nothing in code; one step expression would say what it means.

### 13. Blueprints and runs (process layer)

**Today.** Nothing is built. The process layer needs reasoning patterns (a loop of thinking and
acting, fan out and gather, map and reduce, propose and critique) run from a blueprint that is held
as data, with each run held and linked to the version of the blueprint it ran. None of that is about
reasoning, so it is asked of ankka rather than built here; [`use-cases.md`](use-cases.md) has the
reasons and the two use-cases. Three things ankka lacks today would each be a way round here:

- an autonomous agent's instructions, model, tools and budget are fixed on its companion and its
  class (ankka's limitations: "no per-instance overrides of a definition"), so a role that loops on
  its own would be a request agent inside a workflow step, and a crash would run the whole loop
  again;
- a judgment is asked only from an agent's handler, so a critique or a scoring step would call an
  agent whose only work is to ask one; ankka's roadmap lists a judgment client for workflows as
  unowned;
- a tool is code, or an MCP server an agent lists when its service starts (ankka's feature 029), so
  a blueprint could name only tools its service was built or started with.

**Asked.** Blueprints and runs in ankka: shapes of control flow in the platform, blueprints held and
versioned, roles whose instructions, model, tools and budget come from the blueprint, steps that
ask judgments, and runs started by a request, a schedule or a record. Specified in ankka.

**Would remove.** Everything this repository would otherwise need to run a pattern. What stays here
is the projection of runs into the graph and the tools that write reasoning records.

**Answered** by ankka `0.12.0` (2026-10-14): blueprints as data, versioned by digest; workers whose
instructions, tools and budget are the blueprint's; call, ask, judge and for-each steps; runs
started by a request and held as ankka's own records; `RunContext` for a tool. The process layer
(spec 002) hosts the appraiser on it and keeps exactly what this said would stay: the runs copied
into the graph, and the three tools that write records. What the blueprints lack is 14 to 17 below.

### 14. The worker in `RunRef` (spec 002, R9)

**Today.** `RunRef` names the run, the step, the blueprint and the version, and no worker. An act
has to name the worker that wrote the record, so a tool looks the step up in the blueprint version
the run names and takes its action's worker; a step with no single worker (a gather over several,
a judge or a call step) is refused with `act.worker.unknown` and the tool writes nothing.

**Asked.** The worker in `RunRef`, where the step has one.

**Would remove.** `RunResolver`'s lookup of the version and its cache of versions, and the refusal.

### 15. Tools with more than three parameters, or an object parameter (spec 002, R11)

**Today.** `FunctionTool` takes at most three parameters, each a string, a number, a flag, an
option or a list. `record_evidence` therefore takes a document id, an excerpt and an author, and
reads the source, the locator and the published time from the document in the run's tranche; the
belief layer's route takes all five.

**Asked.** More parameters, or a parameter that is an object with a shape.

**Would remove.** The tool's dependence on the golden set for provenance: it could take the
route's fields and record evidence from any document a worker was given.

### 16. A tool that fails its run with a reason (spec 002, R12)

**Today.** A tool answers a value or a refusal, and the run goes on. The critique's verdict is a
call step after the judge step, whose handler throws naming the first claim the judgment refused,
and the run ends `failed` with that reason. A judgment names no claim, so the handler pairs each
judgment with the reader's item by position.

**Asked.** A way for a tool, or a judge step, to fail the run with a reason where the fault is
found.

**Would remove.** The `critique-verdict` handler, and the pairing by position.

### 17. A judgment provider over a text model, or a model provider that judges (spec 002, R15)

**Today.** ankka's judgments come from Jev and its text from Anthropic, each with a key of its own,
so the live suite needs `ANTHROPIC_API_KEY` and `TYPESAFE_API_KEY` both and is skipped unless both
are set.

**Asked.** A judgment provider that asks a text model the question, so one key serves.

**Would remove.** The second key, and half of `LiveFixture.keysSet`.

### 19. A run's start answers the run as started (spec 002, V6)

**Today.** `agents.runs.start` answers a first start with the run entity's state from before its
`Started` event: `RunEntity.start` persists and then replies `currentState`, which is the empty
record, so the snapshot has no blueprint, version, `startedBy` or `startedAt`. A caller that holds
a record of the run from that answer, as this service does, holds nothing it can use. The way round
here: `RunsEndpoint` starts the run and reads it back with `agents.runs.get`.

**Asked.** `start` to reply with the state after the event (`thenReply(state => state)`), as a
repeated start already does.

**Would remove.** The read back after every start, and a trap for every service that starts runs.

## To ankka's graph sink, and ankka-contrib

### 6. Ask whether a set of elements has been applied (spec 001 R10; now of ankka's graph sink)

**Today.** The graph is kept by ankka's graph sink in ankka-contrib's Neo4j image, and
`/graph/wait` polls the graph database for the version of each element a record published, every
100 ms, until all are there or the limit passes. The process layer adds acts and runs to what a
reader waits for, and `just measure-golden-set` polls the run's answer from the graph for its acts.

**Asked.** A way to ask the sink, or the graph store, whether a set of element keys has been applied
at a version: a route on the sink, or a watermark the store keeps.

**Would remove.** The polling loop in `GraphEndpoint`, `GraphReader.versionsOf`, and the poll in
the measurement.

## To ankka-flow (moot: the sink is ankka's, in ankka-contrib's image)

### 7. A documented way to run a sink-only pipeline without Kubernetes (R7)

**Today.** `docker-compose.yml` runs the released sidecar with a `streamlet.conf` and a descriptor
written by hand from ankka-flow's test fixtures (`compose/flow-graph`), and a one-shot container
creates the topic compacted.

**Asked.** A supported command or compose fragment that runs a blueprint's sink against a broker
and creates its topics as the operator would.

**Would remove.** `compose/flow-graph`, the `topic` service, and the same again in the test
fixture (`GraphFixture.sinkFiles`).

**Moot** since spec 002 (2026-10-18): the graph is kept by ankka's graph sink in ankka-contrib's
image, which `docker-compose.yml` and the test fixture run as one container with its environment.
The `topic` service stays: ankka creates no topics, and a compacted one has to exist first.

### 12. Notes on standard error when the resource goes to standard output (F19)

**Today.** `flow generate … | kubectl apply -f -` is refused by `kubectl` for an unknown field
`note`: `flow` 0.4.0 prints its note about the compacted topic into the same stream as the resource.
The deploy page writes the resource to a file first.

**Asked.** Notes and other messages on standard error, whatever `-o` is.

**Would remove.** The file, and the sentence explaining it.

**Moot** since spec 002: nothing of ankka-flow is generated or applied; the sink is a service
descriptor, `deploy/graph-sink.json`, applied with `ankka services apply`.

## A release

### 8. An ankka release that carries what has merged since `v0.10.0` (F1, F2)

**Today.** `ankka-auth-oidc` is not published, so a person's token cannot be verified and the
gateway is refused with `401`. `GherkinSuite` takes a directory, so each suite is handed a copy of
its one feature file under `target/feature-suites/`.

**Asked.** A release with `ankka-auth-oidc` and a `GherkinSuite` that takes one file. Both are on
ankka's `main`.

**Would remove.** The `401` for the gateway (people would be writers), and
`FeatureSuite.directoryOf`.

**Answered** by ankka `0.12.0` (2026-10-14): the gateway admits a person with a verified token, and
each suite is given its one file. Both ways round are gone.

### 9. ankka-flow's container packages made public (F8)

**Today.** `ghcr.io/thinkmorestupidless/ankka-flow-sidecar:0.3.0` cannot be pulled without a
credential. The graph suites and the laptop walkthrough run against the same image built from the
`v0.3.0` tag and tagged with that name, by hand on a laptop and by two extra steps in CI.

**Asked.** The sidecar package made public.

**Would remove.** The hand-built image, and the two steps in `.github/workflows/ci.yml`.

**Moot.** The graph is kept by ankka's graph sink with ankka-contrib's public image
`ankka-graph-sink-neo4j`, and no ankka-flow image is used. The two steps are gone.

## To ankka-contrib

### 18. The graph sink's image for `linux/arm64` as well (spec 002, F-a)

**Today.** `ankka-graph-sink-neo4j:0.1.0` is published for `linux/amd64` only. On Apple silicon Docker
runs it under emulation: the graph suites and `just up` work, and the sink is slower to start.

**Asked.** A multi-architecture image, `linux/amd64` and `linux/arm64`.

**Would remove.** The emulation, and the platform warning on every start.
