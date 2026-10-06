# Contract: the HTTP API

Every route the service serves. Bodies are JSON. A time is an ISO-8601 instant
(`2026-05-04T09:00:00Z`). A hypothesis is written `<question>/<hypothesis>`. What a refusal looks
like, and every rule's name, is in [refusals.md](refusals.md); who the caller is taken to be is in
[deployment.md](deployment.md). The fields of each record are in
[../data-model.md](../data-model.md).

## Rules for every route

- **H1.** A write is `POST`. Creating something new answers `201`; the same record sent again
  answers `200` with the record already held. Both bodies are the record as held.
- **H2.** A record in a reply has the fields it was sent with, plus `id`, `dated`, `recordedAt` and
  `writer`. Withdrawn text is absent and `withdrawal` is present.
- **H3.** `dated` (or `observedAt` for evidence and price observations) may be left out and is then
  the time recorded.
- **H4.** No route changes or removes a record. There is no `PUT`, `PATCH` or `DELETE`.
- **H5.** A route under `/answers`, and the two market reads marked "graph", read the graph
  database. They take `asOf` and `asRecordedBy`, both optional instants, and answer `503` when the
  graph database is not configured or cannot be reached. Every other route works without it.
- **H6.** An answer contains records and identifiers only. Each record in an answer has `dated` and
  `recordedAt`.
- **H7.** With `asOf`, no stated record in the answer is dated later; with `asRecordedBy`, none was
  recorded later. A source or a holder is registered and not stated: it is shown beside a record
  that counts whenever it was registered itself.

## Belief layer: writes and read-back

| Route | Body | Reply |
|---|---|---|
| `POST /questions` | `{id, statement, hypotheses: [{id, statement}], dated?}` | the question |
| `POST /questions/{question}/hypotheses` | `{id, statement, dated?}` | the question |
| `GET /questions/{question}` | | the question |
| `POST /holders` | `{id, kind, name}` | the holder; the caller speaks for it |
| `POST /holders/{holder}/writers` | `{writer}` | the holder |
| `GET /holders/{holder}` | | the holder |
| `POST /sources` | `{id, name}` | the source |
| `GET /sources/{source}` | | the source |
| `POST /evidence` | `{source, locator, excerpt, author?, publishedAt?, observedAt?}` | the evidence, with its `id` |
| `GET /evidence/{evidence}` | | the evidence |
| `POST /evidence/{evidence}/withdrawal` | `{note}` | the evidence, withdrawn |
| `POST /claims` | `{id, holder, statement, derivesFrom: [evidence], stances: [{hypothesis, stance}], revises?, dated?}` | the claim |
| `GET /claims/{claim}` | | `{claim, revisedBy: [claim id]}`: the claim, and the claims that revise it, empty when none does |
| `POST /claims/{claim}/withdrawal` | `{note}` | the claim, withdrawn |
| `POST /beliefs/revisions` | `{id, holder, hypothesis, probability, restsOn: [{claim, weight?}], follows?, dated?}` | the revision, with its `n` and its `sequence` |
| `GET /beliefs/revisions/{revision}` | | the revision |
| `GET /beliefs/current?holder=&hypothesis=` | | `{holder, hypothesis, count, current?}` |
| `GET /beliefs/line?holder=&hypothesis=&before=&limit=` | | `{revisions: [...], more}`: newest first, from `before` (a revision id, exclusive) or the head; `limit` 50 by default, and a `limit` over 100 is taken as 100 |

`stance` is `supports` or `contradicts`. `kind` is a holder kind in the vocabulary.
`GET /claims/{claim}`'s `revisedBy` comes from a view over the claims, queried by the claim they
revise. It is the one read-back that may lag a write by a moment; nothing is decided from it.

## Belief layer: answers (graph)

| Route | Answer |
|---|---|
| `GET /answers/belief-change?from=&to=` | `{from: revision, to: revision, newlyRestedOn: [support], noLongerRestedOn: [support], stillRestedOn: [{support, weightFrom?, weightTo?}], observedBetween: [evidence]}` |
| `GET /answers/belief?holder=&hypothesis=` | `{revision?, restsOn: [support]}`: the current revision, or the one current at `asOf` |
| `GET /answers/case?hypothesis=&stance=` | `{claims: [support], revisedClaims: [support]}` |
| `GET /answers/learned?question=&after=` | `{evidence: [...], claims: [...], revisions: [...]}`: each dated later than `after`, oldest first |
| `GET /answers/comparison?hypothesis=&a=&b=&hypothesisOfB=?` | `{a: side, b: side, difference, both: [{support, weightA?, weightB?}], onlyA: [support], onlyB: [support]}` |
| `GET /answers/resting-on-revised?question=` | `{beliefs: [{holder, hypothesis, revision, revisedClaims: [support]}]}` |

A `support` is `{claim, weight?, evidence: [evidence], revisedBy: [claim], heldBy: [holder]}`, each piece of evidence with its `source` inside it,
where `claim`, `evidence` and `source` are records. `revisedBy` and `heldBy` (the holders whose current
revisions rest on the claim) are on every support. A `side` is `{holder, revision?, statesNoReasons}`;
a holder with no revision, or one that is not held, is a side with no revision.
`difference` is the gap between the two probabilities, never negative, and absent when either side has no revision; which is the greater is plain from the two.

`from` and `to` are revision ids of one belief, in either order of the line; ids of two beliefs are
refused, and so is a revision that does not count at the time asked about. `a` and `b` are holders;
`hypothesisOfB` may name a second hypothesis only to be told that beliefs in two are not compared. With no revision at the time asked about, `revision` is absent and the reply is `200`.

## Market layer

| Route | Body | Reply |
|---|---|---|
| `POST /markets` | `{id, question, venue, outcomes: [{outcome, hypothesis}], resolutionCriteria, closesAt, dated?}` | the market, with the `holder` it speaks as |
| `GET /markets/{market}` | | the market, with its resolutions |
| `POST /markets/{market}/price-observations` | `{outcome, price, observedAt?}` | `{revision, added}`: the market's current revision for that outcome; `added` is `false` when the price had not moved |
| `POST /markets/{market}/resolutions` | `{id, outcome?, evidence: [evidence], authority, revises?, dated?}` | the market |
| `GET /markets/{market}/resolution` (graph) | | `{resolution, hypothesis?, evidence: [evidence]}`, each piece of evidence with its `source` inside it: why it was resolved; `404` with `market.not-resolved` when it is not, or when no such market is held |
| `GET /markets/{market}/beliefs-at-resolution` (graph) | | `{resolution, hypothesis?, beliefs: [{holder, hypothesis, revision, resolvedTo}]}`: each holder's last revision dated at or before the resolution, for each hypothesis the market offers |

A market's `id` is at most 32 characters and an outcome name is 1 to 16 characters of `[A-Za-z0-9._-]`. A resolution with no `outcome` is void.
A market is compared with a holder through `GET /answers/comparison`, naming the market's `holder`.

## The graph

| Route | Body | Reply |
|---|---|---|
| `POST /graph/wait` | `{kind, id, limitMs?}` | `{caughtUp, waitedMs, missing: [element key]}`; an edge is missing until the record it points to is in the graph too |
| `GET /graph/vocabulary` | | the vocabulary: `{layers: [{name, nodes: [...], edges: [...], holderKinds: [...]}]}` |

`kind` is `question`, `holder`, `source`, `evidence`, `claim`, `revision` or `market`. The wait
ends when the graph holds the record's node and every edge it stated at the record's version or
later, or when `limitMs` passes (5,000 by default, 30,000 at most). Passing the limit is a `200`
with `caughtUp: false`, never an error, and says nothing about whether the record is held: it is.

## Properties a test holds

- Every scenario's steps reach the service through these routes and no other way.
- For every write route, the same body sent twice gives `201` then `200` with equal bodies, and one
  record.
- No reply from an `/answers` route, in any scenario, contains a string that is not a property of a
  held record, an identifier, or a field name on this page.
- With the graph database stopped, every route outside H5 answers as it does with it running.
