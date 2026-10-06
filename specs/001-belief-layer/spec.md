# Feature Specification: The belief layer

**Feature Branch**: `001-belief-layer`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Build on the graph delta work in ankka and ankka-flow to implement the
full reasoning graph. Prediction market as the first domain; layer the vocabulary, with the belief
layer first and the process layer later; immutable revision nodes for the first version. Spec it in
a new repository, ankka-reasoning."

## Context

ankka and ankka-flow already carry a graph from a service to a graph database. A service's graph
consumer publishes each change as nodes and edges under the contract `ankka.graph-delta.v1`, a
compacted topic keeps the latest state of each, and ankka-flow's merge sink applies them to Neo4j
so that redelivery, reordering and a rebuild all leave the same graph. That is a way to copy what a
service holds into a graph. It says nothing about what a reasoning graph holds, what may link to
what, how a belief's history is kept, or how anyone reads an answer out of it.

A reasoning graph records how beliefs form and change. Evidence is kept with where it came from.
Claims are derived from evidence. Hypotheses compete to answer a question. A belief is one holder's
probability for one hypothesis. Three properties make it worth having: any belief can be traced
back through claims and evidence to a source; what was believed at any past time can be read back;
and holders who disagree are kept side by side, with what each rests on.

Three decisions shape this first feature:

- **A prediction market is the first domain.** It forces the hard parts of the model: several
  holders with different beliefs in one hypothesis, beliefs that move over time, and an outcome that
  eventually says who was right.
- **The vocabulary is layered.** The belief layer (questions, hypotheses, holders, sources,
  evidence, claims, beliefs) stands alone and names nothing of markets. The market layer sits above
  it. A process layer (goals, tasks, workers and the recorded calls that make reasoning replayable)
  is a later feature and must be addable the same way.
- **History is kept by never changing a record.** A holder who thinks again states a new record
  linked to the old one. Nothing is edited and nothing is removed. The one exception is text that
  has to come out, which can be withdrawn from a record that otherwise stays as it was.

An earlier reasoning graph on another platform left three lessons this feature takes as rules:

1. A question whose answer must be right cannot be asked of the graph database, which is always a
   moment behind. Every rule here is checked against the service's own records when a record is
   written. The graph database is for reading.
2. A link needs exactly one owner. Here every link is stated by one record, the newer of the two it
   joins, and published with it.
3. When records never change and may link only to records already held, a cycle cannot be made.
   There is no cycle check because there is nothing to check.

This feature holds the reasoning and explains it, whoever wrote it. Records arrive from a writer:
a person, a script, a test. The agents that gather evidence and forecast are the next feature, and
they will be writers like any other.

## Clarifications

### Session 2026-10-06

- Q: Is a belief one holder's probability for one hypothesis, stated independently, or one holder's whole distribution over a question's hypotheses? → A: Independent. One belief per holder and hypothesis; probabilities across a question's hypotheses need not sum to one.
- Q: Which time does an as-of answer read: a record's date, the time it was recorded, or either on request? → A: Either. As of a date by default; a reader may also ask as recorded by a time, which gives only what the service held then and never changes afterwards.
- Q: Who may write as a holder in this first feature? → A: Only the writers bound to it. A holder is registered with the writers that speak for it, anything stated as that holder by another writer is refused, and every record keeps which writer sent it.
- Q: How should this feature treat content that later has to be taken out of a record? → A: Withdraw the content. The text of evidence or of a claim can be withdrawn for good while the record, its links, its dates and the fact of withdrawal stay. It is the one exception to nothing being edited.
- Planning, same day: the wait in FR-020 covers a record's edges as well as its node, since the two reach the graph separately; and FR-034 gains a steward, a writer the deployment names who may withdraw the text of any record, so that text can come out when its writer is gone. `steward` joins the glossary as a ninth term still proposed.
- Q: How are the glossary's proposed terms settled? → A: All settled as written except eight, which stay proposed until they have been used for a while: holder, held, dated, revises, withdrawn, speaks for, rests on, as recorded by.
- After implementation, same day: the terms that had stayed proposed (holder, held, dated, revises, withdrawn, speaks for, rests on, as recorded by, steward) are accepted as written, and no term in the glossary is proposed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record the reasoning about a question (Priority: P1)

A writer opens a question with the hypotheses that compete to answer it, registers the holders and
sources involved, records evidence with its provenance, states claims derived from that evidence
with a stance on a hypothesis, and states and revises beliefs that rest on those claims. Every
record is kept as it was stated, with the writer who sent it. A record that names something not
held, that is dated before something it rests on, that is stated as a holder the writer does not
speak for, or that breaks another rule is refused with the rule named, and nothing changes.

**Why this priority**: It is the model. Nothing can be published, traced or explained until the
records and their rules exist.

**Independent Test**: With the service alone and no graph database, record the launch example and
read each record back: the question and its hypotheses, both pieces of evidence, both claims with
the second revising the first, and the belief with its two revisions in order. Send every record a
second time and observe the same counts. Send each rule's breaking case and observe a refusal that
names the rule.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/record/questions.feature`: a question is opened with the hypotheses that compete to answer it
- added `features/record/questions.feature`: a question needs at least two hypotheses
- added `features/record/questions.feature`: a hypothesis is added to a question
- added `features/record/questions.feature`: a question and its hypotheses are never changed
- added `features/record/questions.feature`: a question opened twice is one question
- added `features/record/questions.feature`: a question needs no market
- added `features/record/holders-and-sources.feature`: a holder is registered with its kind
- added `features/record/holders-and-sources.feature`: a holder of a kind the vocabulary does not name is refused
- added `features/record/holders-and-sources.feature`: a source is registered with its name
- added `features/record/holders-and-sources.feature`: a holder or a source registered twice is one
- added `features/record/holders-and-sources.feature`: the writer who registers a holder speaks for it
- added `features/record/holders-and-sources.feature`: a writer who speaks for a holder adds another
- added `features/record/holders-and-sources.feature`: a writer who does not speak for a holder adds nobody
- added `features/record/holders-and-sources.feature`: a record is held with the writer who sent it
- added `features/record/evidence.feature`: evidence is recorded with its provenance
- added `features/record/evidence.feature`: evidence from a source that is not held is refused
- added `features/record/evidence.feature`: the same evidence recorded twice is one piece of evidence
- added `features/record/evidence.feature`: evidence is never changed
- added `features/record/evidence.feature`: evidence observed on a past day is dated that day
- added `features/record/evidence.feature`: evidence dated later than now is refused
- added `features/record/evidence.feature`: evidence observed before it was published is refused
- added `features/record/evidence.feature`: an excerpt longer than the limit is refused
- added `features/record/claims.feature`: a claim is stated from evidence and takes a stance on a hypothesis
- added `features/record/claims.feature`: a claim may take a stance on several hypotheses
- added `features/record/claims.feature`: a claim derives from at least one piece of evidence
- added `features/record/claims.feature`: a claim takes a stance on at least one hypothesis
- added `features/record/claims.feature`: a claim that names something not held is refused
- added `features/record/claims.feature`: a claim cannot be dated before the evidence it derives from
- added `features/record/claims.feature`: a claim revises an earlier claim, and the earlier claim is kept
- added `features/record/claims.feature`: a claim cannot revise a claim dated later than itself
- added `features/record/claims.feature`: two holders revise one claim differently, and both revisions are kept
- added `features/record/claims.feature`: a claim is never changed
- added `features/record/claims.feature`: a claim from a writer who does not speak for its holder is refused
- added `features/record/claims.feature`: a claim sent twice is one claim
- added `features/record/beliefs.feature`: a holder states a belief in a hypothesis
- added `features/record/beliefs.feature`: a belief is revised, and every revision is kept
- added `features/record/beliefs.feature`: a probability outside nought to one is refused
- added `features/record/beliefs.feature`: a belief may be stated with no claims
- added `features/record/beliefs.feature`: a belief revision may weigh each claim it rests on
- added `features/record/beliefs.feature`: a belief revision rests only on claims about its own question
- added `features/record/beliefs.feature`: a belief revision cannot rest on a claim dated later than itself
- added `features/record/beliefs.feature`: a belief revision cannot be dated before the one it follows
- added `features/record/beliefs.feature`: a belief revision that names something not held is refused
- added `features/record/beliefs.feature`: a belief's revisions form one line
- added `features/record/beliefs.feature`: a belief revision from a writer who does not speak for its holder is refused
- added `features/record/beliefs.feature`: a belief revision sent twice is one belief revision
- added `features/record/beliefs.feature`: two holders hold different beliefs in one hypothesis

---

### User Story 2 - The reasoning is a graph anyone can follow (Priority: P1)

Every record is published as a node and every link as an edge, to a delta topic that fills a graph
database. A reader who knows the vocabulary can start at a belief revision and reach the claims it
rests on, the evidence behind them and the sources. The vocabulary is written down in one place, in
layers, and nothing reaches the graph that it does not name. The graph is a copy: it can be emptied
and rebuilt from the topic alone, and recording never waits for it, though a writer may ask to.

**Why this priority**: The graph is what the foundational work was for, and what every later
feature reads. A vocabulary that is not pinned now is one every later layer argues with.

**Independent Test**: Record the launch example with a market, let the graph catch up, and walk in
the graph database from the current belief revision to the regulator source. List every kind of
node, edge and property in the graph and find each in the vocabulary under a layer. Empty the graph
database, rebuild it from the delta topic and compare the two graphs.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/graph/publication.feature`: every record is in the graph with the links it stated
- added `features/graph/publication.feature`: a belief revision is traced to its sources in the graph
- added `features/graph/publication.feature`: every edge runs from a record to one held before it
- added `features/graph/publication.feature`: a record published twice leaves the graph unchanged
- added `features/graph/publication.feature`: an emptied graph database is rebuilt from the delta topic
- added `features/graph/publication.feature`: a writer waits for the graph to hold what it wrote
- added `features/graph/publication.feature`: a wait that passes its limit says so
- added `features/graph/publication.feature`: a record is held when the graph database cannot be reached
- added `features/graph/vocabulary.feature`: the vocabulary names every kind of node and edge with its layer and the one kind of record that publishes it
- added `features/graph/vocabulary.feature`: nothing is published that the vocabulary does not name
- added `features/graph/vocabulary.feature`: the belief layer names nothing of the market layer
- added `features/graph/vocabulary.feature`: a layer adds to the layer beneath it and changes nothing in it

---

### User Story 3 - Ask why a belief changed (Priority: P1)

A reader asks why a holder's belief moved between two of its revisions and is given the records
that account for it: the claims the later revision newly rests on, the claims it no longer rests
on, which of those were revised and by what, the evidence each derives from and its source, and the
evidence observed between the two. The reader can also ask for the case for or against a
hypothesis. The answers hold records only; no sentence is written for the occasion.

**Why this priority**: It is the question the graph exists to answer, and the proof the model is
right: in the launch example, why the belief went from 0.38 to 0.61.

**Independent Test**: With the launch example in the graph, ask why the belief changed between its
two revisions and receive the regulator's evidence, the claim derived from it and the earlier claim
it revised, each with its source, and nothing that is not a held record.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/explain/belief-change.feature`: a belief change is explained by the claims that came and went
- added `features/explain/belief-change.feature`: an explanation gives the evidence observed between the two belief revisions
- added `features/explain/belief-change.feature`: an explanation holds only records that are held
- added `features/explain/belief-change.feature`: a belief revision that changed the probability and not the claims says so
- added `features/explain/belief-change.feature`: a change of weight is given with both weights
- added `features/explain/belief-change.feature`: any two revisions of one belief can be explained
- added `features/explain/belief-change.feature`: revisions of two beliefs are not explained as one change
- added `features/explain/belief-change.feature`: a belief resting on a revised claim is shown as resting on one
- added `features/explain/belief-change.feature`: a reader lists the beliefs about a question that rest on a revised claim
- added `features/explain/case.feature`: the case for a hypothesis is the claims that support it
- added `features/explain/case.feature`: the case against a hypothesis is the claims that contradict it
- added `features/explain/case.feature`: a revised claim is shown apart from the case
- added `features/explain/case.feature`: a case shows which holders rest on each claim
- added `features/explain/case.feature`: a hypothesis with no claims has an empty case

---

### User Story 4 - Ask what was believed at a past time (Priority: P2)

A reader asks what a holder believed as of a past time and is given the belief revision that was
current then, the claims it rested on and the evidence that had been observed, with nothing dated
later. The reader can ask what was learned about a question after a time. A record entered late
with an earlier date is in the answer for its date, and the answer says when it was recorded. A
reader who needs an answer that can never change asks as recorded by a time instead, and is given
only what the service held then.

**Why this priority**: History is one of the three properties, and immutable revisions were chosen
to make it cheap. It comes after the first three stories because it reads what they write.

**Independent Test**: With the launch example in the graph, ask what the holder believed as of
6 May and receive 0.38 resting on the first claim, with the regulator's evidence and the second
claim absent.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/explain/as-of.feature`: a belief as of a past time is the belief revision that was current then
- added `features/explain/as-of.feature`: nothing dated later than the time asked about is in the answer
- added `features/explain/as-of.feature`: a claim revised later is not shown as a revised claim as of an earlier time
- added `features/explain/as-of.feature`: a holder with no belief revision yet had no belief
- added `features/explain/as-of.feature`: a reader asks what was learned about a question after a time
- added `features/explain/as-of.feature`: a record dated before it was recorded says when it was recorded
- added `features/explain/as-of.feature`: an answer as recorded by a past time holds only what was recorded by then
- added `features/explain/as-of.feature`: an answer as recorded by a past time never changes

---

### User Story 5 - See where two holders disagree, a market among them (Priority: P2)

A reader compares two holders' beliefs in one hypothesis and is given the difference, the claims
both rest on, the claims only one does, and the weights where they differ. A market is a holder
too: a writer opens a market about a question, records price observations, and each becomes the
market's belief revision resting on no claims. Comparing a holder with the market is then the same
comparison, and it shows the market stating no reasons.

**Why this priority**: Keeping a market's probability beside a reasoned belief, and showing why
they differ, is what a prediction market gains from the graph. It is the first proof that a layer
can sit on the belief layer without changing it.

**Independent Test**: With the launch example and a market whose price observation for YES is
0.48, compare the market with the holder and receive 0.48, 0.61, the difference 0.13, the holder's
claims and the market shown as stating no reasons.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/explain/disagreement.feature`: two beliefs in one hypothesis are compared
- added `features/explain/disagreement.feature`: a claim both rest on with different weights is given with both weights
- added `features/explain/disagreement.feature`: a holder that rests on no claims is shown as stating no reasons
- added `features/explain/disagreement.feature`: beliefs in different hypotheses are not compared
- added `features/market/markets.feature`: a market is opened about a question
- added `features/market/markets.feature`: an outcome is for a hypothesis of the market's question
- added `features/market/markets.feature`: a question may have several markets
- added `features/market/markets.feature`: a price observation is the market's belief revision
- added `features/market/markets.feature`: a price observation equal to the current one adds no belief revision
- added `features/market/markets.feature`: a price observation from a writer who does not speak for the market is refused
- added `features/market/markets.feature`: a price observation for an outcome the market does not offer is refused
- added `features/market/markets.feature`: a price observation dated after the closing time is refused
- added `features/market/markets.feature`: a market is compared with a holder as any two holders are

---

### User Story 6 - Resolve a market on evidence (Priority: P3)

A writer resolves a market to one of its outcomes, or as void, naming the evidence and the
authority. The resolution is a record like any other: kept, traceable to its sources, and revised
only by a later resolution. A reader can ask why a market was resolved, and can list what each
holder believed when it was.

**Why this priority**: A resolved market is what later lets reasoning be scored. Scoring is not in
this feature; keeping the outcome beside the beliefs that preceded it is.

**Independent Test**: Resolve the launch example's market to YES on recorded evidence, ask why it
was resolved and receive the outcome, authority, evidence and source; list what each holder
believed at resolution and receive each holder's last earlier belief revision.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/market/resolution.feature`: a market is resolved to one of its outcomes, on evidence
- added `features/market/resolution.feature`: a resolution needs evidence
- added `features/market/resolution.feature`: a resolution from a writer who does not speak for the market is refused
- added `features/market/resolution.feature`: a market may be resolved as void
- added `features/market/resolution.feature`: a resolution to an outcome the market does not offer is refused
- added `features/market/resolution.feature`: a reader asks why a market was resolved
- added `features/market/resolution.feature`: a resolution is revised by a later resolution, and both are kept
- added `features/market/resolution.feature`: a market resolved twice the same way is resolved once
- added `features/market/resolution.feature`: a reader lists what each holder believed when a market was resolved

---

### User Story 7 - Take text out of a record without breaking the reasoning (Priority: P2)

Sooner or later a record holds text that has to come out: a passage someone else owns, a person's
details, a mistake. The writer who recorded a piece of evidence withdraws its text, or a writer who
speaks for a claim's holder withdraws its statement, with a note saying why. The text is gone from the
service and from the graph for good. The record is not: its links, its dates, who withdrew it, when
and the note all stay, so every trace and every explanation that passed through it still holds and shows
the record as withdrawn.

**Why this priority**: It is the one exception to the rule the whole model rests on, so it is
settled with the model and not after real records exist. It comes after the first three stories
because it changes what they hold and show.

**Independent Test**: Withdraw the text of the regulator's evidence in the launch example. Read the
evidence back and find no excerpt, author or locator, and the note, writer and time of the
withdrawal. Ask why the belief changed and receive the same explanation with that evidence shown
as withdrawn. Empty and rebuild the graph database and find the text in neither.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/record/withdrawal.feature`: the text of evidence is withdrawn and the record stays
- added `features/record/withdrawal.feature`: the statement of a claim is withdrawn and the record stays
- added `features/record/withdrawal.feature`: withdrawn text is gone from the graph
- added `features/record/withdrawal.feature`: a graph database rebuilt after a withdrawal does not hold the withdrawn text
- added `features/record/withdrawal.feature`: an explanation shows a withdrawn record as withdrawn
- added `features/record/withdrawal.feature`: an earlier time does not bring withdrawn text back
- added `features/record/withdrawal.feature`: withdrawn evidence recorded again stays withdrawn
- added `features/record/withdrawal.feature`: a withdrawal needs a note
- added `features/record/withdrawal.feature`: a writer who neither sent a record nor speaks for its holder does not withdraw it
- added `features/record/withdrawal.feature`: a steward withdraws the text of a record it did not send

---

### Edge Cases

- A record names something not held: refused, naming it (rows of *a claim that names something not
  held is refused*; *a belief revision that names something not held is refused*).
- A record is dated before something it rests on, or later than now: refused (*a claim cannot be
  dated before the evidence it derives from*; *a belief revision cannot rest on a claim dated later
  than itself*; *evidence dated later than now is refused*).
- Two writers revise one belief at once: one is held, the other is refused and names the current
  belief revision (*a belief's revisions form one line*).
- A writer states something as a holder it does not speak for: refused, naming the holder (*a claim
  from a writer who does not speak for its holder is refused* and its siblings).
- Text is withdrawn from a record others rest on: the record and every link stay, and it is shown
  as withdrawn (*the text of evidence is withdrawn and the record stays*; *an explanation shows a
  withdrawn record as withdrawn*).
- A reader asks about a time before a withdrawal: the text does not come back (*an earlier time
  does not bring withdrawn text back*).
- The same record is sent twice: one record (*a claim sent twice is one claim* and its siblings).
- A history is entered late: it is dated when it happened and says when it was recorded (*a record
  dated before it was recorded says when it was recorded*). A reader who must not see it asks as
  recorded by an earlier time (*an answer as recorded by a past time holds only what was recorded by
  then*).
- The graph database cannot be reached: records are still held, and the graph catches up (*a record
  is held when the graph database cannot be reached*; *a wait that passes its limit says so*).
- A belief rests on a claim someone has since revised: it is shown as such, and is not changed (*a
  belief resting on a revised claim is shown as resting on one*).
- A market's price has not moved: no belief revision is added (*a price observation equal to the
  current one adds no belief revision*).
- A market cannot be decided: it is resolved as void (*a market may be resolved as void*).
- A price observation arrives after a later one was recorded: refused, as any belief revision dated
  before the one it follows is (*a belief revision cannot be dated before the one it follows*).
  Observations are sent in the order they were observed.
- A holder's probabilities across a question's hypotheses do not sum to one: accepted. Each belief
  is one hypothesis's (FR-006).

## Requirements *(mandatory)*

### Functional Requirements

**Recording, in the belief layer**

- **FR-001**: A writer MUST be able to open a question with a statement and at least two
  hypotheses, and add hypotheses later; a question's statement and a hypothesis's statement MUST
  never change, and no hypothesis is removed.
- **FR-002**: A writer MUST be able to register holders, each of a kind the vocabulary names
  (agent, person, model in the belief layer), and sources, each with a name. The writer who
  registers a holder speaks for it, and a writer who speaks for a holder may add another; none is
  removed. A claim, a belief revision, a price observation or a resolution stated as a holder by a
  writer who does not speak for it MUST be refused. Questions, hypotheses, sources and evidence
  belong to no holder, and any writer the service admits may send them.
- **FR-003**: A writer MUST be able to record evidence from a registered source with a locator, an
  excerpt up to a stated limit, and optionally an author and the time it was published. Evidence is
  dated the time it was observed, which the writer may give and which otherwise is the time it was
  recorded. Evidence belongs to no question.
- **FR-004**: Evidence recorded again from the same source with the same locator and the same
  excerpt MUST be the one piece of evidence already held, and the writer is given it. This holds
  after its text is withdrawn: the writer is given the withdrawn evidence, and the text is not held
  again.
- **FR-005**: A holder MUST be able to state a claim: a statement, derived from at least one piece
  of evidence, taking a stance (supports or contradicts) on at least one hypothesis, and optionally
  revising one earlier claim. The revised claim is kept as stated, and one claim may be revised by
  several later claims.
- **FR-006**: A holder MUST be able to state a belief in a hypothesis and revise it. Each belief
  revision has a probability from nought to one, the claims it rests on (none is allowed), an
  optional weight from nought to one for each, and its date. There is one belief for each holder
  and hypothesis. Beliefs are independent: a holder's probabilities across a question's hypotheses
  are not required to sum to one, and a holder may state a belief in only some of them.
- **FR-007**: A belief's revisions MUST form one line. A revision names the revision it follows;
  when that is no longer the current one, the writer is refused and told which is.
- **FR-008**: A belief revision MUST rest only on claims that take a stance on a hypothesis of its
  own question.
- **FR-009**: No record is changed or removed once held. Thinking again is a new record linked to
  the earlier one. The one exception is the withdrawal of text (FR-034 to FR-037).
- **FR-010**: Every record a record names MUST already be held when it is written, checked against
  the service's own records and never against the graph database.
- **FR-011**: A record's date MUST NOT be later than the time it is recorded, nor earlier than the
  date of any record it links to; evidence MUST NOT be observed before it was published. A holder
  and a source are registered, not stated: each is dated when it was registered, and a record that
  names one may be dated before that. The time a
  record was recorded, and the writer who sent it, are set by the service from what it knows of the
  caller, never taken from the record, and kept beside its date.
- **FR-012**: Every write MUST be safe to repeat: the same record sent again is the record already
  held, and a different record under an identifier already used is refused.
- **FR-013**: A refusal MUST name the rule broken and what broke it, and MUST leave every record
  and the graph as they were.
- **FR-014**: A writer MUST be able to read back any record as it was stated, and a belief's
  revisions in order, from the service alone.

**The graph**

- **FR-015**: Every record MUST be published as a node and every link as an edge, as graph deltas
  under `ankka.graph-delta.v1`, to one compacted topic that ankka-flow's merge sink applies to a
  graph database. The pipeline is the sink alone.
- **FR-016**: Every edge MUST be published by the record that stated the link and run from that
  record's node to the node of a record held before it. No node or edge has two writing records.
- **FR-017**: The vocabulary MUST be declared in one place: every kind of node and edge, its layer,
  its properties and the kind of record that publishes it. Nothing outside it is published, and a
  test fails when something is.
- **FR-018**: The belief layer MUST name nothing of the market layer. The market layer MUST add
  only its own kinds of node, edges that run from its own nodes, and holder kinds, and change
  nothing in the belief layer.
- **FR-019**: A graph database emptied and rebuilt from the delta topic alone MUST hold the same
  graph, with the service sending nothing again; a record published twice MUST leave the graph
  unchanged.
- **FR-020**: Recording MUST NOT wait for the graph. A writer MUST be able to ask to be answered
  once the graph has the node for a record it wrote and every edge that record stated, within a
  limit, and is told when the limit passed first.

**Reading**

- **FR-021**: A reader MUST be able to ask why a belief changed between any two of its revisions
  and receive both probabilities; the claims newly rested on, no longer rested on and still rested
  on with a changed weight; for each claim its evidence and sources and any claim that revised it;
  and the evidence observed between the two revisions.
- **FR-022**: Every answer MUST be made only of held records and their links. No text is generated.
- **FR-023**: A reader MUST be able to ask for the case for and the case against a hypothesis: the
  claims taking that stance that are not revised claims, each with its evidence, sources and the
  holders whose current belief revisions rest on it, with revised claims shown apart.
- **FR-024**: A belief whose current revision rests on a revised claim MUST be shown as doing so,
  with the claim that revised it, and a reader MUST be able to list such beliefs for a question.
  Nothing is changed or recomputed.
- **FR-025**: A reader MUST be able to ask any of these as of a past time and receive only records
  dated at or before it, with claims revised later not shown as revised; and to ask what was
  learned about a question after a time. An answer says when a record was recorded where that
  differs from its date. A reader MUST also be able to ask any of these as recorded by a past time
  and receive only records recorded at or before it; the two may be combined, and an answer as
  recorded by a time MUST be the same whenever it is asked.
- **FR-026**: A reader MUST be able to compare two holders' beliefs in one hypothesis and receive
  both probabilities, the difference, the claims both rest on with each weight, and the claims only
  one rests on. A holder whose belief revision rests on no claims is shown as stating no reasons.
- **FR-027**: Answers to FR-021 to FR-026 and FR-032 to FR-033 MUST be computed from the graph
  database, so that anyone reading the graph with the vocabulary reaches the same answer.

**The market layer**

- **FR-028**: A writer MUST be able to open a market about one question: its venue, an outcome for
  each hypothesis it trades (each for a hypothesis of that question), its resolution criteria and
  its closing time. A question may have several markets. Opening a market registers it as a holder
  of kind market, for which the writer who opened it speaks.
- **FR-029**: A writer MUST be able to record a price observation for an outcome, which becomes the
  market's belief revision in that outcome's hypothesis, resting on no claims. An observation equal
  to the current one adds nothing; one for an outcome the market does not offer, or dated later
  than the closing time, is refused.
- **FR-030**: A market MUST be comparable with any holder exactly as two holders are (FR-026).
- **FR-031**: A writer MUST be able to resolve a market to one of its outcomes, or as void, on at
  least one piece of evidence and a named authority, with its date. A later resolution may revise
  an earlier one, and both are kept.
- **FR-032**: A reader MUST be able to ask why a market was resolved and receive the outcome, the
  authority, the evidence and its sources.
- **FR-033**: A reader MUST be able to list, for a resolved market, each holder's last belief
  revision dated before the resolution for each hypothesis the market offers an outcome for, and
  whether that hypothesis is the one resolved to.

**Withdrawing text**

- **FR-034**: The writer who recorded a piece of evidence MUST be able to withdraw its text (its
  excerpt, author and locator), and a writer who speaks for a claim's holder MUST be able to
  withdraw its statement, in each case with a note saying why. A steward, a writer the deployment
  names, MUST be able to withdraw the text of any evidence or claim the same way. Any other writer
  is refused, and so is a withdrawal with no note.
- **FR-035**: A withdrawn record MUST keep its identifier, its links, its dates, the time it was
  recorded and the writer who sent it, and MUST gain the note, the writer who withdrew it and the
  time. A withdrawal is not undone.
- **FR-036**: Withdrawn text MUST NOT be readable from the service, from the graph database, or
  from a graph database rebuilt from the delta topic afterwards, at any time asked about, earlier
  ones included. It MUST NOT remain in the delta topic once the broker has compacted the record's
  key, nor be readable through the service or by any query of its database. Storage the database
  has not yet reclaimed, and backups, are outside this requirement.
- **FR-037**: Every answer that includes a withdrawn record MUST show it as withdrawn, with its
  source or holder and its date, and no text for it.

### Key Entities *(each a term in the project glossary)*

**Belief layer**

- **Question**: something not yet known, stated as a sentence. Has two or more hypotheses.
- **Hypothesis**: one of the answers competing for a question.
- **Holder**: whoever holds a belief or states a claim. Has a kind, a name and the writers that
  speak for it.
- **Source**: where evidence comes from.
- **Evidence**: something observed, with its provenance: source, locator, excerpt, author, the time
  it was published, the time it was observed. Its excerpt, author and locator can be withdrawn.
- **Claim**: a holder's statement derived from evidence, with a stance on one or more hypotheses.
  May revise one earlier claim. Its statement can be withdrawn.
- **Belief**: one holder's probability for one hypothesis; the line of its belief revisions.
- **Belief revision**: one statement of a belief: a probability, the claims it rests on, each with
  an optional weight, and the revision it follows.

**Market layer**

- **Market**: a place where a question is traded: venue, outcomes, resolution criteria, closing
  time. Also a holder, whose belief revisions are its price observations.
- **Resolution**: which outcome a market came to, or void, on what evidence and whose authority.
  May revise one earlier resolution.

**Links, each stated by the record on the left and running to a record held before it**

| Link | From | To | Carries | Layer |
|---|---|---|---|---|
| answers | hypothesis | question | | belief |
| from | evidence | source | | belief |
| stated by | claim | holder | | belief |
| derives from | claim | evidence, one or more | | belief |
| supports, contradicts | claim | hypothesis, one or more | | belief |
| revises | claim | claim, at most one | | belief |
| held by | belief revision | holder | | belief |
| in | belief revision | hypothesis | probability | belief |
| rests on | belief revision | claim, none or more | weight, optional | belief |
| follows | belief revision | belief revision, at most one | | belief |
| about | market | question | | market |
| offers | market | hypothesis, one or more | outcome | market |
| speaks as | market | holder | | market |
| of | resolution | market | | market |
| resolves to | resolution | hypothesis, none when void | outcome | market |
| on | resolution | evidence, one or more | | market |
| revises | resolution | resolution, at most one | | market |

The names of labels, edge types and properties in the graph database are the plan's to settle,
within the delta contract's rules for identifiers and property values.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For the launch example, the answer to why the belief went from 0.38 to 0.61 names the
  regulator's evidence, the claim derived from it and the earlier claim it revised, each with its
  source, and contains nothing that is not a held record.
- **SC-002**: In a seeded set of ten questions with at least 500 records, every belief revision
  that rests on a claim can be walked in the graph database to at least one source, with no gap.
- **SC-003**: Every record sent twice, across the whole seeded set, leaves the count of records and
  the graph unchanged.
- **SC-004**: Every attempt in the test set to make a record rest on something later than itself,
  or on something not held, is refused; no path in the graph returns to the node it started from.
- **SC-005**: After the graph database is emptied and rebuilt from the delta topic, every answer
  for the seeded set is identical to the answer before.
- **SC-006**: Across the seeded set, no as-of answer for any past time contains a record dated
  later than that time, and no answer as recorded by a past time changes after further records,
  back-dated ones among them, are entered.
- **SC-007**: A record is in the graph within five seconds of being recorded, nine times in ten, on
  a developer's machine; an explanation for a question with 1,000 records is answered within one
  second.
- **SC-008**: Every scenario of the belief layer passes with no market held anywhere, and the
  belief layer of the vocabulary contains no kind, edge or property of the market layer.
- **SC-009**: The vocabulary is enough to read the graph by: the trace query printed with it, run
  as written against the launch example, returns the claim, the evidence and the source behind the
  current belief revision.
- **SC-010**: Every attempt in the test set to state a claim, a belief revision, a price observation
  or a resolution as a holder the writer does not speak for is refused, and every held record names
  the writer who sent it.
- **SC-011**: After the text of any record in the seeded set is withdrawn, a search of the service's
  answers, of the graph database, and of a graph database rebuilt from the delta topic finds none
  of that text, and every answer that included the record still includes it, shown as withdrawn.

## Assumptions

**What this is built on**

- ankka-reasoning is an ankka application, as ankka-cloud and satisfactory are, written in Scala 3
  against a released ankka. Its records are ankka entities, each with a graph consumer. What it
  finds missing in ankka or ankka-flow becomes a request to them, not a workaround here.
- The graph database is Neo4j 5.26 or later, filled by ankka-flow's built-in merge sink from a
  compacted delta topic the pipeline declares. Whoever deploys supplies Kafka and Neo4j; the
  pipeline is deployed before the service, because ankka creates no topics.
- The delta contract's limits hold: a node or edge is its whole state, has one writing entity, and
  carries only plain property values. The vocabulary is designed inside them.
- Reads that explain (FR-021 to FR-026, FR-032, FR-033) query the graph database from the service.
  ankka has no graph client, so the service brings its own and its connection is the deployer's to
  configure.

**Choices made where the description left room**

- A claim derives from evidence only. A claim derived from other claims is left for the process
  layer, where an inference is a step someone or something performed.
- The same evidence is recognised only when its source, locator and excerpt are identical. Two
  claims that say the same thing in different words are two claims.
- A writer is whoever the service authenticates the caller as: another service by its identity, or
  a person by a token. Built on ankka 0.10.0 only the first is possible; a person's token waits
  for the ankka release that carries its verifier. Any admitted caller may read.
- The service keeps an excerpt and a locator for evidence, not the original document.
- Text that can be withdrawn has to be kept somewhere it can truly be erased from, which an
  append-only journal of events is not. The plan keeps every record that carries text in a key
  value entity, whose row is overwritten, for that reason.
- A market's price is observed a few times an hour at most. The full price series belongs to the
  market's own system.
- Every name, number and date in the launch example is made up.

**Not in this feature**

- Agents that gather evidence, state claims or forecast; any call to a model.
- The process layer: goals, tasks, work items, workers, recorded worker responses, replay and fork.
- Marking beliefs stale and recomputing them when a claim is revised. This feature shows which
  beliefs rest on a revised claim and changes none of them.
- Scoring: calibration, accuracy, comparing holders by outcome.
- Assumptions, decisions and trades as records.
- Answers in prose, conversation, notifications, any user interface.
- Matching evidence or claims by meaning.
- Removing a record outright. Withdrawing text from a question, a hypothesis, a holder, a source,
  a market or a resolution. Erasing withdrawn text from backups of the service's database or of
  the broker, or from storage the database has not yet reclaimed.
- The market itself: orders, positions, settlement, prices beyond the observations recorded here.
