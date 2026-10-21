# Run a blueprint

> Start a run of the appraiser over HTTP, wait for it to end, read the acts it wrote, find the act behind any record, register a second version of its instructions, and ask every answer with the acts behind it.

Source: https://reasoning.ankka.cloud/build/run-a-blueprint/
A blueprint is a piece of work for a model, held by [ankka](https://docs.ankka.cloud/build/blueprints/)
as data: workers with instructions and tools, and the steps that call them. The service carries one,
[the appraiser](the-appraiser.md), and gives it three tools that write records. A run of it is started
with one request; everything it writes is held with an act saying which run, step and worker wrote
it; and every answer can be asked with those acts beside it.

Every request and reply on this page was run against the service with the
[golden set](golden-set.md) seeded and a scripted model standing in for the real one, so the
instructions were followed to the letter. Replies are shown whole unless the page says it has cut
one down. With a real model, the records' statements and excerpts are the model's own.

## Start a run

`POST /process/runs` names the blueprint, chooses a run id, and gives the blueprint its input. The
appraiser's input is the tranche of documents to read, the holder to write as, the question, and
optionally the belief revision to follow and the date the documents are observed on.

```bash
curl -s localhost:9000/process/runs -d '{
  "blueprint": "appraiser",
  "runId": "appraiser-a-may",
  "input": {
    "tranche": "may",
    "holder": "appraiser-a",
    "question": "launch",
    "observedAt": "2026-05-31T09:00:00Z"
  }
}'
```

```json
{
  "id": "appraiser-a-may",
  "blueprint": "appraiser",
  "version": 1,
  "startedBy": "service:test/writer",
  "dated": "2026-10-21T16:14:13.004Z",
  "recordedAt": "2026-05-31T09:00:00Z",
  "writer": "service:reasoning"
}
```

The reply is the run as held, answered once ankka has accepted it: the run then goes on without the
caller. `version` is the blueprint version the run is of, always the one current when it started.
`startedBy` is the caller, as the service identifies every writer; `writer` is the service itself,
because a run is ankka's record and the service copies it. `dated` is when ankka started the run.

The same request again answers `200` with the same run and starts nothing. A run id held by a run of
a different blueprint or with a different input is refused with `409` and `run.id.held-by-another`.
A run id is at most 47 characters. The holder must be one the blueprint speaks for, which the
service registered when it started; any other is refused before the run starts.
[The process layer](../concepts/process-layer.md) says why.

## Wait for it to end

`GET /process/runs/{run}` is the run, its ending once it has one, and its acts. A run with no `ending`
is still running; the appraiser's May run ends in a second or two with a scripted model and in a few
minutes with a real one.

```bash
curl -s localhost:9000/process/runs/appraiser-a-may
```

```json
{
  "run": {
    "id": "appraiser-a-may",
    "blueprint": "appraiser",
    "version": 1,
    "startedBy": "service:test/writer",
    "dated": "2026-10-21T16:14:13.004Z",
    "recordedAt": "2026-05-31T09:00:00Z",
    "writer": "service:reasoning"
  },
  "ending": {
    "run": "appraiser-a-may",
    "status": "completed",
    "dated": "2026-10-21T16:14:14.076Z",
    "recordedAt": "2026-05-31T09:00:00Z",
    "writer": "service:reasoning"
  },
  "acts": [
    {
      "id": "act:16b7231e0a30218364347d659804fea8e3513ebc492f5d3c798b850a",
      "run": "appraiser-a-may",
      "step": "read",
      "worker": "reader",
      "blueprint": "appraiser",
      "version": 1,
      "record": {
        "kind": "evidence",
        "id": "77397b206387daf9a2eb6bb69d083d243965783a67a2d68c8317ec25a8adfa9c"
      },
      "holder": "appraiser-a",
      "dated": "2026-05-31T09:00:00Z",
      "recordedAt": "2026-05-31T09:00:00Z",
      "writer": "blueprint:appraiser"
    }
  ]
}
```

The reply is cut to one act; the May run has eleven, one for each of the five pieces of evidence and
five claims the reader wrote and one for the belief revision the reviser wrote. `status` is
`completed` or `failed`, and a failed run has a `reason`: the claim its critique rejected, or the
model key it could not find ([Live, against a real model](../deploy/live.md) shows one). The acts
are listed from a view that may lag a write by a moment, so a run read the instant it ended may
show fewer than it wrote; read it again.

The June run follows the May run's revision:

```bash
curl -s localhost:9000/process/runs -d '{
  "blueprint": "appraiser",
  "runId": "appraiser-a-june",
  "input": {
    "tranche": "june",
    "holder": "appraiser-a",
    "question": "launch",
    "follows": "appraiser-a-may.yes",
    "observedAt": "2026-06-30T09:00:00Z"
  }
}'
```

A belief revision a run writes is named `<run id>.<hypothesis id>`, so the May run's revision is
`appraiser-a-may.yes`, and a claim `<run id>.<16 hexadecimal characters of a digest of its statement>`.
Evidence has the identifier evidence always has, a digest of its source, locator and excerpt.

## The acts of a run

`GET /process/runs/{run}/acts` is the acts alone. Each names the run, the step and the worker, the
record it wrote by kind and identifier, and the holder the record was stated as. The June run
wrote seven records:

```bash
curl -s localhost:9000/process/runs/appraiser-a-june/acts
```

```json
{
  "acts": [
    {
      "id": "act:04151cf95eb382e6ca200056420707b6b552709e0c93f59b273bc3d2",
      "run": "appraiser-a-june",
      "step": "read",
      "worker": "reader",
      "blueprint": "appraiser",
      "version": 1,
      "record": {
        "kind": "claim",
        "id": "appraiser-a-june.91a8bcc9c6aad68f"
      },
      "holder": "appraiser-a",
      "dated": "2026-06-30T09:00:00Z",
      "recordedAt": "2026-06-30T09:00:00Z",
      "writer": "blueprint:appraiser"
    },
    {
      "id": "act:2bc75384a53066c9b21bede5718663bae9dbe01e96d8c7d9a6258231",
      "run": "appraiser-a-june",
      "step": "revise",
      "worker": "reviser",
      "blueprint": "appraiser",
      "version": 1,
      "record": {
        "kind": "revision",
        "id": "appraiser-a-june.yes"
      },
      "holder": "appraiser-a",
      "dated": "2026-06-30T09:00:00Z",
      "recordedAt": "2026-06-30T09:00:00Z",
      "writer": "blueprint:appraiser"
    }
  ]
}
```

Cut to two of the seven. An act is dated as the record it names: here the input's `observedAt`. Its
writer is the blueprint, `blueprint:appraiser`, a writer no caller can be.

## The act behind a record

Any piece of evidence, claim or belief revision can be asked for its act, by kind and identifier.
A record a person or a script wrote has none, and the reply is then `{}`.

```bash
curl -s 'localhost:9000/process/acts?record=claim:appraiser-a-june.91a8bcc9c6aad68f'
```

```json
{
  "act": {
    "id": "act:04151cf95eb382e6ca200056420707b6b552709e0c93f59b273bc3d2",
    "run": "appraiser-a-june",
    "step": "read",
    "worker": "reader",
    "blueprint": "appraiser",
    "version": 1,
    "record": {
      "kind": "claim",
      "id": "appraiser-a-june.91a8bcc9c6aad68f"
    },
    "holder": "appraiser-a",
    "dated": "2026-06-30T09:00:00Z",
    "recordedAt": "2026-06-30T09:00:00Z",
    "writer": "blueprint:appraiser"
  }
}
```

`record` is `evidence:<id>`, `claim:<id>` or `revision:<id>`; anything else is refused with `400`
and `record.format`. An act is also read by its own identifier, `GET /process/acts/{act}`.

A record has one act, the first run's. The second holder's May run read the same five documents,
quoted the same passages, and found the evidence already held: its acts are its five claims and its
revision, and the evidence keeps the first run's act.

## The three tools

A run writes nothing directly. Its workers call tools the service gives the blueprint, and each
tool writes a record exactly as the route on [Record reasoning](record-reasoning.md) would, under
the same rules, and then the record's act. A tool answers the model only once both are held.

| Tool | Takes | Writes |
|---|---|---|
| `record_evidence` | `document`, a document of the run's tranche; `excerpt`, a passage of its text; `author` optionally | evidence from the document's source, locator and publication date, observed at the input's `observedAt`; then its act |
| `state_claim` | `statement`; `derivesFrom`, evidence identifiers; `stances`, each `<question>/<hypothesis> supports` or `… contradicts` | a claim stated by the run's holder; then its act |
| `revise_belief` | `hypothesis`; `probability`; `restsOn`, each `<claim id>` or `<claim id>@<weight>` | a belief revision of the run's holder, following the input's `follows`; then its act |

A tool reads the run it is called in, so the holder, the writer and the version are never
parameters. A refusal is handed to the model as the tool's error, with the rule and the names, and
the appraiser's workers are told to stop there. Called outside any run, a tool writes nothing:

```json
{"rule":"tool.called-in-no-run","status":422,"error":"The tool 'record_evidence' writes only in a run of a blueprint.","names":{"tool":"record_evidence"}}
```

## Versions

The appraiser carries two sets of instructions, `v1` and `v2`, and starts at version 1 with the
first. Registering the second makes version 2, and every run started from then on is of it.

```bash
curl -s localhost:9000/process/appraiser/versions -d '{"instructions":"v2"}'
```

```json
{
  "blueprint": "appraiser",
  "version": 2,
  "dated": "2026-10-21T16:14:20.747Z"
}
```

The same request again answers `200` with the version held; an instruction set the service does not
carry is refused with `422` and `appraiser.instructions.unknown`. The versions are listed with the
digest ankka gave each:

```bash
curl -s localhost:9000/process/appraiser/versions
```

```json
{
  "versions": [
    {
      "blueprint": "appraiser",
      "version": 1,
      "digest": "da34f63bf300a554253b010258ba3f9a1c2a3c54bfb8ef8c24afa10aeee435a0",
      "dated": "2026-10-21T16:14:11.977Z"
    },
    {
      "blueprint": "appraiser",
      "version": 2,
      "digest": "3ca364aede02959895cb05003b77900cb4478af32c79546548793a7188a31755",
      "dated": "2026-10-21T16:14:20.747Z"
    }
  ]
}
```

`GET /process/appraiser/versions/{n}` is one version with the whole blueprint as ankka holds it,
input shape, steps and workers with their instructions; [The appraiser](the-appraiser.md) shows it.
The version's record is copied from ankka's event and arrives a moment after the registration
answers, so a read straight after may be `404` once.

## Answers with the acts behind them

Every answer on [Ask for explanations](ask-for-explanations.md) is served again under `/process`,
with the same parameters, `asOf` and `asRecordedBy` included. The reply is `{answer, acts}`:
`answer` is the lower route's reply, byte for byte, and `acts` has one entry for every record in
it that has an act, with the act, its run, the run's ending and the blueprint version.

```bash
curl -s 'localhost:9000/process/answers/belief-change?from=appraiser-a-may.yes&to=appraiser-a-june.yes'
```

```json
{
  "answer": {
    "from": {"id": "appraiser-a-may.yes", "holder": "appraiser-a", "hypothesis": "launch/yes", "n": 1, "probability": 0.35, "dated": "2026-05-31T09:00:00Z", "recordedAt": "2026-05-31T09:00:00Z"},
    "to": {"id": "appraiser-a-june.yes", "holder": "appraiser-a", "hypothesis": "launch/yes", "n": 2, "probability": 0.72, "follows": "appraiser-a-may.yes", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z"},
    "newlyRestedOn": [
      {
        "claim": {"id": "appraiser-a-june.ea9417e0bda58d7d", "holder": "appraiser-a", "statement": "the regulator has approved Product Y for sale", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z", "withdrawn": false},
        "weight": 0.3,
        "evidence": [
          {
            "id": "f6662d3eaf783467d4b1c7352becc74afa91bd73feea684f180f9cc2d3b5e4a8",
            "source": {"id": "regulator", "name": "the regulator", "dated": "2026-05-02T10:00:00Z", "recordedAt": "2026-05-02T10:00:00Z"},
            "locator": "Notice 1187 of 2026",
            "excerpt": "Office of Product Conformity, public notice 1187 of 2026. The Office has",
            "publishedAt": "2026-06-09T10:00:00Z",
            "dated": "2026-06-30T09:00:00Z",
            "recordedAt": "2026-06-30T09:00:00Z",
            "withdrawn": false
          }
        ],
        "revisedBy": [],
        "heldBy": ["appraiser-a"]
      }
    ],
    "noLongerRestedOn": [
      {
        "claim": {"id": "appraiser-a-may.3818ab87ee2f95ef", "holder": "appraiser-a", "statement": "the regulator's decision may take until late October", "dated": "2026-05-31T09:00:00Z", "recordedAt": "2026-05-31T09:00:00Z", "withdrawn": false},
        "weight": 0.25,
        "evidence": [
          {"id": "71d309504908472ad82e17947f0a42ebbf0a542a4673172ea56d389280f5c3bb", "source": {"id": "regulator", "name": "the regulator", "dated": "2026-05-02T10:00:00Z", "recordedAt": "2026-05-02T10:00:00Z"}, "locator": "Notice 1142 of 2026", "excerpt": "Office of Product Conformity, public notice 1142 of 2026. The Office has", "publishedAt": "2026-05-08T10:00:00Z", "dated": "2026-05-31T09:00:00Z", "recordedAt": "2026-05-31T09:00:00Z", "withdrawn": false}
        ],
        "revisedBy": [],
        "heldBy": []
      }
    ],
    "stillRestedOn": [],
    "observedBetween": [
      {"id": "51f5d2eff2e9672edfd0874e3c121fb24bf59e1b83f7cc580c7e5d5bcfa79d7e", "source": {"id": "rumour-mill", "name": "the rumour mill", "dated": "2026-05-02T10:00:00Z", "recordedAt": "2026-05-02T10:00:00Z"}, "locator": "Device Rumours forum, thread 88213, 20 June 2026", "excerpt": "Posted by tarnow_insider on the Device Rumours forum, thread 88213. I work", "publishedAt": "2026-06-20T22:00:00Z", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z", "withdrawn": false}
    ]
  },
  "acts": [
    {
      "record": {"kind": "claim", "id": "appraiser-a-june.ea9417e0bda58d7d"},
      "act": {"id": "act:a00ee49cb7fc12fc2f76c05e27c66e0da20eeccdeac4984e7d150317", "step": "read", "worker": "reader", "recordKind": "claim", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z"},
      "run": {"id": "appraiser-a-june", "blueprint": "appraiser", "version": 1, "startedBy": "service:test/writer", "dated": "2026-10-21T16:14:15.404Z", "recordedAt": "2026-06-30T09:00:00Z"},
      "runEnding": {"run": "appraiser-a-june", "status": "completed", "dated": "2026-10-21T16:14:16.136Z", "recordedAt": "2026-06-30T09:00:00Z"},
      "blueprintVersion": {"id": "appraiser@1", "blueprint": "appraiser", "version": 1, "digest": "da34f63bf300a554253b010258ba3f9a1c2a3c54bfb8ef8c24afa10aeee435a0", "dated": "2026-10-21T16:14:11.977Z", "recordedAt": "2026-05-31T09:00:00Z"}
    }
  ]
}
```

Cut down to one entry in each list: the whole reply has three claims newly rested on, the five May
claims no longer rested on, the three June pieces of evidence observed between the two revisions,
and eighteen acts, one for each of the eight claims, eight pieces of evidence and two revisions in
it.
[The golden set](golden-set.md) reads the whole of it. An act is shown for every record that counts
at the time asked about, whenever the act itself was recorded: it is the record's provenance,
shown as a source is shown beside its evidence.

One answer is the process layer's own, what a run wrote, read from the graph:

```bash
curl -s 'localhost:9000/process/answers/run?run=appraiser-a-june'
```

```json
{
  "run": {"id": "appraiser-a-june", "blueprint": "appraiser", "version": 1, "startedBy": "service:test/writer", "dated": "2026-10-21T16:14:15.404Z", "recordedAt": "2026-06-30T09:00:00Z"},
  "ending": {"run": "appraiser-a-june", "status": "completed", "dated": "2026-10-21T16:14:16.136Z", "recordedAt": "2026-06-30T09:00:00Z"},
  "wrote": [
    {
      "act": {"id": "act:f3eb83b3f3a640ee12da65454d8333d2b1a619836128ab641224e350", "step": "read", "worker": "reader", "recordKind": "claim", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z"},
      "record": {"kind": "claim", "id": "appraiser-a-june.405f185903659588"}
    },
    {
      "act": {"id": "act:2bc75384a53066c9b21bede5718663bae9dbe01e96d8c7d9a6258231", "step": "revise", "worker": "reviser", "recordKind": "revision", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z"},
      "record": {"kind": "revision", "id": "appraiser-a-june.yes"}
    }
  ]
}
```

Cut to two of seven. A run the graph does not hold is `404` with `record.not-held`. Like every
answer, these read the graph database and answer `503` when it cannot be reached.

## Wait for the graph

A run's records reach the graph a few seconds after it writes them, and so do its acts, its run and
its ending, each as a record of its own. `POST /graph/wait` takes the four new kinds, `run`,
`run-ending` and `act` by their identifiers and `blueprint-version` by `<blueprint>@<n>`, as well
as the record a run wrote:

```bash
curl -s localhost:9000/graph/wait -d '{"kind":"revision","id":"appraiser-a-june.yes","limitMs":30000}'
```

```json
{
  "caughtUp": true,
  "waitedMs": 2698,
  "missing": []
}
```

A wait for a record waits for that record; its act is waited for by its own key. Once the graph has
caught up, [Read the graph](read-the-graph.md) traces a revision to the run and version that wrote
it in one query.

## Refusals

| Rule | Status | When |
|---|---|---|
| `blueprint.not-held` | 404 | the service carries no blueprint of that name |
| `run.id.too-long` | 422 | the run id is over 47 characters, or starts `schedule:` |
| `run.id.held-by-another` | 409 | the run id is held by a run of a different blueprint or with a different input |
| `run.input.invalid` | 422 | the input does not fit the blueprint's input shape; `names.field` says which field |
| `holder.writer.does-not-speak` | 403 | the blueprint does not speak for the input's holder |
| `record.not-held` | 404 | the holder is not held; or a run, an act or a version that is not held is read |
| `appraiser.instructions.unknown` | 422 | `instructions` is not `v1` or `v2` |
| `record.format` | 400 | `record` is not `evidence:<id>`, `claim:<id>` or `revision:<id>` |

A tool is refused under every rule its record can break, exactly as a writer is, and under a few
of its own; [Refusals](../reference/refusals.md) lists them all. A run that fails is not a refusal:
it is held, with its ending.
