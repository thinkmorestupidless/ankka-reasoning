# Contract: the HTTP API, process layer

What this feature adds to spec 001's [http-api.md](../../001-belief-layer/contracts/http-api.md).
Rules H1 to H7 there hold here. No route of the belief layer or the market layer changes. The
rules' names are in [refusals.md](refusals.md); the records' fields in
[../data-model.md](../data-model.md).

## Runs and versions

| Route | Body | Reply |
|---|---|---|
| `POST /process/runs` | `{blueprint, runId, input}` | `201` the run as held `{id, blueprint, version, startedBy, dated, recordedAt, writer}`; `200` the same run when `runId` is held with the same blueprint and input |
| `GET /process/runs/{run}` | | `{run, ending?, acts: [act]}`: the run, its ending once it has one, and its acts from the act rows (may lag) |
| `GET /process/runs/{run}/acts` | | `{acts: [act]}` |
| `GET /process/acts/{act}` | | the act |
| `GET /process/acts?record=<kind>:<id>` | | `{act?}`: the act of a record, from the act rows |
| `POST /process/appraiser/versions` | `{instructions}` | `201` `{blueprint: "appraiser", version, dated}` when a new version was registered; `200` with the version held when the instructions are those of the current version |
| `GET /process/appraiser/versions` | | `{versions: [{blueprint, version, digest, dated}]}` |
| `GET /process/appraiser/versions/{n}` | | the blueprint version, with ankka's canonical blueprint as `blueprint` |

- **P1.** `POST /process/runs` answers once ankka has accepted the run; the run then goes on. The
  reply's `dated` is when ankka started it. The caller waits for it to end with `GET /process/runs/{run}`
  (`ending` present) or for its records with `POST /graph/wait`.
- **P2.** `blueprint` is `appraiser`; another name is `404 blueprint.not-held`. `runId` is an
  identifier of at most 47 characters not starting `schedule:`; `input` is checked against the
  appraiser's input shape (`422 run.input.invalid`, naming the field) and its `holder` must be held
  and spoken for by the blueprint (`403 holder.writer.does-not-speak`, before the run starts).
- **P3.** A run id held with a different blueprint or input is `409 run.id.held-by-another`.
- **P4.** `instructions` is `v1` or `v2`, the two instruction sets the service carries
  ([blueprint.md](blueprint.md)); anything else is `422 appraiser.instructions.unknown`.
- **P5.** A run that ends `failed` says why in `ending.reason`, which for a failed critique names
  the claim.

## Answers with acts (graph)

Each is the lower route again under `/process`, with the same parameters, `asOf` and
`asRecordedBy` included, answering `{answer, acts}`: `answer` is the lower route's reply, unchanged;
`acts` is `[{record: {kind, id}, act, run, runEnding?, blueprintVersion}]`, one entry for every
record in the answer that has an act. `503` as H5.

| Route | `answer` is |
|---|---|
| `GET /process/answers/belief-change?from=&to=` | `GET /answers/belief-change` |
| `GET /process/answers/belief?holder=&hypothesis=` | `GET /answers/belief` |
| `GET /process/answers/case?hypothesis=&stance=` | `GET /answers/case` |
| `GET /process/answers/learned?question=&after=` | `GET /answers/learned` |
| `GET /process/answers/comparison?hypothesis=&a=&b=` | `GET /answers/comparison` |
| `GET /process/answers/resting-on-revised?question=` | `GET /answers/resting-on-revised` |
| `GET /process/answers/run?run=` | `{run, ending?, wrote: [{act, record}]}`: everything the run wrote, from the graph (FR-007) |
| `GET /process/markets/{market}/resolution` | `GET /markets/{market}/resolution` |
| `GET /process/markets/{market}/beliefs-at-resolution` | `GET /markets/{market}/beliefs-at-resolution` |

- **P6.** An act is shown for a record that counts at the time asked about, whenever the act, the
  run and the version were recorded themselves: they are the record's provenance, shown as a source
  is shown beside evidence (spec 001 F13).
- **P7.** `/process/answers/run` is refused `404 record.not-held` when no such run is in the graph.

## The graph

`POST /graph/wait` takes four more kinds: `blueprint-version` (id `<blueprint>@<n>`), `run`,
`run-ending` (both the run id) and `act`. A wait for a record a run wrote still waits for that
record; its act is waited for by its own key. The scenario *a writer waits for the graph to hold a
record and its act* waits for both.

`GET /graph/vocabulary` gains the layer `process`.

## Tools

The three tools a blueprint names are not routes; they are in [blueprint.md](blueprint.md). Each
writes exactly what the lower route writes and is refused exactly as it is, the refusal handed to
the model as the tool's error, with `rule` and `names`.

## Properties a test holds

- Every scenario under `features/process/` reaches the service through these routes, spec 001's,
  and a run's tools (or a tool's handler called outside any run), and no other way.
- For `POST /process/runs` and `POST /process/appraiser/versions`, the same body twice gives `201`
  then `200` with equal bodies.
- For every `/process/answers/…` route, `answer` equals the lower route's reply for the same
  parameters, byte for byte.
- With the graph database stopped, every route outside this page's "graph" section answers as it
  does with it running.
