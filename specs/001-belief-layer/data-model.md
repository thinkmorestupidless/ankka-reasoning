# Data Model: The belief layer

What the service holds. Each kind of record is one ankka entity per record (R1 in
[research.md](research.md)). What reaches the graph is in
[contracts/graph-vocabulary.md](contracts/graph-vocabulary.md); the rules' names are in
[contracts/refusals.md](contracts/refusals.md).

## Common to every record

| Field | Type | Set by | Notes |
|---|---|---|---|
| `id` | identifier | the writer; derived for evidence | 1 to 100 characters of `[A-Za-z0-9._-]`, starting with a letter or digit (R11) |
| `dated` | instant | the writer, or the service when left out | never later than `recordedAt`; never earlier than the date of any record it links to |
| `recordedAt` | instant | the service | the endpoint's clock when the record was accepted (R4) |
| `writer` | writer | the service | who sent it (R5); never published to the graph |

Instants are held as whole milliseconds since the epoch and written in the API as ISO-8601.

A record is created once. The same record sent again under its identifier is that record; a
different one is refused. Nothing below is changed after it is held unless the table says it can be
added to or withdrawn.

## Belief layer

### Question (key value entity `question`, id = question id)

| Field | Type | Notes |
|---|---|---|
| `statement` | text | 1 to 500 characters |
| `hypotheses` | list of Hypothesis | at least two when opened; can be added to, never removed or reordered |

**Hypothesis** (held in its question): `id` (unique within the question), `statement` (1 to 500
characters), `dated`, `recordedAt`, `writer`. Referred to everywhere as `<question>/<hypothesis>`.

Rules: at least two hypotheses on opening; a hypothesis id is not reused within the question; a
hypothesis added later is dated no earlier than the question.

### Holder (key value entity `holder`, id = holder id)

| Field | Type | Notes |
|---|---|---|
| `kind` | holder kind | one the composed vocabulary names: `agent`, `person`, `model`, and `market` when the market layer is present |
| `name` | text | 1 to 200 characters |
| `writers` | set of writer | who speaks for it; starts as the writer who registered it; can be added to by one of them, never removed |

### Source (key value entity `source`, id = source id)

| Field | Type | Notes |
|---|---|---|
| `name` | text | 1 to 200 characters |

### Evidence (key value entity `evidence`, id = digest)

| Field | Type | Notes |
|---|---|---|
| `id` | 64 hex characters | SHA-256 of the source id, the locator and the excerpt, each followed by a newline |
| `source` | source id | must be held |
| `locator` | text | 1 to 2,000 characters; withdrawable |
| `excerpt` | text | 1 to `REASONING_EXCERPT_LIMIT` characters (2,000 by default); withdrawable |
| `author` | text, optional | up to 200 characters; withdrawable |
| `publishedAt` | instant, optional | not later than `dated` |
| `dated` | instant | the time it was observed |
| `withdrawal` | Withdrawal, optional | see below |

Evidence belongs to no question and no holder.

### Claim (key value entity `claim`, id = claim id)

| Field | Type | Notes |
|---|---|---|
| `holder` | holder id | must be held; the writer must speak for it |
| `statement` | text | 1 to 1,000 characters; withdrawable |
| `derivesFrom` | list of evidence id | at least one; each held and dated no later than the claim |
| `stances` | list of (hypothesis, `supports` or `contradicts`) | at least one; each hypothesis held; at most one stance per hypothesis |
| `revises` | claim id, optional | held, dated no later than the claim, and not the claim itself |
| `withdrawal` | Withdrawal, optional | |

A claim may take stances on hypotheses of more than one question. One claim may be revised by
several later claims.

### Withdrawal (held in evidence or a claim)

| Field | Type | Notes |
|---|---|---|
| `note` | text | 1 to 500 characters; why |
| `writer` | writer | who withdrew it |
| `at` | instant | the service's clock |

Withdrawing writes the record's state again with `locator`, `excerpt` and `author` (evidence) or
`statement` (claim) removed and `withdrawal` set. Everything else, the identifier and digest
included, stays. It is done once and not undone. Who may: the writer who recorded the evidence, a
writer who speaks for the claim's holder, or a steward.

### Belief (event sourced entity `belief`, id = `<holder>~<question>~<hypothesis>`, at most 302 characters)

State, kept small:

| Field | Type | Notes |
|---|---|---|
| `current` | Revision, optional | the last revision accepted |
| `count` | integer | how many revisions the line has |

Event `RevisionStated`, one per revision, and the read record `belief-revision` (key value entity,
id = revision id) that mirrors it (R8):

| Field | Type | Notes |
|---|---|---|
| `id` | identifier | the writer's |
| `holder`, `hypothesis` | ids | the belief it belongs to |
| `n` | integer | its place in the line, from 1 |
| `probability` | number | from 0 to 1 inclusive |
| `restsOn` | list of (claim id, weight optional) | may be empty; a claim at most once; a weight from 0 to 1 |
| `follows` | revision id, optional | the revision before it; absent for the first |
| `dated`, `recordedAt`, `writer` | | as for every record |
| `sequence` | integer | the event's sequence number, which is its version in the graph (read record only) |

Rules decided by the application before the command (R3): the holder is held and the writer speaks
for it; the hypothesis is held; every claim is held, takes a stance on some hypothesis of the same
question, and is dated no later than the revision.

Rules decided by the entity: `follows` names the current revision, or is absent when there is
none; `dated` is not earlier than the current revision's; a repeat of the current revision is
answered with it; with `unlessUnchanged` set, a revision whose probability equals the current one's
and that rests on no claims is answered with the current one and nothing is persisted.

```text
(no revisions) ── state a revision that follows none ──► current = r1
current = rN  ── state a revision that follows rN ─────► current = rN+1
current = rN  ── state one that follows anything else ─► refused, naming rN
```

## Market layer

### Market (key value entity `market`, id = market id)

| Field | Type | Notes |
|---|---|---|
| `question` | question id | must be held |
| `venue` | text | 1 to 200 characters |
| `id` | identifier | at most 60 characters, so the identifiers derived from it stay within 100 |
| `outcomes` | list of (outcome name, hypothesis) | at least one; a name is 1 to 20 characters of the identifier's; names unique; each hypothesis of that question and offered at most once |
| `resolutionCriteria` | text | 1 to 2,000 characters |
| `closesAt` | instant | after which no price observation is taken |
| `holder` | holder id | the holder the market speaks as; `market.<market id>`, registered when the market is opened, kind `market`, spoken for by the writer who opened it |
| `resolutions` | list of Resolution | can be added to |

**Resolution** (held in its market):

| Field | Type | Notes |
|---|---|---|
| `id` | identifier | unique within the market |
| `outcome` | outcome name, optional | absent means void |
| `evidence` | list of evidence id | at least one; each held and dated no later than the resolution |
| `authority` | text | 1 to 200 characters |
| `revises` | resolution id, optional | must be the market's current resolution when there is one; absent only when there is none |
| `dated`, `recordedAt`, `writer` | | |

The current resolution is the last in the list. Only a writer who speaks for the market's holder
may record a price observation or a resolution.

### Price observation (not a record of its own)

`outcome`, `price` (0 to 1), `observedAt`. It becomes a revision of the belief of the market's
holder in that outcome's hypothesis, resting on no claims, sent with `unlessUnchanged`. Its
revision id is `<market id>.<outcome>.<observedAt in milliseconds>`, so the same observation sent
twice is one revision. Refused when the outcome is not offered or `observedAt` is later than
`closesAt`.

## Writers

A writer is a string the service derives from the caller (R5): `service:<project>/<name>`,
`person:<subject>@<issuer>`, or `local`. Stewards are writers listed in configuration.

## What is not held

- The original of a piece of evidence: only a locator and an excerpt.
- A market's orders, positions or price series beyond the observations recorded.
- Anything about how a record came to be made: goals, tasks, model calls. That is the process
  layer.
