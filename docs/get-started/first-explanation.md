---
title: Your first explanation
description: Record the launch example with one command, wait for the graph, then ask why a belief changed and what was believed on an earlier day.
kind: tutorial
related: [build/record-reasoning.md, build/ask-for-explanations.md, concepts/two-times.md]
---

# Your first explanation

This page records a small piece of reasoning and asks two questions of it: why a belief changed, and
what was believed before it did. It needs the service running as
[Run it on your machine](run-locally.md) leaves it.

## The example

The question is whether Company X will launch Product Y this year. Every name and number is made up.
On 4 May a holder, `agent-a`, reads a filing saying approval is pending and puts the launch at 0.38.
On 11 May the regulator publishes its approval; `agent-a` states a new claim that revises the first and
moves to 0.61.

## Record it

`just seed` posts a seed file's records to the service in order. The file
[`seed/launch-example.json`](https://github.com/thinkmorestupidless/ankka-reasoning/blob/main/seed/launch-example.json)
holds the example as ten records: the question, the holder, two sources, two pieces of evidence, two
claims and two belief revisions.

```bash
just seed launch-example
```

```text
10 sent, 10 new, 0 already held
```

Run it again and it reports `10 sent, 0 new, 10 already held`. A record sent twice is one record: the
second send is answered with the record already held, so a writer can retry anything.
[Record reasoning](../build/record-reasoning.md) sends the same records one at a time.

## Wait for the graph

A write is answered as soon as the service holds the record, and the graph follows a moment later.
Before asking a question of the graph, wait for the last record written:

```bash
curl -s localhost:9000/graph/wait -d '{"kind":"revision","id":"agent-a.launch.2"}'
```

```json
{"caughtUp":true,"waitedMs":1926,"missing":[]}
```

## Ask why the belief changed

Name the two revisions. The answer is the two revisions, the claims the later one newly rests on, the
claims it no longer rests on, and the evidence observed between them. This is an excerpt of the reply,
with the evidence shortened to its excerpt and source:

```bash
curl -s 'localhost:9000/answers/belief-change?from=agent-a.launch.1&to=agent-a.launch.2'
```

```json
{
  "from": { "id": "agent-a.launch.1", "probability": 0.38, "dated": "2026-05-04T09:00:00Z" },
  "to":   { "id": "agent-a.launch.2", "probability": 0.61, "dated": "2026-05-11T09:00:00Z", "follows": "agent-a.launch.1" },
  "newlyRestedOn": [
    {
      "claim": { "id": "launch.barrier-gone", "holder": "agent-a", "statement": "the approval barrier has gone" },
      "evidence": [
        { "excerpt": "Notice 1187: Product Y is approved for sale.", "source": { "id": "regulator", "name": "the regulator" } }
      ],
      "revisedBy": []
    }
  ],
  "noLongerRestedOn": [
    {
      "claim": { "id": "launch.pending", "holder": "agent-a", "statement": "approval is pending and the launch date is uncertain" },
      "evidence": [
        { "excerpt": "Regulatory approval for Product Y remains pending; no launch date is committed.", "source": { "id": "company-x-filings", "name": "Company X filings" } }
      ],
      "revisedBy": ["launch.barrier-gone"]
    }
  ],
  "stillRestedOn": [],
  "observedBetween": [
    { "excerpt": "Notice 1187: Product Y is approved for sale.", "source": { "id": "regulator", "name": "the regulator" } }
  ]
}
```

Every string in it is a record's own: the statements `agent-a` stated, the excerpts as they were
recorded, the sources' names. The service writes no sentence of its own, so the explanation can be
checked against the records by anyone.

## Ask what was believed earlier

Add `asOf` to any answer and only records dated at or before that time count. As of 6 May the notice
had not been observed, so the belief is the first revision, resting on the first claim:

```bash
curl -s 'localhost:9000/answers/belief?holder=agent-a&hypothesis=launch/yes&asOf=2026-05-06T00:00:00Z'
```

```json
{
  "revision": { "id": "agent-a.launch.1", "holder": "agent-a", "hypothesis": "launch/yes", "n": 1, "probability": 0.38 },
  "restsOn": [
    { "claim": { "id": "launch.pending", "statement": "approval is pending and the launch date is uncertain" }, "revisedBy": [] }
  ]
}
```

The reply is again an excerpt. Note `revisedBy` is empty: as of 6 May the claim had not been revised,
because the claim that revises it is dated 11 May. As of 2 May, before `agent-a` had stated anything,
there is no revision and the reply is `{"restsOn":[]}`.

## See a refusal

A record that breaks a rule is refused by the rule's name, and nothing is kept. A claim has to derive
from evidence that is held:

```bash
curl -s localhost:9000/claims -d '{
  "id": "launch.unfounded", "holder": "agent-a", "statement": "a claim from nothing",
  "derivesFrom": ["0000"], "stances": [{"hypothesis": "launch/yes", "stance": "supports"}]
}'
```

```json
{"rule":"claim.derives-from.not-held","status":422,"error":"No evidence '0000' is held.","names":{"claim":"launch.unfounded","evidence":"0000"}}
```

`rule` is stable and is what a client matches on; [Refusals](../reference/refusals.md) lists every one.

## Where to go from here

- [Ask for explanations](../build/ask-for-explanations.md): the other answers, among them the case for
  a hypothesis and where two holders differ.
- [Record reasoning](../build/record-reasoning.md): each kind of record and the order they arrive in.
- [Read the graph](../build/read-the-graph.md): the same trace, asked of Neo4j directly.
- [Records and links](../concepts/records-and-links.md): what the records are and why each link has
  one owner.
