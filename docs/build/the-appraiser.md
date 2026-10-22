---
title: The appraiser
description: The one blueprint the service carries, as data, with its two workers and their instructions, its six steps, the critique that can fail a run, its two instruction sets and what each changes, and what a run of it writes from start to end.
kind: guide
layers: [process]
related: [build/run-a-blueprint.md, build/golden-set.md, concepts/process-layer.md, deploy/live.md, reference/refusals.md]
---

# The appraiser

The appraiser is the blueprint the service carries: the one piece of work it can give a model. It
appraises a question for a holder from a tranche of documents, one document at a time, and ends by
revising the holder's belief in the question's `yes` hypothesis. It is held by
[ankka](https://docs.ankka.cloud/build/blueprints/) as data, with two workers, six steps, two
handlers and one judgment, and the service gives it three tools. Nothing about it is a prompt
written for the occasion: every version is registered, numbered and digested, and every record a
run writes names the version through its act.

## The blueprint as data

`GET /process/appraiser/versions/{n}` is a version with the whole blueprint as ankka holds it. Cut
down to the steps' names and the workers' names, tools and budgets, version 2 is:

```bash
curl -s localhost:9000/process/appraiser/versions/2
```

```json
{
  "id": "appraiser@2",
  "blueprint": {
    "input": {"properties": {"question": {"type": "string"}, "follows": {"type": "string"}, "holder": {"type": "string"}, "observedAt": {"type": "string"}, "tranche": {"type": "string"}}, "required": ["tranche", "holder", "question"], "type": "object"},
    "name": "appraiser",
    "steps": [
      {"does": {"handler": "load-tranche", "type": "Call"}, "name": "load", "reads": ["input"]},
      {"name": "read", "over": {"read": "load", "type": "Each"}, "reads": ["input"], "does": {"type": "Ask", "worker": "reader"}},
      {"name": "critique", "over": {"read": "read", "type": "Each"}, "reads": ["load"], "does": {"questions": ["claim-derives-from-tranche"], "type": "Judge"}},
      {"does": {"handler": "critique-verdict", "type": "Call"}, "name": "verdict", "reads": ["critique", "read"]},
      {"does": {"type": "Ask", "worker": "reviser"}, "name": "revise", "reads": ["verdict", "read", "input"]},
      {"does": {"handler": "revision-check", "type": "Call"}, "name": "close", "reads": ["revise"]}
    ],
    "workers": [
      {"budget": 6, "name": "reader", "tools": ["record_evidence", "state_claim"]},
      {"budget": 4, "name": "reviser", "tools": ["revise_belief"]}
    ]
  },
  "name": "appraiser",
  "version": 2,
  "digest": "3ca364aede02959895cb05003b77900cb4478af32c79546548793a7188a31755"
}
```

The whole reply also carries each step's result shape, each worker's instructions in full, and the
version's `dated`, `recordedAt` and `writer`. The input is the tranche to read, the holder to write
as and the question, with the belief revision to follow and the date the documents are observed on
optional. A budget is the most tool calls a worker may make for one item.

## The six steps

| Step | Does | With |
|---|---|---|
| `load` | calls the handler `load-tranche`, which reads the tranche the input names from [the golden set](golden-set.md) and returns its documents, each with its `id`, `kind`, `source`, `locator`, `publishedAt` and `text` | a tranche the set does not hold fails the run: `no tranche <name>` |
| `read` | asks the `reader` once per document | the tools `record_evidence` and `state_claim`; each item answers `{document, evidence, claim, excerpt, statement, stances}`, or `{document, refused}` |
| `critique` | asks the judgment `claim-derives-from-tranche` once per item | the claim, its evidence and the tranche's documents |
| `verdict` | calls the handler `critique-verdict` | fails the run on the first item the judgment answered `no` for, or whose reader was refused; otherwise `{passed: true, claims: [...]}` |
| `revise` | asks the `reviser` once | the tool `revise_belief`; answers `{revision}`, or `{refused}` |
| `close` | calls the handler `revision-check` | fails the run when the reviser was refused or answered with no revision; otherwise `{revision}` |

A run that reaches `close` with a revision ends `completed`. A run that fails ends `failed` with the
handler's reason: `claim <id> does not derive from evidence recorded from a document of the tranche`,
`the reader was refused: <rule>, naming <names>`, or `the reviser was refused: …`. A failed run
keeps what was written before it failed, each record with its act; a critique that fails the run
leaves the evidence and claims held and the belief unrevised.

## The reader

The reader is given the run's input and one document. It is told to do three things in order: call
`record_evidence` once with the document's id and an excerpt quoted as written; call `state_claim`
once with a one-sentence statement of what the document shows, deriving from the evidence it was
just given, with one stance on the hypothesis the document bears on; and answer with JSON naming
what it recorded, so the critique can read it. It is told that a tool's error is a refusal, to stop
there and answer with the refusal, and to state only what the document says.

## The critique

The judgment is a yes-or-no question asked of every claim, with the claim's statement and stances,
the evidence it derives from, the document that evidence was recorded from, and the tranche's
documents. It answers yes only when both hold: the claim derives from evidence recorded from a
document of this run's tranche, whoever recorded the evidence and whenever, and the statement
follows from the excerpt; and every stance of the claim is on a hypothesis of the run's question.
A claim resting on evidence a person recorded from no document, or on a document of another
tranche, fails it.

The critique asks and decides nothing itself; `verdict` fails the run on its answers. A tool cannot
fail a run, so the fault is found in one step and acted on in the next.

## The reviser

The reviser is given the input, the verdict and what the readers found. It decides the holder's
probability that the question's `yes` hypothesis is true, calls `revise_belief` once with that
probability and every claim that passed as a rest, each with a weight for how much it moved the
reviser, following the input's `follows`, and answers with the revision's id. Its instructions
differ between the two sets in one paragraph, on weighing:

| Set | The reviser is told |
|---|---|
| `v1` | "Weigh every document by its kind: an official document (a filing, a regulator's notice, an approval) and a dated announcement count most, an analyst's view and a supplier's remarks count somewhat, and a rumour counts, but weakly." |
| `v2` | "Weigh an official document (a filing, a regulator's notice, an approval) double, a dated announcement and a supplier's remarks once, an analyst's view a little, and a rumour as nothing at all: give it a weight of nought and let it move you nowhere." |

The reader's instructions are the same in both. The sets are code, so the two versions' digests are
fixed by the build: `da34f63b…` for version 1 and `3ca364ae…` for version 2, as
`GET /process/appraiser/versions` lists them. The service starts at version 1, and
[Run a blueprint](run-a-blueprint.md) registers the second.

## A run, start to end

For the golden set's May tranche, five documents:

1. `POST /process/runs` with the tranche `may`, the holder `appraiser-a` and the question
   `launch`. The service checks that it carries the blueprint, that the input fits, and that the
   blueprint speaks for the holder, then hands the run to ankka and answers with it.
2. `load` returns the five documents. `read` runs the reader five times; each call records one
   piece of evidence and states one claim, two records with two acts, each act in step `read` by
   worker `reader`. Ten records, ten acts.
3. `critique` asks the judgment five times; `verdict` passes.
4. `revise` writes one belief revision, `appraiser-a-may.yes`, resting on the five claims with
   the weights the reviser chose, one record with one act in step `revise` by worker `reviser`.
5. `close` checks there is a revision; ankka ends the run `completed`, and the service holds the
   ending.

The June run is the same over three documents, with `follows` naming `appraiser-a-may.yes`, so the
belief is one line. A second holder's May run finds the evidence already held and writes its five
claims and its revision, six acts; the evidence keeps the first run's act.

## What a scripted model does

The build never calls a real model. Its suites script both providers from the golden set: for each
document the scripted reader calls `record_evidence` with the document's first sentence and
`state_claim` with the reading the set expects, and the scripted reviser calls `revise_belief` with
the probability the set gives for the instruction set in force and the expected weight for each
claim; the scripted judgment answers yes unless a suite tells it otherwise. Every reply on these
pages comes from that. [Live, against a real model](../deploy/live.md) runs the same blueprint
against the real ones.
