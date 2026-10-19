# Contract: refusals, process layer

What this feature adds to spec 001's [refusals.md](../../001-belief-layer/contracts/refusals.md).
The body, F1 to F3 and the statuses are the same. A tool's refusal is the same body, handed to the
model as the tool's error instead of over HTTP.

**Runs and versions**

| Rule | Status | When |
|---|---|---|
| `blueprint.not-held` | 404 | a run is started of a blueprint the service does not carry |
| `run.id.too-long` | 422 | a run id of the appraiser is over 47 characters, or starts `schedule:`; `names.limit` |
| `run.id.held-by-another` | 409 | the run id is held by a run of a different blueprint or with a different input |
| `run.input.invalid` | 422 | the input does not fit the blueprint's input shape; `names.field` |
| `run.not-held` | 422 | a run ending or an act names a run that is not held (the service's own consumers; should not occur) |
| `run-ending.dated.before-run` | 422 | a run ending is dated before its run (the service's own consumers; should not occur) |
| `blueprint-version.not-held` | 422 | a run names a version that is not held (should not occur) |
| `appraiser.instructions.unknown` | 422 | `instructions` is not one the service carries |

**Acts and tools**

| Rule | Status | When |
|---|---|---|
| `tool.called-in-no-run` | 422 | a tool is called with no `RunContext` |
| `act.worker.unknown` | 422 | the step the tool is called in has no single worker |
| `act.record.not-held` | 422 | an act names a record that is not held (should not occur) |
| `act.held-by-another-run` | — | not a refusal: the record keeps its earlier act and the tool answers with it |
| `document.not-in-tranche` | 422 | `record_evidence` names a document the run's tranche does not hold |
| `belief.rest.format` | 422 | a rest is not `<claim>` or `<claim>@<weight>` |

A tool is also refused under every rule of spec 001 its record can break: `holder.writer.does-not-speak`
(the blueprint does not speak for the input's holder), `claim.derives-from.not-held`,
`claim.stance.unknown`, `belief.dated.not-before-claim`, `belief.rests-on.other-holder` and so on,
exactly as a writer is.

**Answers**

| Rule | Status | When |
|---|---|---|
| `record.not-held` | 404 | `/process/answers/run` names a run not in the graph |

**A failed run** is not a refusal: the run is held with its ending, `status` `failed` and a
`reason`. The reasons the appraiser produces are in [blueprint.md](blueprint.md).
