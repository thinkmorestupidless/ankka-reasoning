---
title: The process layer
description: How a run of a blueprint is recorded beside what it wrote, as blueprint versions, runs, run endings and acts, what each links to, why the act is written after its record, and what a reader gets from an answer with acts.
kind: concept
layers: [process]
related: [concepts/layers.md, build/run-a-blueprint.md, build/the-appraiser.md, reference/graph-vocabulary.md]
---

# The process layer

The belief layer records what was concluded and on what grounds. The process layer records how a
record came to be made: which run of which version of which blueprint wrote it, in which step, as
which worker, speaking for which holder. It adds four kinds of record and changes nothing beneath
it. A graph with the process layer's nodes removed is a complete belief-and-market graph.

## The four records

| Record | Is | Written |
|---|---|---|
| blueprint version | one registration of a blueprint as ankka holds it: its name, number, digest and when it was registered | by the service, from ankka's own record, once per version |
| run | one carrying out of one blueprint version under a run id, and when it started | by the service, from ankka's own record, once |
| run ending | how a run ended: its status, a reason when it failed, and when | by the service, from ankka's own record, once; a run with no ending is running |
| act | one record a run wrote: the run, the step, the worker, the record and the holder it was stated as | with the record, by the tool that wrote it, and never without it |

A blueprint is held by [ankka](https://docs.ankka.cloud/build/blueprints/), as data: its workers,
their instructions and tools, and its steps. The service copies what ankka holds about versions
and runs into records of its own, so that they are in the graph beside the records they explain,
dated and never edited like everything else. It holds no second copy of the blueprint itself: a
version's record carries ankka's digest, and the blueprint is read from ankka by the version's
number.

The act is the process layer's own. Every record a run writes, a piece of evidence, a claim or a
belief revision, is written through a tool the service gives the blueprint, and the tool writes
the act with it. A record written by a person or a script has no act, and an answer says so by
listing none for it.

## What an act links to

![A run of the appraiser at blueprint version 1 writes three kinds of record through the three tools. Each record has one act: the act is in the run, wrote the record, and was stated as the holder the run speaks for. The run is of its blueprint version, and its run ending ended it. Every arrow is a link stated by the record it leaves and points to a record already held.](../assets/diagrams/process.svg)

An act states three links: `IN` the run that made it, `WROTE` the record, and `AS` the holder the
record was stated for. A run states one, `OF` its blueprint version, and a run ending one, `ENDED`
its run. Every one of them points backwards: the version is held before the run, the run before
any act in it and before its ending, and the record before its act. A reader walks from any
record to its act, from the act to the run, and from the run to the version, and so from a
belief to the instructions that produced it.

An act is dated as the record it names. It carries the step and the worker as properties, so a
query can ask which worker of which step wrote what without reading the blueprint.

## A record has one act

A piece of evidence is identified by what it says, so two runs that quote the same passage of
the same document record one piece of evidence. The act is the first run's. The second run meets
a record already held, writes nothing, and gains no act for it. Its claim, which derives from that
evidence, is its own and has an act of its own. The answer to what a run wrote is therefore exact:
the records whose acts name it, and not every record it touched.

## Why the act is written after its record

A tool's write is three steps, in order: resolve the run, the worker and the holder, and refuse if
any cannot be; write the record, which the belief layer may refuse under a rule of its own; write
the act. The tool answers the model only after the third, so every call the model is answered for
has left a record with its act. A refused record leaves nothing, and no act names a record that is
not held.

Between the second and the third step the service could stop. The record is then held with no act
until the same call is made again: ankka runs the step again after a restart, the model calls the
tool again, the record is a repeat, and the act is written. No run that has ended leaves a record
without its act. The window is listed on [Limitations](../reference/limitations.md).

## A blueprint speaks for its holders

A run writes as a holder named in its input, and the writer of everything it writes is the
blueprint: `blueprint:appraiser`, a writer no caller can be. The service registers the holders a
blueprint may be run for when it starts, each of kind `agent` and spoken for by that writer. A run
for any other holder is refused before it starts, with `holder.writer.does-not-speak`, as a
person's claim for a holder they do not speak for is.

A belief revision a run writes rests only on claims its own holder stated. Two holders run over
the same documents therefore each state claims of their own, deriving from the same evidence, and
their beliefs are compared claim by claim through the evidence the claims share.
[Writers, holders and stewards](writers-and-holders.md) has the rule for everyone else.

## Versions

A blueprint registered again with different instructions is a new version, numbered by ankka, and
the version that was current goes on being held. A run is always of the version current when it
started, and every record it wrote names that version through its act's run. Two holders
appraised by two versions of the same blueprint over the same documents are two beliefs whose
difference is the instructions', and an answer with acts shows the version behind each.
[The appraiser](../build/the-appraiser.md) registers its second instruction set that way.

## Answers with acts

Every answer of the belief and market layers is served again under `/process`: the same route,
the same reply, inside `answer`, and beside it `acts`, the act of every record in the answer that
has one, each with its run, the run's ending if it has one, and the blueprint version. A reader
asking why a belief changed is told which run read the document that changed it and which version
of the instructions the run followed. One more answer is the process layer's own: what a run
wrote. [Ask for explanations](../build/ask-for-explanations.md) has the lower answers, and
[Run a blueprint](../build/run-a-blueprint.md) the ones with acts.
