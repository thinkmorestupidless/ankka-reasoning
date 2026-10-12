# Data Model: The process layer

What the service holds for the process layer, and what the golden set is. Each kind of record is
one key value entity per record, as in the belief layer (spec 001 R1); none carries text a writer
supplied except an act's step and worker names and a run ending's reason, which are ankka's words,
not a person's, and are never withdrawn. What reaches the graph is in
[contracts/graph-vocabulary.md](contracts/graph-vocabulary.md); the rules' names are in
[contracts/refusals.md](contracts/refusals.md); the appraiser as data is in
[contracts/blueprint.md](contracts/blueprint.md).

## Common to every record

As spec 001's [data-model.md](../001-belief-layer/data-model.md): `id`, `dated`, `recordedAt`,
`writer`. A record is created once; the same record sent again under its identifier is that record
(`Same`), a different one is refused. Nothing below is changed after it is held. The writer of a
process-layer record is the service itself for what it copies from ankka (`service:reasoning` when
it runs in a cluster, `local` outside one) and `blueprint:<name>` for an act.

## Process layer

### Blueprint version (key value entity `blueprint-version`, id = `<blueprint>@<n>`)

| Field | Type | Notes |
|---|---|---|
| `blueprint` | identifier | ankka's blueprint name, `^[a-z0-9][a-z0-9-]*$` |
| `version` | integer | from 1 |
| `digest` | 64 hex characters | ankka's SHA-256 of the canonical blueprint |
| `dated` | instant | when ankka registered it (`registeredAt`) |

Written by `BlueprintVersions` from `BlueprintEvent.VersionRegistered`, and by a tool before it
writes a run of a version not yet held (research R7). The canonical blueprint text is not held
here: ankka holds it, and `GET /process/appraiser/versions/{n}` reads it from ankka.

### Run (key value entity `run`, id = the run id)

| Field | Type | Notes |
|---|---|---|
| `id` | identifier | the run id, 1 to 64 characters of the identifier's; the appraiser's at most 47 (R11); never starting `schedule:` |
| `blueprint` | identifier | |
| `version` | integer | the blueprint version the run uses, held before the run |
| `startedBy` | text | ankka's `startedBy`, 1 to 200 characters |
| `dated` | instant | when ankka started it (`startedAt`) |

Written by the first of a tool's first call in the run or `RunFollower` on `RunEvent.Started`, both
from ankka's run record (R7). A run with no run ending is running.

### Run ending (key value entity `run-ending`, id = the run id)

| Field | Type | Notes |
|---|---|---|
| `run` | run id | held |
| `status` | run status | `completed`, `failed` or `cancelled` |
| `reason` | text, optional | ankka's reason, up to 2,000 characters; present on `failed` and `cancelled` |
| `dated` | instant | when ankka ended it (`endedAt`); not earlier than the run's `dated` |

Written by `RunFollower` on `RunEvent.Ended`, once. ankka's `waiting-for-decision` is not an ending
and is not held: a run waiting is a run running.

### Act (key value entity `act`, id = `act:` + 56 hex characters)

| Field | Type | Notes |
|---|---|---|
| `id` | | `act:` followed by the first 56 hex characters of SHA-256 of `<run id>\n<kind>\n<record id>\n`, so one record in one run has one act id |
| `run` | run id | held |
| `step` | text | the step of the blueprint version, 1 to 200 characters |
| `worker` | text | the step's worker, 1 to 200 characters (R9) |
| `blueprint`, `version` | | the run's; the version held |
| `record` | (kind, id) | `evidence`, `claim` or `revision`, and its identifier; held |
| `holder` | holder id | held; the writer `blueprint:<name>` speaks for it |
| `dated` | instant | the record's `dated` |
| `writer` | writer | `blueprint:<name>` |

Rules decided before the record is written (R8): the run is held (written if ankka holds it), the
version is held, the step has one worker, the holder is held and spoken for. Rules decided when the
act is written: the record is held; no act for that record is held by another run (then the record
keeps its act and none is written, the tool answering with the record and the earlier act); the
same act again is a repeat.

**The window.** Between the record's write and the act's the process may die; the next call with
the same arguments writes the act, since the record is then a repeat. No call is answered and no
run ends with a record of this service's lacking its act.

### Act rows (view `act-rows` over `act`)

`ActRow(id, run, record kind, record id, holder, dated)`, queried by `run` and by `record`. Serves
`GET /process/runs/{run}/acts` and `GET /process/acts?record=`; may lag a write by a moment;
nothing is decided from it.

## The golden set

Files under `golden-set/`, read as resources by the `process` module and as files by the seed
client (R17). Every name, number and date is made up.

### `question.json`

```json
{ "id": "launch", "statement": "Will Company X launch Product Y this year?",
  "hypotheses": [ { "id": "yes", "statement": "it launches this year" },
                  { "id": "no",  "statement": "it does not launch this year" } ],
  "sources": [ { "id": "company-x-filings", "name": "Company X filings" }, … ],
  "market": { "id": "launch-market", "venue": "…", "outcomes": [ { "outcome": "YES", "hypothesis": "launch/yes" }, { "outcome": "NO", "hypothesis": "launch/no" } ], "resolutionCriteria": "…", "closesAt": "2026-12-31T00:00:00Z" } }
```

### `may.json` and `june.json` (a tranche)

```json
{ "name": "may", "month": "2026-05",
  "documents": [
    { "id": "filing", "kind": "a filing", "source": "company-x-filings", "locator": "10-Q 2026 Q1, p. 14",
      "publishedAt": "2026-05-04T09:00:00Z",
      "text": "… a few hundred words …",
      "expectedReading": { "hypothesis": "launch/yes", "stance": "contradicts",
                           "statement": "approval is pending and the launch date is uncertain",
                           "weight": { "v1": 0.3, "v2": 0.2 } } },
    …
  ] }
```

| Field | Notes |
|---|---|
| `id` | an identifier, unique within the golden set; the tool's `document` parameter |
| `kind` | the everyday name the features use: "a filing", "a regulator's notice", "a trade-press piece", "an analyst note", "a supplier's earnings call"; "approval granted", "a launch date announced", "a contradicting rumour" |
| `source` | a source id `question.json` registers |
| `locator`, `publishedAt`, `text` | the document's; `text` is what a reader is given |
| `expectedReading` | the hypothesis it bears on, the stance, a one-sentence statement a scripted reader states as its claim, and the weight each instruction set gives the claim |

The May tranche has five documents whose readings, weighed by either instruction set, put the belief
below one half; the June tranche has three whose readings put it above. The document with id
`approval` is the approval document. The scripted model's probabilities are the weighted readings:
`v1` gives `0.35` after May and `0.72` after June; `v2` gives `0.40` and `0.66`.

### The run input (the appraiser's input shape)

| Field | Type | Notes |
|---|---|---|
| `tranche` | `may` or `june` | which tranche the run reads |
| `holder` | holder id | the holder the run writes as |
| `question` | question id | `launch` |
| `follows` | revision id, optional | the belief revision the run's revision follows |
| `observedAt` | instant, optional | the date of every record the run writes; the clock when absent |

### `seed.json`

A seed file in the format of `seed/*.json`: the question, the sources and the market, for a laptop
(`just seed golden-set`). The service registers the question's sources and the appraiser's holders
itself at start, so a run on an empty service needs only the question and, for the resolution
scenario, the market.

## What is not held

- The text of a document: a run reads it from the golden set; evidence holds an excerpt.
- A step's model calls, sessions, tokens or the model's words: ankka holds them in the run.
- A score: the next feature's.
