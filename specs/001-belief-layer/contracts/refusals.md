# Contract: refusals

What the service says when it does not accept a write or cannot answer a read, on any route in
[http-api.md](http-api.md). A refusal changes nothing: no record, and nothing in the graph.

## The body

```json
{
  "status": 422,
  "error": "A claim cannot be dated before the evidence it derives from.",
  "rule": "claim.dated.not-before-evidence",
  "names": { "claim": "c2", "evidence": "9f2c…", "dated": "2026-05-03T00:00:00Z" }
}
```

- **F1.** `rule` is stable and is what a client or a test matches on. `error` is a sentence for a
  person and may be reworded.
- **F2.** `names` holds the identifiers and values that broke the rule: the record refused and
  whatever it named that was wrong.
- **F3.** A request the service cannot read (malformed JSON, a missing required field, a value of
  the wrong type) is ankka's own `400` and has no `rule`.

## Statuses

| Status | Means |
|---|---|
| `401` | The caller could not be identified as a writer |
| `403` | The writer does not speak for the holder, or may not withdraw the record |
| `404` | A read named a record that is not held |
| `409` | The identifier is held by a different record, or the revision does not follow the current one |
| `422` | The record breaks a rule |
| `503` | A route that reads the graph database could not reach it |

## Rules

**Any record**

| Rule | Status | When |
|---|---|---|
| `id.format` | 422 | an identifier is not 1 to 64 characters of `[A-Za-z0-9._-]` starting with a letter or digit, or is over the shorter limit of its kind (a market's 32, an outcome name's 16); `names.limit` gives it |
| `id.held-by-another` | 409 | the identifier is held by a record that differs from the one sent |
| `dated.later-than-now` | 422 | `dated` or `observedAt` is later than the service's clock; `names.now` gives it |
| `text.length` | 422 | a text field is empty or over its limit, an evidence's excerpt over `REASONING_EXCERPT_LIMIT` included; `names` gives the field and the limit |

**Questions and holders**

| Rule | Status | When |
|---|---|---|
| `question.hypotheses.at-least-two` | 422 | a question is opened with fewer than two hypotheses |
| `question.not-held` | 422 | a hypothesis is added to a question that is not held |
| `question.hypothesis.dated-before-question` | 422 | a hypothesis is dated before its question |
| `holder.not-held` | 422 | a writer is added to a holder that is not held |
| `holder.kind.not-in-vocabulary` | 422 | the kind is not a holder kind the vocabulary names |
| `holder.writer.does-not-speak` | 403 | the writer does not speak for the holder it writes as, or adds a writer to |

**Evidence**

| Rule | Status | When |
|---|---|---|
| `evidence.source.not-held` | 422 | the source is not registered |
| `evidence.observed-before-published` | 422 | `observedAt` is earlier than `publishedAt` |

**Claims**

| Rule | Status | When |
|---|---|---|
| `claim.holder.not-held` | 422 | |
| `claim.derives-from.none` | 422 | no evidence is named |
| `claim.derives-from.not-held` | 422 | a named piece of evidence is not held |
| `claim.stance.none` | 422 | no stance is taken |
| `claim.stance.hypothesis-not-held` | 422 | |
| `claim.stance.unknown` | 422 | a stance is neither `supports` nor `contradicts` |
| `claim.stance.twice-on-one-hypothesis` | 422 | two stances are taken on one hypothesis |
| `claim.revises.not-held` | 422 | |
| `claim.revises.itself` | 422 | |
| `claim.dated.not-before-hypothesis` | 422 | the claim is dated before a hypothesis it takes a stance on |
| `claim.dated.not-before-evidence` | 422 | the claim is dated before a piece of evidence it derives from |
| `claim.dated.not-before-revised` | 422 | the claim is dated before the claim it revises |

**Beliefs**

| Rule | Status | When |
|---|---|---|
| `belief.holder.not-held` | 422 | |
| `belief.hypothesis.not-held` | 422 | |
| `belief.probability.range` | 422 | the probability is not from 0 to 1 |
| `belief.weight.range` | 422 | a weight is not from 0 to 1 |
| `belief.rests-on.not-held` | 422 | a named claim is not held |
| `belief.rests-on.claim-twice` | 422 | a claim is named twice |
| `belief.rests-on.other-question` | 422 | a named claim takes no stance on a hypothesis of the belief's question |
| `belief.dated.not-before-hypothesis` | 422 | the revision is dated before its hypothesis |
| `belief.dated.not-before-claim` | 422 | the revision is dated before a claim it rests on |
| `belief.dated.not-before-current` | 422 | the revision is dated before the one it follows |
| `belief.follows.not-current` | 409 | `follows` is not the current revision; `names.current` gives it |

**Withdrawal**

| Rule | Status | When |
|---|---|---|
| `withdrawal.note.none` | 422 | no note is given |
| `withdrawal.writer.may-not` | 403 | the writer did not record the evidence, does not speak for the claim's holder, and is not a steward |

A withdrawal of a record already withdrawn answers `200` with the record: it is a repeat.

**Markets**

| Rule | Status | When |
|---|---|---|
| `market.question.not-held` | 422 | |
| `market.not-held` | 422 | a price observation or a resolution names a market that is not held |
| `market.dated.not-before-hypothesis` | 422 | the market is dated before a hypothesis it offers an outcome for |
| `market.outcome.none` | 422 | a market is opened with no outcome |
| `market.outcome.name-twice` | 422 | two outcomes have one name |
| `market.outcome.hypothesis-twice` | 422 | two outcomes are for one hypothesis |
| `market.outcome.hypothesis-of-other-question` | 422 | an outcome is for a hypothesis that is not one of the market's question |
| `market.outcome.not-offered` | 422 | a price observation or a resolution names an outcome the market does not offer |
| `market.price.range` | 422 | the price is not from 0 to 1 |
| `market.price.after-close` | 422 | `observedAt` is later than `closesAt`; `names.closesAt` gives it |
| `market.resolution.evidence.none` | 422 | |
| `market.resolution.evidence.not-held` | 422 | |
| `market.resolution.dated.not-before-evidence` | 422 | |
| `market.resolution.dated.not-before-market` | 422 | the resolution is dated before its market |
| `market.resolution.revises.not-current` | 409 | `revises` is not the market's current resolution, or is absent when there is one |

**Reads**

| Rule | Status | When |
|---|---|---|
| `answer.revisions.of-two-beliefs` | 422 | `from` and `to` are revisions of different beliefs; `names` gives both beliefs |
| `answer.beliefs.in-two-hypotheses` | 422 | a comparison names beliefs in different hypotheses |
| `answer.revision.later-than-asked` | 422 | a revision named by `from` or `to` is dated later than `asOf`, or was recorded later than `asRecordedBy`; `names` gives both of its times |
| `time.format` | 422 | `asOf`, `asRecordedBy` or `after` is not an ISO-8601 instant, or `after` is left out; `names` gives the parameter |
| `market.not-resolved` | 404 | the resolution of a market that has none is asked for |
| `record.not-held` | 404 | a read names a record that is not held; `names` gives its kind and identifier |
| `command.refused` | as the entity says | an entity refused a command with no rule of its own; it should not occur |
| `graph.unavailable` | 503 | |

## Properties a test holds

- Every rule on this page is broken by at least one test, which asserts on `rule` and on the
  `names` the scenario says the refusal names.
- After every refused write in the test set, the count of held records and, once caught up, the
  graph are what they were before.
