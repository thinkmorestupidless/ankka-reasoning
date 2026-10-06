# Research: The belief layer

Decisions for [plan.md](plan.md), each with what it rests on. File references are to the sibling
checkouts as read on 2026-10-06: `ankka` at `v0.10.0` plus 22 commits (`eee47a44`), `ankka-flow` at
`v0.3.0` plus 6 commits on `main`. "Verify first" marks a claim read from code or documentation and
not yet run; the task that touches it starts with a test that would show it false. They are
collected at the end.

## R1. A record that carries text is a key value entity; only the belief line is event sourced

**Decision**: Question, Holder, Source, Evidence, Claim and Market are key value entities, one per
record, keyed by the record's identifier. A market's resolutions are held in the market's state. A
belief (one holder, one hypothesis) is an event sourced entity whose events are its revisions and
which carries numbers, identifiers and times only. Each belief revision also has a key value read
record (R8). No free text a writer supplied is ever written to an event.

**Rationale**: FR-036 requires withdrawn text to be gone from everything the service keeps. ankka
never removes an event: `deleteEntity()` appends a marker (`EventSourcedEntityHost.scala:240,
301-303`), retention is `RetentionCriteria.snapshotEvery(n, 2)` with no deletion of events
(`:77-79`), and nothing under `modules/runtime/src/main` deletes from `event_journal`. A deleted
entity's snapshot still holds its last value. A key value entity is one row in `durable_state`,
updated in place (`UPDATE … SET revision = ?, … state_payload = ?`), and "a durable state store
keeps no history" (`DurableStateSourceProvider.scala:26`). So text can be erased from a key value
entity by writing the state again without it, and from nowhere else.

Questions, holders, sources and markets carry text too (statements, names, criteria, an authority).
Their text is not withdrawable in this feature, but holding it in key value entities means it can
become so without a migration.

What is given up is the event log as an audit trail. Each record carries the time it was recorded
and its writer, and a withdrawal carries its note, writer and time, which is what the spec asks to
be kept. A record that is stated once has one event's worth of history either way.

The belief line is the exception because it has what the others do not: many revisions, an order
that must hold when two writers race (FR-007), and no text. An event sourced entity gives one
revision per event and a state that stays small.

**Alternatives considered**: Everything event sourced, with text in a side store keyed by record
and only a digest in the event: two writes per record and a consumer to reconcile them, to keep a
journal nobody reads. Everything event sourced with the text encrypted under a per-record key that
is destroyed on withdrawal: the key still has to live somewhere erasable, which is a key value
entity again. The belief line as a key value entity holding every revision: an unbounded state, the
mistake of a twin that grew until it could not be written.

**Verify first**: V1, V2.

## R2. Five sbt modules, so the layering is a fact of the build

**Decision**: `graph` ← `belief` ← `market` ← `service`, and `seed` beside them depending on none.
`graph` holds the vocabulary types, the element builder and the graph reader. `belief` and `market`
each contribute a vocabulary value, a component list and endpoints. `service` composes them.

**Rationale**: FR-018 says the belief layer names nothing of the market layer. With `market`
depending on `belief`, a reference the other way does not compile. This is the family's habit of
making a boundary a build-level fact (`ankka/CLAUDE.md`: "`operator` depends only on `crd` … so
'the operator cannot reach into the control plane' is a build-level fact"). The holder kind
`market` is the one place the upper layer reaches down: the belief layer validates a holder's kind
against the composed vocabulary it is handed, and stores a string.

**Alternatives considered**: One module with packages and a test that greps imports: catches less
and later.

## R3. Rules that span records are checked by the application, before the command

**Decision**: A `Recorder` in each layer's `application` package takes a record, reads every record
it names through the component client, applies the rules, and only then sends one command to the
record's own entity. The entity checks what it alone can know: that the identifier is new or the
record is the same one, and for a belief that the revision follows the current one.

**Rationale**: ankka's rule is that "an entity's command handler never [calls other components],
because … a check made by calling out would not hold by the time the effect is applied"
(`docs/concepts/effects.md:132-136`), and that "checks that must hold belong in an entity or a
workflow" (`docs/concepts/designing-services.md`). The concern is a fact that can change between
the check and the write. Here none can. A record that is held stays held; its date and its links
never change; a holder's writers are only added to; a question's hypotheses are only added to; a
market's closing time is fixed. Every rule in FR-008, FR-010 and FR-011 reads facts of that kind,
so a check made before the command still holds after it. The two rules that do depend on changing
state are both inside one entity: "follows the current revision" and "equal to the current price"
are decided by the belief entity under its own single-writer guarantee.

Reads are of entities by id, which are strongly consistent, never of a view or of the graph.

**Alternatives considered**: A workflow per write: durable steps and compensation for a write that
has nothing to compensate. Passing the referenced records' facts into the entity and re-checking
there: the entity cannot verify what it is told, so it adds nothing.

## R4. The time is read once, by the endpoint, and carried in the command

**Decision**: An injectable `Clock` in the service gives the endpoint "now". The `Recorder` uses it
for the rule that nothing is dated later than now, defaults a missing date to it, and puts it in
the command as the time recorded. Handlers read no clock.

**Rationale**: `CommandContext` has `entityId`, `componentId`, `metadata` and `sequenceNumber` and
no clock (`modules/sdk/.../contexts.scala`); the documented rule is that "a value that is not
repeatable, such as the current time, belongs in the event the handler persists"
(`docs/concepts/effects.md:128`). satisfactory does the same and has asked for a clock in the
context. A fixed clock is also what lets a scenario say "today is 20 May".

## R5. A writer is the caller's identity, read by the ACL and carried in the command

**Decision**: One ACL for every endpoint, `Acl.Authenticate`, turns the caller into a writer:

| Caller | Writer |
|---|---|
| `Caller.Service(project, name)` | `service:<project>/<name>` |
| `Caller.Gateway` with a bearer token an accepted issuer signed | `person:<subject>@<issuer name>` |
| `Caller.Gateway` otherwise | refused, 401 |
| `Caller.Local` (outside a cluster) | `local` |

The writer is put in every command and kept with the record. "Speaks for" is the holder's own list
of writers. Stewards (FR-034) are writers named in `REASONING_STEWARDS`. Any admitted caller may
read. The writer is not published to the graph.

**Rationale**: `HttpEndpoint` exposes `caller` always and `principal` once the ACL has authenticated
(`HttpEndpoint.scala:215-237`); a service's identity comes from its certificate; a person's from a
token verified by `ankka-auth-oidc` against `ANKKA_AUTH_*` issuers. Nothing carries identity into a
command automatically (`ShardingTransport.ask` adds trace entries only), so it travels in the
command input, set by the endpoint and never taken from the request body. `AnkkaTestKit.asCaller`
gives a test distinct service callers, which is how "a writer who does not speak for the holder" is
exercised. Writers are kept out of the graph because a person's subject is personal data and plays
no part in the reasoning.

**Alternatives considered**: A writer header the caller sets: anyone could be anyone. API keys the
service issues: a second identity system beside the platform's.

**Verify first**: V5.

## R6. One graph consumer per kind of entity, publishing its node and the edges that leave it

**Decision**: Each entity has one graph consumer on the one topic. For a key value entity the
source is `ChangeSource.stateOf(...)` and the version is the state's revision; for the belief it is
`ChangeSource.eventsOf(...)` and the version is the event's sequence number. A consumer publishes
the record's node and every edge the record stated, built through the vocabulary (R14). A question
publishes its hypotheses too, since it is their only writer. A market publishes its resolutions.

**Rationale**: The delta contract's rules are exactly the spec's: an element is its whole state,
has one writing entity, and its version rises with that entity's history
(`ankka/docs/build/graph.md`, "The rules a writer keeps"). A graph consumer over a key value
entity is supported (`modules/testkit/src/test/.../GraphComponents.scala:50-54`). Because records
do not change, a consumer is a function of the state it is given, so a change handled twice
publishes equal deltas and nothing has to be made safe to repeat. An edge to a node another entity
publishes is fine: the sink creates a placeholder until that node arrives.

Edge types and endpoints never change, which matters: the sink finds an edge by its type and
endpoints, so an edge re-published under the same id with different ones would leave the old edge
live.

**Verify first**: V2, V7.

## R7. One compacted topic and a pipeline that is the sink alone, pinned at ankka-flow 0.3.0

**Decision**: The topic is `reasoning-graph`, declared by the pipeline's blueprint as its own so
the operator creates it compacted; the blueprint names one streamlet, `builtin/neo4j-merge-sink`.
The pipeline is deployed before the service. The topic sets a bound on how long a superseded record
may wait to be compacted. On a laptop a compose file runs Kafka, Neo4j and the sink's sidecar
image, and creates the topic compacted itself.

**Rationale**: The merge sink and compacted delta topics are in the released `v0.3.0`, and the
sidecar image is published as `ghcr.io/thinkmorestupidless/ankka-flow-sidecar:0.3.0`. ankka creates
no topics, and a service that publishes first on a broker that auto-creates gets an uncompacted
topic the operator then only reports (`ankka-flow/docs/build/graph-from-ankka.md`). The sink
replaces a node's properties whole (`SET n = d.properties`), which is what removes withdrawn text
from the graph. 0.3.0's tombstone leaves labels and properties in place, which does not matter
here because nothing is ever tombstoned. The compaction bound exists for FR-036: a withdrawn
record's earlier delta stays in the log until its key is compacted.

No compose file or script in either repository runs an ankka service, the sink and Neo4j together
without Kubernetes; `ankka-flow/samples/checkout-graph/docker-compose.yml` is the model, with the
mapper's sidecar dropped and the service reaching Kafka on the external listener.

**Verify first**: V3, V6.

## R8. A belief is a line entity plus a read record per revision; a line is read by walking back

**Decision**: The belief entity (id `<holder>~<question>~<hypothesis>`) accepts a revision when it
names the current revision as the one it follows, or none when there is none, and persists it as
an event. Its state is the current revision and a count. A consumer over its events writes each
revision to a key value read record keyed by the revision's identifier; the endpoint writes it too,
straight after the command, so the writer can read it back at once. A revision is read by id; a
line is read from its head by following `follows`, a page at a time.

Repeat-safety: the `Recorder` first reads the revision's read record. Present and the same, it is
returned; present and different, the identifier is refused; absent, the command is sent, and the
entity recognises a repeat of its current revision itself.

**Rationale**: FR-007 needs one point that orders revisions, which is one entity. FR-014 needs the
line readable from the service alone. A view cannot give it: a view has exactly one source and one
row per source entity (`ankka/docs/reference/limitations.md`, "Views read one source into one
table"), so the row would be the whole line. Multi-source views are specified and not built
(`ankka/specs/031-multi-source-views/`). A read record per revision is bounded, strongly
consistent to read, and already what every other record is. The graph consumer for revisions reads
the belief's events, not the read records, so the graph does not wait on them.

**Alternatives considered**: Creating the revision record first and advancing the line second: a
refused advance leaves a revision that was already published to the graph. Keeping every revision
in the belief's state: unbounded for a market observed for months.

## R9. An answer is a walk of the graph that starts from a node found by its id

**Decision**: `GraphReader` has one method per answer. Each is one Cypher query, parameterised,
that starts at a node matched on `Element.id` and walks typed edges. Times are properties holding
whole milliseconds since the epoch. As of a date `d` and as recorded by a time `r`, a record counts
when `dated <= d` and `recordedAt <= r`; a claim counts as revised when a claim that revises it
counts; a belief's revision then is the last in its line that counts. Results are mapped to answer
types whose every field is a record's property or identifier.

**Rationale**: The sink writes whole numbers as 64-bit integers and has no date type
(`ankka-flow/sidecar/.../Deltas.scala:20-32`), and it creates one constraint, unique `Element.id`,
and no other index (`Neo4jMergeStage.scala:283-284`). Every answer in the spec is anchored on
something the reader names (a belief, a hypothesis, a question, a market), so each query begins
with an indexed lookup and nothing has to be indexed by this application. Both conditions are
monotonic along a belief's line, since a revision is neither dated nor recorded before the one it
follows, so "the last that counts" is a prefix of the line and well defined. Withdrawn text cannot
come back at an earlier time because it is no longer in the graph at all.

**Alternatives considered**: Answering from views in the service: joins across records are what a
view cannot do. Storing history in the graph as validity intervals: unnecessary when nothing
changes.

**Verify first**: V10, V11.

## R10. Waiting for the graph means the record's node and every edge it stated

**Decision**: `POST /graph/wait` names a record. The service reads the record's entity for its
version, computes the keys of its elements with the same function its graph consumer uses, and
polls the graph until every one is present at that version or later, or a limit passes. FR-020 and
its scenario are reworded to say node and edges.

**Rationale**: A node's `_version` is the version of the last delta applied to that node. Its edges
have other keys, hash to other partitions, and commit in other transactions, so a node being
current does not show its edges are (`ankka-flow/docs/reference/graph-deltas.md`, rule 4). An
answer read after a wait on the node alone could miss the claim's evidence.

## R11. Identifiers are the writer's, except evidence, whose identifier is what it is

**Decision**: A writer chooses the identifier of a question, a hypothesis within its question, a
holder, a source, a claim, a belief revision, a market and a resolution within its market:
1 to 64 characters of `[A-Za-z0-9._-]`, starting with a letter or digit (F3). A hypothesis is referred
to as `<question>/<hypothesis>`. An evidence identifier is the SHA-256, in hex, of its source,
locator and excerpt. The same record under a held identifier is that record; a different one is
refused.

**Rationale**: FR-012 needs a repeat to be recognised, which needs the identifier to arrive with
the record. FR-004 needs the same evidence recorded twice to be one, whoever sends it, which needs
the identifier to follow from the content. The digest is kept when text is withdrawn, so withdrawn
evidence recorded again is recognised and stays withdrawn. A digest cannot be turned back into the
excerpt, though someone who already has the text can confirm it was once held.

**Verify first**: V8.

## R12. Every scenario runs as a test, through ankka's GherkinSuite

**Decision**: One suite per feature file in `modules/service/src/test`, each a `GherkinSuite` over
that file, sharing step definitions by area. The five suites of `features/record` other than
withdrawal run the whole service with an in-memory broker; the rest run over the graph fixture
(R13). Steps speak to the service over HTTP only. A suite is added in the slice that implements
its feature, so no suite ever meets a step that is not yet bound.

**Rationale**: `GherkinSuite` is in the main sources of the published `ankka-testkit`
(`modules/testkit/src/main/scala/.../GherkinSuite.scala`): one munit test per scenario or outline
row, and an undefined step fails. Driving the service over HTTP means a scenario proves the
contract a writer and a reader actually use. The speckit-bdd checker keeps the words honest;
these suites keep the behaviour honest.

**Verify first**: V4, V9.

## R13. The graph is tested with the sink that will run, not a stand-in for it

**Decision**: The graph fixture starts, once per test JVM, a Kafka container, a Neo4j container and
the released sidecar image configured as the sink, on one container network, and creates the topic
compacted. The service under test publishes to Kafka on its external listener and reads Neo4j over
Bolt. Images are named by system properties forwarded to the forked test JVM, never by a literal
in a test.

**Rationale**: A test double for the sink would pass while the real one did something else, and the
behaviours that matter here are the sink's: replacing properties whole, placeholders, skipping a
stale version. ankka-flow's own suites start Kafka and Neo4j the same way
(`Neo4jSuite.scala:21-26`, `KafkaSuite.scala:31-33`) with the same image properties.

**Verify first**: V3.

## R14. The vocabulary is a value, and elements can be built only through it

**Decision**: `Vocabulary` is a list of `NodeKind` (label, layer, properties with their types, the
kind of record that publishes it) and `EdgeKind` (type, layer, from, to, properties, publisher).
`Elements` builds a graph element from a kind and refuses a property the kind does not name.
Consumers have no other way to build one. The composed vocabulary is served at
`GET /graph/vocabulary` and written out as [contracts/graph-vocabulary.md](contracts/graph-vocabulary.md).

Three tests hold FR-017 and FR-018: every element any consumer publishes for the seeded set is of
a declared kind with declared properties; no kind has two publishers; and the belief layer's
vocabulary, taken alone, names no kind of the market layer and loses nothing when the market layer
is absent.

**Rationale**: Nothing in ankka or ankka-flow knows a graph's schema: labels and types are
whatever a consumer writes. A vocabulary that is only a document drifts; one the code builds
through cannot.

## R15. Routes take at most two path parameters, and a refusal names its rule

**Decision**: Records are created by `POST` to a collection with the identifier in the body, read by
`GET` with one path parameter, and beliefs and answers are addressed by query parameters. A refusal
is a JSON body with the HTTP status, a sentence, the rule's stable name and what broke it. Rule
names and statuses are in [contracts/refusals.md](contracts/refusals.md).

**Rationale**: A handler takes up to two path parameters (`HttpEndpoint.scala:343-461`), and a
belief is addressed by a holder, a question and a hypothesis. FR-013 asks that a refusal name the
rule, and a scenario asserts on it, so the name is part of the contract and not the sentence.
`Respond(body, status, headers)` lets an endpoint choose its error body.

## R16. The launch example and the ten questions are JSON, sent by a client of the service

**Decision**: `seed/launch-example.json` and `seed/ten-questions.json` hold records in the order
they are stated, with their dates. The `seed` module reads a file and posts it to a running
service. The tests use the same files through the same client.

**Rationale**: SC-002, SC-003, SC-005 and SC-006 are stated over a seeded set, and US1's test wants
history entered with past dates. A client that goes through HTTP is the first writer other than a
test, and what a later agent will be.

## R17. Which claims revise a claim is read from a view over the claims

**Decision**: `ClaimRows`, a view over the claim entity's state with one row per claim, is queried
by the claim a row revises. A row holds identifiers and dates and never a statement, so no
withdrawable text is copied into a view's table. It serves `revisedBy` on a claim read back from the service, and
nothing else.

**Rationale**: A claim knows the claim it revises; the revised claim does not know its revisers,
and telling it would be a second write to another record and a link with two owners. A view is the
platform's answer for "every row with this value in a field" (`ViewClient.where`,
`modules/runtime/.../ViewClient.scala:91-127`). It lags, so no rule reads it: whether a claim is a
revised claim is never a condition for accepting anything.

## R18. The market layer writes through the belief layer and adds nothing to it

**Decision**: Opening a market registers a holder of kind `market` and then creates the market,
both repeat-safe, so a failure between them is mended by sending the market again. A price
observation is sent to the belief entity of that holder as a revision with `unlessUnchanged`, a
flag any caller may set. Resolutions are held in the market's own state, where one entity orders
them.

**Rationale**: FR-029 makes a price observation a belief revision, so the belief entity is the one
place that decides what is current, for a market as for anyone. "Equal to the current one adds
nothing" is a comparison with current state and belongs in that entity; expressed as a general
flag, the belief layer still names nothing of markets. A market has at most a handful of
resolutions, so holding them in the market keeps their order under one writer without another kind
of entity.

## Verify first

| # | What | Settles |
|---|---|---|
| V1 | After a key value entity's state is written again without its text, no row of `durable_state` holds the earlier payload | R1, FR-036 |
| V2 | A graph consumer over `ChangeSource.stateOf` is delivered the state after a withdrawal at a higher revision, and sink 0.3.0 then holds the node without the withdrawn properties | R1, R6 |
| V3 | The sidecar image 0.3.0 runs as the sink alone from mounted configuration, against a testcontainers Kafka with an internal and an external listener and a Neo4j on the same network | R7, R13 |
| V4 | `GherkinSuite` resolves `features/` from a module's forked test JVM, and three suites over three subtrees run every scenario exactly once | R12 |
| V5 | An `Acl.Authenticate` decision can read the caller and, for the gateway, defer to `ankka-auth-oidc`; `asCaller` yields distinct service callers in a test | R5 |
| V6 | A blueprint topic can carry `max.compaction.lag.ms`, and the operator creates the topic with it | R7, FR-036 |
| V7 | A service with graph consumers starts under `AnkkaTestKit` with an in-memory broker and no Kafka | R6, R12 |
| V8 | An entity id may contain `~`, `.`, `-`, `_` and 64 hex characters, and may be as long as a belief's | R8, R11 |
| V9 | `ankka-testkit` 0.10.0 on Maven Central contains `GherkinSuite`, and `ankka-auth-oidc` 0.10.0 is published | R12, R5 |
| V10 | With default consumer and sink settings a record reaches Neo4j within five seconds nine times in ten | R9, SC-007 |
| V11 | The Neo4j driver's blocking session runs on an endpoint's virtual thread without pinning it for the length of a query | R9 |

## Found during implementation

Outcomes of the claims above, and what else building it found. Each changed the plan where it says.

| # | Outcome |
|---|---|
| V4 | **Settled differently.** `features/` resolves from the forked test JVM once `Test / baseDirectory` is the repository's root. But testkit 0.10.0's `GherkinSuite` runs every feature under a directory and cannot be given one file (F2). |
| V5 | **Passed**, for services and local callers (`WritersSuite`). A person's token is not verified at all (F1). |
| V1 | **Passed.** A key value entity's row is overwritten in place: after a withdrawal no row of the service's database holds the text (`ErasureSuite`, which first shows the row did hold it). |
| V2 | **Passed.** A graph consumer over `ChangeSource.stateOf` is delivered each revision and sink 0.3.0 replaces the node whole (`PublicationFeatures`); a withdrawn record's node loses its text, in the graph and in a graph rebuilt from the topic (`WithdrawalFeatures`), and the earlier delta leaves the compacted topic (`CompactionErasureSuite`). |
| V3 | **Passed**, with the image built from ankka-flow's `v0.3.0` tag (`SinkSmokeSuite`); see F8. |
| V6 | **Passed.** A blueprint topic takes Kafka settings under `topic { … }`, and `flow verify` accepts `max.compaction.lag.ms` there. |
| V10 | **Passed.** Nine in ten records are in the graph within 3.1 s; see Measurements. |
| V7 | **Passed.** The service starts with its graph consumers over an in-memory broker (`ServiceFixture`) and over Kafka (`GraphFixture`). |
| V11 | **Passed** by construction: every read runs on a virtual thread of its own and the caller waits on it with a deadline (`Neo4jGraph.read`). |
| V8 | **Settled differently.** Characters pass, `~` among them. Length does not: see F3. |
| V9 | **Partly.** `ankka-testkit` 0.10.0 has `GherkinSuite`. `ankka-auth-oidc` is not published at any version (F1). |

**F1. ankka 0.10.0 cannot verify a person's token.** `ankka-auth-oidc` arrived with ankka's feature 022, which merged after `v0.10.0`, and has never been published to Maven Central. R5's second row (the gateway with a bearer token) therefore cannot be built on a release. The service names another service by its certificate and a local caller as `local`, and refuses the gateway with `401`. Person tokens wait for the first ankka release that carries the module; nothing else in this feature depends on them.

**F2. `GherkinSuite` in 0.10.0 takes a directory, not a file, and has no way to mark a scenario as run elsewhere.** Both arrived after `v0.10.0`. R12 wants one suite per feature file so that a suite never meets unbound steps. Until the build moves to a release with them, each suite is handed a directory under `target/feature-suites/` holding a copy of its one file (`FeatureSuite.directoryOf`). Test names therefore show the copy's path.

**F3. An entity's id is bounded by a 255-character column it shares with its component's name.** `persistence_id VARCHAR(255)` in ankka's schema holds `<component>|<entity id>`. A belief's id is three identifiers joined, so an identifier is at most **64** characters (not 100), a market's at most 32 and an outcome name at most 16, which keeps every derived identifier within 64 and a belief's id within 194. `EntityIdSuite` round-trips the longest of each.

**F4. The test kit of 0.10.0 starts a Postgres per kit.** One service fixture is therefore shared by every suite in the test JVM, and a scenario keeps out of the others' way by naming its records after itself. The kit of the release after shares one container itself.

**F5. A repeat is recognised by what the writer supplied.** "The same record sent again" cannot compare the date when the writer left it out, since the service supplied a different one each time. The entities compare the writer's content and the date only when it was stated (`Same`).

**F6. Three rule names the contract did not have** were needed and are added to it: `claim.stance.unknown` (a stance that is neither word), `record.not-held` (the body of a `404`), and `command.refused` (an entity's own error, which should not occur).

**F7. A holder and a source have no date a writer states, so the date rule exempts them.** History is entered with past dates, but a holder or a source is registered whenever it is registered. Held to "never dated before a record it links to", evidence observed last May could not name a source registered today. They are the cast and not the plot: `recordedAt` still never rises along any edge, and FR-011, the vocabulary's V3 and the data model say so. The same test found that a claim and a belief revision were not checked against their hypothesis's date; they are now (`claim.dated.not-before-hypothesis`, `belief.dated.not-before-hypothesis`).

**F8. The released sink image cannot be pulled.** `ghcr.io/thinkmorestupidless/ankka-flow-sidecar:0.3.0` answers `denied` to an anonymous pull, so the package is private although ankka-flow is public. The graph suites ran against the same image built from the `v0.3.0` tag and tagged with that name locally. Until the package is public, CI here builds the image from that tag before it tests, which no pull request has yet run, and the laptop walkthrough needs the image built by hand.

**F9. Two listeners on Kafka, and the fixture's descriptor.** The sink's container reaches Kafka on an internal listener and the service on the mapped one (`KafkaContainer.withListener`). The sink accepts the built-in descriptor copied from ankka-flow's fixtures as it is.

**F10. A step's body takes at most four values in testkit 0.10.0.** A step that names five things is written with one of them fixed in its expression. Nothing in the features changed for it.

**F11. A price observation is recognised as a repeat by its derived identifier before it is sent on.** `<market>.<outcome>.<observedAt in milliseconds>` is checked against the revisions held; without that check the same observation sent after a later one was refused for not following the current revision, where FR-027 wants it answered with the revision it already is.

**F12. A revision named by its identifier has to count at the time asked about.** `/answers/belief-change` took its two revisions by id and applied `asOf` only to what they rest on, so it answered about revisions dated after the time asked. `AsOfPropertySuite` found it; the answer is now refused with `answer.revision.later-than-asked`. The scenarios had not, because each asks about revisions that exist at its time.

**F13. An answer as of a time names a source or a holder registered after it.** This follows from F7 and is the reading of SC-006 the suites hold: no *stated* record in an answer is dated later than the time asked about. A source is shown beside evidence that counts, and a holder beside a claim or a revision that counts, whenever each was registered. `asRecordedBy` is not read this way for links: `recordedAt` never rises along an edge, so nothing in such an answer was recorded later.

**F14. The time to the graph is the consumer's poll.** A graph consumer over a key value entity's state is fed by polling, and Pekko's `refresh-interval` defaults to three seconds. Under a burst of 555 writes that put nine in ten at 5.1 s, over SC-007's five. The service sets `pekko.persistence.r2dbc.refresh-interval = 1s` in its `application.conf`, as ankka's control plane sets it to 500 ms for itself; it is the polling knob and not the `behind-current-time` guard. Request 10 asks ankka to own the setting.

**F15. Tier 5 of the quickstart was not run.** The laptop's `kubectl` context pointed at a cluster that is not the local one. The blueprint is verified by `flow verify` and the descriptor by ankka's own decoding rules (`DeploymentSuite`); what tier 5 would add, a deployment through the gateway, is unproven.

**F16. On a laptop that already runs a Postgres, the compose file's has to move.** `REASONING_POSTGRES_PORT` moves it for `just up` and `just run` both.

**F17. A wait ended while the record at the far end of an edge was still a placeholder.** The sink applies an edge before the node it points to when their deltas arrive in that order, and creates a placeholder for the node. `/graph/wait` counted the edge as held, so a writer that recorded a source and then evidence could wait for the evidence and find it linked to a source with no name yet. The first run in CI found it; it had never shown on a laptop. An edge now counts only when the node it points to is held and is not a placeholder.

## Measurements

On a laptop (Apple silicon, Docker Desktop), 2026-10-06.

| What | How | Measured | Target (SC-007) |
|---|---|---|---|
| A record's `201` to the graph holding its node and every edge | `LatencySuite`: 30 records, one at a time, under the test kit with Kafka, the v0.3.0 sink and Neo4j in containers; polling every three seconds | median 3.0 s, nine in ten within 3.1 s, slowest 3.2 s | five seconds, nine in ten |
| The same | `just measure` against `just up` and `just run`: the seeded set's 555 records in one burst of about fifteen seconds; polling every three seconds | median 3.4 s, nine in ten within **5.1 s**, slowest 6.3 s | five seconds, nine in ten: **missed** |
| The same | as above, polling every second (F14) | median 2.4 s, nine in ten within 3.8 s, slowest 4.4 s | five seconds, nine in ten: met |
| `/answers/belief-change` on a question of 1,002 records (498 claims, each with its evidence and source, in the answer), asked 20 times | `just measure`, as above | median 14 ms, slowest 46 ms (26 ms and 255 ms on the first run, cold) | one second: met |
| The graph database emptied and rebuilt from the topic: 3,752 nodes and 11,350 edges | `just rebuild` | about six seconds; the same answer before and after, byte for byte | none set |

One at a time, the time to the graph is the consumer's poll and the same for every record. In a burst it is the poll and then the queue behind it. It is ankka's to shorten further.

## Requests

What this application would like from the platform. Each is written out, with what it would remove
from this repository, in [`notes/ankka-requests.md`](../../notes/ankka-requests.md). None blocks
this feature.

**To ankka**

1. A way to erase an event sourced entity's history, or to keep part of an event outside the
   journal. Today any text in an event is permanent (R1).
2. A clock in `CommandContext`. Already asked for by satisfactory (R4).
3. The caller's identity in `CommandContext`, set by the platform, so an entity need not trust the
   endpoint for it (R5).
4. More than two path parameters on a route. Already asked for by satisfactory (R15).
5. A view keyed by something other than its source entity's id, or multi-source views as specified
   in 031. Either removes the read record per belief revision (R8).

**To ankka-flow**

6. A way to ask whether a set of elements has been applied, in place of polling each one's version
   (R10).
7. A documented way to run a sink-only pipeline without Kubernetes, with its topic created
   compacted (R7).

**To ankka, found while building**

10. A setting of ankka's own for how often a consumer over an entity's state polls, with a default
    shorter than three seconds, or state changes pushed as events are. Today the service sets
    Pekko's `refresh-interval` itself (F14).
11. A step body of more than four values in `GherkinSuite` (F10).

**A release**

8. An ankka release that carries what has merged since `v0.10.0`: `ankka-auth-oidc` (F1), and a
   `GherkinSuite` that takes one file (F2). Both are on ankka's `main` today.
9. ankka-flow's container packages made public, so a released sidecar can be pulled without a
   credential (F8).
