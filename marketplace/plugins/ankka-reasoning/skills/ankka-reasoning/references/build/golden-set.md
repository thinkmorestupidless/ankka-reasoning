# The golden set

> The eight documents the appraiser reads, two tranches a month apart, with the reading expected of each and the probabilities the two instruction sets arrive at, and the four answers the four runs give.

Source: https://reasoning.ankka.cloud/build/golden-set/
The golden set is a small, fixed body of documents the appraiser reads, with the reading expected
of each. It is how the appraiser is proved: a run over it with a scripted model must produce exactly
the records the set says, and a run with a real model must move the belief the way the set says.
Every name, number and date in it is made up.

The question is the one the launch example asks, "Will Company X launch Product Y this year?", with
the hypotheses `launch/yes` and `launch/no`, six sources, and a market, `launch-market`, that
offers `YES` and `NO` on the two.

## The files

The set is four JSON files in the repository's `golden-set/` directory, packaged with the service:

| File | Holds |
|---|---|
| `question.json` | the question with its hypotheses, the six sources, and the market |
| `seed.json` | the same as a [seed file](seed-files.md), for `just seed golden-set`: the question and the market; the sources are registered by the service itself when it starts |
| `may.json` | the May tranche: five documents, each with the reading expected of it and the weight each instruction set is expected to give it, and the probability each set is expected to arrive at |
| `june.json` | the June tranche: three documents, likewise |

A document has an `id`, a `kind`, a `source`, a `locator`, a `publishedAt` and its `text`, the
whole passage the reader is given. Its `expectedReading` is the stance a reader should take on
`launch/yes`, a statement of what it shows, and the weight each instruction set should give the
claim. The set can be swapped: `REASONING_GOLDEN_SET` names a directory whose `question.json` and
tranche files win over the packaged ones, to run the appraiser over other documents in the same
shape.

## The documents

| Tranche | Document | Kind | Source | Locator | Published | Expected stance on `launch/yes` | Expected statement | Weight, v1 | Weight, v2 |
|---|---|---|---|---|---|---|---|---|---|
| May | `filing` | a filing | `company-x-filings` | 10-Q 2026 Q1, p. 14 | 2026-05-04 | contradicts | approval is pending and the launch date is uncertain | 0.25 | 0.3 |
| May | `notice` | a regulator's notice | `regulator` | Notice 1142 of 2026 | 2026-05-08 | contradicts | the regulator's decision may take until late October | 0.25 | 0.3 |
| May | `trade-press` | a trade-press piece | `trade-press` | Devices Weekly, 12 May 2026, Launch watch | 2026-05-12 | supports | distributors are being briefed for an autumn launch | 0.2 | 0.3 |
| May | `analyst` | an analyst note | `analyst-notes` | Halvard Research, Company X initiation, 15 May 2026, p. 3 | 2026-05-15 | contradicts | an analyst models the launch next year, not this year | 0.15 | 0.1 |
| May | `supplier-call` | a supplier's earnings call | `supplier-calls` | Orrin Components Q1 2026 earnings call, transcript, question 7 | 2026-05-19 | supports | the sensor supplier will be at volume by the third quarter | 0.15 | 0.2 |
| June | `approval` | approval granted | `regulator` | Notice 1187 of 2026 | 2026-06-09 | supports | the regulator has approved Product Y for sale | 0.3 | 0.2 |
| June | `launch-date` | a launch date announced | `company-x-filings` | Press release, 16 June 2026 | 2026-06-16 | supports | Company X has set a launch date of 5 October | 0.24 | 0.12 |
| June | `rumour` | a contradicting rumour | `rumour-mill` | Device Rumours forum, thread 88213, 20 June 2026 | 2026-06-20 | contradicts | a forum rumour says a sensor batch failed inspection and the date may slip | 0.1 | 0.0 |

The two tranches tell one story. In May the dossier is filed but approval is pending, a regulator's
notice gives it until late October, an analyst models the launch next year, the trade press hears
of autumn plans and a supplier expects volume by the third quarter: below one half. In June the
regulator approves, the company announces 5 October, and a forum rumour says a sensor batch failed:
above one half. The rumour was published on 20 June at 22:00, so a June run observed before then
cannot record it: evidence observed before it was published is refused. The runs below observe the
May tranche on 31 May and the June tranche on 30 June.

## The two instruction sets, and where they differ

The appraiser's two instruction sets differ in one paragraph, what the reviser is told about
weighing. Version 1 weighs every document by its kind and lets a rumour count, weakly. Version 2
weighs an official document double and a rumour as nothing. The set gives the probability each
arrives at:

| Tranche | Version 1 | Version 2 |
|---|---|---|
| May | 0.35 | 0.4 |
| June | 0.72 | 0.66 |

Both fall below one half in May and rise above it in June; the difference between them is the
rumour and the official documents. [The appraiser](the-appraiser.md) has the instructions in full.

## Run it

With the service running on a laptop and the stack beside it, as
[Run it on your machine](../get-started/run-locally.md) starts them:

```bash
just seed golden-set
```

```text
201 /questions                   
200 /sources                     
200 /sources                     
200 /sources                     
200 /sources                     
200 /sources                     
200 /sources                     
201 /markets                     
8 sent, 2 new, 6 already held
```

The sources answer `200` because the service registered them when it started, as it registered the
holders `appraiser-a` and `appraiser-b` the blueprint speaks for. Then the four runs, two holders
over two tranches, with the second holder's runs at version 2:

```bash
curl -s localhost:9000/process/runs -d '{"blueprint":"appraiser","runId":"appraiser-a-may","input":{"tranche":"may","holder":"appraiser-a","question":"launch","observedAt":"2026-05-31T09:00:00Z"}}'
curl -s localhost:9000/process/runs -d '{"blueprint":"appraiser","runId":"appraiser-a-june","input":{"tranche":"june","holder":"appraiser-a","question":"launch","follows":"appraiser-a-may.yes","observedAt":"2026-06-30T09:00:00Z"}}'
curl -s localhost:9000/process/appraiser/versions -d '{"instructions":"v2"}'
curl -s localhost:9000/process/runs -d '{"blueprint":"appraiser","runId":"appraiser-b-may","input":{"tranche":"may","holder":"appraiser-b","question":"launch","observedAt":"2026-05-31T09:00:00Z"}}'
curl -s localhost:9000/process/runs -d '{"blueprint":"appraiser","runId":"appraiser-b-june","input":{"tranche":"june","holder":"appraiser-b","question":"launch","follows":"appraiser-b-may.yes","observedAt":"2026-06-30T09:00:00Z"}}'
```

Each run is waited for with `GET /process/runs/{run}` until it has an `ending`, as
[Run a blueprint](run-a-blueprint.md) shows, and the last revision with `POST /graph/wait` before
the answers are asked. A run needs a model: [Live, against a real model](../deploy/live.md) says
which keys, and what a run does without them.

## The four answers

The replies below are the service's, for the four runs with a scripted model that follows the
instructions to the letter and reads exactly what the set expects. With a real model the
statements, excerpts and weights are the model's own, and the four answers hold in direction: below
one half then above it, the approval's claim newly rested on, two holders apart by their
instructions, both right at resolution.

**Why the belief changed.** The first holder's belief went from 0.35 to 0.72. Asked why, the
service answers with the three June claims newly rested on, the five May claims no longer rested
on, the three June documents observed between the two revisions, and an act for every one of the
eighteen records, naming the run and the version. The reply is on
[Run a blueprint](run-a-blueprint.md), cut down; its shape is the one
[Ask for explanations](ask-for-explanations.md) describes.

```bash
curl -s 'localhost:9000/process/answers/belief-change?from=appraiser-a-may.yes&to=appraiser-a-june.yes'
```

**Where the two holders differ.** The two holders read the same documents and quoted the same
passages, so they share every piece of evidence; but a claim is one holder's, so each belief rests
on claims of its own and nothing is under `both`. The difference is the instructions': version 1
gave the rumour a weight of 0.1 and version 2 gave it nought, and the two revisions are 0.06
apart. The acts show version 1 behind one holder's revision and version 2 behind the other's.

```bash
curl -s 'localhost:9000/process/answers/comparison?hypothesis=launch/yes&a=appraiser-a&b=appraiser-b'
```

```json
{
  "answer": {
    "a": {"holder": "appraiser-a", "revision": {"id": "appraiser-a-june.yes", "holder": "appraiser-a", "hypothesis": "launch/yes", "n": 2, "probability": 0.72, "follows": "appraiser-a-may.yes", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z"}, "statesNoReasons": false},
    "b": {"holder": "appraiser-b", "revision": {"id": "appraiser-b-june.yes", "holder": "appraiser-b", "hypothesis": "launch/yes", "n": 2, "probability": 0.66, "follows": "appraiser-b-may.yes", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z"}, "statesNoReasons": false},
    "difference": 0.06,
    "both": [],
    "onlyA": [
      {
        "claim": {"id": "appraiser-a-june.405f185903659588", "holder": "appraiser-a", "statement": "a forum rumour says a sensor batch failed inspection and the date may slip", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z", "withdrawn": false},
        "weight": 0.1,
        "evidence": [{"id": "51f5d2eff2e9672edfd0874e3c121fb24bf59e1b83f7cc580c7e5d5bcfa79d7e", "source": {"id": "rumour-mill", "name": "the rumour mill", "dated": "2026-05-02T10:00:00Z", "recordedAt": "2026-05-02T10:00:00Z"}, "locator": "Device Rumours forum, thread 88213, 20 June 2026", "excerpt": "Posted by tarnow_insider on the Device Rumours forum, thread 88213. I work", "publishedAt": "2026-06-20T22:00:00Z", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z", "withdrawn": false}],
        "revisedBy": [],
        "heldBy": ["appraiser-a"]
      }
    ],
    "onlyB": [
      {
        "claim": {"id": "appraiser-b-june.405f185903659588", "holder": "appraiser-b", "statement": "a forum rumour says a sensor batch failed inspection and the date may slip", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z", "withdrawn": false},
        "weight": 0,
        "evidence": [{"id": "51f5d2eff2e9672edfd0874e3c121fb24bf59e1b83f7cc580c7e5d5bcfa79d7e", "source": {"id": "rumour-mill", "name": "the rumour mill", "dated": "2026-05-02T10:00:00Z", "recordedAt": "2026-05-02T10:00:00Z"}, "locator": "Device Rumours forum, thread 88213, 20 June 2026", "excerpt": "Posted by tarnow_insider on the Device Rumours forum, thread 88213. I work", "publishedAt": "2026-06-20T22:00:00Z", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z", "withdrawn": false}],
        "revisedBy": [],
        "heldBy": ["appraiser-b"]
      }
    ]
  },
  "acts": [
    {
      "record": {"kind": "revision", "id": "appraiser-a-june.yes"},
      "act": {"id": "act:2bc75384a53066c9b21bede5718663bae9dbe01e96d8c7d9a6258231", "step": "revise", "worker": "reviser", "recordKind": "revision", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z"},
      "run": {"id": "appraiser-a-june", "blueprint": "appraiser", "version": 1, "startedBy": "service:test/writer", "dated": "2026-10-21T16:14:15.404Z", "recordedAt": "2026-06-30T09:00:00Z"},
      "runEnding": {"run": "appraiser-a-june", "status": "completed", "dated": "2026-10-21T16:14:16.136Z", "recordedAt": "2026-06-30T09:00:00Z"},
      "blueprintVersion": {"id": "appraiser@1", "blueprint": "appraiser", "version": 1, "digest": "da34f63bf300a554253b010258ba3f9a1c2a3c54bfb8ef8c24afa10aeee435a0", "dated": "2026-10-21T16:14:11.977Z", "recordedAt": "2026-05-02T10:00:00Z"}
    },
    {
      "record": {"kind": "revision", "id": "appraiser-b-june.yes"},
      "act": {"id": "act:502f7386c263672ce0a4a37320519cd22c772af8dbf91f6b5c6b261a", "step": "revise", "worker": "reviser", "recordKind": "revision", "dated": "2026-06-30T09:00:00Z", "recordedAt": "2026-06-30T09:00:00Z"},
      "run": {"id": "appraiser-b-june", "blueprint": "appraiser", "version": 2, "startedBy": "service:test/writer", "dated": "2026-10-21T16:14:22.068Z", "recordedAt": "2026-06-30T09:00:00Z"},
      "runEnding": {"run": "appraiser-b-june", "status": "completed", "dated": "2026-10-21T16:14:22.200Z", "recordedAt": "2026-06-30T09:00:00Z"},
      "blueprintVersion": {"id": "appraiser@2", "blueprint": "appraiser", "version": 2, "digest": "3ca364aede02959895cb05003b77900cb4478af32c79546548793a7188a31755", "dated": "2026-10-21T16:14:20.747Z", "recordedAt": "2026-06-30T09:00:00Z"}
    }
  ]
}
```

Cut to one claim a side and to the two revisions' acts, of eleven: one for each of the six claims,
the three pieces of evidence the two holders share, and the two revisions.

**The market resolved afterwards.** A resolution is recorded as [Markets and resolutions](markets.md)
records one, naming the approval's evidence, which the first holder's June run recorded. The
answer carries that run's act:

```bash
curl -s localhost:9000/markets/launch-market/resolutions -d '{"id":"announced","outcome":"YES","evidence":["f6662d3eaf783467d4b1c7352becc74afa91bd73feea684f180f9cc2d3b5e4a8"],"authority":"Company X announcement","dated":"2026-11-14T12:00:00Z"}'
curl -s localhost:9000/process/markets/launch-market/resolution
```

```json
{
  "answer": {
    "resolution": {
      "id": "announced",
      "outcome": "YES",
      "void": false,
      "authority": "Company X announcement",
      "dated": "2026-11-14T12:00:00Z",
      "recordedAt": "2026-11-14T12:00:00Z"
    },
    "hypothesis": "launch/yes",
    "evidence": [
      {
        "id": "f6662d3eaf783467d4b1c7352becc74afa91bd73feea684f180f9cc2d3b5e4a8",
        "source": {
          "id": "regulator",
          "name": "the regulator",
          "dated": "2026-05-02T10:00:00Z",
          "recordedAt": "2026-05-02T10:00:00Z"
        },
        "locator": "Notice 1187 of 2026",
        "excerpt": "Office of Product Conformity, public notice 1187 of 2026. The Office has",
        "publishedAt": "2026-06-09T10:00:00Z",
        "dated": "2026-06-30T09:00:00Z",
        "recordedAt": "2026-06-30T09:00:00Z",
        "withdrawn": false
      }
    ]
  },
  "acts": [
    {
      "record": {
        "kind": "evidence",
        "id": "f6662d3eaf783467d4b1c7352becc74afa91bd73feea684f180f9cc2d3b5e4a8"
      },
      "act": {
        "id": "act:699d6c8f4842787e25a04845480951be340f58dcfcc9268ea64fbb02",
        "step": "read",
        "worker": "reader",
        "recordKind": "evidence",
        "dated": "2026-06-30T09:00:00Z",
        "recordedAt": "2026-06-30T09:00:00Z"
      },
      "run": {
        "id": "appraiser-a-june",
        "blueprint": "appraiser",
        "version": 1,
        "startedBy": "service:test/writer",
        "dated": "2026-10-21T16:14:15.404Z",
        "recordedAt": "2026-06-30T09:00:00Z"
      },
      "runEnding": {
        "run": "appraiser-a-june",
        "status": "completed",
        "dated": "2026-10-21T16:14:16.136Z",
        "recordedAt": "2026-06-30T09:00:00Z"
      },
      "blueprintVersion": {
        "id": "appraiser@1",
        "blueprint": "appraiser",
        "version": 1,
        "digest": "da34f63bf300a554253b010258ba3f9a1c2a3c54bfb8ef8c24afa10aeee435a0",
        "dated": "2026-10-21T16:14:11.977Z",
        "recordedAt": "2026-05-02T10:00:00Z"
      }
    }
  ]
}
```

**Each holder's belief at resolution.** Both holders had it right, and the acts say which version
each was running.

```bash
curl -s localhost:9000/process/markets/launch-market/beliefs-at-resolution
```

```json
{
  "answer": {
    "resolution": {
      "id": "announced",
      "outcome": "YES",
      "void": false,
      "authority": "Company X announcement",
      "dated": "2026-11-14T12:00:00Z",
      "recordedAt": "2026-11-14T12:00:00Z"
    },
    "hypothesis": "launch/yes",
    "beliefs": [
      {
        "holder": "appraiser-a",
        "hypothesis": "launch/yes",
        "revision": {
          "id": "appraiser-a-june.yes",
          "holder": "appraiser-a",
          "hypothesis": "launch/yes",
          "n": 2,
          "probability": 0.72,
          "follows": "appraiser-a-may.yes",
          "dated": "2026-06-30T09:00:00Z",
          "recordedAt": "2026-06-30T09:00:00Z"
        },
        "resolvedTo": true
      },
      {
        "holder": "appraiser-b",
        "hypothesis": "launch/yes",
        "revision": {
          "id": "appraiser-b-june.yes",
          "holder": "appraiser-b",
          "hypothesis": "launch/yes",
          "n": 2,
          "probability": 0.66,
          "follows": "appraiser-b-may.yes",
          "dated": "2026-06-30T09:00:00Z",
          "recordedAt": "2026-06-30T09:00:00Z"
        },
        "resolvedTo": true
      }
    ]
  },
  "acts": [
    {
      "record": {
        "kind": "revision",
        "id": "appraiser-a-june.yes"
      },
      "act": {
        "id": "act:2bc75384a53066c9b21bede5718663bae9dbe01e96d8c7d9a6258231",
        "step": "revise",
        "worker": "reviser",
        "recordKind": "revision",
        "dated": "2026-06-30T09:00:00Z",
        "recordedAt": "2026-06-30T09:00:00Z"
      },
      "run": {
        "id": "appraiser-a-june",
        "blueprint": "appraiser",
        "version": 1,
        "startedBy": "service:test/writer",
        "dated": "2026-10-21T16:14:15.404Z",
        "recordedAt": "2026-06-30T09:00:00Z"
      },
      "runEnding": {
        "run": "appraiser-a-june",
        "status": "completed",
        "dated": "2026-10-21T16:14:16.136Z",
        "recordedAt": "2026-06-30T09:00:00Z"
      },
      "blueprintVersion": {
        "id": "appraiser@1",
        "blueprint": "appraiser",
        "version": 1,
        "digest": "da34f63bf300a554253b010258ba3f9a1c2a3c54bfb8ef8c24afa10aeee435a0",
        "dated": "2026-10-21T16:14:11.977Z",
        "recordedAt": "2026-05-02T10:00:00Z"
      }
    },
    {
      "record": {
        "kind": "revision",
        "id": "appraiser-b-june.yes"
      },
      "act": {
        "id": "act:502f7386c263672ce0a4a37320519cd22c772af8dbf91f6b5c6b261a",
        "step": "revise",
        "worker": "reviser",
        "recordKind": "revision",
        "dated": "2026-06-30T09:00:00Z",
        "recordedAt": "2026-06-30T09:00:00Z"
      },
      "run": {
        "id": "appraiser-b-june",
        "blueprint": "appraiser",
        "version": 2,
        "startedBy": "service:test/writer",
        "dated": "2026-10-21T16:14:22.068Z",
        "recordedAt": "2026-06-30T09:00:00Z"
      },
      "runEnding": {
        "run": "appraiser-b-june",
        "status": "completed",
        "dated": "2026-10-21T16:14:22.200Z",
        "recordedAt": "2026-06-30T09:00:00Z"
      },
      "blueprintVersion": {
        "id": "appraiser@2",
        "blueprint": "appraiser",
        "version": 2,
        "digest": "3ca364aede02959895cb05003b77900cb4478af32c79546548793a7188a31755",
        "dated": "2026-10-21T16:14:20.747Z",
        "recordedAt": "2026-06-30T09:00:00Z"
      }
    }
  ]
}
```

## Measure it

`just measure-golden-set` runs the four runs against a running service under identifiers of its
own, times each run, times every record it wrote from the run's end to the graph holding the record
and its act, and times the whole set to the comparison being answered from the graph. The service
needs a model; the build's suite runs the same measurement with a scripted one:

```text
measure-golden-set (scripted model): the four runs 655, 451, 666, 845 ms; 20 records to the graph with their acts, from each run's end: median 1497 ms, nine in ten within 1572 ms, slowest 1574 ms; the whole set 8471 ms
```

Those figures are from a laptop with everything in Docker and the sink under emulation. The second
holder's runs wrote twenty records and not twenty-eight because the first holder's had already
recorded the evidence. The command exits `1` when nine in ten records take longer than five seconds
or the whole set longer than two minutes. A record is timed from its run's end, which is after the
record was written, so each figure is an upper bound on the time from the write.
