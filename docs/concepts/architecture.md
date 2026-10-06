---
title: How ankka-reasoning works
description: The path of a record from a writer's request to a node in the graph and back out as an answer, and why the write side and the graph are kept apart.
kind: concept
related: [concepts/records-and-links.md, concepts/nothing-is-edited.md, build/read-the-graph.md, deploy/deploy-on-ankka.md]
---

# How ankka-reasoning works

ankka-reasoning is one ankka service with three parts: records, graph consumers and answers. A writer
sends a record; the service checks it and keeps it; the record is published to a Kafka topic as a node
and its edges; ankka-flow's merge sink copies the topic into Neo4j; and a reader's question is answered
by walking that graph. The write never waits for the graph, and the graph is never written by the
service.

![How ankka-reasoning works. Writers, a person, a script or a test, send records to the ankka-reasoning service, which runs in an ankka project. Inside the service, Records holds one entity per record, never edited, and checks every rule against the service's own records; every record is kept in the service's Postgres database. Each change goes to the graph consumers, one per kind of record, which turn each record into a node and each link it stated into an edge and publish them as graph deltas to a compacted delta topic in Kafka under the contract ankka.graph-delta.v1. An ankka-flow pipeline that is the built-in Neo4j merge sink alone reads the topic and merges each delta into the graph database, Neo4j, which is a copy that can be emptied and rebuilt from the topic alone. Readers ask the service's Answers, which query the graph database, read only, and reply with records only. A write never waits for the graph.](../assets/diagrams/architecture.svg)

## Records: where the truth is

Each record is one ankka entity, kept in the service's Postgres database. A question, a holder, a
source, a piece of evidence, a claim and a market are each a
[key value entity](https://docs.ankka.cloud/build/key-value-entities/); a belief, one holder's
probability for one hypothesis, is an
[event sourced entity](https://docs.ankka.cloud/build/event-sourced-entities/) whose events are its
revisions.

Whether a record may be held is decided here and nowhere else. A claim must name evidence that is
held; a belief revision cannot rest on a claim dated later than itself; a writer must speak for the
holder it writes as. Each rule is checked against the service's own records before the record is kept,
and a record that breaks one is refused by the rule's name with nothing changed.
[Refusals](../reference/refusals.md) lists them.

The graph database is never consulted for a rule. It is a moment behind the records, so a rule checked
against it could pass for a record that should be refused.

The check runs before the entity's command and is still true after it, with no lock, because nothing a
rule depends on can stop being true: a record that is held stays held, its date and its links never
change, and a question's hypotheses and a holder's writers are only ever added to. Two rules do depend
on state that changes, which revision of a belief is current and which resolution of a market is, and
those two are decided inside the one entity that owns that state.

## Graph consumers: one writer per element

Every record is published as a node, and every link it stated as an edge, by an ankka
[graph consumer](https://docs.ankka.cloud/build/graph/). There is one consumer per kind of record, and
each publishes the record whole: its node and every edge that leaves it.

A link is stated by one record, the newer of the two it joins, and is published with that record and
by nothing else. So no node and no edge has two writers. That is what makes the topic safe to compact
and the graph safe to rebuild: the latest delta under an element's key is the whole truth about that
element.

What may be published is fixed by the [vocabulary](../reference/graph-vocabulary.md), a value in the
code that names every kind of node and edge, its properties, and the kind of record that publishes it.
A consumer builds an element only through the vocabulary, so an undeclared label or property is an
error in a unit test and never reaches the topic.

## The topic and the sink: the graph is a copy

The deltas go to one compacted Kafka topic, `reasoning-graph`, keyed by element. An
[ankka-flow](https://flow.ankka.cloud/) pipeline that is the built-in
[Neo4j merge sink](https://flow.ankka.cloud/reference/neo4j-merge-sink/) and nothing else reads the
topic and merges each delta into Neo4j, replacing a node's properties whole and passing over a delta
older than what the graph holds.

The graph database holds nothing the topic does not. It can be emptied and filled again from the
topic alone, with the service not involved, and every answer is the same afterwards.
[Operate the service](../deploy/operate.md) does it.

## Answers: a walk, made of records

An answer is a Cypher query that starts from one node found by its identifier and walks outwards: from
a belief revision to the claims it rests on, the evidence each derives from, and each piece's source.
The reply is those records and their identifiers. No text is generated, so an explanation cannot say
anything the records do not.

Answers read the graph database; nothing else does. A route that reads it answers `503` when it cannot
be reached, and every other route, writes included, is unaffected.
[Ask for explanations](../build/ask-for-explanations.md) shows each answer.

## What a write waits for

A write is answered when the service's database holds the record. It does not wait for Kafka, the
sink or Neo4j, so a graph database that is down costs a writer nothing.

The graph follows within a few seconds: a consumer learns of a changed record by polling the database
once a second, then the delta crosses Kafka and the sink applies it. A writer that needs to read its
own write from the graph asks `POST /graph/wait`, which ends when the graph holds the record's node
and every edge it stated, or when a limit passes.

## The modules

The code is five sbt modules, and the direction they depend in is the layering:

| Module | Holds |
|---|---|
| `graph` | the vocabulary as a value, the only way to build an element, the reader of the graph database |
| `belief` | the belief layer: its records, rules, graph consumers, answers and routes |
| `market` | the market layer, built on `belief` |
| `service` | both layers composed into the one deployable |
| `seed` | a client of the service over HTTP: seed files and the measuring tool |

`belief` does not depend on `market`, so a market word in the belief layer does not compile.
[Layers and the vocabulary](layers.md) says what that buys.
