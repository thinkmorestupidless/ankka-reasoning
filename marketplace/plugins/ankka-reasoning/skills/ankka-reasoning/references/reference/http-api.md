# HTTP API

> Look up every route the service serves, the body each takes, the reply each gives, and how an answer is read as of a past time.

Source: https://reasoning.ankka.cloud/reference/http-api/
Every route the service serves, on port `9000` by default. Bodies and replies are JSON. A time is
an ISO-8601 instant such as `2026-05-04T09:00:00Z`. A hypothesis is named by its question and its
own identifier, `<question>/<hypothesis>`, wherever it appears in a body or a query. What a refusal
looks like, and the name of every rule, is on [Refusals](refusals.md).

## What holds for every route

A write is a `POST`, and nothing else changes anything. There is no `PUT`, `PATCH` or `DELETE`: a
record is never changed or removed, and the one exception, [withdrawing text](../build/withdraw-text.md),
is a `POST` of its own.

- **A new record answers `201`; the same record sent again answers `200`.** Both bodies are the
  record as held. A different record under an identifier already held is refused with `409`.
- **A record in a reply has what it was sent with, plus `dated`, `recordedAt` and `writer`.**
  `recordedAt` is the service's clock when it accepted the record. `writer` is who sent it, decided
  by the service from the connection and never from the request.
- **`dated` may be left out, and is then the time recorded.** For evidence and for a price
  observation the field is `observedAt`. It is never later than the service's clock.
- **A field with no value is left out of a reply.** A first revision has no `follows`; withdrawn
  evidence has no `excerpt`. Nothing is sent as `null`.
- **An identifier is 1 to 64 characters of `[A-Za-z0-9._-]`, starting with a letter or a digit.** A
  market's is at most 32 and an outcome name at most 16.
- **Who the caller is decides the writer.** Another ankka service is `service:<project>/<name>`.
  Outside a cluster every caller is `local`. A caller through the gateway is answered `401`.

```bash
curl -s localhost:9000/sources -d '{"id":"regulator","name":"the regulator"}'
```

```json
{"id":"regulator","name":"the regulator","dated":"2026-10-06T12:11:18.441Z","recordedAt":"2026-10-06T12:11:18.441Z","writer":"local"}
```

## Reading as of a time

Every route marked "graph" on this page takes two optional query parameters, each an instant:

| Parameter | The answer holds only records |
|---|---|
| `asOf` | dated at or before it |
| `asRecordedBy` | recorded at or before it |

They may be given together. With `asOf`, a claim is a revised claim only when a claim that revises
it is itself dated by then, and a belief's revision is the last in its line dated by then. An
answer as recorded by a time never changes afterwards, whatever is recorded later with an earlier
date. A source or a holder is registered and not stated, so it is shown beside a record that counts
whenever it was registered itself. [Two times on every record](../concepts/two-times.md) explains
both.

A graph route reads the graph database and answers `503` with `graph.unavailable` when none is
configured or it cannot be reached. Every other route works without it.

## Questions

| Route | Body | Reply |
|---|---|---|
| `POST /questions` | `{id, statement, hypotheses: [{id, statement}], dated?}` | the question |
| `POST /questions/{question}/hypotheses` | `{id, statement, dated?}` | the question, with the hypothesis added |
| `GET /questions/{question}` | | the question |

A question is opened with at least two hypotheses. A statement is 1 to 500 characters. A hypothesis
can be added later and is never removed; each carries its own `dated`, `recordedAt` and `writer`.

```json
{
  "id": "launch",
  "statement": "Will Company X launch Product Y this year?",
  "hypotheses": [
    {"id": "yes", "statement": "it launches this year", "dated": "2026-05-01T09:00:00Z", "recordedAt": "2026-10-06T12:11:18.282Z", "writer": "local"},
    {"id": "no", "statement": "it does not launch this year", "dated": "2026-05-01T09:00:00Z", "recordedAt": "2026-10-06T12:11:18.282Z", "writer": "local"}
  ],
  "dated": "2026-05-01T09:00:00Z",
  "recordedAt": "2026-10-06T12:11:18.282Z",
  "writer": "local"
}
```

## Holders and sources

| Route | Body | Reply |
|---|---|---|
| `POST /holders` | `{id, kind, name}` | the holder, with `writers: [writer]`; the caller is the first |
| `POST /holders/{holder}/writers` | `{writer}` | the holder |
| `GET /holders/{holder}` | | the holder |
| `POST /sources` | `{id, name}` | the source |
| `GET /sources/{source}` | | the source |

`kind` is a holder kind the [vocabulary](graph-vocabulary.md) names: `agent`, `person`, `model`,
and `market` for the holder a market speaks as. A name is 1 to 200 characters. Only a writer who
already speaks for a holder may add another, and none is ever removed. A holder and a source take
no `dated`: each is dated when it is registered.

## Evidence

| Route | Body | Reply |
|---|---|---|
| `POST /evidence` | `{source, locator, excerpt, author?, publishedAt?, observedAt?}` | the evidence, with its `id` |
| `GET /evidence/{evidence}` | | the evidence |
| `POST /evidence/{evidence}/withdrawal` | `{note}` | the evidence, withdrawn; always `200` |

The writer does not choose the `id`. It is the SHA-256 digest, as 64 hexadecimal characters, of the
source's identifier, the locator and the excerpt, so the same passage from the same place is one
record whoever records it and however often. A locator is 1 to 2,000 characters, an excerpt 1 to
`REASONING_EXCERPT_LIMIT` (2,000 by default) and an author up to 200. `publishedAt` is not later
than `observedAt`. Evidence belongs to no question and no holder.

Withdrawn evidence has no `locator`, `excerpt` or `author`, and carries
`withdrawal: {note, writer, at}`.

## Claims

| Route | Body | Reply |
|---|---|---|
| `POST /claims` | `{id, holder, statement, derivesFrom: [evidence], stances: [{hypothesis, stance}], revises?, dated?}` | the claim |
| `GET /claims/{claim}` | | `{claim, revisedBy: [claim]}` |
| `POST /claims/{claim}/withdrawal` | `{note}` | the claim, withdrawn; always `200` |

`stance` is `supports` or `contradicts`. A claim names at least one piece of evidence and takes at
least one stance, at most one on any hypothesis; the hypotheses may belong to different questions.
A statement is 1 to 1,000 characters. `revises` names one earlier claim. The writer must speak for
the holder.

`GET /claims/{claim}` wraps the claim: `revisedBy` lists the claims that revise it, oldest first,
and comes from a view that may lag a write by a moment. Nothing is decided from it.

```json
{
  "claim": {
    "id": "launch.pending",
    "holder": "agent-a",
    "statement": "approval is pending and the launch date is uncertain",
    "derivesFrom": ["4478a133ed11105e0434c1973dd5511ffe0885dff7b1abf41f9d5c9bf74facdc"],
    "stances": [{"hypothesis": "launch/yes", "stance": "contradicts"}],
    "dated": "2026-05-04T09:00:00Z",
    "recordedAt": "2026-10-06T12:11:18.460Z",
    "writer": "local"
  },
  "revisedBy": ["launch.barrier-gone"]
}
```

## Beliefs

| Route | Body | Reply |
|---|---|---|
| `POST /beliefs/revisions` | `{id, holder, hypothesis, probability, restsOn: [{claim, weight?}], follows?, dated?}` | the revision |
| `GET /beliefs/revisions/{revision}` | | the revision |
| `GET /beliefs/current?holder=&hypothesis=` | | `{holder, hypothesis, count, current?}` |
| `GET /beliefs/line?holder=&hypothesis=&before=&limit=` | | `{revisions: [revision], more}` |

A belief is one holder's probability for one hypothesis, and is not created by a route of its own:
its first revision creates it. `probability` and each `weight` are from 0 to 1. `restsOn` may be
empty. `follows` names the belief's current revision and is left out only for the first; a revision
that follows anything else is refused with `409`, and the refusal names the current one. A reply
adds `n`, the revision's place in the line from 1, and `sequence`, the version the revision is
published at in the graph.

`/beliefs/current` answers `200` with `count: 0` and no `current` for a belief nobody has stated.
`/beliefs/line` returns revisions newest first, starting at the current one or at the one before
`before` (a revision's identifier, exclusive). `limit` is from 1 to 100 and 50 when left out; `more`
says whether older revisions remain.

```json
{"id":"agent-a.launch.2","holder":"agent-a","hypothesis":"launch/yes","n":2,"probability":0.61,"restsOn":[{"claim":"launch.barrier-gone"}],"follows":"agent-a.launch.1","dated":"2026-05-11T09:00:00Z","recordedAt":"2026-10-06T12:11:18.567Z","writer":"local","sequence":2}
```

## Answers

Every answer is read from the graph, takes `asOf` and `asRecordedBy`, and is made of records and
identifiers only: no text in it was generated.

| Route (graph) | Answer |
|---|---|
| `GET /answers/belief-change?from=&to=` | `{from, to, newlyRestedOn: [support], noLongerRestedOn: [support], stillRestedOn: [{support, weightFrom?, weightTo?}], observedBetween: [evidence]}` |
| `GET /answers/belief?holder=&hypothesis=` | `{revision?, restsOn: [support]}` |
| `GET /answers/case?hypothesis=&stance=` | `{claims: [support], revisedClaims: [support]}` |
| `GET /answers/learned?question=&after=` | `{evidence: [evidence], claims: [claim], revisions: [revision]}` |
| `GET /answers/comparison?hypothesis=&a=&b=` | `{a: side, b: side, difference?, both: [{support, weightA?, weightB?}], onlyA: [support], onlyB: [support]}` |
| `GET /answers/resting-on-revised?question=` | `{beliefs: [{holder, hypothesis, revision, revisedClaims: [support]}]}` |

- **`belief-change`** explains why a belief changed. `from` and `to` are two revisions of one
  belief, named in either order; revisions of two beliefs are refused, and so is a revision dated
  or recorded later than the time asked about. `observedBetween` is the evidence behind the later
  revision's claims that was observed after the earlier revision and no later than the later one.
- **`belief`** is the current revision and what it rests on, or the one current at the time asked
  about. With no revision by then, `revision` is absent, `restsOn` is empty and the reply is `200`.
- **`case`** is the claims taking one stance on a hypothesis, oldest first. `stance` is `supports`
  or `contradicts`. Claims a later claim revises are in `revisedClaims`, apart from the rest.
- **`learned`** is what is dated later than `after`, which is required, about one question: the
  claims taking a stance on its hypotheses, the evidence they derive from, and the belief revisions
  in its hypotheses, each oldest first.
- **`comparison`** sets two holders' beliefs in one hypothesis side by side. `difference` is the
  gap between the two probabilities, never negative, and absent when either holder has no
  revision. A market is compared by naming the holder it speaks as. A `hypothesisOfB` that differs
  from `hypothesis` is refused: beliefs in two hypotheses are not compared.
- **`resting-on-revised`** lists the beliefs about a question whose current revision rests on a
  claim that has since been revised, with those claims.

### The shapes in an answer

| Shape | Fields |
|---|---|
| `revision` | `id`, `holder`, `hypothesis`, `n`, `probability`, `follows?`, `dated`, `recordedAt` |
| `claim` | `id`, `holder`, `statement?`, `dated`, `recordedAt`, `withdrawn`, `withdrawnAt?` |
| `evidence` | `id`, `source`, `locator?`, `excerpt?`, `author?`, `publishedAt?`, `dated`, `recordedAt`, `withdrawn`, `withdrawnAt?` |
| `source` | `id`, `name`, `dated`, `recordedAt` |
| `support` | `claim`, `weight?`, `evidence: [evidence]`, `revisedBy: [claim id]`, `heldBy: [holder id]` |
| `side` | `holder`, `revision?`, `statesNoReasons` |

A `support` is a claim as a reason: the claim, the weight the revision gave it where it gave one,
each piece of evidence the claim derives from with its source inside it, the claims that revise it,
and the holders whose current revisions rest on it. `statesNoReasons` is `true` for a side whose
revision rests on no claims, as a market's always does, and for a side with no revision.

A record in an answer is the record as the graph holds it, so it has no `writer`, and a withdrawn
claim or piece of evidence has `withdrawn: true`, `withdrawnAt` and no text.

```bash
curl -s 'localhost:9000/answers/belief?holder=agent-a&hypothesis=launch/yes'
```

```json
{
  "revision": {"id": "agent-a.launch.2", "holder": "agent-a", "hypothesis": "launch/yes", "n": 2, "probability": 0.61, "follows": "agent-a.launch.1", "dated": "2026-05-11T09:00:00Z", "recordedAt": "2026-10-06T12:11:18.567Z"},
  "restsOn": [
    {
      "claim": {"id": "launch.barrier-gone", "holder": "agent-a", "statement": "the approval barrier has gone", "dated": "2026-05-11T09:00:00Z", "recordedAt": "2026-10-06T12:11:18.555Z", "withdrawn": false},
      "evidence": [
        {
          "id": "fcc1d4a2479250e20568ecdf842752940e6927dfca110d39ac7d0585ea2f9b5f",
          "source": {"id": "regulator", "name": "the regulator", "dated": "2026-10-06T12:11:18.441Z", "recordedAt": "2026-10-06T12:11:18.441Z"},
          "locator": "https://regulator.example/notices/1187",
          "excerpt": "Notice 1187: Product Y is approved for sale.",
          "publishedAt": "2026-05-11T08:00:00Z",
          "dated": "2026-05-11T09:00:00Z",
          "recordedAt": "2026-10-06T12:11:18.542Z",
          "withdrawn": false
        }
      ],
      "revisedBy": [],
      "heldBy": ["agent-a"]
    }
  ]
}
```

## Markets

| Route | Body | Reply |
|---|---|---|
| `POST /markets` | `{id, question, venue, outcomes: [{outcome, hypothesis}], resolutionCriteria, closesAt, dated?}` | the market, with the `holder` it speaks as and `resolutions: []` |
| `GET /markets/{market}` | | the market, with its resolutions |
| `POST /markets/{market}/price-observations` | `{outcome, price, observedAt?}` | `{revision, added}` |
| `POST /markets/{market}/resolutions` | `{id, outcome?, evidence: [evidence], authority, revises?, dated?}` | the market |
| `GET /markets/{market}/resolution` (graph) | | `{resolution, hypothesis?, evidence: [evidence]}` |
| `GET /markets/{market}/beliefs-at-resolution` (graph) | | `{resolution, hypothesis?, beliefs: [{holder, hypothesis, revision, resolvedTo}]}` |

Opening a market registers the holder it speaks as, `market.<market>`, of kind `market`, spoken for
by the writer who opened it. A market offers at least one outcome, each for one hypothesis of its
own question. A venue and an authority are 1 to 200 characters and the resolution criteria 1 to
2,000.

A price observation is a revision of the belief of the market's holder in that outcome's
hypothesis, resting on no claims. Its identifier is `<market>.<outcome>.<observedAt in
milliseconds>`, so the same observation sent twice is one revision. `added` is `false`, and the
status `200`, when the observation was already held or the price had not moved from the current
one; `revision` is then the current revision. An observation later than `closesAt` is refused.

A resolution names at least one piece of evidence. With no `outcome` it is void. `revises` names
the market's current resolution and is left out only for the first.

The two graph reads take `asOf` and `asRecordedBy`. `resolution` answers with the market's current
resolution, the hypothesis it came to, and its evidence with sources; a market with no resolution
is answered `404` with `market.not-resolved`. `beliefs-at-resolution` gives, for each hypothesis
the market offers, each holder's last revision dated no later than the resolution, the market's own
among them, and `resolvedTo` says whether that hypothesis is the one the market came to. A
`resolution` in a reply is `{id, outcome?, void, authority, dated, recordedAt}`.

For the market `launch-market` on the launch example's question, as
[Markets and resolutions](../build/markets.md) records it:

```bash
curl -s localhost:9000/markets/launch-market/beliefs-at-resolution
```

```json
{
  "resolution": {"id": "second", "outcome": "YES", "void": false, "authority": "the Lantern exchange", "dated": "2026-10-01T12:00:00Z", "recordedAt": "2026-10-06T12:13:55.416Z"},
  "hypothesis": "launch/yes",
  "beliefs": [
    {
      "holder": "agent-a",
      "hypothesis": "launch/yes",
      "revision": {"id": "agent-a.launch.2", "holder": "agent-a", "hypothesis": "launch/yes", "n": 2, "probability": 0.61, "follows": "agent-a.launch.1", "dated": "2026-05-11T09:00:00Z", "recordedAt": "2026-10-06T12:11:18.567Z"},
      "resolvedTo": true
    },
    {
      "holder": "agent-b",
      "hypothesis": "launch/yes",
      "revision": {"id": "agent-b.launch.1", "holder": "agent-b", "hypothesis": "launch/yes", "n": 1, "probability": 0.43, "dated": "2026-05-12T09:00:00Z", "recordedAt": "2026-10-06T12:12:44.625Z"},
      "resolvedTo": true
    },
    {
      "holder": "market.launch-market",
      "hypothesis": "launch/yes",
      "revision": {"id": "launch-market.YES.1778601600000", "holder": "market.launch-market", "hypothesis": "launch/yes", "n": 2, "probability": 0.55, "follows": "launch-market.YES.1778083200000", "dated": "2026-05-12T16:00:00Z", "recordedAt": "2026-10-06T12:13:40.743Z"},
      "resolvedTo": true
    }
  ]
}
```

## The graph

| Route | Body | Reply |
|---|---|---|
| `POST /graph/wait` | `{kind, id, limitMs?}` | `{caughtUp, waitedMs, missing: [element key]}` |
| `GET /graph/vocabulary` | | `{layers: [{name, nodes, edges, holderKinds}]}` |

`/graph/wait` answers once the graph holds a record's node and every edge the record stated, at the
record's version or later, or once the limit has passed. An edge counts only when the record it
points to is in the graph too, so a reader who has waited for a record can follow every link from it. `kind` is `question`, `holder`, `source`,
`evidence`, `claim`, `revision` or `market`; a resolution is waited for through its market, and a
price observation as the revision it is. `limitMs` is 5,000 when left out and at most
`REASONING_WAIT_LIMIT_MAX_MS` (30,000 by default). Passing the limit is a `200` with
`caughtUp: false` and the keys of the elements still missing, never an error, and says nothing
about whether the record is held: it is. A record that is not held is a `404`. With no broker or no
graph database configured the route answers `503`.

```bash
curl -s localhost:9000/graph/wait -d '{"kind":"revision","id":"agent-a.launch.2"}'
```

```json
{"caughtUp":true,"waitedMs":1926,"missing":[]}
```

`/graph/vocabulary` serves [the graph vocabulary](graph-vocabulary.md) as JSON: for each layer its
kinds of node (`label`, `id`, `properties`, `publishedBy`), its kinds of edge (`type`, `from`, `to`,
`properties`, `publishedBy`) and its holder kinds. A property is `{name, type, optional}`, where
`type` is `text`, `number`, `whole number` or `flag`.
