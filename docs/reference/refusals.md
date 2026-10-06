---
title: Refusals
description: Look up what the service answers when it does not accept a write or cannot answer a read, with the status and the name of every rule.
kind: reference
layers: [belief, market]
related: [reference/http-api.md, build/record-reasoning.md, concepts/writers-and-holders.md]
---

# Refusals

A refusal is what the service answers when it does not accept a write or cannot answer a read, on
any route of the [HTTP API](http-api.md). It names the rule that was broken, and it changes
nothing: no record is held, and nothing reaches the graph.

## The body

A refusal is a JSON object with four fields:

```bash
curl -s localhost:9000/beliefs/revisions -d '{"id":"agent-a.launch.3","holder":"agent-a","hypothesis":"launch/yes","probability":0.7,"follows":"agent-a.launch.1"}'
```

```json
{
  "rule": "belief.follows.not-current",
  "status": 409,
  "error": "A revision follows the belief's current revision, which is 'agent-a.launch.2'.",
  "names": {"revision": "agent-a.launch.3", "current": "agent-a.launch.2", "follows": "agent-a.launch.1"}
}
```

| Field | Meaning |
|---|---|
| `rule` | The rule's name. It is stable, and is what a client or a test matches on. |
| `status` | The HTTP status, the same as the reply's own. |
| `error` | A sentence for a person. It may be reworded; do not match on it. |
| `names` | The identifiers and values that broke the rule: the record refused and whatever it named that was wrong. Every value is a string. |

A request the service cannot read at all, such as malformed JSON, a missing required field, a
value of the wrong type or a missing required query parameter, is ankka's own `400`. Its body has
`status` and `error` and no `rule`.

## Statuses

| Status | Means |
|---|---|
| `400` | The request could not be read. No `rule`. |
| `401` | The caller could not be identified as a writer. Today that is every caller through the gateway. |
| `403` | The writer does not speak for the holder, or may not withdraw the record. |
| `404` | A read, a wait or a withdrawal named a record that is not held. |
| `409` | The identifier is held by a different record, or a revision or resolution does not follow the current one. |
| `422` | The record, or the question asked, breaks a rule. |
| `503` | A route that reads the graph database could not reach it. |

## Rules for any record

| Rule | Status | When |
|---|---|---|
| `id.format` | 422 | An identifier is not 1 to 64 characters of `[A-Za-z0-9._-]` starting with a letter or a digit, or is over the shorter limit of its kind: 32 for a market, 16 for an outcome name. `names.limit` gives the limit. |
| `id.held-by-another` | 409 | The identifier is held by a record that differs from the one sent. The same record sent again is not a refusal: it answers `200`. |
| `dated.later-than-now` | 422 | `dated` or `observedAt` is later than the service's clock. `names.now` gives the clock. |
| `text.length` | 422 | A text field is empty or over its limit, an excerpt over `REASONING_EXCERPT_LIMIT` included. `names.field` and `names.limit` give the field and the limit. |

## Questions and holders

| Rule | Status | When |
|---|---|---|
| `question.hypotheses.at-least-two` | 422 | A question is opened with fewer than two hypotheses. |
| `question.not-held` | 422 | A hypothesis is added to a question that is not held. |
| `question.hypothesis.dated-before-question` | 422 | A hypothesis is dated before its question. |
| `holder.kind.not-in-vocabulary` | 422 | The kind is not a holder kind the vocabulary names. |
| `holder.not-held` | 422 | A writer is added to a holder that is not held. |
| `holder.writer.does-not-speak` | 403 | The writer does not speak for the holder it writes as, or for the holder it adds a writer to. For a market, the writer does not speak for the holder the market speaks as. |

## Evidence

| Rule | Status | When |
|---|---|---|
| `evidence.source.not-held` | 422 | The source is not registered. |
| `evidence.observed-before-published` | 422 | `observedAt` is earlier than `publishedAt`. |

## Claims

| Rule | Status | When |
|---|---|---|
| `claim.holder.not-held` | 422 | The holder is not registered. |
| `claim.derives-from.none` | 422 | No evidence is named. |
| `claim.derives-from.not-held` | 422 | A named piece of evidence is not held. |
| `claim.stance.none` | 422 | No stance is taken. |
| `claim.stance.hypothesis-not-held` | 422 | A stance is on a hypothesis that is not held. |
| `claim.stance.unknown` | 422 | A stance is neither `supports` nor `contradicts`. Also the refusal of `GET /answers/case` with any other `stance`. |
| `claim.stance.twice-on-one-hypothesis` | 422 | Two stances are taken on one hypothesis. |
| `claim.revises.not-held` | 422 | The claim named by `revises` is not held. |
| `claim.revises.itself` | 422 | `revises` names the claim itself. |
| `claim.dated.not-before-hypothesis` | 422 | The claim is dated before a hypothesis it takes a stance on. |
| `claim.dated.not-before-evidence` | 422 | The claim is dated before a piece of evidence it derives from. |
| `claim.dated.not-before-revised` | 422 | The claim is dated before the claim it revises. |

## Beliefs

| Rule | Status | When |
|---|---|---|
| `belief.holder.not-held` | 422 | The holder is not registered. |
| `belief.hypothesis.not-held` | 422 | The hypothesis is not held. |
| `belief.probability.range` | 422 | The probability is not from 0 to 1. |
| `belief.weight.range` | 422 | A weight is not from 0 to 1. |
| `belief.rests-on.not-held` | 422 | A named claim is not held. |
| `belief.rests-on.claim-twice` | 422 | A claim is named twice. |
| `belief.rests-on.other-question` | 422 | A named claim takes no stance on any hypothesis of the belief's question. |
| `belief.dated.not-before-hypothesis` | 422 | The revision is dated before its hypothesis. |
| `belief.dated.not-before-claim` | 422 | The revision is dated before a claim it rests on. |
| `belief.dated.not-before-current` | 422 | The revision is dated before the revision it follows. |
| `belief.follows.not-current` | 409 | `follows` is not the belief's current revision, or is left out when there is one. `names.current` gives the current revision. |

## Withdrawal

| Rule | Status | When |
|---|---|---|
| `withdrawal.note.none` | 422 | No note is given, or it is blank. |
| `withdrawal.writer.may-not` | 403 | The writer did not record the evidence, does not speak for the claim's holder, and is not a steward. |

Withdrawing a record that is already withdrawn is not a refusal. It answers `200` with the record,
as any repeat does.

## Markets

| Rule | Status | When |
|---|---|---|
| `market.question.not-held` | 422 | The market's question is not held. |
| `market.not-held` | 422 | A price observation or a resolution names a market that is not held. |
| `market.outcome.none` | 422 | A market is opened with no outcome. |
| `market.outcome.name-twice` | 422 | Two outcomes have one name. |
| `market.outcome.hypothesis-twice` | 422 | Two outcomes are for one hypothesis. |
| `market.outcome.hypothesis-of-other-question` | 422 | An outcome is for a hypothesis that is not one of the market's question. |
| `market.dated.not-before-hypothesis` | 422 | The market is dated before a hypothesis it offers an outcome for. |
| `market.outcome.not-offered` | 422 | A price observation or a resolution names an outcome the market does not offer. |
| `market.price.range` | 422 | The price is not from 0 to 1. |
| `market.price.after-close` | 422 | `observedAt` is later than the market's closing time. `names.closesAt` gives it. |
| `market.resolution.evidence.none` | 422 | A resolution names no evidence. |
| `market.resolution.evidence.not-held` | 422 | A named piece of evidence is not held. |
| `market.resolution.dated.not-before-evidence` | 422 | The resolution is dated before a piece of evidence it names. |
| `market.resolution.dated.not-before-market` | 422 | The resolution is dated before its market. |
| `market.resolution.revises.not-current` | 409 | `revises` is not the market's current resolution, or is left out when there is one, or is given when there is none. `names.current` gives the current resolution when there is one. |

## Reads

| Rule | Status | When |
|---|---|---|
| `record.not-held` | 404 | A read, a wait or a withdrawal names a record that is not held. `names` gives its kind and identifier. |
| `answer.revisions.of-two-beliefs` | 422 | `from` and `to` of `GET /answers/belief-change` are revisions of different beliefs. `names` gives both beliefs. |
| `answer.revision.later-than-asked` | 422 | A revision named by `from` or `to` is dated later than `asOf`, or was recorded later than `asRecordedBy`. `names` gives the revision and both of its times. |
| `answer.beliefs.in-two-hypotheses` | 422 | `GET /answers/comparison` names a second hypothesis that differs from the first. |
| `time.format` | 422 | `asOf`, `asRecordedBy` or `after` is not an ISO-8601 instant, or `after` is left out. `names` gives the parameter and what was sent. |
| `market.not-resolved` | 404 | The resolution of a market that has none, or of a market that is not held, is asked for. |
| `graph.unavailable` | 503 | A route that reads the graph database has none configured or could not reach it, or `POST /graph/wait` is asked of a service with no broker. |
| `command.refused` | as the entity says | An entity refused a command with no rule of its own. It should not occur; report it if it does. |

## What a refusal leaves behind

A refused write leaves nothing: the count of held records is what it was, and once the graph has
caught up it holds what it held before. A client can therefore correct the record and send it
again under the same identifier. Because the same record sent again answers `200` and not a
refusal, a client that is unsure whether a write arrived sends it again and reads the status.
