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

**V1. Holds, at `0.12.0`.** ankka tagged `v0.12.0` on 2026-10-14 with blueprints merged; every
module the build names is on Maven Central at it, `ankka-agent` and `ankka-auth-oidc` among them, so
the build never needed `-Dankka.version`. The move from `0.10.0` compiled with nothing changed but two
matches 0.12.0's enums made inexhaustive: `ErrorCode` gained `WorkflowFailed` and `NotHome`, mapped
to `424` and `421` as ankka's own HTTP layer maps them, and `Caller` gained `Machine` (below).

**V2. Holds.** `ankka-graph-sink-neo4j:0.1.0` is pulled with no credential. `SinkSmokeSuite`,
`PublicationFeatures`, `WithdrawalFeatures` and `CompactionErasureSuite` pass against it unchanged
(20 tests): the store writes `Element`, `id`, `_version`, `_deleted` and placeholders at `-1` as the
sidecar did, and a hand-written delta with no CloudEvents headers is applied, so `sinkHasDrained` and
`SinkOwn` stay as they were. The fixture sets `ANKKA_KAFKA_BOOTSTRAP_SERVERS` on the container; in a
cluster the platform gives it to every service with components, so the descriptor does not.

**V3. Holds.** On a laptop, `just up`, the service, `just seed launch-example` (12 nodes, 18 edges),
then `just rebuild`: the graph emptied, the sink started at version 2 and the graph was whole again
within 3 s, with `/answers/belief` byte for byte the same. The version is kept in `.env`.

**F-a. The contrib image is `linux/amd64` only.** On Apple silicon Docker runs it under emulation and
says so on start; the suites and the compose stack work, slower to start. A request to ankka-contrib
for a multi-architecture image.

**F-b. A caller 0.12.0 can name that R5 did not list: a machine registered on an organization**
(`Caller.Machine`, ankka feature 040), which arrives through the gateway with a token the control
plane issued and the platform verified. It is the writer `machine:<organization>/<name>`, ankka's own
form for it, beside `service:` and `person:`; before 0.12.0 the same request was the gateway and was
refused. A holder's list of writers still decides what it may write as anyone.

**V14. Holds.** `GherkinSuite` takes one feature file; `FeatureSuite.directoryOf` and
`target/feature-suites` are gone. A gateway caller with a token from an issuer in `ANKKA_AUTH_ISSUERS`
is `person:<subject>@<issuer name>` through `ankka-auth-oidc`'s own verifier; with no issuer listed,
no token or a bad one it is refused with `401`; a malformed list stops the service starting.
`WritersSuite` proves each against an issuer in the test JVM whose key is handed to the verifier.

**V10. Holds.** One Postgres container served both the service fixture's kit and the graph
fixture's in one test JVM: the kit of 0.12.0 shares it. The fixtures stay shared for speed.

**F-c. An act's id is its record's alone,** `act:` and 56 hex of SHA-256 of `<kind>\n<id>\n`, not
the run's and the record's. R8 has a record keep its first act and a later run gain none for it, and
that needs the earlier act found when a second run meets evidence already held; with the run in the
id it could be found only through a view, which the write side does not read. Keyed by the record,
the act entity itself holds the rule: the same act again is a repeat, another run's act for the
record is answered with the one held, and anything else under the id is `id.held-by-another`.

**F-d. One kind of edge to several kinds of node.** `WROTE` runs to evidence, a claim or a belief
revision, and an edge kind named one label to. `EdgeKind` gains `alsoTo`, the labels besides `to`;
the vocabulary's check and `GET /graph/vocabulary` read every target, the route writing them as
`Evidence|Claim|BeliefRevision`. No kind of edge of the lower layers changes.

**F-e. The act's property is `recordKind`, not `kind`.** A holder of the belief layer has a `kind`,
and the vocabulary's own scenario holds that no node beneath the process layer has a property of
it. The act's says what kind of record it wrote.

**F-f. What the service copies from ankka is written as `service:reasoning`**, in a cluster and on a
laptop alike. The service's own name in ankka's form for a service, less the project, so no caller
can be named so (a caller is `service:<project>/<name>`). The data model's `local` outside a cluster
would have made a copied run look like a laptop writer's.

**F-g. A run ending is not dated before its run** (`run-ending.dated.before-run`, 422), checked by
the recorder as R6 says. Both dates are ankka's, so it should not occur.

**V9, in part.** One `TestModelProvider` and one `TestJudgmentProvider` are built in
`ServiceFixture`, handed to both fixtures' agent runtimes, and reset before every scenario. No run
asks them until the tools exist (Phase 3), so whether a reset between scenarios is enough is not yet
shown.

**The release page** attaches `ankka-reasoning-<version>-graph-sink.json` in place of the blueprint;
`deployDescriptors` renders both descriptors.

**V4. Holds.** `BlueprintCheck` accepts a judge step over `Over.Each` (`Step("critique").judge(q).each("read")`)
with no problem, at both instruction sets, and its result per item is
`{"<question id>": {answer, probability}}`, which `critique-verdict` reads. The two instruction
sets give two digests (`AppraiserSuite`).

**V5. Holds.** `FunctionTool` takes `List[String]` and `Option[String]` parameters, three at most;
`state_claim` and `revise_belief` take exactly three. A `Refused` thrown inside a handler reaches the
model as the exception's message only, so a tool wraps it in `ToolRefused`, whose message is the
refusal as the HTTP API writes it, `{rule, error, names}`; the scripted reader parses that and the
workers are instructed to answer with its `rule` and `names`.

**V6. Holds, with one trap.** `RunContext.current` names the run, step, blueprint and version in a
tool; `agents.runs.get(runId)` from inside the handler gives the run with its input, `startedBy`
and `startedAt`, and `agents.blueprints.version` the blueprint to read the step's worker from.
The trap: `agents.runs.start` answers a *first* start with the run entity's state from before its
`Started` event (ankka's `thenReply(_ => currentState)`), so the snapshot has no blueprint, version
or start time, and holding the run from it built an empty entity id. `RunsEndpoint` starts the run
and then reads it back; `RunResolver` never trusts a snapshot's `runId`, only the id it asked for.
Request 19.

**V7. Holds, with a lag.** `BlueprintVersions` over `eventsOf(BlueprintEntity)` receives
`VersionRegistered` for the blueprint the service carries, and `RunFollower` over
`eventsOf(RunEntity)` receives `Started` and `Ended`; the version, the run and its ending are in
the graph with their edges (*a blueprint version is in the graph when it is registered*, *a run is
in the graph with the version it ran…*). The registration happens as the runtime starts and the
projection delivers it a few seconds later, so a reader who asks the moment the service is ready
finds no version yet: the suites' catch-up wait asks again for a record not yet held, and a run
holds its version itself when it comes first (R7).

**V8. Holds.** The first scenario to register `v2` is answered `201 {blueprint: "appraiser",
version: 2}`, the holder's earlier runs keep version 1 in their acts, and the second holder's acts
name 2. Sent again, the same instructions are answered `200` with the version held. Later scenarios
see higher numbers (F-p).

**V11. Holds.** The critique-fails scenario: the verdict handler throws, the run ends `failed` with
the reason `verdict: the handler 'critique-verdict' failed: claim <id> does not derive from
evidence recorded from a document of the tranche`, naming the filing's claim; the five claims stay
held, each with its act; no revision follows and the holder has no belief in the question.

**V12. Holds.** `POST /process/runs` with the same body again answers `200` with the run as held;
ankka answers the run as it stands and calls no tool again. Five pieces of evidence, five claims and
one belief revision, eleven acts, after the second send as after the first.

**V13. Does not hold as written; `Scripted` serves otherwise.** `Scripted.itemOf` recognises only
items of the form `"item":"i<n>"`, and a `read` item here is a document object. The scripted model
reads the JSON after the step's first line (`Scripted.userText`), which carries `input` and `item`
for a for-each step and `verdict`, `read` and `input` for the reviser; `Scripted.forStep`,
`afterTool`, `call` and `answer` are used as they are. The tool results a request carries
(`ChatMessage.ToolResults`) give the ids the tools answered with.

**V9, in full.** Two providers shared by every suite in the JVM, reset before each scenario, are
enough: `ToolsFeatures` runs fourteen scenarios, each installing the appraiser's script afresh, on
the fixture every other suite shares.

**F-h. A sixth step, `close`.** R13's five steps end with the reviser's answer; a reviser that was
refused answers `{refused}` and the run would end `completed`. The spec's outline has such a run
refused, naming the rule, and a tool cannot fail a run (R12), so a call step `close` with the
handler `revision-check` follows `revise`: it fails the run `the reviser was refused: <rule>,
naming <names>` or passes `{revision}` on. The `revise` step's result shape allows either answer.
Request 16 would remove it.

**F-i. The reader's answer carries what the critique judges.** R13 has the judgment given the
claim's statement and stances and the evidence's excerpt, but a `read` item of `{document,
evidence, claim}` holds only ids. The reader answers `{document, evidence, claim, excerpt,
statement, stances}` (the first required, the rest optional in the shape), so the judgment reads
them from the item without a read of the service.

**F-j. `belief.rests-on.other-holder` is this layer's rule.** The refusals contract lists it among
spec 001's; the belief layer has `belief.rests-on.other-question` and no rule on the holder, since a
writer may rest on anyone's claim. A run's revision rests only on claims its holder stated, so
`revise_belief` refuses it (422) before the belief layer is asked. *A stance on a hypothesis of
another question* is bound to a question that is not held (`claim.stance.hypothesis-not-held`): a
stance on another held question's hypothesis is what the critique judges (FR-010's second check),
and the belief layer accepts it as it accepts any writer's.

**F-k. The appraiser's holders and the golden set's sources are registered as the registry is
built,** inside `AgentRuntime.withBlueprints`, where the component client is to hand and before
any run can start; T034 placed it in `Main`. An id held by another record is left to it. The
service's tests add a tranche of their own, `stray.json`, whose one document comes from a source
nobody registers, through `REASONING_GOLDEN_SET` pointing at the test resources: a file in that
directory wins over the one the `process` module carries.

**F-l. Each scenario's evidence is its own.** Evidence is identified by source, locator and excerpt;
the scripted reader quotes a passage of the document chosen from the scenario's number, so
scenarios on one shared service do not meet each other's evidence, while two runs of one scenario
do, as *evidence an earlier run recorded* needs. The number is the test JVM's scenario counter,
so the passage is the number's own over thousands of scenarios, not its remainder. What twice made
*a run sent twice* find four pieces of evidence where it had written five in CI was not a shared
passage but the act rows: a view, which lags the writes by a moment on a loaded runner, so a step
that counts a run's acts waits for them as it waits for the run to end.

**F-m. The seed suite's sources are repeats.** The launch example's two sources are the golden set's,
which the service registers as it starts (F-k), so a seed file sent to a fresh service is answered
`200` for those two and `201` for the rest; the suite expects exactly that. The same is true of
anyone following *record reasoning* in the docs, which the docs tasks (T057) say.

**F-n. The answers with acts nest the lower codec.** `WithActs[A]` is serialised by a codec made
with the same configuration as the lower layer's (`Replies.codec`), so `answer` is the lower
route's reply byte for byte without the process layer holding the lower layer's bytes; the
contract's property is asserted by the golden set's scenarios against the lower routes.
`/process/answers/run` applies `asOf` and `asRecordedBy` to the acts it lists, which are dated as
their records; the run and its ending are shown whenever they were recorded.

**F-o. The service without the process layer runs beside the graph fixture.** SC-008 asks every
scenario of the belief layer and the market layer to pass with no process record held, and most of
them read the graph. `NoProcessFixture` is a second service in the test JVM, built with
`process = false`, publishing to the graph fixture's topic and reading its graph database through
the one sink; the suites of `features/record/`, `features/explain/` and `features/market/` run
again against it under their own prefixes.

**F-p. "Blueprint version 1" is the version carrying the first instruction set.** ankka numbers
every registration that changes the blueprint, a run is always of the current version
(`RunCalls.start` names none), and one service serves every scenario of a test JVM. Once a scenario
has registered `v2`, the appraiser carries `v1` again only at a third number, and so on. The
Background step puts `v1` back when it is not current, through `POST /process/appraiser/versions`,
and a feature's "blueprint version n" is read as the latest version carrying `v<n>`
(`ProcessFeatureSuite.versionAt`). The first scenario to register `v2` sees the literal 1 and 2.

**F-q. The case for the hypothesis holds both tranches' supporting claims.** The case (spec 001,
`GLOSSARY.md`) is every claim that supports the hypothesis and is not revised. The May tranche has
two supporting documents, the trade-press piece and the supplier's call, and the appraiser states
claims from them; after the second run the case holds four claims, two from each tranche, and *the
case for the hypothesis after the second run walks back to the June tranche only* could not hold.
The scenario is now *…holds the supporting claims of both tranches, each with the run that wrote
it*; spec US3 and FR-012 say the same. What walks back to June alone is what the current belief
revision rests on, which *the appraiser run over the June tranche revises the belief…* checks.

**F-r. The two holders rest on claims of their own.** FR-011 says a second holder's run rests on
claims of its own, F-j refuses a run's revision that rests on another holder's claim, and the
comparison pairs claims by id (spec 001). So `both` is empty and each side's claims are one
holder's, though every one of them derives from the same evidence, which is shared between runs
(F-l). *The two holders rest on the same claims with different weights* is now *…rest on claims of
their own from the same evidence, with different weights*, pairing the two sides by the evidence
their claims derive from; spec US3 and FR-012 say the same. A comparison that pairs claims by
their evidence would be a belief-layer answer, and is not asked for here.

**F-s. A tranche is read on the last day of its month.** R19 said a day in May and a day in June.
The June rumour is published on 20 June at 22:00, and evidence observed before it was published
is refused (`evidence.observed-before-published`), so a June run dated 20 June at 09:00 failed its
critique. The suites date the May run 31 May and the June run 30 June; the market resolved
afterwards is dated 14 November, and the clock moves there first (`dated.later-than-now`).

**F-t. A second holder's run writes no evidence act.** Evidence is identified by its content, so
the second holder's run finds the first holder's evidence held and that evidence keeps the earlier
run's act (*the act of the evidence names the earlier run*). Its acts are the five claims and the
revision; the suite's expected counts say so. A resolution reaches the graph with its market,
whose version moves, so the wait for it is a wait on the market.

**F-u. The scripted judgment's standing answer wins.** `TestJudgmentProvider` looks a standing
answer up before the queue, so a no queued after `always(yes)` never reaches the critique.
`ScriptedAppraiser.judgeNo()` resets the provider and makes no the standing answer; the verdict
then fails on the first item, the filing's, whose claim `withClaimFrom(evidence, "filing")` made
derive from evidence a writer recorded from no document. T047's `judgeNo(<that claim>)` is that.

## Measurements

| Criterion | What was timed | Measured | Where | Verdict |
|---|---|---|---|---|
| SC-006 | the golden set's four runs, the graph caught up and an answer (*the two holders…*, *the market resolved afterwards…*) | 6 s a scenario; the four runs alone 4.2 s; the eleven scenarios of `GoldenSetFeatures` 50 s | 2026-10-19, an Apple silicon laptop, the scripted model, the sink under amd64 emulation | well under the two minutes asked |

*(SC-007: ten live runs; SC-009: time to the graph of a record a run writes, with its act: Phase 7)*

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
19. `RunEntity.start` to answer the state after its `Started` event, not before it (V6).

**To ankka, carried over**

6. A way to ask whether a set of elements has been applied, now of ankka's graph sink (spec 001 R10).
10. A setting of ankka's own for how often a state consumer polls (spec 001 F14).
