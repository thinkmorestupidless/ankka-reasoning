---
name: ankka-reasoning
description: What ankka-reasoning is and how to use it — a reasoning graph on ankka that holds questions, hypotheses, holders, sources, evidence, claims and belief revisions as records that are never edited, publishes them to Neo4j through an ankka-flow merge sink, and answers why a belief changed, what was believed at a past time and where two holders differ; also markets, price observations and resolutions, withdrawing text, the HTTP API, every refusal and the graph vocabulary. Use for any question about recording reasoning in ankka-reasoning, asking it for explanations, querying its graph, or deploying it.
---

# ankka-reasoning

ankka-reasoning is a reasoning graph: a record of how beliefs form and change. A writer sends records
over HTTP to one ankka service. Each record is checked against the records already held, kept or
refused by name, and published as a node with its edges to a compacted Kafka topic that ankka-flow's
merge sink copies into Neo4j. A reader asks the service for answers, each a walk of that graph made of
records only.

## Rules

1. **Nothing is edited.** Never look for a way to update or delete a record. Thinking again is a new
   record linked to the old one: a claim with `revises`, a belief revision with `follows`, a
   resolution with `revises`. The one exception is `POST /evidence/{id}/withdrawal` and
   `POST /claims/{id}/withdrawal`, which take the text out for good and leave the record and its links.
2. **A write is safe to repeat.** The first send answers `201`; the same record under the same
   identifier answers `200` with the record held; a different record under a taken identifier is
   refused with `409` and `id.held-by-another`. Evidence has no identifier to send: its `id` is the
   SHA-256 of its source, locator and excerpt, returned in the reply.
3. **Links point backwards.** A record may name only records already held and dated no later than
   itself, so records are sent in order: question, holder and sources, evidence, the claim that
   derives from it, the belief revision that rests on the claim. A holder and a source are exempt from
   the date half: they are dated when registered.
4. **A belief is one line.** A revision names the current revision in `follows`, and none for the
   first. Anything else is refused with `409` and `belief.follows.not-current`, and `names.current`
   gives the revision to read and follow.
5. **A hypothesis is named `<question>/<hypothesis>`**, in bodies and in query strings, as in
   `launch/yes`.
6. **Match on `rule`, never on `error`.** A refusal is `{status, error, rule, names}`. `rule` is
   stable; `error` is a sentence for a person. `names` holds what broke the rule.
7. **The writer is not in the request.** The service decides it from the connection:
   `service:<project>/<name>` for another ankka service, `local` outside a cluster, and `401` for a
   caller through the gateway. A writer states claims and beliefs only for a holder it speaks for,
   else `403` and `holder.writer.does-not-speak`.
8. **The graph follows a write by a few seconds.** A write is answered when the record is held, not
   when the graph has it. Before reading the graph for something just written, call
   `POST /graph/wait` with `{kind, id}`. Routes under `/answers`, and a market's `resolution` and
   `beliefs-at-resolution`, read the graph and answer `503` when it cannot be read; nothing else does.
9. **Two times, two bounds.** `dated` is when the thing happened and is the writer's; `recordedAt` is
   when the service accepted it. Every answer takes `asOf` (counts records dated at or before) and
   `asRecordedBy` (counts records recorded at or before). Use `asRecordedBy` for what could have been
   known at the time.
10. **An answer contains records and nothing else.** No text is generated. Do not paraphrase an
    answer as if the service had said it; quote the claims' statements and the evidence's excerpts.
11. **A market is a holder.** Opening a market registers the holder `market.<market id>`; each price
    observation is a revision of that holder's belief resting on no claims, and an unchanged price
    adds nothing. Compare a market with a holder through `/answers/comparison`.
12. **The service never writes to Neo4j.** Query the graph database directly for reading only, using
    the kinds and properties `GET /graph/vocabulary` lists. Every node has the label `Element`, an
    `id` of the form `<kind>:<identifier>`, and `dated` and `recordedAt` in milliseconds.

## Before writing a client

- Which holders will it write as, and does its writer speak for them? The writer who registers a
  holder speaks for it.
- How will identifiers be chosen so that a retry sends the same identifier? Derive them from what the
  record is, not from a counter or a clock.
- Does it enter history? Then set `dated` (or `observedAt` for evidence and price observations) on
  every record; left out, it is the time of the request.
- Does it read its own writes from the graph? Then it waits with `/graph/wait`, not with a sleep.

## Mistakes to check for

- Sending a claim before its evidence, or a revision before the claim it rests on.
- Stating a second revision of a belief with no `follows`, or one that follows a revision that is no
  longer current.
- Dating a record before something it links to, or in the future.
- Treating `caughtUp: false` from `/graph/wait` as a failed write. The record is held.
- Expecting a withdrawn record to disappear from answers. It stays, marked `withdrawn`, without its
  text.
- Expecting a person's token to work. A caller through the gateway is refused until ankka releases
  its token verifier; see the limitations page.

## Reference files

Open the one a task needs; each is one topic and stands alone.

### Start here

- `references/index.md` — What ankka-reasoning is, the three questions a reasoning graph answers, how a record becomes a node, and where in this documentation to start.

### Get started

- `references/get-started/run-locally.md` — Start the ankka-reasoning service on a laptop with Postgres, Kafka, the merge sink and Neo4j beside it, and check that a record reaches the graph.
- `references/get-started/first-explanation.md` — Record the launch example with one command, wait for the graph, then ask why a belief changed and what was believed on an earlier day.

### Concepts

- `references/concepts/architecture.md` — The path of a record from a writer's request to a node in the graph and back out as an answer, and why the write side and the graph are kept apart.
- `references/concepts/records-and-links.md` — The kinds of record ankka-reasoning holds, the links each one states, and the three rules that keep the graph a history that cannot loop or be rewritten.
- `references/concepts/nothing-is-edited.md` — Why a record is never changed once held, how thinking again is recorded instead, and the one exception, withdrawing text, with what it leaves behind.
- `references/concepts/two-times.md` — The difference between when a record is dated and when it was recorded, and how asOf and asRecordedBy use each to answer a question about the past.
- `references/concepts/layers.md` — How the graph's vocabulary is divided into a belief layer and a market layer, what a layer may and may not do, and how the vocabulary is enforced.
- `references/concepts/writers-and-holders.md` — Who a caller is taken to be, how a writer comes to speak for a holder, what that permits, and what a steward may do that other writers may not.

### Build

- `references/build/record-reasoning.md` — Open a question, register a holder and its sources, record evidence, state claims and state belief revisions over HTTP, in the order the links between them require.
- `references/build/ask-for-explanations.md` — Ask why a belief changed, what a holder believed at a past time, what the case for a hypothesis is and where two holders disagree, and read each answer as records traced to their sources.
- `references/build/markets.md` — Open a market on a question, record its prices as revisions of the market's own belief, compare it with a holder, resolve it on evidence, and ask why it resolved and who believed what.
- `references/build/withdraw-text.md` — Take the text out of a piece of evidence or a claim with a note saying why, and know what stays, who may do it, and where and when the text is gone.
- `references/build/read-the-graph.md` — Query the graph database directly with Cypher, using the vocabulary's labels, identifiers and edges, filter by either of a record's two times, and know when the graph has caught up with a write.
- `references/build/seed-files.md` — Write a seed file of records to post in order, name a record so a later step can refer to it, send the file with just seed, and use the two files the repository ships.

### Deploy and operate

- `references/deploy/deploy-on-ankka.md` — Deploy the pipeline that fills the graph database, then the ankka-reasoning service, to an ankka installation with ankka-flow, Kafka and Neo4j, and know which of these steps is unproven.
- `references/deploy/operate.md` — Rebuild the graph database from the topic, measure how long a record takes to reach the graph, know what withdrawing text removes and when, and read the service's failures.

### Reference

- `references/reference/http-api.md` — Look up every route the service serves, the body each takes, the reply each gives, and how an answer is read as of a past time.
- `references/reference/refusals.md` — Look up what the service answers when it does not accept a write or cannot answer a read, with the status and the name of every rule.
- `references/reference/graph-vocabulary.md` — Look up every kind of node and edge the graph can hold, with its identifier, its properties, its layer and the kind of record that publishes it, and the query that traces a belief to its sources.
- `references/reference/configuration.md` — Every setting of the ankka-reasoning service, the environment variable that overrides each, its default, and the settings that belong to ankka.
- `references/reference/limitations.md` — Check what ankka-reasoning does not do yet, what is unproven, and which bounds a writer, a reader or an operator will meet.
- `references/reference/glossary.md` — Look up what each word means in ankka-reasoning, where every term has exactly one sense and no two words name the same thing.
