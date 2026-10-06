---
title: Ask for explanations
description: Ask why a belief changed, what a holder believed at a past time, what the case for a hypothesis is and where two holders disagree, and read each answer as records traced to their sources.
kind: guide
layers: [belief]
related: [build/record-reasoning.md, build/read-the-graph.md, concepts/two-times.md, reference/http-api.md]
---

# Ask for explanations

Six routes under `/answers` explain the reasoning that has been recorded. Each answer is made of held
records and the links between them and of nothing else: no text is generated, and every claim in an
answer comes with the evidence it derives from and the source of that evidence.

| Route | Answers |
|---|---|
| `GET /answers/belief-change` | why a belief changed between two of its revisions |
| `GET /answers/belief` | what a holder believes, or believed at a past time, and on what |
| `GET /answers/case` | the claims that support or contradict a hypothesis |
| `GET /answers/learned` | what was learned about a question after a time |
| `GET /answers/comparison` | where two holders' beliefs in one hypothesis differ |
| `GET /answers/resting-on-revised` | which beliefs rest on a claim that has since been revised |

The answers are read from the graph database, which the service fills by publishing every record.
Recording is described on [Record reasoning](record-reasoning.md).

## What is recorded here

The answers on this page are about the question `launch`, with these records held:

| Holder | Record | Dated | Says |
|---|---|---|---|
| agent-a | claim `launch.pending` | 4 May | contradicts `launch/yes`, from Company X's filing |
| agent-a | revision `agent-a.launch.1` | 4 May | 0.38, resting on `launch.pending` |
| agent-b | claim `launch.tooling` | 8 May | contradicts `launch/yes`, from the trade press |
| agent-a | claim `launch.barrier-gone` | 11 May | supports `launch/yes`, from the regulator's notice; revises `launch.pending` |
| agent-a | revision `agent-a.launch.2` | 11 May | 0.61, resting on `launch.barrier-gone` |
| agent-b | revision `agent-b.launch.1` | 12 May | 0.43, resting on `launch.tooling` (weight 0.6) and `launch.barrier-gone` (weight 0.4) |
| agent-b | claim `launch.tooling-ordered` | 15 May | supports `launch/yes`; revises `launch.tooling` |

agent-a's records are the worked example that `just seed launch-example` records. agent-b's were added
with the requests on [Record reasoning](record-reasoning.md). All of them were entered on 6 October
2026, months after the dates they carry, which is what makes the two ways of asking about the past
differ in [asking about a past time](#ask-about-a-past-time).

## Wait for the graph first

An answer is read from the graph, and the graph is a moment behind the service: a record is held as
soon as its write is answered, and reaches the graph a second or so later. A writer that wants to ask
about what it has written waits for it first:

```bash
curl -s localhost:9000/graph/wait -d '{ "kind": "revision", "id": "agent-b.launch.1" }'
```

```json
{"caughtUp":true,"waitedMs":1395,"missing":[]}
```

The wait ends when the graph holds the record's node and every edge the record stated, or when the
limit passes. `kind` is `question`, `holder`, `source`, `evidence`, `claim`, `revision` or `market`.
`limitMs` is how long to wait at most, 5,000 when left out. Passing the limit is an answer and not an
error. It lists what the graph still lacks, and says nothing against the record, which is held:

```bash
curl -s localhost:9000/sources -d '{ "id": "weekly-digest", "name": "the weekly digest" }'
curl -s localhost:9000/graph/wait -d '{ "kind": "source", "id": "weekly-digest", "limitMs": 0 }'
```

```json
{"caughtUp":false,"waitedMs":39,"missing":["node:source:weekly-digest"]}
```

## Why a belief changed

`GET /answers/belief-change` takes two revisions of one belief and answers with what the later one
rests on that the earlier did not, what it no longer rests on, and what both rest on.

```bash
curl -s 'localhost:9000/answers/belief-change?from=agent-a.launch.1&to=agent-a.launch.2' | python3 -m json.tool
```

```json
{
    "from": {
        "id": "agent-a.launch.1",
        "holder": "agent-a",
        "hypothesis": "launch/yes",
        "n": 1,
        "probability": 0.38,
        "dated": "2026-05-04T09:00:00Z",
        "recordedAt": "2026-10-06T12:11:18.472Z"
    },
    "to": {
        "id": "agent-a.launch.2",
        "holder": "agent-a",
        "hypothesis": "launch/yes",
        "n": 2,
        "probability": 0.61,
        "follows": "agent-a.launch.1",
        "dated": "2026-05-11T09:00:00Z",
        "recordedAt": "2026-10-06T12:11:18.567Z"
    },
    "newlyRestedOn": [
        {
            "claim": {
                "id": "launch.barrier-gone",
                "holder": "agent-a",
                "statement": "the approval barrier has gone",
                "dated": "2026-05-11T09:00:00Z",
                "recordedAt": "2026-10-06T12:11:18.555Z",
                "withdrawn": false
            },
            "evidence": [
                {
                    "id": "fcc1d4a2479250e20568ecdf842752940e6927dfca110d39ac7d0585ea2f9b5f",
                    "source": {
                        "id": "regulator",
                        "name": "the regulator",
                        "dated": "2026-10-06T12:11:18.441Z",
                        "recordedAt": "2026-10-06T12:11:18.441Z"
                    },
                    "locator": "https://regulator.example/notices/1187",
                    "excerpt": "Notice 1187: Product Y is approved for sale.",
                    "publishedAt": "2026-05-11T08:00:00Z",
                    "dated": "2026-05-11T09:00:00Z",
                    "recordedAt": "2026-10-06T12:11:18.542Z",
                    "withdrawn": false
                }
            ],
            "revisedBy": [],
            "heldBy": [
                "agent-a",
                "agent-b"
            ]
        }
    ],
    "noLongerRestedOn": [
        {
            "claim": {
                "id": "launch.pending",
                "holder": "agent-a",
                "statement": "approval is pending and the launch date is uncertain",
                "dated": "2026-05-04T09:00:00Z",
                "recordedAt": "2026-10-06T12:11:18.460Z",
                "withdrawn": false
            },
            "evidence": [
                {
                    "id": "4478a133ed11105e0434c1973dd5511ffe0885dff7b1abf41f9d5c9bf74facdc",
                    "source": {
                        "id": "company-x-filings",
                        "name": "Company X filings",
                        "dated": "2026-10-06T12:11:18.430Z",
                        "recordedAt": "2026-10-06T12:11:18.430Z"
                    },
                    "locator": "https://filings.example/company-x/2026-q1",
                    "excerpt": "Regulatory approval for Product Y remains pending; no launch date is committed.",
                    "author": "Company X",
                    "publishedAt": "2026-05-04T07:00:00Z",
                    "dated": "2026-05-04T09:00:00Z",
                    "recordedAt": "2026-10-06T12:11:18.449Z",
                    "withdrawn": false
                }
            ],
            "revisedBy": [
                "launch.barrier-gone"
            ],
            "heldBy": []
        }
    ],
    "stillRestedOn": [],
    "observedBetween": [
        {
            "id": "fcc1d4a2479250e20568ecdf842752940e6927dfca110d39ac7d0585ea2f9b5f",
            "source": {
                "id": "regulator",
                "name": "the regulator",
                "dated": "2026-10-06T12:11:18.441Z",
                "recordedAt": "2026-10-06T12:11:18.441Z"
            },
            "locator": "https://regulator.example/notices/1187",
            "excerpt": "Notice 1187: Product Y is approved for sale.",
            "publishedAt": "2026-05-11T08:00:00Z",
            "dated": "2026-05-11T09:00:00Z",
            "recordedAt": "2026-10-06T12:11:18.542Z",
            "withdrawn": false
        }
    ]
}
```

Read as a sentence: agent-a's belief went from 0.38 to 0.61 because it came to rest on the claim "the
approval barrier has gone", derived from the regulator's Notice 1187, and stopped resting on "approval
is pending and the launch date is uncertain", which that claim revised.

Each entry is a support: a claim, the evidence it derives from with the source of each piece,
`revisedBy` (the claims that revise it) and `heldBy` (the holders whose current revision rests on it).
This shape recurs in every answer. `observedBetween` is the evidence behind the later revision that was
observed after the earlier one: what arrived in between. A claim in `stillRestedOn` carries
`weightFrom` and `weightTo`, the weight each revision gave it.

`from` and `to` may be given in either order. Revisions of two different beliefs are refused:

```bash
curl -s 'localhost:9000/answers/belief-change?from=agent-a.launch.1&to=agent-b.launch.1' | python3 -m json.tool
```

```json
{
    "rule": "answer.revisions.of-two-beliefs",
    "status": 422,
    "error": "The two revisions are of different beliefs, and a change is of one belief.",
    "names": {
        "from": "agent-a in launch/yes",
        "to": "agent-b in launch/yes"
    }
}
```

## What a holder believes, and on what

`GET /answers/belief` answers with the current revision of one holder's belief in one hypothesis and a
support for each claim it rests on.

```bash
curl -s 'localhost:9000/answers/belief?holder=agent-a&hypothesis=launch/yes' | python3 -m json.tool
```

This reply is an excerpt: the evidence under the claim, which is the regulator's notice exactly as in
[the change of belief](#why-a-belief-changed), is left out.

```json
{
    "revision": {
        "id": "agent-a.launch.2",
        "holder": "agent-a",
        "hypothesis": "launch/yes",
        "n": 2,
        "probability": 0.61,
        "follows": "agent-a.launch.1",
        "dated": "2026-05-11T09:00:00Z",
        "recordedAt": "2026-10-06T12:11:18.567Z"
    },
    "restsOn": [
        {
            "claim": {
                "id": "launch.barrier-gone",
                "holder": "agent-a",
                "statement": "the approval barrier has gone",
                "dated": "2026-05-11T09:00:00Z",
                "recordedAt": "2026-10-06T12:11:18.555Z",
                "withdrawn": false
            },
            "revisedBy": [],
            "heldBy": [
                "agent-a",
                "agent-b"
            ]
        }
    ]
}
```

## The case for or against a hypothesis

`GET /answers/case` answers with every claim that takes one stance on a hypothesis, whoever stated it.
Claims that have since been revised are shown apart, in `revisedClaims`, so a reader sees at once which
reasons still stand.

```bash
curl -s 'localhost:9000/answers/case?hypothesis=launch/yes&stance=contradicts' | python3 -m json.tool
```

This reply is an excerpt: the `evidence` of each claim is left out.

```json
{
    "claims": [],
    "revisedClaims": [
        {
            "claim": {
                "id": "launch.pending",
                "holder": "agent-a",
                "statement": "approval is pending and the launch date is uncertain",
                "dated": "2026-05-04T09:00:00Z",
                "recordedAt": "2026-10-06T12:11:18.460Z",
                "withdrawn": false
            },
            "revisedBy": [
                "launch.barrier-gone"
            ],
            "heldBy": []
        },
        {
            "claim": {
                "id": "launch.tooling",
                "holder": "agent-b",
                "statement": "tooling has not been ordered, so a launch this year is tight",
                "dated": "2026-05-08T10:00:00Z",
                "recordedAt": "2026-10-06T12:12:44.611Z",
                "withdrawn": false
            },
            "revisedBy": [
                "launch.tooling-ordered"
            ],
            "heldBy": [
                "agent-b"
            ]
        }
    ]
}
```

Both claims against a launch have been revised, so nothing stands against it. The second still has a
holder: agent-b's current revision rests on `launch.tooling` although agent-b has revised that claim.
`stance=supports` answers the other side, here `launch.barrier-gone` and `launch.tooling-ordered`.

## What was learned after a time

`GET /answers/learned` answers with the evidence, the claims and the belief revisions about a question
dated later than `after`, oldest first.

```bash
curl -s 'localhost:9000/answers/learned?question=launch&after=2026-05-13T00:00:00Z' | python3 -m json.tool
```

```json
{
    "evidence": [
        {
            "id": "353ea5b6bd411e82c13b162d6013b2263a578ab581d99d43ee2d4df4782d3623",
            "source": {
                "id": "trade-press",
                "name": "the trade press",
                "dated": "2026-10-06T12:12:36.452Z",
                "recordedAt": "2026-10-06T12:12:36.452Z"
            },
            "locator": "https://trade.example/2026/05/15/product-y-tooling-ordered",
            "excerpt": "Tooling for Product Y was ordered on 14 May.",
            "dated": "2026-05-15T10:00:00Z",
            "recordedAt": "2026-10-06T12:14:10.443Z",
            "withdrawn": false
        }
    ],
    "claims": [
        {
            "id": "launch.tooling-ordered",
            "holder": "agent-b",
            "statement": "tooling is now ordered, so the timetable holds",
            "dated": "2026-05-15T10:00:00Z",
            "recordedAt": "2026-10-06T12:14:10.516Z",
            "withdrawn": false
        }
    ],
    "revisions": []
}
```

Evidence belongs to no question, so the evidence in this answer is the evidence that claims about the
question derive from. `after` is required and is an ISO-8601 instant.

## Where two holders disagree

`GET /answers/comparison` sets two holders' beliefs in one hypothesis side by side: both probabilities,
the gap between them, the claims both rest on with the weight each gives, and the claims only one rests
on.

```bash
curl -s 'localhost:9000/answers/comparison?hypothesis=launch/yes&a=agent-a&b=agent-b' | python3 -m json.tool
```

This reply is an excerpt: the `evidence` of each claim is left out.

```json
{
    "a": {
        "holder": "agent-a",
        "revision": {
            "id": "agent-a.launch.2",
            "holder": "agent-a",
            "hypothesis": "launch/yes",
            "n": 2,
            "probability": 0.61,
            "follows": "agent-a.launch.1",
            "dated": "2026-05-11T09:00:00Z",
            "recordedAt": "2026-10-06T12:11:18.567Z"
        },
        "statesNoReasons": false
    },
    "b": {
        "holder": "agent-b",
        "revision": {
            "id": "agent-b.launch.1",
            "holder": "agent-b",
            "hypothesis": "launch/yes",
            "n": 1,
            "probability": 0.43,
            "dated": "2026-05-12T09:00:00Z",
            "recordedAt": "2026-10-06T12:12:44.625Z"
        },
        "statesNoReasons": false
    },
    "difference": 0.18,
    "both": [
        {
            "support": {
                "claim": {
                    "id": "launch.barrier-gone",
                    "holder": "agent-a",
                    "statement": "the approval barrier has gone",
                    "dated": "2026-05-11T09:00:00Z",
                    "recordedAt": "2026-10-06T12:11:18.555Z",
                    "withdrawn": false
                },
                "revisedBy": [],
                "heldBy": [
                    "agent-a",
                    "agent-b"
                ]
            },
            "weightB": 0.4
        }
    ],
    "onlyA": [],
    "onlyB": [
        {
            "claim": {
                "id": "launch.tooling",
                "holder": "agent-b",
                "statement": "tooling has not been ordered, so a launch this year is tight",
                "dated": "2026-05-08T10:00:00Z",
                "recordedAt": "2026-10-06T12:12:44.611Z",
                "withdrawn": false
            },
            "weight": 0.6,
            "revisedBy": [
                "launch.tooling-ordered"
            ],
            "heldBy": [
                "agent-b"
            ]
        }
    ]
}
```

The two agree on the regulator's notice and differ by 0.18 because agent-b also rests on a claim about
tooling, with more weight, and that claim has since been revised. `difference` is the gap between the
two probabilities and is never negative; which is the greater is plain from the two. `weightA` is
absent because agent-a gave the claim no weight. A side whose revision rests on no claims has
`statesNoReasons: true`, which is how a market's price reads when a market is one of the two holders:
[Markets and resolutions](markets.md).

## Which beliefs rest on a revised claim

`GET /answers/resting-on-revised` answers with every belief about a question whose current revision
rests on a claim that a later claim has revised. These are the beliefs that are due to be thought about
again.

```bash
curl -s 'localhost:9000/answers/resting-on-revised?question=launch' | python3 -m json.tool
```

This reply is an excerpt: the `evidence` of the claim is left out.

```json
{
    "beliefs": [
        {
            "holder": "agent-b",
            "hypothesis": "launch/yes",
            "revision": {
                "id": "agent-b.launch.1",
                "holder": "agent-b",
                "hypothesis": "launch/yes",
                "n": 1,
                "probability": 0.43,
                "dated": "2026-05-12T09:00:00Z",
                "recordedAt": "2026-10-06T12:12:44.625Z"
            },
            "revisedClaims": [
                {
                    "claim": {
                        "id": "launch.tooling",
                        "holder": "agent-b",
                        "statement": "tooling has not been ordered, so a launch this year is tight",
                        "dated": "2026-05-08T10:00:00Z",
                        "recordedAt": "2026-10-06T12:12:44.611Z",
                        "withdrawn": false
                    },
                    "weight": 0.6,
                    "revisedBy": [
                        "launch.tooling-ordered"
                    ],
                    "heldBy": [
                        "agent-b"
                    ]
                }
            ]
        }
    ]
}
```

agent-a is not in the answer. It stated a new revision when it revised its claim, so its current
revision rests on nothing revised. Nothing marks or changes agent-b's belief: the answer is read from
the records each time it is asked.

## Ask about a past time

Every answer takes two optional instants, and they ask different questions:

| Parameter | Counts a record when | Asks |
|---|---|---|
| `asOf` | it is `dated` at or before the instant | what was the case then |
| `asRecordedBy` | it was `recordedAt` at or before the instant | what had been entered by then |

`asOf` reads the past by the dates the records carry. On 6 May agent-a had stated one revision, and the
regulator's notice had not been observed:

```bash
curl -s 'localhost:9000/answers/belief?holder=agent-a&hypothesis=launch/yes&asOf=2026-05-06T00:00:00Z' | python3 -m json.tool
```

This reply is an excerpt: the `evidence` of the claim, Company X's filing, is left out.

```json
{
    "revision": {
        "id": "agent-a.launch.1",
        "holder": "agent-a",
        "hypothesis": "launch/yes",
        "n": 1,
        "probability": 0.38,
        "dated": "2026-05-04T09:00:00Z",
        "recordedAt": "2026-10-06T12:11:18.472Z"
    },
    "restsOn": [
        {
            "claim": {
                "id": "launch.pending",
                "holder": "agent-a",
                "statement": "approval is pending and the launch date is uncertain",
                "dated": "2026-05-04T09:00:00Z",
                "recordedAt": "2026-10-06T12:11:18.460Z",
                "withdrawn": false
            },
            "revisedBy": [],
            "heldBy": [
                "agent-a"
            ]
        }
    ]
}
```

`revisedBy` is empty: on 6 May nothing had revised the claim. An answer as of a time is the answer that
would have been given then, down to which claims count as revised and who rests on what. Before any
revision is stated, the reply is `200` with no `revision`:

```bash
curl -s 'localhost:9000/answers/belief?holder=agent-a&hypothesis=launch/yes&asOf=2026-05-02T00:00:00Z'
```

```json
{"restsOn":[]}
```

`asRecordedBy` reads the past by when records were entered, whatever date they carry. agent-b's claim
is dated 8 May but was entered at 12:12:44 on 6 October, so an answer as recorded by 12:12:00 that day
does not hold it:

```bash
curl -s 'localhost:9000/answers/case?hypothesis=launch/yes&stance=contradicts&asRecordedBy=2026-10-06T12:12:00Z' | python3 -m json.tool
```

This reply is an excerpt: the `evidence` of the claim is left out.

```json
{
    "claims": [],
    "revisedClaims": [
        {
            "claim": {
                "id": "launch.pending",
                "holder": "agent-a",
                "statement": "approval is pending and the launch date is uncertain",
                "dated": "2026-05-04T09:00:00Z",
                "recordedAt": "2026-10-06T12:11:18.460Z",
                "withdrawn": false
            },
            "revisedBy": [
                "launch.barrier-gone"
            ],
            "heldBy": []
        }
    ]
}
```

An answer as recorded by a time never changes afterwards, whatever is entered later and however it is
dated. That is the one to keep when an answer has to be reproduced. The two parameters may be given
together.

A source or a holder is shown beside a record that counts whenever it was registered itself. Both are
registered and not stated, so the date they carry is the day they were registered, which is why the
regulator in these answers is dated October beside a notice dated May.

A revision named by `from` or `to` has to count at the time asked about, or the answer is refused:

```bash
curl -s 'localhost:9000/answers/belief-change?from=agent-a.launch.1&to=agent-a.launch.2&asOf=2026-05-06T00:00:00Z' | python3 -m json.tool
```

```json
{
    "rule": "answer.revision.later-than-asked",
    "status": 422,
    "error": "The revision is dated or was recorded later than the time asked about.",
    "names": {
        "revision": "agent-a.launch.2",
        "dated": "2026-05-11T09:00:00Z",
        "recordedAt": "2026-10-06T12:11:18.567Z"
    }
}
```

A value that is not an ISO-8601 instant is refused with the rule `time.format`, naming the parameter.
[Two times on every record](../concepts/two-times.md) explains why a record has both times.

## When the graph cannot be read

Every route on this page answers `503` with the rule `graph.unavailable` when no graph database is
configured or the one configured does not answer. The service gives up on a read after a bounded wait
and does not queue it. Recording is unaffected: a write is decided from the service's own records and
is accepted whether or not the graph database is there, and the records reach the graph when it is
back. `POST /graph/wait` answers `503` the same way when the service has no broker to publish to.

A reader that wants more than these six answers reads the graph database itself:
[Read the graph](read-the-graph.md).
