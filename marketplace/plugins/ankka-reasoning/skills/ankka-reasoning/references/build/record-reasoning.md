# Record reasoning

> Open a question, register a holder and its sources, record evidence, state claims and state belief revisions over HTTP, in the order the links between them require.

Source: https://reasoning.ankka.cloud/build/record-reasoning/
Reasoning is recorded as six kinds of record, each sent with one `POST`: a question with its
hypotheses, a holder, a source, a piece of evidence, a claim, and a revision of a belief. This guide
sends each of them once, using the worked example the rest of this documentation shares: agent-a's
belief that Company X will launch Product Y this year, which moves from 0.38 to 0.61 when a regulator's
notice arrives.

The commands assume the service is listening on `localhost:9000`, as
[Run it on your machine](../get-started/run-locally.md) leaves it. Outside a cluster every caller is the
writer `local`, which is why `"writer": "local"` appears in every reply here. Every field of every
request is listed on [HTTP API](../reference/http-api.md).

## The order records arrive in

A record names only records that are already held, so the records of one piece of reasoning are sent
oldest link first:

| Order | Record | Names |
|---|---|---|
| 1 | question, with its hypotheses | nothing |
| 2 | holder, source | nothing |
| 3 | evidence | its source |
| 4 | claim | its holder, the evidence it derives from, the hypotheses it takes a stance on, and the claim it revises |
| 5 | belief revision | its holder, its hypothesis, the claims it rests on, and the revision it follows |

A record that names something not held is refused, and nothing is recorded. There is no way to send a
claim first and its evidence afterwards. That rule is what makes the graph free of cycles without
anything checking for them: every link points from a record to one that was there before it.

## Open a question

A question is opened with at least two hypotheses, the competing answers a belief can be held in:

```bash
curl -s localhost:9000/questions -d '{
  "id": "launch",
  "statement": "Will Company X launch Product Y this year?",
  "hypotheses": [
    { "id": "yes", "statement": "it launches this year" },
    { "id": "no", "statement": "it does not launch this year" }
  ],
  "dated": "2026-05-01T09:00:00Z"
}'
```

```json
{
  "id": "launch",
  "statement": "Will Company X launch Product Y this year?",
  "hypotheses": [
    { "id": "yes", "statement": "it launches this year", "dated": "2026-05-01T09:00:00Z", "recordedAt": "2026-10-06T12:11:18.282Z", "writer": "local" },
    { "id": "no", "statement": "it does not launch this year", "dated": "2026-05-01T09:00:00Z", "recordedAt": "2026-10-06T12:11:18.282Z", "writer": "local" }
  ],
  "dated": "2026-05-01T09:00:00Z",
  "recordedAt": "2026-10-06T12:11:18.282Z",
  "writer": "local"
}
```

The reply is the record as held: what was sent, plus `recordedAt`, the service's clock when it accepted
the record, and `writer`, who sent it. Everywhere else a hypothesis is named by its question and its own
identifier joined with a slash: `launch/yes`. A hypothesis can be added later with
`POST /questions/launch/hypotheses`; none can be removed.

## Register a holder and the sources

A holder is who holds a belief or states a claim, and a source is where evidence comes from. Both are
registered by identifier and name, and a holder also by its kind, which is one the vocabulary names:
`agent`, `person` or `model`.

```bash
curl -s localhost:9000/holders -d '{ "id": "agent-a", "kind": "agent", "name": "agent-a" }'
```

```json
{
  "id": "agent-a",
  "kind": "agent",
  "name": "agent-a",
  "writers": ["local"],
  "dated": "2026-10-06T12:11:18.420Z",
  "recordedAt": "2026-10-06T12:11:18.420Z",
  "writer": "local"
}
```

`writers` is who speaks for the holder: the writer who registered it, until one of them adds another
with `POST /holders/agent-a/writers`. Only a writer who speaks for a holder may state its claims and
beliefs. [Writers, holders and stewards](../concepts/writers-and-holders.md) explains the difference
between the two.

```bash
curl -s localhost:9000/sources -d '{ "id": "company-x-filings", "name": "Company X filings" }'
curl -s localhost:9000/sources -d '{ "id": "regulator", "name": "the regulator" }'
```

```json
{"id":"company-x-filings","name":"Company X filings","dated":"2026-10-06T12:11:18.430Z","recordedAt":"2026-10-06T12:11:18.430Z","writer":"local"}
{"id":"regulator","name":"the regulator","dated":"2026-10-06T12:11:18.441Z","recordedAt":"2026-10-06T12:11:18.441Z","writer":"local"}
```

## Record evidence

Evidence is a passage from a source: where it is (`locator`), what it says (`excerpt`), and when it was
observed. It belongs to no question and no holder, so one piece of evidence can stand behind claims
about several questions.

```bash
curl -s localhost:9000/evidence -d '{
  "source": "company-x-filings",
  "locator": "https://filings.example/company-x/2026-q1",
  "excerpt": "Regulatory approval for Product Y remains pending; no launch date is committed.",
  "author": "Company X",
  "publishedAt": "2026-05-04T07:00:00Z",
  "observedAt": "2026-05-04T09:00:00Z"
}'
```

```json
{
  "id": "4478a133ed11105e0434c1973dd5511ffe0885dff7b1abf41f9d5c9bf74facdc",
  "source": "company-x-filings",
  "locator": "https://filings.example/company-x/2026-q1",
  "excerpt": "Regulatory approval for Product Y remains pending; no launch date is committed.",
  "author": "Company X",
  "publishedAt": "2026-05-04T07:00:00Z",
  "dated": "2026-05-04T09:00:00Z",
  "recordedAt": "2026-10-06T12:11:18.449Z",
  "writer": "local"
}
```

The writer does not choose the identifier of evidence. It is the SHA-256 digest of the source, the
locator and the excerpt, so the same passage recorded twice, by anyone, is one record, and a claim names
evidence by the `id` in this reply. `observedAt` is the date of the evidence and comes back as `dated`.

The regulator's notice, a week later, is recorded the same way and answers with the identifier
`fcc1d4a2479250e20568ecdf842752940e6927dfca110d39ac7d0585ea2f9b5f`.

## State a claim

A claim is what a holder concludes from evidence. It names the evidence it derives from, takes a stance
of `supports` or `contradicts` on at least one hypothesis, and may revise an earlier claim.

```bash
curl -s localhost:9000/claims -d '{
  "id": "launch.pending",
  "holder": "agent-a",
  "statement": "approval is pending and the launch date is uncertain",
  "derivesFrom": ["4478a133ed11105e0434c1973dd5511ffe0885dff7b1abf41f9d5c9bf74facdc"],
  "stances": [{ "hypothesis": "launch/yes", "stance": "contradicts" }],
  "dated": "2026-05-04T09:00:00Z"
}'
```

```json
{
  "id": "launch.pending",
  "holder": "agent-a",
  "statement": "approval is pending and the launch date is uncertain",
  "derivesFrom": ["4478a133ed11105e0434c1973dd5511ffe0885dff7b1abf41f9d5c9bf74facdc"],
  "stances": [{ "hypothesis": "launch/yes", "stance": "contradicts" }],
  "dated": "2026-05-04T09:00:00Z",
  "recordedAt": "2026-10-06T12:11:18.460Z",
  "writer": "local"
}
```

When the holder thinks again, the earlier claim is not edited. A new claim says `revises`, and both are
kept:

```bash
curl -s localhost:9000/claims -d '{
  "id": "launch.barrier-gone",
  "holder": "agent-a",
  "statement": "the approval barrier has gone",
  "derivesFrom": ["fcc1d4a2479250e20568ecdf842752940e6927dfca110d39ac7d0585ea2f9b5f"],
  "stances": [{ "hypothesis": "launch/yes", "stance": "supports" }],
  "revises": "launch.pending",
  "dated": "2026-05-11T09:00:00Z"
}'
```

```json
{
  "id": "launch.barrier-gone",
  "holder": "agent-a",
  "statement": "the approval barrier has gone",
  "derivesFrom": ["fcc1d4a2479250e20568ecdf842752940e6927dfca110d39ac7d0585ea2f9b5f"],
  "stances": [{ "hypothesis": "launch/yes", "stance": "supports" }],
  "revises": "launch.pending",
  "dated": "2026-05-11T09:00:00Z",
  "recordedAt": "2026-10-06T12:11:18.555Z",
  "writer": "local"
}
```

A claim takes at most one stance on a hypothesis, may take stances on hypotheses of more than one
question, and is dated no earlier than the evidence it derives from, the hypotheses it takes a stance
on, or the claim it revises.

## State a belief revision

A belief is one holder's probability for one hypothesis, and it is recorded as a line of revisions. The
first revision of a belief follows none:

```bash
curl -s localhost:9000/beliefs/revisions -d '{
  "id": "agent-a.launch.1",
  "holder": "agent-a",
  "hypothesis": "launch/yes",
  "probability": 0.38,
  "restsOn": [{ "claim": "launch.pending" }],
  "dated": "2026-05-04T09:00:00Z"
}'
```

```json
{
  "id": "agent-a.launch.1",
  "holder": "agent-a",
  "hypothesis": "launch/yes",
  "n": 1,
  "probability": 0.38,
  "restsOn": [{ "claim": "launch.pending" }],
  "dated": "2026-05-04T09:00:00Z",
  "recordedAt": "2026-10-06T12:11:18.472Z",
  "writer": "local",
  "sequence": 1
}
```

Every later revision names the one it `follows`, which has to be the belief's current revision:

```bash
curl -s localhost:9000/beliefs/revisions -d '{
  "id": "agent-a.launch.2",
  "holder": "agent-a",
  "hypothesis": "launch/yes",
  "probability": 0.61,
  "restsOn": [{ "claim": "launch.barrier-gone" }],
  "follows": "agent-a.launch.1",
  "dated": "2026-05-11T09:00:00Z"
}'
```

```json
{
  "id": "agent-a.launch.2",
  "holder": "agent-a",
  "hypothesis": "launch/yes",
  "n": 2,
  "probability": 0.61,
  "restsOn": [{ "claim": "launch.barrier-gone" }],
  "follows": "agent-a.launch.1",
  "dated": "2026-05-11T09:00:00Z",
  "recordedAt": "2026-10-06T12:11:18.567Z",
  "writer": "local",
  "sequence": 2
}
```

`n` is the revision's place in the line, counted from 1. `follows` is how two writers who each read the
belief at 0.38 cannot both replace it: the second to arrive is refused and told which revision is
current.

`restsOn` names the claims the holder gives as its reasons, each at most once. It may be empty, which
records a number with no reasons. Each claim may carry a `weight` from 0 to 1, and a revision may rest
on another holder's claim. Here a second holder, agent-b, rests on a claim of its own and on agent-a's.
The holder `agent-b` and its claim `launch.tooling` are recorded first, the same way as agent-a's:

```bash
curl -s localhost:9000/beliefs/revisions -d '{
  "id": "agent-b.launch.1",
  "holder": "agent-b",
  "hypothesis": "launch/yes",
  "probability": 0.43,
  "restsOn": [
    { "claim": "launch.tooling", "weight": 0.6 },
    { "claim": "launch.barrier-gone", "weight": 0.4 }
  ],
  "dated": "2026-05-12T09:00:00Z"
}' -w '\nHTTP %{http_code}\n'
```

```text
{"id":"agent-b.launch.1","holder":"agent-b","hypothesis":"launch/yes","n":1,"probability":0.43,"restsOn":[{"claim":"launch.tooling","weight":0.6},{"claim":"launch.barrier-gone","weight":0.4}],"dated":"2026-05-12T09:00:00Z","recordedAt":"2026-10-06T12:12:44.625Z","writer":"local","sequence":1}
HTTP 201
```

Every claim a revision rests on takes a stance on some hypothesis of the same question, and is dated no
later than the revision.

## The same record sent again is the record held

A write is safe to repeat. Creating a record answers `201`; the same record sent again under its
identifier answers `200` with the record already held, and nothing is recorded twice. The revision of
agent-b's belief, sent a second time with the same command:

```text
{"id":"agent-b.launch.1","holder":"agent-b","hypothesis":"launch/yes","n":1,"probability":0.43,"restsOn":[{"claim":"launch.tooling","weight":0.6},{"claim":"launch.barrier-gone","weight":0.4}],"dated":"2026-05-12T09:00:00Z","recordedAt":"2026-10-06T12:12:44.625Z","writer":"local","sequence":1}
HTTP 200
```

`recordedAt` is the first time's. A writer that does not know whether its request arrived sends it
again.

A different record under an identifier that is held is not a repeat, and is refused:

```bash
curl -s localhost:9000/sources -d '{ "id": "regulator", "name": "a different regulator" }'
```

```json
{
  "rule": "id.held-by-another",
  "status": 409,
  "error": "The identifier 'regulator' is held by a different source.",
  "names": { "source": "regulator" }
}
```

## Dates, stated or left out

Every record has two times. `dated` is when the thing happened: when the evidence was observed, when
the claim was stated, when the belief was held. `recordedAt` is when the service accepted the record,
and the writer cannot set it.

A writer entering history states the date: `dated` on a question, a claim and a revision, and
`observedAt` on evidence. Left out, the date is the time recorded, which is right for a writer
recording things as they happen. A holder and a source have no date a writer states; both are dated
when they are registered.

A stated date is never later than the service's clock, and never earlier than the date of a record the
new record links to. The second is checked by name:

```bash
curl -s localhost:9000/claims -d '{
  "id": "launch.early",
  "holder": "agent-a",
  "statement": "approval was certain all along",
  "derivesFrom": ["fcc1d4a2479250e20568ecdf842752940e6927dfca110d39ac7d0585ea2f9b5f"],
  "stances": [{ "hypothesis": "launch/yes", "stance": "supports" }],
  "dated": "2026-05-02T09:00:00Z"
}'
```

```json
{
  "rule": "claim.dated.not-before-evidence",
  "status": 422,
  "error": "A claim cannot be dated before the evidence it derives from.",
  "names": {
    "claim": "launch.early",
    "evidence": "fcc1d4a2479250e20568ecdf842752940e6927dfca110d39ac7d0585ea2f9b5f",
    "dated": "2026-05-02T09:00:00Z"
  }
}
```

[Two times on every record](../concepts/two-times.md) says what the two times are for.

## When a record is refused

A refusal changes nothing and says which rule was broken. `rule` is stable and is what a client matches
on; `error` is a sentence for a person; `names` holds the identifiers and values that broke the rule.

A claim that names evidence that is not held:

```bash
curl -s localhost:9000/claims -d '{
  "id": "launch.unfounded",
  "holder": "agent-a",
  "statement": "a rival will launch first",
  "derivesFrom": ["0000000000000000000000000000000000000000000000000000000000000000"],
  "stances": [{ "hypothesis": "launch/yes", "stance": "contradicts" }]
}'
```

```json
{
  "rule": "claim.derives-from.not-held",
  "status": 422,
  "error": "No evidence '0000000000000000000000000000000000000000000000000000000000000000' is held.",
  "names": {
    "claim": "launch.unfounded",
    "evidence": "0000000000000000000000000000000000000000000000000000000000000000"
  }
}
```

A revision that follows a revision that is no longer current:

```bash
curl -s localhost:9000/beliefs/revisions -d '{
  "id": "agent-a.launch.3",
  "holder": "agent-a",
  "hypothesis": "launch/yes",
  "probability": 0.7,
  "follows": "agent-a.launch.1"
}'
```

```json
{
  "rule": "belief.follows.not-current",
  "status": 409,
  "error": "A revision follows the belief's current revision, which is 'agent-a.launch.2'.",
  "names": {
    "revision": "agent-a.launch.3",
    "current": "agent-a.launch.2",
    "follows": "agent-a.launch.1"
  }
}
```

The writer reads `names.current`, decides whether its revision still stands, and sends it again
following that one. Every rule and its status is on [Refusals](../reference/refusals.md). A body the
service cannot read at all, malformed JSON or a missing field, answers `400` with no `rule`.

## Read a record back

Every record is read back by its identifier from the service's own store, with no graph database
involved: `GET /questions/{question}`, `/holders/{holder}`, `/sources/{source}`,
`/evidence/{evidence}`, `/claims/{claim}` and `/beliefs/revisions/{revision}`. A claim comes back with
the claims that revise it:

```bash
curl -s localhost:9000/claims/launch.pending
```

```json
{
  "claim": {
    "id": "launch.pending",
    "holder": "agent-a",
    "statement": "approval is pending and the launch date is uncertain",
    "derivesFrom": ["4478a133ed11105e0434c1973dd5511ffe0885dff7b1abf41f9d5c9bf74facdc"],
    "stances": [{ "hypothesis": "launch/yes", "stance": "contradicts" }],
    "dated": "2026-05-04T09:00:00Z",
    "recordedAt": "2026-10-06T12:11:18.460Z",
    "writer": "local"
  },
  "revisedBy": ["launch.barrier-gone"]
}
```

A belief is read as its current revision, which is what a writer needs before it states the one that
follows:

```bash
curl -s 'localhost:9000/beliefs/current?holder=agent-a&hypothesis=launch/yes'
```

```json
{
  "holder": "agent-a",
  "hypothesis": "launch/yes",
  "count": 2,
  "current": {
    "id": "agent-a.launch.2",
    "holder": "agent-a",
    "hypothesis": "launch/yes",
    "n": 2,
    "probability": 0.61,
    "restsOn": [{ "claim": "launch.barrier-gone" }],
    "follows": "agent-a.launch.1",
    "dated": "2026-05-11T09:00:00Z",
    "recordedAt": "2026-10-06T12:11:18.567Z",
    "writer": "local",
    "sequence": 2
  }
}
```

`GET /beliefs/line?holder=&hypothesis=` returns the whole line, newest first. A record that is not held
answers `404` with the rule `record.not-held`.

These reads say what is held. Why a belief changed, what was believed on a past day and where two
holders disagree are answers read from the graph:
[Ask for explanations](ask-for-explanations.md).
