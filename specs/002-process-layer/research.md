# Research: The process layer

The decisions this plan rests on, each with what it rests on and what else was considered. R1 to
R4 are about what this feature is built on; R5 to R12 about writing as a run; R13 to R19 about the
appraiser, the golden set and the suites. "Verify first" lists what the plan asserts about ankka's
unreleased blueprints and ankka-contrib's sink and has not yet run here. "Requests" is what the
platform could do to remove a way round.

Spec 001's `research.md` still holds: nothing here changes R1 to R18 there.

## R1. The build pins the first ankka release that carries blueprints

**Decision.** `V.ankka` moves from `0.10.0` to the first release after `0.11.0` that carries
feature 036 (blueprints, merged to ankka's `main` as pull request 93 on 2026-10-10, sixteen commits
after `v0.11.0` at planning time). The build gains `ankka-agent` (blueprints, tools, the model and
judgment providers, the test providers). Until that release exists the build is proven against a
`publishLocal` of ankka's `main` by `sbt -Dankka.version=<local>`, as ankka-contrib does; the pin
in `Dependencies.scala` is never a snapshot.

**Rests on.** Blueprints exist in no release (`git tag` in ankka: `v0.11.0` is the latest and
precedes the merge). The release is being prepared beside this plan. Request 8 of spec 001 (a
release carrying `ankka-auth-oidc` and a one-file `GherkinSuite`) is answered by `0.11.0`, so
`FeatureSuite.directoryOf` and the gateway's `401` go with this move; spec 001's F1 and F2 close.

**Considered.** Pinning a snapshot: the family forbids it (a published name is immutable; a
snapshot is not). Vendoring the agent module: it would fork the platform.

## R2. The graph is kept by ankka's graph sink with ankka-contrib's Neo4j store, in place of ankka-flow

**Decision.** The sink that reads the delta topic into Neo4j is ankka-contrib's image
`ghcr.io/thinkmorestupidless/ankka-graph-sink-neo4j` (its `0.1.0` is built against ankka `0.11.0`),
a service registering ankka's `ankka-graph-sink` over the contrib `Neo4jStore`, configured by
environment: `ANKKA_GRAPH_SINK_TOPIC`, `ANKKA_GRAPH_SINK_VERSION`, `NEO4J_URI`, `NEO4J_USERNAME`,
`NEO4J_PASSWORD`, `NEO4J_DATABASE`. The ankka-flow pipeline, its sidecar image, `compose/flow-graph`,
`deploy/pipeline/` and the two CI steps that built the sidecar go. The topic is declared on the
project (`ankka projects topics set reasoning-graph --partitions 3 --cleanup compact`) and locally by
the compose file's one-shot `topic` service as today. A rebuild is: empty the store, raise
`ANKKA_GRAPH_SINK_VERSION` by one, start the sink again; `compose/rebuild.sh` does exactly that. The
reader's conventions are unchanged: a node is `Element` with `id`, `_version`, `_deleted`; a
placeholder has `_version = -1`.

This is the prerequisite the spec names, done first as slice 0 of this plan, since no other spec
holds it. It touches no record, no rule and no answer: `Neo4jGraph.versionsOf` reads the same
properties, and `GraphFixture` runs a different container with environment variables instead of
mounted files.

**Rests on.** ankka's graph-sink page (`docs/deploy/graph-sink.md` at `main`) and ankka-contrib's
README: the store's rules (version guard, placeholders, tombstones, whole-state replacement) are the
same `ankka.graph-delta.v1` rules the sidecar applied; the delta the service publishes is ankka's own
`GraphConsumer`'s and does not change. The contrib package is public (verify, V2). Spec 001's F8
(the private sidecar package) and requests 7 and 9 become moot; request 6 (ask whether a set of
elements has been applied) stands against the new sink.

**Considered.** The service registering `GraphSink` with `Neo4jStore` itself, so there is one
deployable and no sink container: rejected, because the service would then write to the graph
database, which `deployment.md` D3 forbids, and because the graph suites must run the sink that
will run (spec 001 R13). The in-memory store for tests: rejected for the same reason; it is the
reference store, not the one deployed.

## R3. A sixth sbt module, `process`, between `market` and `service`

**Decision.** `graph` ← `belief` ← `market` ← `process` ← `service`. `process` depends on `market`
because it re-offers the market layer's two graph answers with acts (FR-007a) and resolves nothing
itself. It is published as `ankka-reasoning-process`. Package root `reasoning.process`, with the
`domain` / `application` / `answers` / `api` split of the other layers and `ProcessLayer.scala` for
its vocabulary, components, recorder and endpoints. The appraiser (blueprint, tools, handlers,
questions) is `reasoning.process.appraiser`, in the same module: it is the worked example the
layer is proven by, and the service carries it (FR-010).

**Rests on.** Spec 001 R2: the layering is a fact of the build. `belief` and `market` are not
touched by this feature except where a new layer plugs in as the market layer already does
(`ReasoningService.vocabulary`, `components`, `endpoints`, `published`).

**Considered.** Putting the appraiser in `service`: then the libraries would ship a layer with
nothing that writes to it, and the blueprint could not be tested in tier 1 beside its tools. A
separate `appraiser` module: one more module for one file's worth of data.

## R4. The service hosts ankka's blueprints itself

**Decision.** The service registers `AgentRuntime.descriptors` (the platform's `ankka-blueprint`,
`ankka-run`, `ankka-blueprint-ask`, `ankka-blueprint-worker`, `ankka-blueprint-runs` and
`ankka-blueprint-schedule` components) beside its own, with an `AgentRuntime` extension built as
`withDefaultModel(model).withJudgments(judge).withBlueprints(ctx => registry)`. Blueprint versions
and runs are therefore records of this service's own journal, and the consumers that project them
(R6) read `ChangeSource.eventsOf(BlueprintEntity)` and `eventsOf(RunEntity)` directly.

**Rests on.** ankka's blueprint and run entities are visible only to the service that hosts them;
another service would have to receive them through a topic (036's FR-025/26 name this repository
as that consumer, but the hosting is simpler and the spec's "every blueprint version ankka holds"
means the ones this service holds). The service already runs `ProjectionRuntime` for its graph
consumers, which `runs.list` needs. A `TimerRuntime` is not registered: the appraiser has no
schedule, and `hasTimers` is false (a request, not a schedule, starts a run: spec, "Not in this
feature").

**Considered.** A second service hosting the blueprints and this one following over a topic: two
deployables and a topic for a feature whose point is that the writer and the graph are one service.

## R5. A record a run writes is written through the belief layer's `Recorder`, as the writer `blueprint:<name>`

**Decision.** The three tools call `Recorder.recordEvidence`, `stateClaim` and `stateBelief`, the
same application methods the endpoints call, with the writer `blueprint:<blueprint name>` and the
clock the endpoints use. Every rule of spec 001 therefore applies unchanged and refuses the same
way (FR-001); the refusal's `rule` and `names` are handed back to the model as the tool's error. The
writer is a string the service derives, as `service:…` and `local` are (spec 001 R5); `Writers`
gains the `blueprint:` form, which no caller over HTTP can claim: the ACL never produces it.

**Rests on.** `Recorder` is the one write path and takes the writer as a value, so a tool is just
another caller of it. "The blueprint is the writer" is a clarified choice of the spec.

**Considered.** The tools calling the HTTP routes: a loop through the service's own port, and a
writer the ACL cannot derive.

## R6. Four key value entities, two consumers and a view make the process layer

**Decision.**

| Record | Entity | Id | Written by |
|---|---|---|---|
| blueprint version | `blueprint-version` | `<blueprint>@<n>` | `BlueprintVersions`, a consumer over `eventsOf(BlueprintEntity)`, on `VersionRegistered` |
| run | `run` | the run id | the first of: a tool's first call in the run (R7), or `RunFollower`, a consumer over `eventsOf(RunEntity)`, on `Started` |
| run ending | `run-ending` | the run id | `RunFollower`, on `Ended` |
| act | `act` | `act:` + 56 hex of SHA-256 of `<run id>\n<record kind>\n<record id>\n` | the tool, after the record (R8) |

Each is a `KeyValueEntity[Slot[A]]` with the `Same`-based repeat rule of the other layers: the same
record again is a repeat, a different one under the same id is `id.held-by-another`. A run is
dated when it started, a run ending when it ended, a blueprint version when it was registered, an
act when the record it names was recorded; `recordedAt` is the service's clock as everywhere. A
view `ActRows` over `act` (keyed by `run` and by `record`, as `ClaimRows` is keyed by `revises`)
serves read-back of a run's acts and of a record's act without the graph; nothing is decided from
it (spec 001 R17).

The two consumers write through `ProcessRecorder`, which checks what a tool's write checks (the
blueprint version a run names is held before the run; the run before its ending) and so keeps
"links point backwards" on the write side.

**Rests on.** `BlueprintEvent.VersionRegistered(number, canonical, digest, registeredAt)` and
`RunEvent.Started(blueprint, version, input, startedBy, deadline, at)` / `Ended(status, reason, at)`
carry everything the records need; the event's subject is the blueprint name or the run id. Both
entities are event sourced, so `stateOf` is unavailable and `eventsOf` is the only source. A
consumer writes to entities through the component client as the market recorder does.

**Considered.** One `run` record updated on ending: refused by the clarified spec. A graph consumer
straight over ankka's `RunEntity` events, with no record of this service's own: then the run's
node would be published by a platform record this service does not hold, the act's `IN` edge would
point at a node with no record behind it, and `/graph/wait` could not say what version to wait for.

## R7. The run is held by whichever learns of it first, from ankka's own record

**Decision.** A tool's first call in a run reads ankka's run through `agents.runs.get(runId)` (the
run id from `RunContext.current`) and writes the `run` record from it: blueprint, version,
`startedBy`, `startedAt`. `RunFollower` writes the same record from `Started`. Both build it from
ankka's record, so the second to arrive is a repeat. The tool caches the run it has seen for the
process's lifetime, so the read is once per run per instance. Before writing the run, the tool
makes sure the blueprint version is held (from `agents.blueprints.versions(name)`), for the same
reason: `BlueprintVersions` may not have caught up with a version registered a moment ago.

**Rests on.** `RunCalls.get` reads the run entity (strongly consistent), and a run has started
before any of its steps call a tool. `RunContext.current` is a thread local set around each tool
invocation and around a handler, carrying `RunRef(runId, step, blueprint, version)`.

**Considered.** The tool waiting for `RunFollower`: a wait on a projection, which spec 001 R3 rules
out of the write side.

## R8. The act is written after its record, with every check made before either

**Decision.** A tool's write is: (1) resolve the run, the worker (R9) and the holder (R10), and
refuse if any cannot be, writing nothing; (2) write the record through `Recorder`, which may refuse
under the belief layer's rules, writing nothing; (3) write the act. The tool answers the model only
after (3), so for every answered call the record is held with its act (FR-004). If the record was
already held (evidence recorded by an earlier run, or a repeat of this run's own write), (3) is
skipped when an act for the record is already held by another run and is a repeat when it is this
run's: a record keeps its first act, and a run gains none for a record it did not write.

Between (2) and (3) the process could die. The record is then held without an act until the same
call is made again: ankka re-runs the step after a restart, the model calls the tool again, (2) is
a repeat and (3) is written. No answered call, and so no run that ended, leaves a record without
its act; the window is named in `data-model.md` and in the limitations page.

**Rests on.** No cross-entity transaction exists (spec 001, constraints). Every check the act
needs is decidable before the record is written: the run is ankka's record, the worker is the
blueprint version's data, the holder is the run's input, and the holder's writers are held.

**Considered.** Writing the act first: it would name a record not yet held, against "links point
backwards", and a refused record would leave a dangling act. Publishing the act from the record's
own consumer: the belief layer would have to know of acts.

## R9. The worker an act names is the step's worker, read from the blueprint version

**Decision.** `RunRef` carries no worker. The act's worker is found from the blueprint version the
run names: the step named in `RunRef` is looked up in `agents.blueprints.version(name, n)` (cached
per version, since a version never changes), and its action's worker is the act's. A tool called in
a step that has no single worker (a gather over several workers, a judge step or a call step) is
refused with `act.worker.unknown`, writing nothing. The appraiser's steps each have one worker where
a tool is called.

**Rests on.** `Action.Ask(worker)`, `Action.Work(worker)` and `Over.Each` name one worker per step;
a `Step` is data and a version's data is fixed. Request 14 asks ankka for the worker in `RunRef`,
which removes the lookup.

**Considered.** One tool per worker (`record_evidence_as_reader`): the tool list would grow with
every worker and say nothing a blueprint reader could not infer. Parsing the session id: it is not
available to a tool.

## R10. The holder a run writes as is named by the run's input, and the blueprint speaks for it

**Decision.** The appraiser's input is `{tranche, holder, question, follows?, observedAt?}`. A tool
reads the run's input (`agents.runs.get(runId).input`, cached with the run, R7) and writes as
`input.holder`. The check is the belief layer's own: the holder is held and `blueprint:<name>` is
among its writers; otherwise `holder.writer.does-not-speak` (403), as for any writer. The holders a
blueprint speaks for are registered when the service starts, from `reasoning.appraiser.holders`
(`appraiser-a,appraiser-b` by default): each of kind `agent`, with the blueprint as its first
writer, a repeat on every later start. A holder already registered by someone else is not touched,
and the blueprint does not speak for it unless one of its writers adds the blueprint.

This realises FR-003's "the holder each of its workers speaks for" as: every worker of a run speaks
for the run's holder, and the blueprint's registration names the holders it may be run for. The
features' wording ("whose worker *reader* speaks for the holder *appraiser-a*") stays true for every
run over that holder. The spec's key entities are not changed; the plan's summary says so.

**Rests on.** A `Worker` is `name, instructions, model, tools, guardrails, budget` and carries no
metadata of this service's. The spec's FR-010 already names the holder as part of the input.

**Considered.** Encoding the holder in a worker's instructions and parsing it: fragile. A table
of worker to holder per version in this service's configuration: a second place a version is
described, which drifts.

## R11. The three tools take at most three parameters, and read the rest from the run

**Decision.** `FunctionTool` takes up to three parameters, each a string, number, boolean, option
or list of those. So:

| Tool | Parameters | The rest, from the run |
|---|---|---|
| `record_evidence` | `document` (its id in the tranche), `excerpt`, `author?` | source, locator, `publishedAt` from the document in the run's tranche; `dated` from `input.observedAt` or the clock |
| `state_claim` | `statement`, `derivesFrom: List[String]` (evidence ids), `stances: List[String]` (each `<question>/<hypothesis> supports` or `… contradicts`) | holder from the input; id `<run id>.<16 hex of SHA-256 of the statement>` |
| `revise_belief` | `hypothesis`, `probability`, `restsOn: List[String]` (each `<claim>` or `<claim>@<weight>`) | holder and `follows` from the input; id `<run id>.<hypothesis id>` |

Ids a tool derives are deterministic, so a call made again (after a restart, say) is a repeat. A
claim id is at most 64 characters when the run id is at most 47; the API refuses a longer run id
for the appraiser (`run.id.too-long`). The evidence tool names a document, not a source and
locator: the document is what evidence is recorded from (glossary), and the run already holds the
tranche. FR-001's provenance fields are all on the record; the tool's surface is narrower than the
requirement's list, which Complexity Tracking names beside request 15.

**Rests on.** `FunctionTool.named(...).param[A](...).param[B](...).param[C](...).handle(...)`,
with `SchemaType` instances for `String`, `Boolean`, `Double`, `Int`, `Long`, `Option[A]` and
`List[A]` only (verify, V5).

**Considered.** A single JSON-string parameter: a model fills it worse than named fields, and the
schema says nothing. Two tools for evidence (one with, one without an author): more surface for
one optional field.

## R12. A tool cannot fail a run; a call step's handler can, and does, naming the claim

**Decision.** A tool's error goes back to the model as a failed tool result and the turn goes on.
So the run is failed, where the spec needs it failed, by a call step whose `BlueprintHandler`
throws: ankka ends the run `Failed` with the reason `"<step>: the handler '<name>' failed: <message>"`,
and no step that reads the failed step runs. The appraiser's `verdict` step (R15) is that handler.
A refusal in a tool (a rule broken, a holder not spoken for) is returned to the model; the
appraiser's workers are instructed to stop and answer with the refusal, and the `verdict` handler
fails the run naming it when a reader's item carries one. Nothing is held in either case.

**Rests on.** `ToolRunner` sends a thrown exception as `ToolResult(isError = true)`; there is no
public call that fails a run; `agents.runs.cancel` gives `Cancelled`, which is not what the spec
says a failed critique gives. Request 16 asks ankka for a tool to fail its run.

**Considered.** Cancelling from the tool: the status would be wrong. Exhausting the worker's
budget: the reason would name the budget, not the claim.

## R13. The appraiser as data: five steps, two workers, one question, two handlers

**Decision.** Blueprint `appraiser`, input shape `{tranche, holder, question, follows?, observedAt?}`:

| Step | Does | Over / reads | Result |
|---|---|---|---|
| `load` | `call("load-tranche")` | `input` | the tranche's documents, a list |
| `read` | `forEach("reader", "load")` | one document per item | `{document, evidence, claim}` per item |
| `critique` | `judge("claim-derives-from-tranche")` | each item of `read` (`Over.Each`) | the judgment's `{answer, probability}` per item |
| `verdict` | `call("critique-verdict")` | `critique`, `read` | `{passed: true, claims: [...]}` or the run fails naming the claim |
| `revise` | `ask("reviser")` | `verdict`, `read`, `input` | `{revision}` |

Workers: `reader` (tools `record_evidence`, `state_claim`; budget 6 model calls), `reviser` (tool
`revise_belief`; budget 4). Both use the model `default`. The question
`claim-derives-from-tranche` is a yes/no judgment whose instructions are the two checks of FR-010;
its structured state per item is the claim (statement, stances), its evidence (source, locator,
excerpt, the document it was recorded from) and the tranche's documents (id, source, locator,
published). `critique-verdict` fails the run with
`claim <id> does not derive from evidence recorded from a document of the tranche` on the first
item whose answer is `no` (or whose reader item carries a refusal, R12), and otherwise returns the
claims passed. `revise` runs only after `verdict`, so a failed critique revises nothing.

The second version differs in the reviser's instructions (how to weigh a contradicting rumour
against an approval): `Appraiser.blueprint(Instructions.v1)` and `Appraiser.blueprint(Instructions.v2)`,
the second registered by `POST /process/appraiser/versions` (R16).

**Rests on.** The builders and shapes of `Blueprint.scala`; a judge step over `Each` (verify, V4);
`Verdict.Judgment` inside an `until` loop re-drafts and names no claim, so the critique is a judge
step and a handler rather than `Step.critique(...)`; the clarified spec asks for a model's judgment
through ankka, which the judge step is. Spec Q4's words "through ankka's critique step" are kept in
spirit: the verdict is ankka's judgment, not this service's check.

**Considered.** `Step("revise").critique("reviser", Verdict.Judgment(q), rounds = 1)`: the drafter
would write the belief revision through its tool before the verdict, so a failed critique would
have revised the belief; and the run's failure reason names the question, not the claim. One
judgment over all claims: the handler could not name the claim that failed.

## R14. Offline, the model and the judgment are the test providers, scripted from the golden set

**Decision.** `ServiceFixture` builds the `AgentRuntime` with a `TestModelProvider` and a
`TestJudgmentProvider`, both exposed to the suites. `ScriptedAppraiser` installs, per scenario, the
standing rules that drive a run from the golden set: for the `read` step, `Scripted.itemOf(req)`
finds the document and the rule answers `call("record_evidence", …)`, then after that tool result
`call("state_claim", …)` with the statement and stance of the document's expected reading, then
`answer({document, evidence, claim})`; for `revise`, `call("revise_belief", …)` with the
probability and weights the tranche's expected readings give (`Instructions.v1` weights, or `v2`'s
for a version-2 run), then `answer({revision})`. The judgment provider answers `yes` at `0.95` to
every item unless a scenario scripts a `no`. The providers are reset in `beforeEach`, so a scenario
scripts only what differs from the golden set's reading (a claim from evidence no document gave,
say). The service on a laptop (`just run`) uses `AnthropicProvider.fromEnv()` and
`JevProvider.fromEnv()` when their keys are set, and otherwise providers that fail every call
saying which key is missing, so the service starts and every route but a run works without a key
(FR-013; deployment D10).

**Rests on.** `TestModelProvider.respondWhen` and the `Scripted` helpers of ankka's testkit; a
scripted `ModelResponse` can carry tool calls; `TestJudgmentProvider.always` and `expect`. The test
kit needs Docker for Postgres, as every tier from 2 already does.

**Considered.** A model provider of this service's own that reads the golden set: it would be a
second script, outside ankka's testkit, and would not fail loudly on an unexpected request as the
test provider does.

## R15. Live, the same blueprint against Anthropic and Jev, in a suite the build skips

**Decision.** `LiveAppraiserSuite` under `service/test` starts its own fixture with
`AnthropicProvider.fromEnv()` and `JevProvider.fromEnv()` and runs the appraiser for one holder over
both tranches. It asserts the first revision's probability below one half and the second's above,
the run status `completed` for both, and every record held with its act (through
`/process/runs/{run}/acts`). It is `assume`d on both `ANTHROPIC_API_KEY` and `TYPESAFE_API_KEY`
being set: munit reports it skipped, never failed, without them, and CI never sets them. "A model
key" in the features means these two. SC-007's nine runs in ten is measured by hand and recorded
in quickstart.md, not asserted by the suite on every run.

**Rests on.** ankka's model provider is Anthropic's and its judgment provider is TypeSafe AI's Jev;
there is no judgment provider over a text model (request 17 asks for one). A document "plainly for
or against" (spec, Assumptions) is the golden set's own design.

**Considered.** A `Verdict.Critic` worker as the live critique, so one key would do: the offline and
live critiques would then differ, which US4 forbids.

## R16. Runs are started, and a version registered, over HTTP; the appraiser only

**Decision.** `POST /process/runs` starts a run of the appraiser with a run id and an input, and
answers the run as held once ankka has accepted it. `POST /process/appraiser/versions` registers
the appraiser with the instructions named in the body (`{instructions: "v1" | "v2"}`, the two the
service carries), answering the version number and whether it was new. No route registers an
arbitrary blueprint: the service carries its blueprints as code, as ankka's `carrying(...)`
intends, and a version is a deployment. The two instruction sets exist so that the golden set's
second holder can be produced without a redeploy, in a suite and on a laptop.

**Rests on.** Every scenario reaches the service through its routes (spec 001 contracts, properties
a test holds). `BlueprintCalls.register` is idempotent on the digest and gives a new version on a
change. `RunCalls.start` is idempotent on run id, blueprint and input and refuses a different input
under a held run id with `Conflict`, which the API passes through as `409 run.id.held-by-another`.

**Considered.** Registering version 2 by restarting the service with other code: true to
deployment but not runnable as a scenario on a laptop without a second image.

## R17. The golden set is files at the repository root, packaged as resources

**Decision.** `golden-set/question.json`, `golden-set/may.json`, `golden-set/june.json`, and
`golden-set/seed.json` (the question, the sources and the market, as a seed file for a laptop).
The `process` module adds `golden-set/` to its unmanaged resources, so the service, the suites, the
live run and the docs tool read the same files; the seed client reads them from disk. A document is
`{id, source, locator, publishedAt, text, expectedReading: {hypothesis, stance, statement}}`; a
tranche is `{name, month, documents}`. Each text is a few hundred words and every name and date is
made up, as the launch example's are. The documents' sources are registered by `seed.json` (the
appraiser records evidence, and a source has to be held first: spec 001's rule stands; the service
registers the golden set's sources at start, as it registers the holders, so a run needs no seed).

**Rests on.** FR-009: one set of files every suite, page and live run read. The docs tool includes
regions of code, not of JSON, so the pages quote a document's reading from the service's reply as
every page does.

**Considered.** Test resources only: the laptop and the pages could not see them. A database of
documents: fetching and storing documents is out of scope.

## R18. Answers with acts are the lower answers plus one walk

**Decision.** `ProcessAnswers` takes a `BeliefAnswers` and a `MarketAnswers` and, for each answer,
runs the lower answer unchanged, collects every record id in it (evidence digests, claim ids,
revision ids), and runs one Cypher walk:

```cypher
MATCH (a:Act)-[:WROTE]->(r:Element) WHERE r.id IN $records
MATCH (a)-[:IN]->(run:Run)-[:OF]->(v:BlueprintVersion)
OPTIONAL MATCH (e:RunEnding)-[:ENDED]->(run)
RETURN r.id, a, run, e, v
```

The reply is `{answer: <the lower reply, byte for byte>, acts: [{record: {kind, id}, act, run,
runEnding?, blueprintVersion}]}`, one entry per record that has an act, under `/process/answers/…`
with the lower routes' paths and parameters, and `/process/markets/{market}/resolution` and
`/beliefs-at-resolution`. `asOf` and `asRecordedBy` apply to the lower answer; an act is shown for a
record that counts, whenever the act was recorded (as a source is shown beside evidence, spec 001
F13). A reader asks once (FR-007a).

**Rests on.** Spec 001 R9: an answer is a walk from a node found by id; FR-027: computed from the
graph. The lower layers' answer types and routes are unchanged, so the belief layer names nothing
above it.

**Considered.** A generic "written by" part on every lower answer: the belief layer would have to
know a `WROTE` edge exists.

## R19. Time in a run is the service's clock, or the input's `observedAt`

**Decision.** A record a run writes is dated by `input.observedAt` when the input names it, and by
the service's clock otherwise, as a writer's record is dated by its stated date or the clock (H3).
The suites move the shared `SettableClock` to a day in May before the May run and to a day in June
before the June run, so the as-of scenario reads two revisions dated a month apart; the laptop
walkthrough passes `observedAt` instead. A document's `publishedAt` is the document's; the evidence
rule that `publishedAt` is not later than `dated` holds because every document is dated in its
tranche's month and the run is dated after it.

**Rests on.** Spec 001 R4 (the time is read once by the endpoint and carried in the command): here
the tool reads it once, from the input or the clock, and carries it. The run's own `startedAt` is
ankka's clock, not the service's: the run node's `dated` is ankka's `startedAt`, so a run started
on a laptop today over a May tranche is dated today and its evidence in May. That is right: the run
happened today; what it read was published in May.

## Verify first

Each is a claim this plan makes about something not yet run here. Each is tried first, and its
outcome written beside it.

| # | Claim | How |
|---|---|---|
| V1 | The first ankka release after `0.11.0` carries `ankka-agent` with blueprints, `RunContext`, the test providers and the `Scripted` helpers, and publishes to Maven Central | `sbt update` with `V.ankka` set to it; until then, `publishLocal` of ankka's `main` and `-Dankka.version` |
| V2 | `ghcr.io/thinkmorestupidless/ankka-graph-sink-neo4j:0.1.0` is pulled anonymously and reads a topic with the service's deltas into Neo4j with `Element`, `id`, `_version`, `_deleted` and placeholders at `-1` | `GraphFixture` with the new container; `SinkSmokeSuite` as it is |
| V3 | The contrib image accepts `ANKKA_GRAPH_SINK_VERSION` raised by one as a rebuild from the topic's start | `compose/rebuild.sh`, then an answer before and after |
| V4 | A judge step over `Over.Each` passes `BlueprintCheck` and gives a list of judgment results | `AppraiserSuite` registering the blueprint in tier 1 |
| V5 | `FunctionTool` takes `List[String]` and `Option[String]` parameters and at most three | compiling the tools |
| V6 | `agents.runs.get(runId)` from inside a tool handler returns the run with its input and `startedAt` before any step's tool is called | `ToolsFeatures`, first scenario |
| V7 | A consumer over `ChangeSource.eventsOf(RunEntity)` and one over `eventsOf(BlueprintEntity)` in the hosting service receive `Started`, `Ended` and `VersionRegistered` | `RunsFeatures` |
| V8 | `agents.blueprints.register` with changed instructions gives version 2 while a run of version 1 keeps 1 in its `RunRef` | `GoldenSetFeatures`, the second-version scenario |
| V9 | The same `TestModelProvider` and `TestJudgmentProvider` instances serve every suite in the JVM and are reset between scenarios without restarting the kit | `ServiceFixture` |
| V10 | The test kit of the release shares one Postgres across kits (spec 001 F4), or still does not; either way the fixtures stay shared | the first run of `sbt test` |
| V11 | A handler that throws fails the run with the message in `reason` and no dependent step runs | the critique-fails scenario |
| V12 | A run sent again under its run id with the same input calls no tool again | *a run sent twice writes each record once* |
| V13 | `Scripted.itemOf(req)` identifies the document of a `read` item from the request's text | `ScriptedAppraiser` |
| V14 | The `GherkinSuite` of the release takes one feature file, so `FeatureSuite.directoryOf` goes | the first suite compiled against it |

## Found during implementation

*(written as the tasks are done)*

## Measurements

*(SC-006: the golden set's four runs, the graph caught up and the four answers, on a laptop;
SC-007: ten live runs; SC-009: time to the graph of a record a run writes, with its act)*

## Requests

Each is written out, with what it would remove, in `notes/ankka-requests.md`. None blocks this
feature. Requests 8 and 9 of spec 001 are answered or moot with R1 and R2; request 13 is answered
by ankka's blueprints and stays as the record of why they were asked for.

**To ankka**

14. The worker in `RunRef`, so a tool knows who called it without reading the blueprint version (R9).
15. Tools with more than three parameters, or a parameter that is an object, so `record_evidence` can
    take provenance as the belief layer's route does (R11).
16. A way for a tool to fail its run with a reason, so a critique can fail a run from where it finds
    the fault instead of from a handler step after it (R12).
17. A judgment provider over a text model, or a model provider that judges, so a live suite needs one
    key (R15).

**To ankka, carried over**

6. A way to ask whether a set of elements has been applied, now of ankka's graph sink (spec 001 R10).
10. A setting of ankka's own for how often a state consumer polls (spec 001 F14).
