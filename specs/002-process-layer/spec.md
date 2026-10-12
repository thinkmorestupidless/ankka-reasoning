# Feature Specification: The process layer

**Feature Branch**: `002-process-layer`

**Created**: 2026-10-10

**Status**: Draft

**Input**: User description: "The process layer, proven by an appraiser over a golden set of
documents. ankka now runs blueprints: workers and steps as data, runs as records, a tool told its
run, consumers that follow runs and blueprint versions. ankka's own example (the research digest)
covers what needs no reasoning graph; this feature is what only a reasoning graph gives a run.
Three things are built here: tools a blueprint names that write reasoning records as the holder a
worker speaks for; the process layer as a projection of ankka's blueprint versions and runs, every
record a run writes linked to its run; and a golden set, the worked example and the acceptance
test: the launch question with two dated tranches of documents, an appraiser blueprint run over
each, a second blueprint version for a second holder, and the three questions answered from
records a process wrote. Offline the model is scripted from the golden set's expected readings; a
separate, optional live suite runs a real model. Scoring is the next feature."

## Context

The belief layer holds reasoning that a writer sends: a person, a script, a test. Nothing in it
yet shows reasoning being *produced*. The launch example is seeded by hand, so every answer the
service gives is about records someone typed, and the question a reasoning graph is for — which
process wrote this belief, from what, under which instructions — has no answer.

ankka now runs **blueprints**: a description of workers (instructions, tools, a model, a budget)
and the steps between them, held as data, versioned, never edited; and **runs**, each a record of
one carrying out of one blueprint version, with every step's result kept. A tool a worker calls
is told which run it serves. A service's consumers can follow ankka's blueprint versions and runs
as they would any of its own records. ankka's own example, a weekly research digest, needs no
reasoning graph, and so lives there (`notes/use-cases.md`; request 13 in
`notes/ankka-requests.md` is answered).

What only a reasoning graph gives a run is this feature:

- **Tools that write reasoning records.** A worker in a run records evidence with its provenance,
  states a claim deriving from that evidence with a stance on a hypothesis, and revises a belief
  resting on claims with weights — each as the holder the worker speaks for, and each kept with
  the run and the blueprint version that wrote it.
- **The process layer.** A new layer of the vocabulary, above the belief layer and beside the
  market layer: ankka's blueprint versions and runs as nodes, and an **act** for every record a
  run wrote, linking the run to the record. A belief then traces to the run and the version that
  wrote it, as it already traces to its claims, evidence and sources. The belief layer names
  nothing of it, as it names nothing of markets.
- **A golden set.** The launch example made real: the question "Will Company X launch Product Y
  this year?", every name made up, with two dated tranches of documents checked into the
  repository, each document with its source, locator, published date, text and an **expected
  reading** — which hypothesis it bears on and how. A **appraiser** blueprint reads each
  document, records evidence and claims, has the claims critiqued, and revises the belief. Run
  over the May tranche it gives a holder's first belief; run over the June tranche, with that
  belief as input, it gives the revision. A second version of the blueprint, with different
  instructions, gives a second holder's belief from the same documents.

The golden set is the worked example every page shows and the acceptance test every suite runs.
Offline, the model is scripted from the expected readings, so every feature passes with no model
key and no network; the same blueprint run against a real model, in a separate and optional
suite, is asserted on the direction of each move and on the critique passing, not on its words.

The market layer resolves the market afterwards, so that the two versions can be scored against
the outcome. Scoring is the next feature; this one keeps everything it needs.

The rules this repository keeps (CLAUDE.md) hold throughout: nothing is edited; rules are checked
on the write side; a link has one owner; links point backwards; a layer adds edges only from its
own nodes; answers are records.

## Clarifications

### Session 2026-10-12

- Q: The critique passes a claim only if it derives from evidence "this run recorded"; but evidence an earlier run recorded is recorded once and keeps the earlier run's act, so a later run reading the same document would fail. Which gives? → A: The critique checks by the run's input: every claim derives from evidence recorded from a document of this run's tranche, whoever recorded it and whenever. The act stays the first recorder's.
- Q: A run's node "shows its run status and when it ended once it has", which is a held record changing; and an act must link to a run already held. What is a run as a record of this service? → A: Two records. The run (run id, blueprint, version, when started) is held once, when the service first learns of it from ankka's run record or from a tool's first call, whichever comes first; its run ending (run status, when ended), naming the run, is held once when the run ends. A run with no ending is running. Nothing is updated.
- Q: The three questions' answers come back "with the act and the run that wrote it", but the belief layer's answers cannot name the process layer. Which layer gives an answer its acts? → A: The process layer offers each belief-layer and market-layer answer again: the same answer, with each record's act, run, run ending and blueprint version attached where the record has one. A reader asks the process layer once. The lower layers' answers change in nothing.
- Q: The critique's checks are decidable from records; is the critique a check the service makes, or a model's judgment? → A: A model's judgment, through a judgment step of ankka's, with the two checks as its instructions: every claim derives from evidence recorded from a document of this tranche, every stance is on a hypothesis of the question. Offline the judgment is scripted to its verdict; in the live suite a real model judges.
- Q: Are the 24 proposed terms right, in particular *appraiser* (since *forecaster* is refused for holder), *act*, and *document* no longer refused for evidence? → A: All accepted as written. Refused: for run, execution and job; for act, attribution and authorship; for tranche, batch; for document, article and source text. *Forecaster* stays refused for holder.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A run writes reasoning records as a holder (Priority: P1)

A blueprint names the tools this service registers for it: record evidence, state a claim, revise
a belief. A worker in a run calls them, and each writes a belief-layer record exactly as a writer
would send it, as the holder that worker speaks for, under the same rules — nothing not held is
named, nothing is dated out of order, nothing is stated as a holder the blueprint does not speak
for — and each record is held together with an act that names the run, the step, the worker and
the blueprint version that wrote it. A record a run wrote is never held without its act. The
blueprint is the writer the service keeps with each record, and it speaks for the holders its
workers speak for, which are registered when the blueprint is.

**Why this priority**: Without it a run cannot touch the graph, and nothing else here can be
tested.

**Independent Test**: With the service alone, no graph database and a scripted model, register the
appraiser and run it over the May tranche. Read back five pieces of evidence, five claims and one
belief revision, each with an act naming the run and version 1. Send the run again under the same
run id and observe the same counts. Call the tools from a run whose worker speaks for a holder the
blueprint does not speak for, and observe a refusal naming the holder.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/process/tools.feature`: a worker in a run records evidence, and the evidence is held with its act
- added `features/process/tools.feature`: a worker in a run states a claim deriving from evidence it recorded
- added `features/process/tools.feature`: a worker in a run revises a belief resting on claims it stated
- added `features/process/tools.feature`: a record a run writes is never held without its act
- added `features/process/tools.feature`: a record a run writes breaks no rule a writer's would
- added `features/process/tools.feature`: a blueprint speaks for the holders its workers speak for
- added `features/process/tools.feature`: a record stated as a holder the blueprint does not speak for is refused
- added `features/process/tools.feature`: a run sent twice writes each record once
- added `features/process/tools.feature`: evidence an earlier run recorded is recorded once, and the act is the earlier run's
- added `features/process/tools.feature`: a tool called in no run writes nothing

---

### User Story 2 - Runs and blueprint versions are in the graph (Priority: P1)

Every blueprint version ankka holds and every run it starts is published as a node of the process
layer, and every act as a node with its edges: to the run, to the record it wrote, and to the
holder it was stated as. A reader starts at a belief revision and reaches the act that wrote it,
the run, the blueprint version, and from there every other record that run wrote. The process
layer adds its own kinds and edges from its own nodes and changes nothing in the belief layer or
the market layer. The graph is still a copy that can be emptied and rebuilt from the delta topic.

**Why this priority**: It is what the use-cases called the process layer, and the second of the
three things only a reasoning graph gives a run.

**Independent Test**: Run the appraiser over the May tranche, let the graph catch up, and walk in
the graph database from the belief revision to its act, its run and version 1, then from the run
to every act and every record it wrote. List every kind of node, edge and property and find each
in the vocabulary under the process layer. Empty the graph database, rebuild it from the delta
topic and compare the two graphs.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/process/runs.feature`: a blueprint version is in the graph when it is registered
- added `features/process/runs.feature`: a run is in the graph with the version it ran, its run status and when it started and ended
- added `features/process/runs.feature`: an act links a run to the record it wrote and the holder it was stated as
- added `features/process/runs.feature`: a belief revision is traced to the run and the blueprint version that wrote it
- added `features/process/runs.feature`: a reader lists every record a run wrote
- added `features/process/runs.feature`: a writer waits for the graph to hold a record and its act
- added `features/process/runs.feature`: an emptied graph database is rebuilt with the process layer
- changed `features/graph/vocabulary.feature`: the belief layer names nothing of the market layer or the process layer
- added `features/graph/vocabulary.feature`: every kind of edge in the process layer runs from a node of the process layer

---

### User Story 3 - The golden set: an appraiser answers the three questions (Priority: P1)

The golden set is two tranches of made-up documents about the launch question. The appraiser
blueprint, run over the May tranche, reads each document, records evidence and states a claim
with the stance the document bears, has the claims critiqued — every claim derives from evidence
recorded from a document of this tranche, and every stance is on a hypothesis of the question —
and revises the holder's
belief resting on those claims with weights. Run over the June tranche with the first belief
revision as input, it revises the belief again, following the first. A second version of the
blueprint, registered afterwards with different instructions, is run the same way for a second
holder. Then the three questions are answered from records a process wrote: the case for *it
launches this year* after the second run walks back to the June documents and nothing else; as of
a day between the tranches the belief is the first run's and the June tranche is learned since;
the explanation of the change names the approval document and the run that read it; the two
holders rest on the same claims with different weights; and every critique passed.

**Why this priority**: It is the proof. The three questions were the reason for the belief layer;
answering them about records a process wrote, rather than typed, is what this feature is for.

**Independent Test**: With a scripted model answering from the expected readings and no model key,
run the appraiser twice for the first holder and twice for the second, let the graph catch up,
and ask the three questions and the comparison; receive the answers the golden set says, made of held records
only, and nothing dated or recorded out of order.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/process/golden-set.feature`: the golden set is two tranches of documents, each with its expected reading
- added `features/process/golden-set.feature`: the appraiser run over the May tranche states the first belief, each claim with its document's stance, and the critique passes
- added `features/process/golden-set.feature`: the appraiser run over the June tranche revises the belief, following the first
- added `features/process/golden-set.feature`: a run whose critique fails revises no belief, and what it wrote stays held
- added `features/process/golden-set.feature`: a second blueprint version gives a second holder's belief from the same documents
- added `features/process/golden-set.feature`: the case for the hypothesis after the second run walks back to the June tranche only
- added `features/process/golden-set.feature`: as of a day between the tranches the belief is the first run's, and the June tranche is learned since
- added `features/process/golden-set.feature`: the explanation of the change names the approval document and the run that read it
- added `features/process/golden-set.feature`: the two holders rest on the same claims with different weights
- added `features/process/golden-set.feature`: the market resolved afterwards lists each holder's belief with the version that wrote it
- added `features/process/golden-set.feature`: the golden set runs with no model key and no network

---

### User Story 4 - The same appraiser against a real model (Priority: P2)

A developer with a model key runs the same blueprint over the same golden set against a real
model, in a suite that is not part of the build. The claims' words are the model's and are not
asserted; what is asserted is that each tranche moves the belief in the direction the expected
readings say, that every critique passed, and that every record the runs wrote is held with its
act and traced in the graph. A failure says which document's reading the model got wrong.

**Why this priority**: The offline suites prove the pipeline and the graph. This is the one check
that the blueprint, its instructions and its tools make sense to a model; it is optional because
it needs a key and a network and answers differently each time.

**Independent Test**: With a model key set, run the live suite and observe each holder's first
belief below one half and the revised belief above it, every critique passed, and every record
with its act.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/process/live.feature`: a real model moves the belief in the direction the expected readings say
- added `features/process/live.feature`: a real model's claims are critiqued the same way
- added `features/process/live.feature`: the live suite is skipped, not failed, when there is no model key

---

### Edge Cases

- A tool is called outside any run — by an agent of the service's own, say: it writes nothing and
  says why (*a tool called in no run writes nothing*).
- A run's worker speaks for a holder the blueprint does not: the record is refused, naming the
  holder, and the run fails at that step (*a record stated as a holder the blueprint does not
  speak for is refused*).
- A document in a tranche was already recorded as evidence by an earlier run: it is the one piece
  of evidence already held, its act is the earlier run's, and the new claim deriving from it has
  this run's act (*evidence an earlier run recorded is recorded once, and the act is the earlier
  run's*).
- A run is sent twice under one run id: ankka runs it once, and every record it wrote is held
  once (*a run sent twice writes each record once*).
- A run fails part way: the records it wrote stay held with their acts, the run's ending is in
  the graph with its run status, and no belief revision is stated for it. Nothing in this feature
  cancels a run; a cancelled ending, were ankka to record one, is held as a failed one is (*a run whose
  critique fails revises no belief, and what it wrote stays held*; *a run is in the graph with
  the version it ran, its run status and when it started and ended*).
- The critique fails: no belief is revised, the claims stay held, and the run is failed naming the
  claim (*a run whose critique fails revises no belief, and what it wrote stays held*).
- A belief revision written by a run is dated before the claims it rests on, or names a claim of
  another holder: refused under the belief layer's rules, as a writer's would be, and the run
  fails at that step (*a record a run writes breaks no rule a writer's would*).
- A record and its act reach the graph apart: the wait covers both (*a writer waits for the graph
  to hold a record and its act*).
- A blueprint version is registered while a run of the earlier version is going: the run keeps
  its version, and every act names it (*a run is in the graph with the version it ran, its run status and
  when it started and ended*).
- The graph database cannot be reached: records and acts are held, and the graph catches up, as
  before.
- The second holder is given the June tranche before the May one: refused, as a belief revision
  cannot be dated before the one it follows; the tranches are run in order.

## Requirements *(mandatory)*

### Functional Requirements

**Tools a blueprint names**

- **FR-001**: The service MUST register, for blueprints, a tool to record evidence, a tool to state
  a claim and a tool to revise a belief. Each writes the belief-layer record a writer would send,
  with every field that record carries (evidence: source, locator, excerpt, author, the time
  published, the time observed; a claim: a statement, the evidence it derives from, a stance on
  each of one or more hypotheses; a belief revision: a hypothesis, a probability, the claims it
  rests on with optional weights, the belief revision it follows), checked by the same rules
  (FR-001 to FR-014 of the belief layer), and refuses the same way. What a tool takes from the
  worker and what it reads from the run is the plan's to settle; a claim a run states revises no
  claim in this feature.
- **FR-002**: A tool called in a run MUST write as the holder the run's input names, for which
  every worker of that run speaks, and MUST write nothing when called outside any run, saying so.
- **FR-003**: A blueprint MUST be a writer: every record a run of it writes names the blueprint as
  the writer that sent it. A blueprint's registration MUST name the holders it may be run for,
  and registering the blueprint MUST register each such holder, of kind agent, with the blueprint
  as a writer that speaks for it; a holder already registered is left as it is, and a run whose
  input names a holder the blueprint does not speak for is refused when it writes.
- **FR-004**: Every record a run writes MUST be held together with an act: a record of the process
  layer naming the run, the step, the worker, the blueprint version, the record written and the
  holder it was stated as. A record a run wrote is never held without its act, and an act names
  one record. A record already held when a run writes it — evidence recorded by an earlier run,
  say — keeps its own act, and the run gains none for it.

**The process layer**

- **FR-005**: The vocabulary MUST gain a process layer: a blueprint version node (name, version,
  digest, when registered), a run node (run id, blueprint, version, who started it, when started), a run ending
  node (run status, when ended) and an act node (step, worker), with edges only from its own
  nodes: run to blueprint version, run ending to run, act to run, act to the record it wrote, act
  to the holder it was stated as. The belief layer and the market layer MUST name nothing of it
  and change in nothing.
- **FR-006**: The service MUST hold and publish every blueprint version ankka holds and every run
  it starts, from ankka's own records of them: a version when it is registered; a run once, when
  the service first learns of it, from ankka's run record or from a tool's first call in it,
  whichever comes first; and the run's ending once, when the run ends, with its run status and
  when it ended. No record of the process layer is updated: a run with no ending is running. The
  service copies what ankka holds and writes nothing about a run that ankka does not.
- **FR-007**: A reader MUST be able to trace a belief revision, a claim or evidence to the act that
  wrote it, the run, its run ending and the blueprint version, and to list every record a run
  wrote, from the graph database, with the vocabulary alone.
- **FR-007a**: The process layer MUST offer every answer of the belief layer and the market layer
  (FR-021 to FR-026 and FR-032 to FR-033 of the belief layer) again, as the same answer with each
  record's act, run, run ending and blueprint version attached where the record has one, computed
  from the graph database as the others are. A reader asks once. The belief layer's and the market
  layer's answers change in nothing.
- **FR-008**: The wait a writer asks for (FR-020 of the belief layer) MUST cover a record's act as
  well as the record; and a graph database emptied and rebuilt from the delta topic MUST hold the
  process layer as it was.

**The golden set and the appraiser**

- **FR-009**: The repository MUST hold a golden set: the launch question with its two hypotheses,
  and two tranches of documents. Tranche A, dated in May, holds a filing, a regulator's notice, a
  trade-press piece, an analyst note and a supplier's earnings call; tranche B, dated in June,
  holds an approval granted, a launch date announced and a contradicting rumour. Each document has
  a source, a locator, the time it was published, its text, and an expected reading: the
  hypothesis it bears on, the stance, and a short statement of what it says. Every name, number
  and date is made up, as the launch example's are. The golden set is one set of files that every
  suite, every page and the live run read.
- **FR-010**: The service MUST carry the appraiser blueprint: it reads each document of a tranche
  and records it as evidence and states a claim with a stance; critiques the claims — every claim
  derives from evidence recorded from a document of this run's tranche, whoever recorded it and
  whenever, and every stance is on a hypothesis of the question — as a model's judgment through
  a judgment step of ankka's, given the claims, their evidence and the tranche's documents, with those
  two checks as its instructions — and revises the belief only when the critique passes, resting
  on the claims with weights and following the belief revision the input names, if any. Its input is a tranche, a holder, a question and optionally the belief
  revision to follow. A run whose critique fails is failed, naming the claim, and revises no
  belief. Evidence an earlier run recorded from the same document passes the critique and keeps
  the earlier run's act.
- **FR-011**: A second version of the appraiser, with different instructions, MUST be registrable
  after the first holder's runs, and runs of it for a second holder MUST give that holder's belief
  from the same documents, resting on claims of its own with weights of its own.
- **FR-012**: Run over the golden set with a model scripted from the expected readings, the
  appraiser MUST give, for each holder, a first belief revision resting on five claims and a
  second following it resting on claims from the June documents, and the four answers: the case
  for *it launches this year* after the second run derives only from June documents; as of a day
  between the tranches the belief is the first revision and the June evidence, claims and
  revisions are learned since; the explanation of the change names the claim derived from the
  approval document, its evidence, and the run that wrote it; and the two holders' beliefs rest on
  the same claims with weights that differ.
- **FR-013**: Every living feature of this repository MUST pass with no model key and no network,
  the golden set's included; the model is scripted from the expected readings, and the critique's
  verdict is scripted too.
- **FR-014**: A separate suite, run only when a model key is set (today the text model's and the
  judgment's, both) and never by the build, MUST run
  the same blueprint over the same golden set against a real model and assert only the direction
  of each holder's move, the critique passing, and every record held with its act; it is skipped,
  not failed, without a key.
- **FR-015**: The market layer MUST resolve the golden set's market afterwards on the June
  evidence, so that the list of what each holder believed at resolution (FR-033 of the belief
  layer) shows each belief revision with the blueprint version that wrote it. Scoring the two
  versions is not in this feature.

### Key Entities *(each a term in the project glossary)*

**Process layer**

- **Blueprint version**: one registration of a blueprint, as ankka holds it: name, version number,
  digest, when registered. Written by the service from what ankka holds.
- **Run**: one carrying out of one blueprint version under a run id, as ankka holds it: when it
  started. Written by the service from what ankka holds, once.
- **Run ending**: how a run ended: its run status and when it ended, naming the run. Written by
  the service from what ankka holds, once; a run with no ending is running.
- **Act**: one record a run wrote: the run, the step, the worker, the blueprint version, the record
  and the holder it was stated as. Written with the record and never without it.
- **Appraiser**: the blueprint this service carries: a reader per document, a critique, and a
  revision of the holder's belief.

**The golden set**

- **Golden set**: the files the example and the suites share: the question, two tranches of
  documents and their expected readings.
- **Tranche**: the documents a run of the appraiser reads, dated in one month.
- **Document**: one made-up text a tranche holds, with a source, a locator, when it was published
  and its expected reading. A document is what evidence is recorded from; it is not evidence.
- **Expected reading**: what a document bears on and how: a hypothesis, a stance and a statement,
  from which the scripted model answers and against which a real model's direction is judged.

**Links, each stated by the record on the left and running to a record held before it**

| Link | From | To | Carries | Layer |
|---|---|---|---|---|
| of | run | blueprint version | | process |
| ended | run ending | run | run status, when ended | process |
| in | act | run | | process |
| wrote | act | evidence, claim or belief revision, exactly one | | process |
| as | act | holder | | process |

The names of labels, edge types and properties in the graph database are the plan's to settle,
within the delta contract's rules.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After the appraiser's two runs for the first holder, the case for *it launches this
  year* names claims deriving only from the three June documents, each traced to its act and the
  second run, and nothing that is not a held record.
- **SC-002**: As of any day between the last May document and the first June document, the first
  holder's belief is the first run's revision, and what was learned after that day is exactly the
  June evidence, claims and the second revision.
- **SC-003**: The explanation of the change between the two revisions names the claim derived
  from the approval document, that evidence and its source, and the act and run that wrote the
  claim; it contains nothing from the May tranche as newly rested on.
- **SC-004**: The comparison of the two holders shows the claims both rest on and a different
  weight on at least one; no claim is only one holder's.
- **SC-005**: Every record the four runs wrote has exactly one act, every act's edges reach held
  records, and every path from a belief revision to a blueprint version in the graph database has
  no gap; the count of records written by the runs equals the count of acts plus the records held
  before.
- **SC-006**: Every living feature, the golden set's included, passes with no model key and no
  network, and the whole golden set — four runs, the graph caught up, the four answers — completes
  within two minutes on a developer's machine.
- **SC-007**: Run against a real model with a key set, both holders' first beliefs are below one
  half and their second above it, every critique passes, and every record is held with its act, in
  nine runs of ten.
- **SC-008**: Every scenario of the belief layer and the market layer passes with no process record
  held anywhere, and the belief layer and the market layer of the vocabulary contain no kind, edge
  or property of the process layer.
- **SC-009**: A record a run writes is in the graph with its act within five seconds of being
  recorded, nine times in ten, on a developer's machine, as a writer's record is (SC-007 of the
  belief layer).

## Assumptions

**What this is built on**

- An ankka release that carries blueprints (spec 036 on ankka's `main`, after `0.11.0`), and
  ankka's graph sink (`0.11.0`) with ankka-contrib's Neo4j store and sink image in place of
  ankka-flow. Moving the service off ankka-flow is a prerequisite task, done before this feature
  and not part of it.
- ankka's blueprints as they are: workers and steps as data, runs as records with each step's
  result, a tool told its run, a judgment or a critic as a step's verdict, and consumers over
  ankka's blueprint and run records. What a blueprint cannot do here becomes a request to ankka,
  named in the plan's Complexity Tracking, never a way round in this service.
- The scripted model and scripted judgment ankka's test kit provides, so that a run is driven
  offline from the expected readings.

**Choices made where the description left room**

- The blueprint is the writer. A record a run writes names the blueprint as the writer that sent
  it, and the run and version are the act's to name, not the writer's identity. A holder a worker
  speaks for is registered by the blueprint's registration, so a run never registers anything.
- An act is a record of its own, not a property of the record it names, so that the belief layer
  changes in nothing and a record held before any run exists has no act and needs none.
- The critique comes before the revision, so a run that fails it leaves claims held — nothing is
  removed — and states no belief. A failed run is a record like any other.
- A document is what evidence is recorded from, and is not evidence; the glossary stops refusing
  the word for evidence and defines it for the golden set.
- The second holder's belief comes from a second *version* of the appraiser, not a second
  blueprint, so that the two versions are what scoring will compare. The second version is
  registered after the first holder's runs; a version is current from when it is registered, and
  a run keeps the version it started with.
- The golden set's documents are short — a few hundred words each — and made up. The expected
  readings are the golden set's own files, not the service's records.
- The live suite asserts direction only. A real model's words differ from run to run; its stance
  on a document that is plainly for or against should not.
- Run ids are the suite's to choose and name the holder and the tranche, so a run sent twice is
  one run.

**Not in this feature**

- Scoring: comparing versions or holders by the outcome a market resolves to. This feature keeps
  the version on every record so that the next can.
- Starting a run from a record — a new claim on an open question, say. Runs here are started by a
  request. Cancelling a run.
- The decision layer, challenges and assessing again (use-case 2, the cultivated food).
- Fetching documents from anywhere; the golden set is checked in.
- A tool that reads the graph for a worker, beyond the records a run's input names.
- Any model call in the build; any user interface; answers in prose.
- Withdrawing an act, or the text of a document.
