# Requests to ankka and ankka-flow

What this application would like from the platform it is built on. Each says what is done here
today and what the request would remove. None blocks the belief layer; the reasons are in
[`specs/001-belief-layer/research.md`](../specs/001-belief-layer/research.md), by the number in
brackets.

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

### 13. An autonomous agent's definition overridden per instance or per task (process layer)

**Today.** Nothing is built. The process layer would hold a reasoning blueprint as a record, and a
run of it as a record, carried out by code deployed once. An autonomous agent's instructions, model,
accepted task types and budget are fixed on its companion, and its tools on its class
(ankka's `docs/reference/limitations.md`: "no per-instance overrides of a definition"). Only a
task's instructions and attachments vary. A role that loops on its own would be a request agent
called from a workflow step, so a crash runs the step's whole loop again.

**Asked.** Instructions, model, a subset of the agent's tools and the iteration budget given per
instance or per task.

**Would remove.** The request agent standing in for a role that loops, and the repeated model
calls after a crash.

### 14. Tools as data (process layer)

**Today.** Nothing is built. A request agent's effect takes its instructions, model and tools at
run time, but a `FunctionTool` can only be built in code: `FunctionTool.raw` is
`private[ankka]` and the public builders are typed at compile time. A blueprint could choose among
the tools compiled into this service and could name no other.

**Asked.** A public way to build a tool from a schema and a call to another service's endpoint, or
the MCP tools ankka's spec 029 drafts.

**Would remove.** Every tool a blueprint names having to be written into this service.

### 15. A judgment asked from a workflow step (process layer)

**Today.** Nothing is built. A judgment is asked only from an agent's handler, so a workflow step
that gates on one calls an agent whose only work is to ask it. ankka's roadmap lists a judgment
client for workflows as out of scope for its feature 018 and unowned.

**Asked.** A judgment client a workflow step can call.

**Would remove.** The agent that exists only to ask a judgment for a critique or a scoring step.

## To ankka-flow

### 6. Ask whether a set of elements has been applied (R10)

**Today.** `/graph/wait` polls the graph database for the version of each element a record
published, every 100 ms, until all are there or the limit passes.

**Asked.** A way to ask the sink, or the graph, whether a set of element keys has been applied at a
version.

**Would remove.** The polling loop in `GraphEndpoint`, and `GraphReader.versionsOf`.

### 7. A documented way to run a sink-only pipeline without Kubernetes (R7)

**Today.** `docker-compose.yml` runs the released sidecar with a `streamlet.conf` and a descriptor
written by hand from ankka-flow's test fixtures (`compose/flow-graph`), and a one-shot container
creates the topic compacted.

**Asked.** A supported command or compose fragment that runs a blueprint's sink against a broker
and creates its topics as the operator would.

**Would remove.** `compose/flow-graph`, the `topic` service, and the same again in the test
fixture (`GraphFixture.sinkFiles`).

### 12. Notes on standard error when the resource goes to standard output (F19)

**Today.** `flow generate … | kubectl apply -f -` is refused by `kubectl` for an unknown field
`note`: `flow` 0.4.0 prints its note about the compacted topic into the same stream as the resource.
The deploy page writes the resource to a file first.

**Asked.** Notes and other messages on standard error, whatever `-o` is.

**Would remove.** The file, and the sentence explaining it.

## A release

### 8. An ankka release that carries what has merged since `v0.10.0` (F1, F2)

**Today.** `ankka-auth-oidc` is not published, so a person's token cannot be verified and the
gateway is refused with `401`. `GherkinSuite` takes a directory, so each suite is handed a copy of
its one feature file under `target/feature-suites/`.

**Asked.** A release with `ankka-auth-oidc` and a `GherkinSuite` that takes one file. Both are on
ankka's `main`.

**Would remove.** The `401` for the gateway (people would be writers), and
`FeatureSuite.directoryOf`.

### 9. ankka-flow's container packages made public (F8)

**Today.** `ghcr.io/thinkmorestupidless/ankka-flow-sidecar:0.3.0` cannot be pulled without a
credential. The graph suites and the laptop walkthrough run against the same image built from the
`v0.3.0` tag and tagged with that name, by hand on a laptop and by two extra steps in CI.

**Asked.** The sidecar package made public.

**Would remove.** The hand-built image, and the two steps in `.github/workflows/ci.yml`.
