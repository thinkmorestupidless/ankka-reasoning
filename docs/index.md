---
title: ankka-reasoning
description: What ankka-reasoning is, the three questions a reasoning graph answers, how a record becomes a node, and where in this documentation to start.
kind: concept
related: [get-started/run-locally.md, concepts/architecture.md, concepts/records-and-links.md]
---

# ankka-reasoning

ankka-reasoning is a reasoning graph: a record of how beliefs form and change. Evidence is kept with
where it came from, claims are derived from evidence, hypotheses compete to answer a question, and a
belief is one holder's probability for one hypothesis. Nothing in it is ever edited, so any belief can
be traced to its sources, read back as it stood on any past day, and set beside a belief that disagrees
with it. It is an [ankka](https://docs.ankka.cloud/) application, and it keeps its graph in Neo4j
through an [ankka-flow](https://flow.ankka.cloud/) pipeline.

## The three questions

A forecast, a recommendation or a decision usually survives as a number or a sentence, and the
reasoning that produced it does not. Three questions then have no answer, and they are the ones
ankka-reasoning is built to answer:

- **Where did this come from?** Which claims the belief rests on, what evidence they derive from, and
  who published it.
- **What did we believe then?** What the belief was on a past day, what evidence had been seen by then,
  and what arrived afterwards.
- **Why do these two disagree?** Whether two holders saw different evidence, or weighed the same
  evidence differently.

Asking a model afterwards does not answer them: it writes a plausible argument at the moment it is
asked, which is not the argument that was made. ankka-reasoning records the reasoning as it is stated,
and every answer it gives is made of those records and nothing else.

A prediction market is the first domain it is built for. A market says the crowd puts an outcome at
38%, not why, and when the price moves to 61% nothing in the market says what changed.

## The shape, in five lines

- A writer sends **records** over HTTP: a question with its hypotheses, a holder, a source, evidence,
  a claim, a belief revision. Each is checked against the records already held and kept or refused by
  name.
- Records are **never edited**. Thinking again is a new record linked to the old one: a claim that
  revises a claim, a belief revision that follows the one before.
- Every record is published as a **node** and every link it stated as an **edge**, to a compacted
  Kafka topic, and ankka-flow's merge sink keeps them in **Neo4j**. The graph database is a copy that
  can be emptied and rebuilt from the topic.
- A reader asks for **answers**: why a belief changed, the case for a hypothesis, what was believed as
  of a date, where two holders differ. Each is a walk of the graph, made of records only.
- The vocabulary is in **layers**. The belief layer names nothing of markets; the market layer adds
  markets and resolutions on top and changes nothing beneath it.

![How ankka-reasoning works. Writers, a person, a script or a test, send records to the ankka-reasoning service, which runs in an ankka project. Inside the service, Records holds one entity per record, never edited, and checks every rule against the service's own records; every record is kept in the service's Postgres database. Each change goes to the graph consumers, one per kind of record, which turn each record into a node and each link it stated into an edge and publish them as graph deltas to a compacted delta topic in Kafka under the contract ankka.graph-delta.v1. An ankka-flow pipeline that is the built-in Neo4j merge sink alone reads the topic and merges each delta into the graph database, Neo4j, which is a copy that can be emptied and rebuilt from the topic alone. Readers ask the service's Answers, which query the graph database, read only, and reply with records only. A write never waits for the graph.](assets/diagrams/architecture.svg)

## An example

Will Company X launch Product Y this year? Every name and number here is made up, and the whole of
this documentation uses it.

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

The claim of 4 May is kept: it was right for its time. Asked why the belief went from 0.38 to 0.61,
the service answers with the regulator's notice, the claim derived from it and the earlier claim it
revised, each with its source. Asked what agent-a believed as of 6 May, it answers 0.38, resting on
the first claim. [Your first explanation](get-started/first-explanation.md) does both with `curl`.

## Where to start

- **Trying it.** [Run it on your machine](get-started/run-locally.md) starts the service with Kafka,
  the merge sink and Neo4j beside it; [Your first explanation](get-started/first-explanation.md)
  records the example and asks why the belief changed.
- **Understanding it.** [How ankka-reasoning works](concepts/architecture.md) is the overview.
  [Records and links](concepts/records-and-links.md), [Nothing is edited](concepts/nothing-is-edited.md)
  and [Two times on every record](concepts/two-times.md) are the three ideas everything else follows.
  [Layers and the vocabulary](concepts/layers.md) and
  [Writers, holders and stewards](concepts/writers-and-holders.md) complete the model.
- **Writing to it.** [Record reasoning](build/record-reasoning.md) is every kind of record in the
  order it has to arrive; [Markets and resolutions](build/markets.md) adds a market;
  [Seed files](build/seed-files.md) sends a whole history at once.
- **Reading from it.** [Ask for explanations](build/ask-for-explanations.md) is every answer the
  service gives; [Read the graph](build/read-the-graph.md) queries Neo4j directly.
- **Taking text out.** [Withdraw text](build/withdraw-text.md).
- **Running it.** [Deploy on ankka](deploy/deploy-on-ankka.md) and
  [Operate the service](deploy/operate.md).
- **Looking something up.** The [HTTP API](reference/http-api.md), the
  [refusals](reference/refusals.md), the [graph vocabulary](reference/graph-vocabulary.md), the
  [configuration](reference/configuration.md) and the [glossary](reference/glossary.md).
- **Checking what is missing.** [Limitations](reference/limitations.md).

## For models and agents

This documentation is published in forms a model can read directly. `llms.txt` at the site root lists
every page with a one-sentence description, `llms-full.txt` holds every page in one file, and each page
is also served as Markdown at its own path with a `.md` suffix. The same pages are rendered as an agent
skill, `ankka-reasoning`, in
[the repository](https://github.com/thinkmorestupidless/ankka-reasoning/tree/main/marketplace/plugins/ankka-reasoning).
