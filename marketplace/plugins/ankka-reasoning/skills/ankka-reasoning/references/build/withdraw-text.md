# Withdraw text

> Take the text out of a piece of evidence or a claim with a note saying why, and know what stays, who may do it, and where and when the text is gone.

Source: https://reasoning.ankka.cloud/build/withdraw-text/
Withdrawing is the one way a held record changes. The text a writer supplied, an excerpt that should
not have been copied or a statement that names someone, is taken out, and the record stays: its
identifier, its links, its dates and its place in every answer. Two kinds of record carry such text and
can have it withdrawn:

| Record | Route | Text taken out | What stays |
|---|---|---|---|
| evidence | `POST /evidence/{evidence}/withdrawal` | `locator`, `excerpt`, `author` | identifier, source, `publishedAt`, dates, every claim that derives from it |
| claim | `POST /claims/{claim}/withdrawal` | `statement` | identifier, holder, the evidence it derives from, its stances, what it revises, dates |

A withdrawal is done once and is not undone. There is no route that puts text back, and no route that
removes a record.

## The records used here

This guide withdraws the text of one piece of evidence and of one claim made for the purpose, on the
question `launch` from [Record reasoning](record-reasoning.md). The evidence quotes a named person:

```bash
curl -s localhost:9000/evidence -d '{
  "source": "trade-press",
  "locator": "https://trade.example/2026/05/09/launch-team",
  "excerpt": "J. Doe, an engineer at Company X, says the launch team has been reassigned.",
  "author": "A. Reporter",
  "observedAt": "2026-05-09T10:00:00Z"
}'
```

```json
{
  "id": "1335475a76d0e9b7abf4a2c6f90eef64305e6c53121ea2ca83f1c5bce4681c99",
  "source": "trade-press",
  "locator": "https://trade.example/2026/05/09/launch-team",
  "excerpt": "J. Doe, an engineer at Company X, says the launch team has been reassigned.",
  "author": "A. Reporter",
  "dated": "2026-05-09T10:00:00Z",
  "recordedAt": "2026-10-06T12:14:45.933Z",
  "writer": "local"
}
```

The claim `launch.rumour`, by agent-b, derives from it, contradicts `launch/yes`, and states "J. Doe
says the launch team has been reassigned". Once both have reached the graph, the graph database holds
the text too:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (c:Claim {id: 'claim:launch.rumour'})-[:DERIVES_FROM]->(e:Evidence) RETURN c.statement, c.withdrawn, e.excerpt, e.author, e.withdrawn"
```

```text
c.statement, c.withdrawn, e.excerpt, e.author, e.withdrawn
"J. Doe says the launch team has been reassigned", FALSE, "J. Doe, an engineer at Company X, says the launch team has been reassigned.", "A. Reporter", FALSE
```

## Withdraw a claim's statement

A withdrawal is a `POST` with a note that says why. The reply is the claim as it is now held, with no
`statement` and with `withdrawal` set:

```bash
curl -s localhost:9000/claims/launch.rumour/withdrawal -d '{ "note": "names a person who asked not to be named" }'
```

```json
{
  "id": "launch.rumour",
  "holder": "agent-b",
  "derivesFrom": ["1335475a76d0e9b7abf4a2c6f90eef64305e6c53121ea2ca83f1c5bce4681c99"],
  "stances": [{ "hypothesis": "launch/yes", "stance": "contradicts" }],
  "dated": "2026-05-09T10:00:00Z",
  "recordedAt": "2026-10-06T12:14:46.026Z",
  "writer": "local",
  "withdrawal": {
    "note": "names a person who asked not to be named",
    "writer": "local",
    "at": "2026-10-06T12:14:49.032Z"
  }
}
```

The note is kept with who withdrew the text and when, so a reader who finds a claim with no statement
can see that it was withdrawn and why. The note is itself text that stays, so it says why without
repeating what was taken out. A withdrawal with no note is refused:

```bash
curl -s localhost:9000/claims/launch.rumour/withdrawal -d '{}'
```

```json
{
  "rule": "withdrawal.note.none",
  "status": 422,
  "error": "A withdrawal gives a note saying why.",
  "names": { "claim": "launch.rumour" }
}
```

## Withdraw evidence's text

Evidence is withdrawn the same way, by its identifier. Its `locator`, `excerpt` and `author` all go:
the locator, because where a passage is can say as much as the passage.

```bash
curl -s localhost:9000/evidence/1335475a76d0e9b7abf4a2c6f90eef64305e6c53121ea2ca83f1c5bce4681c99/withdrawal \
  -d '{ "note": "names a person who asked not to be named" }'
```

```json
{
  "id": "1335475a76d0e9b7abf4a2c6f90eef64305e6c53121ea2ca83f1c5bce4681c99",
  "source": "trade-press",
  "dated": "2026-05-09T10:00:00Z",
  "recordedAt": "2026-10-06T12:14:45.933Z",
  "writer": "local",
  "withdrawal": {
    "note": "names a person who asked not to be named",
    "writer": "local",
    "at": "2026-10-06T12:14:49.051Z"
  }
}
```

The identifier stays. It is the digest of the source, the locator and the excerpt, and it goes on
naming the record after the text it was made from has gone, which is what keeps every claim's
`derivesFrom` pointing at something.

## Who may withdraw

Three writers may withdraw the text of a record:

- the writer who recorded the evidence;
- a writer who speaks for the holder of the claim;
- a steward, a writer the deployment lists in `REASONING_STEWARDS`, who may withdraw the text of any
  record.

Anyone else is refused with `403` and the rule `withdrawal.writer.may-not`. Outside a cluster every
caller is the writer `local`, who recorded everything, so nobody is refused there.
[Writers, holders and stewards](../concepts/writers-and-holders.md) says how a caller becomes a writer.

## A withdrawal sent again

A withdrawal is safe to repeat. Sent again, by anyone and with any note, it answers `200` with the
record as withdrawn, and the first note stands:

```bash
curl -s localhost:9000/evidence/1335475a76d0e9b7abf4a2c6f90eef64305e6c53121ea2ca83f1c5bce4681c99/withdrawal \
  -d '{ "note": "asked again" }'
```

```json
{
  "id": "1335475a76d0e9b7abf4a2c6f90eef64305e6c53121ea2ca83f1c5bce4681c99",
  "source": "trade-press",
  "dated": "2026-05-09T10:00:00Z",
  "recordedAt": "2026-10-06T12:14:45.933Z",
  "writer": "local",
  "withdrawal": {
    "note": "names a person who asked not to be named",
    "writer": "local",
    "at": "2026-10-06T12:14:49.051Z"
  }
}
```

Recording the same evidence again does not bring the text back either. The same source, locator and
excerpt make the same identifier, the record under it is the withdrawn one, and that is what a repeat
is answered with: the `curl` that first recorded the evidence now answers `200` with exactly this body.

## What an answer shows afterwards

A withdrawn record is still in every answer it was in, marked, with its links and without its text.
The case against `launch/yes`, cut down to the withdrawn claim:

```bash
curl -s 'localhost:9000/answers/case?hypothesis=launch/yes&stance=contradicts' | python3 -m json.tool
```

This reply is an excerpt: one entry of `claims`.

```json
{
    "claim": {
        "id": "launch.rumour",
        "holder": "agent-b",
        "dated": "2026-05-09T10:00:00Z",
        "recordedAt": "2026-10-06T12:14:46.026Z",
        "withdrawn": true,
        "withdrawnAt": "2026-10-06T12:14:49.032Z"
    },
    "evidence": [
        {
            "id": "1335475a76d0e9b7abf4a2c6f90eef64305e6c53121ea2ca83f1c5bce4681c99",
            "source": {
                "id": "trade-press",
                "name": "the trade press",
                "dated": "2026-10-06T12:12:36.452Z",
                "recordedAt": "2026-10-06T12:12:36.452Z"
            },
            "dated": "2026-05-09T10:00:00Z",
            "recordedAt": "2026-10-06T12:14:45.933Z",
            "withdrawn": true,
            "withdrawnAt": "2026-10-06T12:14:49.051Z"
        }
    ],
    "revisedBy": [],
    "heldBy": []
}
```

The reasoning is still whole: there was a claim by agent-b against a launch, from the trade press,
dated 9 May. What it said is gone. A belief that rested on the claim still rests on it, and its
explanation still names the claim. Withdrawing text is not asking by date: an answer with `asOf` set
before the withdrawal does not show the text either, because the text is no longer anywhere to show.

## Where the text is gone from, and when

The text is held in three places, and leaves each at its own time:

| Place | Gone when |
|---|---|
| the service's own record | at once: the withdrawal's `200` means the row no longer holds it |
| the graph database | when the merge sink has applied the record's new delta, usually within seconds |
| the delta topic | when the broker compacts the record's key, at the latest one day after the withdrawal with the pipeline as shipped |

The service's record goes at once because the record is one row that is written again without the
text. No earlier version of the row is kept, and no event holds the text.

The graph database follows because the service publishes the record whole each time it changes, and
the sink replaces the node's properties with what the new delta carries. Wait for it as for any write,
and read the node:

```bash
curl -s localhost:9000/graph/wait -d '{ "kind": "claim", "id": "launch.rumour" }'
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (c:Claim {id: 'claim:launch.rumour'})-[:DERIVES_FROM]->(e:Evidence) RETURN c.statement, c.withdrawn, c.withdrawnAt, e.excerpt, e.author, e.withdrawn"
```

```text
{"caughtUp":true,"waitedMs":998,"missing":[]}
c.statement, c.withdrawn, c.withdrawnAt, e.excerpt, e.author, e.withdrawn
NULL, TRUE, 1791288889032, NULL, NULL, TRUE
```

The edges are untouched:

```bash
docker compose exec neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (c:Claim {id: 'claim:launch.rumour'})-[r]->(n) RETURN type(r), n.id"
```

```text
type(r), n.id
"STATED_BY", "holder:agent-b"
"CONTRADICTS", "hypothesis:launch/yes"
"DERIVES_FROM", "evidence:1335475a76d0e9b7abf4a2c6f90eef64305e6c53121ea2ca83f1c5bce4681c99"
```

The topic is last. It is compacted, so the broker keeps the latest delta for each element and in time
discards the earlier ones; the delta that carried the text is superseded as soon as the new one is
written, and is removed when the broker next compacts that part of the log. The pipeline sets the
longest that can take, `max.compaction.lag.ms`, to one day. Until then a reader of the raw topic can
still find the earlier delta. A graph database rebuilt from the topic applies the deltas in order and
ends at the latest, so a rebuilt graph does not hold the text even inside that day.

Withdrawal does not reach a backup of the database or of the broker taken before it. A deployment that
has to meet a deadline for erasure sets its backup retention to match;
[Operate the service](../deploy/operate.md) covers what an operator has to arrange.

## What cannot be withdrawn

Only the text of evidence and of a claim can be withdrawn. A question's and a hypothesis's statements,
a holder's and a source's names, a market's venue and resolution criteria, and a withdrawal's own note
stay as recorded, so none of them is the place for text that may have to come out. A record cannot be
removed, and a link cannot be taken away: a claim stated in error is revised by a later claim, and the
earlier one stays, with or without its statement.
