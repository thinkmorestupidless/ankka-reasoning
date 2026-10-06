# Glossary

The words this project's features use, each in exactly one sense. A term marked *Proposed.* has
still to be settled by `/speckit-clarify`.

## Records

### record
*Proposed.* One thing the service holds: a question, a hypothesis, a holder, a source, a piece of
evidence, a claim, a belief revision, a market or a resolution. A record is never changed or removed
once it is held.
Avoid: object, entity, item

### held
*Proposed.* Accepted by the service and kept. A record that is held can be read back as it was
stated, for as long as the service runs.
Avoid: stored, saved, persisted

### writer
*Proposed.* Whoever sends records to the service: a person, a program, and later an agent.

### reader
*Proposed.* Whoever asks the service, or the graph database, a question about the records.

### refused
*Proposed.* Not accepted. A refused writer or reader is told which rule was broken, and nothing is
held or changed.
Avoid: rejected, denied

### rule
*Proposed.* A condition a record must meet to be held, such as naming only records that are held.

### registered
*Proposed.* Of a holder or a source: held, so that later records may name it.

### dated
*Proposed.* The time a record says it holds from: the time evidence was observed, or the time a
claim, a belief revision or a resolution was stated. The writer gives it; left out, it is the time
the record was recorded. It is never later than the time the record was recorded.
Avoid: timestamp, effective time

### recorded
*Proposed.* Of a record: accepted by the service. The time a record was recorded is set by the
service and never by the writer.

### link
*Proposed.* A named connection a record states to a record held before it: the evidence a claim
derives from, the claim a belief revision rests on.
Avoid: relationship, reference

### limit
*Proposed.* A bound the service applies and names when it refuses: the longest excerpt, the longest
wait.

## The belief layer

### question
*Proposed.* Something not yet known that the reasoning is about, stated as a sentence.

### hypothesis
*Proposed.* One of the answers that compete for a question. A question has at least two.

### holder
*Proposed.* Whoever holds a belief or states a claim: an agent, a person, a model, and in the
market layer a market.
Avoid: forecaster, participant, actor

### kind
*Proposed.* What sort of thing a holder, a node or an edge is, as the vocabulary names it.

### source
*Proposed.* Where evidence comes from: a publisher, a registry, a feed.

### evidence
*Proposed.* Something observed outside the service, recorded with its provenance. It belongs to no
question.
Avoid: fact, document

### provenance
*Proposed.* What says where evidence came from and when: its source, its locator, its author, the
time it was published and the time it was observed.

### locator
*Proposed.* Where exactly in its source a piece of evidence is found, such as an address.

### excerpt
*Proposed.* The part of a piece of evidence the service keeps as text, up to a limit. The whole
original is not kept.

### published
*Proposed.* Of evidence: made public by its source. Of a node or an edge: written to the delta
topic by the service.

### observed
*Proposed.* Of evidence: first seen by whoever recorded it. Evidence is dated the time it was
observed. Of a price: read from its venue.

### claim
*Proposed.* A statement a holder derives from evidence, with a stance on one or more hypotheses.

### statement
*Proposed.* The sentence a question, a hypothesis or a claim says.

### derives from
*Proposed.* The link from a claim to each piece of evidence it was made from.

### stance
*Proposed.* How a claim bears on a hypothesis: it supports it or it contradicts it.

### supports
*Proposed.* A stance: the claim makes the hypothesis more likely.

### contradicts
*Proposed.* A stance: the claim makes the hypothesis less likely.

### revises
*Proposed.* The link from a claim to an earlier claim it replaces, or from a resolution to an
earlier resolution it replaces. The earlier record is kept.
Avoid: supersedes, updates, corrects

### revised claim
*Proposed.* A claim that a later claim revises. It reads as it was stated.

### belief
*Proposed.* One holder's probability for one hypothesis, with every belief revision it has had.
Avoid: forecast, prediction, confidence

### belief revision
*Proposed.* One statement of a belief: the probability at that time and the claims it rests on.
A belief's revisions form one line, each following the one before.

### probability
*Proposed.* A number from nought to one saying how likely a holder takes a hypothesis to be.

### rests on
*Proposed.* The link from a belief revision to each claim the holder gives as a reason for it.

### weight
*Proposed.* A number from nought to one a belief revision may give a claim it rests on, saying how
much that claim counts for that holder.

### follows
*Proposed.* The link from a belief revision to the belief revision before it in its belief's line.

### current
*Proposed.* Of a belief revision: the last in its belief's line. Of a resolution: one that no later
resolution revises. Of a price observation: the last recorded for its outcome.

### reasons
*Proposed.* The claims a belief revision rests on. A holder whose belief revision rests on none
states no reasons.

## Reading the reasoning

### explanation
*Proposed.* The answer to why a belief changed between two of its belief revisions, made only of
records: the claims that came and went, their evidence and its sources.

### case
*Proposed.* The claims that take one stance on a hypothesis and are not revised claims, with their
evidence. The case for a hypothesis is the claims that support it; the case against, the claims
that contradict it.

### as of
*Proposed.* Reading the records as they stood at a past time: only records dated at or before it.

### learned
*Proposed.* Of evidence, claims and belief revisions: dated later than a given time.

### difference
*Proposed.* One probability less another.

## The graph

### graph
*Proposed.* The records and their links as nodes and edges in the graph database.

### node
*Proposed.* What a record is in the graph.

### edge
*Proposed.* What a link is in the graph. It runs from the node of the record that stated it.

### property
*Proposed.* A named value on a node or an edge.

### graph database
*Proposed.* The database that holds the graph and answers questions about paths in it.

### delta topic
*Proposed.* The topic the service publishes nodes and edges to, which holds the latest state of
each and from which a graph database is filled.

### rebuilt
*Proposed.* Of a graph database: filled again from the delta topic alone.

### caught up
*Proposed.* Of the graph: holding a node for every record, and an edge for every link, that the
service held when the reader asked.

### wait
*Proposed.* A writer's request to be answered only once the graph has a node for a record it
wrote, or once a limit has passed.

### vocabulary
*Proposed.* The list of every kind of node and edge the graph may hold, with each kind's layer,
its properties and the kind of record that publishes it.
Avoid: schema, ontology

### layer
*Proposed.* A part of the vocabulary that can be used without the parts above it.

### belief layer
*Proposed.* The layer of questions, hypotheses, holders, sources, evidence, claims and beliefs.

### market layer
*Proposed.* The layer of markets, outcomes and resolutions, above the belief layer.

### service
*Proposed.* This project's running program, which holds the records and publishes the graph.

## The market layer

### market
*Proposed.* A place where a question is traded. It is about one question, offers outcomes, and is
a holder whose belief revisions are its price observations.

### venue
*Proposed.* Who runs a market.

### outcome
*Proposed.* One of the results a market offers, each for one hypothesis of the market's question.

### price observation
*Proposed.* The price of one outcome of a market, read from its venue at a time, as a probability.
Avoid: market probability, quote, tick

### resolution criteria
*Proposed.* The text that says how a market's outcome will be decided.

### closing time
*Proposed.* The time after which a market takes no price observations.

### resolution
*Proposed.* The record of which outcome a market came to, on what evidence and on whose authority.

### resolved
*Proposed.* Of a market: having a resolution.

### authority
*Proposed.* Who or what decided a resolution.

### void
*Proposed.* Of a resolution: naming no outcome, because the market could not be decided.

## The example

### launch example
*Proposed.* The worked example the features share; every name and number in it is made up. The
question "Will Company X launch Product Y this year?" has the hypotheses "it launches this year"
and "it does not launch this year". The holder "agent-a" is an agent. Evidence from the source
"Company X filings" is dated "4 May"; from it the holder states the claim "approval is pending and
the launch date is uncertain", which contradicts "it launches this year", and a belief of "0.38" in
that hypothesis resting on the claim, both dated "4 May". Evidence from the source "the regulator"
is dated "11 May"; from it the holder states the claim "the approval barrier has gone", which
supports the hypothesis and revises the earlier claim, and revises the belief to "0.61" resting on
the later claim, both dated "11 May".

## Everyday words

a, an, the, and, or, of, on, for, to, from, with, in, by, at, as, is, are, was, were, has, have,
had, does, do, not, no, it, its, that, which, who, whose, each, every, any, both, one, two, three,
other, another, same, different, only, own, when, then, given, between, before, after, later,
earlier, first, second, third, last, now, today, time, day, year, past, yet, again, once, twice,
still, also, than, what, why, whether, there, they, them, their, never, hypotheses, piece, author,
name, names, named, naming, order, answer, against, apart, newly, longer, adds, asks, believed,
belongs, came, went, comes, change, changed, changes, compares, emptied, ends, gives, hold, offers,
opens, opened, reaches, reached, read, reads, returns, runs, says, send, sends, sent, shown,
started, stated, states, stating, takes, told, walks
