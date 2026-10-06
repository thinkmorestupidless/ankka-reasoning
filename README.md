<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/brand/ankka-reasoning-lockup-white.png">
  <img src="docs/assets/brand/ankka-reasoning-lockup-black.png" alt="ankka-reasoning" width="400">
</picture>

A reasoning graph on [ankka](https://github.com/thinkmorestupidless/ankka): a record of how beliefs
form and change. Evidence is kept with where it came from, claims are derived from evidence,
hypotheses compete to answer a question, and a belief is one holder's probability for one
hypothesis. Nothing in it is ever edited, so any belief can be traced to its sources, read back as
it stood on any past day, and set beside a belief that disagrees with it.

**This repository is at the design stage.** It holds a specification, the scenarios that
specification names, and a glossary. There is no code yet, and this page describes what is being
built.

## The problem

A forecast, a recommendation or a decision usually survives as a number or a sentence. The
reasoning that produced it does not. That is true of a person's judgement, of a market's price,
and most of all of an agent's answer, where the reasoning lived in a prompt and was gone when the
call returned.

Three questions then have no answer:

- **Where did this come from?** Which claims does the belief rest on, what evidence are they
  derived from, and who published it.
- **What did we believe then?** What the belief was last Tuesday, what evidence had been seen by
  then, and what arrived afterwards.
- **Why do these two disagree?** Whether two holders saw different evidence, or weighed the same
  evidence differently.

Asking a model afterwards does not answer them. It writes a plausible argument at the moment it is
asked, which is not the argument that was made.

A prediction market shows the gap plainly, and it is the first domain this is built for. A market
says the crowd puts an outcome at 38%. It does not say why, and when the price moves to 61% nothing
in the market says what changed. A forecaster beside the market, human or agent, has the same
problem unless its reasoning is recorded as it happens.

## What this does

ankka-reasoning records the reasoning as it is stated, checks it, and keeps it as a graph that can
be asked those three questions.

![How ankka-reasoning works. Writers, a person, a script or a test and later agents, send records to the ankka-reasoning service, which runs in an ankka project. Inside the service, Records holds one entity per record, never edited, and checks every rule against the service's own records; its events are kept in the service's Postgres database. Each change goes to the graph consumers, one per kind of record, which turn each record into a node and each link it stated into an edge and publish them as graph deltas to a compacted delta topic in Kafka under the contract ankka.graph-delta.v1. An ankka-flow pipeline that is the built-in Neo4j merge sink alone reads the topic and merges each delta into the graph database, Neo4j, which is a copy that can be emptied and rebuilt from the topic alone. Readers ask the service's Answers, which query the graph database, read only, and reply with records only. A write never waits for the graph.](docs/assets/diagrams/architecture.svg)

It is an ankka application with three parts:

- **Records.** Each question, piece of evidence, claim and belief revision is an ankka entity. A
  writer sends a record; the service checks it against the records it already holds and either
  keeps it or refuses it, naming the rule. A claim must name evidence that is held. A belief
  revision cannot rest on a claim dated later than itself. Nothing held is changed afterwards.
- **Graph consumers.** Every record is published as a node, and every link it stated as an edge,
  using ankka's [graph consumers](https://docs.ankka.cloud/build/graph/). The deltas go to a
  compacted topic, and [ankka-flow](https://github.com/thinkmorestupidless/ankka-flow)'s built-in
  [merge sink](https://flow.ankka.cloud/reference/neo4j-merge-sink/) applies them to Neo4j. The
  graph database is a copy: it can be emptied and rebuilt from the topic alone.
- **Answers.** A reader asks why a belief changed, what the case for a hypothesis is, what was
  believed as of a date, or where two holders differ. Each answer is a walk of the graph and is
  made of records only. No sentence is written for the occasion.

## What the graph holds

![The kinds of node in the graph and the links between them, in two layers. In the belief layer: a hypothesis answers a question; evidence is from a source; a claim derives from evidence, supports or contradicts a hypothesis, is stated by a holder, and may revise an earlier claim; a belief revision is in a hypothesis with a probability, rests on claims each with an optional weight, is held by a holder, and follows the belief revision before it. In the market layer, above: a market is about a question, offers an outcome for a hypothesis, and speaks as a holder; a resolution is of a market, resolves to a hypothesis, is on evidence, and may revise an earlier resolution. Every arrow is a link stated by the record it leaves and points to a record that was already held. No record is ever edited.](docs/assets/diagrams/graph.svg)

The vocabulary is in layers, and each layer can be used without the ones above it.

| Layer | Kinds of node | What it is for |
|---|---|---|
| Belief | question, hypothesis, holder, source, evidence, claim, belief revision | Reasoning about anything not yet known. It names nothing of markets. |
| Market | market, resolution | A question that is traded. The market is a holder too, and each price observation is its belief revision. |
| Process | goal, task, worker and the calls they made | How the reasoning was carried out, so it can be replayed. A later feature. |

Reading the picture from a belief revision outwards gives the trace that makes a belief auditable:
the claims it rests on, the evidence each derives from, and the source of that evidence.

## How it stays right

Five rules do most of the work. Each one removes a problem instead of handling it.

- **Nothing is edited.** A holder who thinks again states a new record linked to the old one: a
  claim that revises a claim, a belief revision that follows the one before. History is not
  reconstructed, because it was never overwritten.
- **Links point backwards.** A record may link only to records that are already held and dated no
  later than itself. A cycle cannot be made, so nothing has to check for one, and no record can
  rest on something that had not happened yet.
- **A link has one owner.** Every link is stated by one record, the newer of the two it joins, and
  published with it. No node or edge has two writers, which is the rule ankka's graph deltas need.
- **Rules are checked where the truth is.** Whether a record may be held is decided from the
  service's own records. The graph database is always a moment behind, so it is only ever read.
- **Answers are records.** An explanation contains the claims, evidence and sources that are held
  and nothing else, so it can be checked against the graph by anyone.

## An example

Will Company X launch Product Y this year? Every name and number here is made up.

```text
4 May    evidence   a filing, from "Company X filings"
         claim      "approval is pending and the launch date is uncertain"
                      derives from the filing · contradicts "it launches this year"
         belief     agent-a puts "it launches this year" at 0.38, resting on that claim

11 May   evidence   a notice, from "the regulator"
         claim      "the approval barrier has gone"
                      derives from the notice · supports "it launches this year"
                      revises the claim of 4 May
         belief     agent-a moves to 0.61, resting on the new claim
                      follows the belief revision of 4 May
```

The claim of 4 May is kept. It was right for its time.

Asked why the belief went from 0.38 to 0.61, the graph answers with the regulator's notice, the
claim derived from it and the earlier claim it revised, each with its source. Asked what agent-a
believed as of 6 May, it answers 0.38, resting on the first claim, with the notice absent because
it had not been observed. If a market prices the same outcome at 0.48, the comparison gives 0.48
against 0.61, the claims agent-a rests on, and the market stating no reasons.

## What is here now

| | |
|---|---|
| [`specs/001-belief-layer/spec.md`](specs/001-belief-layer/spec.md) | The first feature: the belief layer and the market layer, recorded, published and explained. |
| [`features/`](features) | What the service does, as scenarios in four areas: `record`, `graph`, `explain`, `market`. The spec names them. |
| [`GLOSSARY.md`](GLOSSARY.md) | The words the scenarios use, each in one sense. |
| [`CLAUDE.md`](CLAUDE.md) | The working rules for the repository, these five among them. |

The first feature has no agents in it. Records arrive from a person, a script or a test, which is
enough to prove the model holds and explains reasoning whoever wrote it. What follows, in the order
it is expected:

1. Agents as writers: one that gathers evidence and one that forecasts, with every model call
   recorded.
2. The process layer: goals, tasks and workers, so a session of reasoning can be replayed.
3. Beliefs that know when to think again: a belief resting on a revised claim is marked and
   revisited.
4. Scoring: once a market resolves, which holders and which evidence were worth listening to.

## Beside ankka and ankka-flow

ankka-reasoning is an application of both and part of neither. ankka supplies the entities, the
graph consumers and, later, the agents. ankka-flow supplies the delta contract and the merge sink.
What this project finds missing in either becomes a request to it.
