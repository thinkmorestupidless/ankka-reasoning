---
title: Markets and resolutions
description: Open a market on a question, record its prices as revisions of the market's own belief, compare it with a holder, resolve it on evidence, and ask why it resolved and who believed what.
kind: guide
layers: [market]
related: [build/record-reasoning.md, build/ask-for-explanations.md, concepts/layers.md, reference/http-api.md]
---

# Markets and resolutions

A market is a place where a question is traded. Here it is a record about one question that offers an
outcome for each hypothesis it trades, and it speaks as a holder: every price observed on the market is
a revision of that holder's belief. A price is therefore read, compared and explained with the same
answers as any other belief, and nothing in the belief layer knows that markets exist.

This guide opens a market on the question `launch` from [Record reasoning](record-reasoning.md),
records prices, compares the market with agent-a, and resolves it. The commands assume the service is
on `localhost:9000`.

## Open a market

A market is opened on a question that is held, with an outcome for each hypothesis it trades, the
criteria it will resolve by, and the time it closes.

```bash
curl -s localhost:9000/markets -d '{
  "id": "launch-market",
  "question": "launch",
  "venue": "the Lantern exchange",
  "outcomes": [
    { "outcome": "YES", "hypothesis": "launch/yes" },
    { "outcome": "NO", "hypothesis": "launch/no" }
  ],
  "resolutionCriteria": "Resolves YES if Product Y is on sale to the public by 31 December 2026, and NO otherwise.",
  "closesAt": "2026-12-31T23:59:59Z",
  "dated": "2026-05-02T09:00:00Z"
}'
```

```json
{
  "id": "launch-market",
  "question": "launch",
  "venue": "the Lantern exchange",
  "outcomes": [
    { "outcome": "YES", "hypothesis": "launch/yes" },
    { "outcome": "NO", "hypothesis": "launch/no" }
  ],
  "resolutionCriteria": "Resolves YES if Product Y is on sale to the public by 31 December 2026, and NO otherwise.",
  "closesAt": "2026-12-31T23:59:59Z",
  "holder": "market.launch-market",
  "resolutions": [],
  "dated": "2026-05-02T09:00:00Z",
  "recordedAt": "2026-10-06T12:13:40.673Z",
  "writer": "local"
}
```

Opening the market registers the holder it speaks as, `market.<market id>`, of kind `market` and
spoken for by the writer who opened the market:

```bash
curl -s localhost:9000/holders/market.launch-market
```

```json
{
  "id": "market.launch-market",
  "kind": "market",
  "name": "the Lantern exchange",
  "writers": ["local"],
  "dated": "2026-10-06T12:13:40.676Z",
  "recordedAt": "2026-10-06T12:13:40.676Z",
  "writer": "local"
}
```

Outcome names are unique within the market, each hypothesis belongs to the market's own question and
is offered at most once, and a market is dated no earlier than the hypotheses it offers outcomes for. A
market's identifier is at most 32 characters and an outcome name at most 16, shorter than other
identifiers because longer identifiers are built from them.

## Record a price observation

A price observation is an outcome, a price from 0 to 1 and the time it was observed. It becomes a
revision of the market's belief in that outcome's hypothesis, resting on no claims.

```bash
curl -s localhost:9000/markets/launch-market/price-observations -d '{
  "outcome": "YES", "price": 0.48, "observedAt": "2026-05-06T16:00:00Z"
}' -w '\nHTTP %{http_code}\n'
```

```text
{"revision":{"id":"launch-market.YES.1778083200000","holder":"market.launch-market","hypothesis":"launch/yes","n":1,"probability":0.48,"restsOn":[],"dated":"2026-05-06T16:00:00Z","recordedAt":"2026-10-06T12:13:40.716Z","writer":"local","sequence":1},"added":true}
HTTP 201
```

The writer sends no identifier and no `follows`. The revision's identifier is the market, the outcome
and the time observed in milliseconds, and it follows whatever revision is current.

A price that has not moved adds nothing. The next day's observation at the same price answers `200`
with the revision that is still current, and `added` is `false`:

```bash
curl -s localhost:9000/markets/launch-market/price-observations -d '{
  "outcome": "YES", "price": 0.48, "observedAt": "2026-05-07T16:00:00Z"
}' -w '\nHTTP %{http_code}\n'
```

```text
{"revision":{"id":"launch-market.YES.1778083200000","holder":"market.launch-market","hypothesis":"launch/yes","n":1,"probability":0.48,"restsOn":[],"dated":"2026-05-06T16:00:00Z","recordedAt":"2026-10-06T12:13:40.716Z","writer":"local","sequence":1},"added":false}
HTTP 200
```

So a writer can poll a venue and send every reading, and the market's line holds one revision for each
time the price changed. A price that has moved is a new revision:

```bash
curl -s localhost:9000/markets/launch-market/price-observations -d '{
  "outcome": "YES", "price": 0.55, "observedAt": "2026-05-12T16:00:00Z"
}' -w '\nHTTP %{http_code}\n'
```

```text
{"revision":{"id":"launch-market.YES.1778601600000","holder":"market.launch-market","hypothesis":"launch/yes","n":2,"probability":0.55,"restsOn":[],"follows":"launch-market.YES.1778083200000","dated":"2026-05-12T16:00:00Z","recordedAt":"2026-10-06T12:13:40.743Z","writer":"local","sequence":2},"added":true}
HTTP 201
```

The same observation sent again, here the first one after two later ones, is answered with the revision
it already is, `launch-market.YES.1778083200000`, and `added: false`. An outcome the market does not
offer is refused:

```bash
curl -s localhost:9000/markets/launch-market/price-observations -d '{ "outcome": "MAYBE", "price": 0.5 }'
```

```json
{
  "rule": "market.outcome.not-offered",
  "status": 422,
  "error": "The market does not offer the outcome 'MAYBE'.",
  "names": { "market": "launch-market", "outcome": "MAYBE" }
}
```

An observation dated later than the market's `closesAt` is refused with `market.price.after-close`, and
only a writer who speaks for the market's holder may record an observation or a resolution.

## Compare a market with a holder

A market is compared with a holder by naming the holder the market speaks as, on the same route that
compares any two holders. Wait for the latest price to reach the graph, then ask:

```bash
curl -s localhost:9000/graph/wait -d '{ "kind": "revision", "id": "launch-market.YES.1778601600000" }'
curl -s 'localhost:9000/answers/comparison?hypothesis=launch/yes&a=agent-a&b=market.launch-market' | python3 -m json.tool
```

This reply is an excerpt: the `evidence` of agent-a's claim is left out.

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
        "holder": "market.launch-market",
        "revision": {
            "id": "launch-market.YES.1778601600000",
            "holder": "market.launch-market",
            "hypothesis": "launch/yes",
            "n": 2,
            "probability": 0.55,
            "follows": "launch-market.YES.1778083200000",
            "dated": "2026-05-12T16:00:00Z",
            "recordedAt": "2026-10-06T12:13:40.743Z"
        },
        "statesNoReasons": true
    },
    "difference": 0.06,
    "both": [],
    "onlyA": [
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
    ],
    "onlyB": []
}
```

agent-a is at 0.61 on the regulator's notice and the market at 0.55. The market has
`statesNoReasons: true`: a price is a number with no claims behind it, and the answer says so and does
not invent reasons for it. Every other answer on [Ask for explanations](ask-for-explanations.md) works
for the market's holder too, `asOf` included, so "what did the market say on 9 May" is
`/answers/belief?holder=market.launch-market&hypothesis=launch/yes&asOf=2026-05-09T00:00:00Z`.

## Resolve the market on evidence

A resolution says which outcome the market came to, on what evidence and on whose authority. It names
at least one piece of evidence that is held and dated no later than the resolution, so a resolution can
always be traced to a source. A market with no resolution has none to explain:

```bash
curl -s localhost:9000/markets/launch-market/resolution
```

```json
{
  "rule": "market.not-resolved",
  "status": 404,
  "error": "The market 'launch-market' has no resolution.",
  "names": { "market": "launch-market" }
}
```

In late September the trade press reports a postponement. That is recorded as evidence in the usual
way and answers with its identifier:

```bash
curl -s localhost:9000/evidence -d '{
  "source": "trade-press",
  "locator": "https://trade.example/2026/09/28/product-y-postponed",
  "excerpt": "Company X is said to have postponed Product Y to next year.",
  "observedAt": "2026-09-28T09:00:00Z"
}'
```

```json
{
  "id": "bf69e8d5016dfbfd6e3a31f09a5b04cd0a900bfb8db1b0f0c780885878eae12f",
  "source": "trade-press",
  "locator": "https://trade.example/2026/09/28/product-y-postponed",
  "excerpt": "Company X is said to have postponed Product Y to next year.",
  "dated": "2026-09-28T09:00:00Z",
  "recordedAt": "2026-10-06T12:13:47.861Z",
  "writer": "local"
}
```

The venue resolves the market NO on it:

```bash
curl -s localhost:9000/markets/launch-market/resolutions -d '{
  "id": "first",
  "outcome": "NO",
  "evidence": ["bf69e8d5016dfbfd6e3a31f09a5b04cd0a900bfb8db1b0f0c780885878eae12f"],
  "authority": "the Lantern exchange",
  "dated": "2026-09-28T12:00:00Z"
}'
```

The reply is the market, with the resolution in its `resolutions`. This is an excerpt of it:

```json
{
  "id": "launch-market",
  "resolutions": [
    {
      "id": "first",
      "outcome": "NO",
      "evidence": ["bf69e8d5016dfbfd6e3a31f09a5b04cd0a900bfb8db1b0f0c780885878eae12f"],
      "authority": "the Lantern exchange",
      "dated": "2026-09-28T12:00:00Z",
      "recordedAt": "2026-10-06T12:13:55.373Z",
      "writer": "local"
    }
  ]
}
```

A resolution with no `outcome` is void: the market could not be decided. A resolution's identifier is
unique within its market.

## Revise a resolution

A resolution is never edited. When it turns out to be wrong, a later resolution revises it, and both
are kept. Three days later Company X announces that Product Y is on sale; that announcement is recorded
as evidence from the source `company-x-press` and answers with the identifier
`29dd7d513bccf474c290f53f33732e3b2ed5118038ebc2809dea0ab79acc65b2`.

A second resolution has to say which resolution it revises, and that has to be the market's current
one. Sent without `revises`, it is refused:

```bash
curl -s localhost:9000/markets/launch-market/resolutions -d '{
  "id": "second",
  "outcome": "YES",
  "evidence": ["29dd7d513bccf474c290f53f33732e3b2ed5118038ebc2809dea0ab79acc65b2"],
  "authority": "the Lantern exchange",
  "dated": "2026-10-01T12:00:00Z"
}'
```

```json
{
  "rule": "market.resolution.revises.not-current",
  "status": 409,
  "error": "A resolution revises the market's current resolution, which is 'first'.",
  "names": { "market": "launch-market", "resolution": "second", "current": "first" }
}
```

With `"revises": "first"` added to the same body it is accepted, and the market's `resolutions` hold
both. This is an excerpt of the reply:

```json
{
  "id": "launch-market",
  "resolutions": [
    {
      "id": "first",
      "outcome": "NO",
      "evidence": ["bf69e8d5016dfbfd6e3a31f09a5b04cd0a900bfb8db1b0f0c780885878eae12f"],
      "authority": "the Lantern exchange",
      "dated": "2026-09-28T12:00:00Z",
      "recordedAt": "2026-10-06T12:13:55.373Z",
      "writer": "local"
    },
    {
      "id": "second",
      "outcome": "YES",
      "evidence": ["29dd7d513bccf474c290f53f33732e3b2ed5118038ebc2809dea0ab79acc65b2"],
      "authority": "the Lantern exchange",
      "revises": "first",
      "dated": "2026-10-01T12:00:00Z",
      "recordedAt": "2026-10-06T12:13:55.416Z",
      "writer": "local"
    }
  ]
}
```

The market's current resolution is the last one. The rule is the one a belief's revisions follow, for
the same reason: two writers cannot both replace the resolution they each read.

## Ask why the market resolved

`GET /markets/{market}/resolution` answers with the market's current resolution, the hypothesis it came
to and the evidence it rests on, each piece with its source. It is read from the graph, so wait for the
market first:

```bash
curl -s localhost:9000/graph/wait -d '{ "kind": "market", "id": "launch-market" }'
curl -s localhost:9000/markets/launch-market/resolution | python3 -m json.tool
```

```json
{
    "resolution": {
        "id": "second",
        "outcome": "YES",
        "void": false,
        "authority": "the Lantern exchange",
        "dated": "2026-10-01T12:00:00Z",
        "recordedAt": "2026-10-06T12:13:55.416Z"
    },
    "hypothesis": "launch/yes",
    "evidence": [
        {
            "id": "29dd7d513bccf474c290f53f33732e3b2ed5118038ebc2809dea0ab79acc65b2",
            "source": {
                "id": "company-x-press",
                "name": "Company X press office",
                "dated": "2026-10-06T12:13:47.910Z",
                "recordedAt": "2026-10-06T12:13:47.910Z"
            },
            "locator": "https://press.example/company-x/product-y-on-sale",
            "excerpt": "Product Y is on sale from today in all regions.",
            "publishedAt": "2026-10-01T08:00:00Z",
            "dated": "2026-10-01T09:00:00Z",
            "recordedAt": "2026-10-06T12:13:47.923Z",
            "withdrawn": false
        }
    ]
}
```

The earlier resolution is still there, and `asOf` reads it back. On 30 September the market stood
resolved NO, on the report of a postponement:

```bash
curl -s 'localhost:9000/markets/launch-market/resolution?asOf=2026-09-30T00:00:00Z' | python3 -m json.tool
```

```json
{
    "resolution": {
        "id": "first",
        "outcome": "NO",
        "void": false,
        "authority": "the Lantern exchange",
        "dated": "2026-09-28T12:00:00Z",
        "recordedAt": "2026-10-06T12:13:55.373Z"
    },
    "hypothesis": "launch/no",
    "evidence": [
        {
            "id": "bf69e8d5016dfbfd6e3a31f09a5b04cd0a900bfb8db1b0f0c780885878eae12f",
            "source": {
                "id": "trade-press",
                "name": "the trade press",
                "dated": "2026-10-06T12:12:36.452Z",
                "recordedAt": "2026-10-06T12:12:36.452Z"
            },
            "locator": "https://trade.example/2026/09/28/product-y-postponed",
            "excerpt": "Company X is said to have postponed Product Y to next year.",
            "dated": "2026-09-28T09:00:00Z",
            "recordedAt": "2026-10-06T12:13:47.861Z",
            "withdrawn": false
        }
    ]
}
```

## Ask what was believed when it resolved

`GET /markets/{market}/beliefs-at-resolution` answers with each holder's last revision dated before the
resolution, for each hypothesis the market offers, and whether that hypothesis is the one the market
resolved to. The market's own holder is among them.

```bash
curl -s localhost:9000/markets/launch-market/beliefs-at-resolution | python3 -m json.tool
```

```json
{
    "resolution": {
        "id": "second",
        "outcome": "YES",
        "void": false,
        "authority": "the Lantern exchange",
        "dated": "2026-10-01T12:00:00Z",
        "recordedAt": "2026-10-06T12:13:55.416Z"
    },
    "hypothesis": "launch/yes",
    "beliefs": [
        {
            "holder": "agent-a",
            "hypothesis": "launch/yes",
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
            "resolvedTo": true
        },
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
            "resolvedTo": true
        },
        {
            "holder": "market.launch-market",
            "hypothesis": "launch/yes",
            "revision": {
                "id": "launch-market.YES.1778601600000",
                "holder": "market.launch-market",
                "hypothesis": "launch/yes",
                "n": 2,
                "probability": 0.55,
                "follows": "launch-market.YES.1778083200000",
                "dated": "2026-05-12T16:00:00Z",
                "recordedAt": "2026-10-06T12:13:40.743Z"
            },
            "resolvedTo": true
        }
    ]
}
```

The market resolved YES with agent-a at 0.61, the market at 0.55 and agent-b at 0.43. The answer is the
record of who believed what. It keeps no score: nothing here says which holder was more worth listening
to.

Both routes take `asOf` and `asRecordedBy`, and both answer `503` with the rule `graph.unavailable`
when the graph database cannot be read. Opening a market, recording a price and recording a resolution
do not read the graph and are accepted without it.

## What a market is in the graph

A market publishes its own node, an edge to its question, an edge to the holder it speaks as, an edge
to each hypothesis it offers, and a node for each resolution with edges to the market, the hypothesis
resolved to, the evidence and the resolution revised. Its prices are `BeliefRevision` nodes like any
holder's. [Read the graph](read-the-graph.md) queries them, and
[Graph vocabulary](../reference/graph-vocabulary.md) lists every kind.
