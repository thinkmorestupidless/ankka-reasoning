# Contract: the appraiser and its tools

The blueprint the service carries, as data, and the tools any blueprint of this service may name.
Research R9 to R13 say why each is shaped so. ankka's words for a blueprint's parts are in its
blueprints guide; this page uses them.

## The tools

Registered on the service's `BlueprintRegistry`, each wrapped by ankka so that `RunContext.current`
names the run, step, blueprint and version it is called in. Each writes the record through the
belief layer's `Recorder` as the writer `blueprint:<blueprint name>`, as the holder the run's input
names, and then its act. A call outside a run writes nothing and answers
`tool.called-in-no-run`.

| Tool | Parameters | Writes | Answers |
|---|---|---|---|
| `record_evidence` | `document` (a document id of the run's tranche), `excerpt` (1 to 2,000 characters of the document's text), `author?` | evidence from the document's source, locator and `publishedAt`, dated by `input.observedAt` or the clock; then its act | `{evidence, act}` as held; when the evidence was held before, the evidence and its earlier act |
| `state_claim` | `statement`, `derivesFrom: [evidence id]`, `stances: [string]` each `<question>/<hypothesis> supports` or `… contradicts` | a claim, id `<run id>.<16 hex of SHA-256 of statement>`, stated by the holder; then its act | `{claim, act}` |
| `revise_belief` | `hypothesis` (`<question>/<hypothesis>`), `probability`, `restsOn: [string]` each `<claim id>` or `<claim id>@<weight>` | a belief revision, id `<run id>.<hypothesis id>`, following `input.follows`; then its act | `{revision, act}` |

A refusal (any rule of spec 001's refusals, or one of this feature's) is the tool's error:
`{rule, error, names}`. The model is told it and goes on; the appraiser's workers are instructed to
stop and answer with the refusal (research R12).

A document id the tranche does not hold is `document.not-in-tranche`. A stance or rest the tool
cannot parse is `claim.stance.unknown` or `belief.rest.format`.

## The appraiser

```scala
Blueprint("appraiser")
  .input(Shape.obj(
    "tranche"    -> Shape.string,          // "may" or "june"
    "holder"     -> Shape.string,
    "question"   -> Shape.string,
    "follows"    -> Shape.string.optional,
    "observedAt" -> Shape.string.optional))
  .worker(Worker("reader").instructions(instructions.reader).tools("record_evidence", "state_claim").budget(6))
  .worker(Worker("reviser").instructions(instructions.reviser).tools("revise_belief").budget(4))
  .step(Step("load").call("load-tranche").reads("input").result(Shape.list(document)))
  .step(Step("read").forEach("reader", "load").result(Shape.obj("document" -> Shape.string, "evidence" -> Shape.string, "claim" -> Shape.string)))
  .step(Step("critique").judge("claim-derives-from-tranche").each("read"))
  .step(Step("verdict").call("critique-verdict").reads("critique", "read"))
  .step(Step("revise").ask("reviser").reads("verdict", "read", "input").result(Shape.obj("revision" -> Shape.string)))
```

| Part | What it is |
|---|---|
| `load-tranche` (handler) | reads the golden set's tranche named by `input.tranche` and returns its documents: `{id, kind, source, locator, publishedAt, text}` each. Unknown tranche: the run fails `load: the handler 'load-tranche' failed: no tranche <name>` |
| `reader` (worker) | given one document, records evidence (an excerpt of the text) and states one claim with the stance the document bears, then answers `{document, evidence, claim}`; told to stop and answer `{document, refused: {rule, names}}` on a refusal |
| `claim-derives-from-tranche` (yes/no judgment) | instructed with FR-010's two checks; its state per item is the claim (statement, stances), its evidence (source, locator, excerpt, document) and the tranche's documents |
| `critique-verdict` (handler) | fails the run on the first item whose judgment answered `no` (`claim <id> does not derive from evidence recorded from a document of the tranche`) or whose reader item carries a refusal (`the reader was refused: <rule>, naming <names>`); otherwise returns `{passed: true, claims: [id]}` |
| `reviser` (worker) | given the passed claims, the readers' items and the input, revises the holder's belief in the question's `yes` hypothesis, resting on the claims with the weights its instructions say, following `input.follows`; answers `{revision}` |

Instructions `v1` and `v2` differ only in the reviser's: `v1` weighs every document by its
kind and treats a rumour as weak; `v2` weighs an official document (a filing, a notice, an approval)
double and a rumour as nothing. Both make the May tranche's belief fall below one half and the June
tranche's rise above it. The sets are code (`Instructions.scala`), so the two versions' digests are
fixed by the build.

Rules ankka checks at registration: names, budgets above zero, tools that exist, reads that
resolve. The service fails to start if the appraiser does not register.

## A run, start to end

1. `POST /process/runs {blueprint: "appraiser", runId: "appraiser-a-may", input: {tranche: "may", holder: "appraiser-a", question: "launch"}}`.
2. `load` returns five documents. `read` runs the reader five times; each item records evidence,
   states a claim, and answers. Ten records, ten acts, each act's `step` is `read` and `worker`
   `reader`.
3. `critique` asks the judgment five times. `verdict` passes.
4. `revise` revises the belief: one record, one act with `step` `revise` and `worker` `reviser`.
5. ankka ends the run `completed`; `RunFollower` holds the run ending. The June run names
   `follows: "appraiser-a-may.yes"`.

## What a scripted model does

For each `read` item it calls `record_evidence(document, <the document's first sentence>)`, then
`state_claim(<expected reading's statement>, [<the evidence id the tool answered>], ["launch/yes <stance>"])`,
then answers. For `revise` it calls `revise_belief("launch/yes", <the tranche's probability for the
instruction set>, [<claim>@<weight> …])` and answers. The judgment answers `yes` at `0.95` unless
scripted otherwise.
