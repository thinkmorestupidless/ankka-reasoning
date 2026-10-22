---
name: ankka-reasoning
description: What ankka-reasoning is and how to use it — a reasoning graph on ankka that holds questions, hypotheses, holders, sources, evidence, claims and belief revisions as records that are never edited, publishes them to Neo4j through ankka's graph sink, and answers why a belief changed, what was believed at a past time and where two holders differ; also markets, price observations and resolutions, runs of the appraiser blueprint with the act behind every record it wrote, the golden set, withdrawing text, the HTTP API, every refusal and the graph vocabulary. Use for any question about recording reasoning in ankka-reasoning, running its blueprint, asking it for explanations, querying its graph, or deploying it.
pages:
  - index.md
  - get-started/run-locally.md
  - get-started/first-explanation.md
  - concepts/architecture.md
  - concepts/records-and-links.md
  - concepts/nothing-is-edited.md
  - concepts/two-times.md
  - concepts/layers.md
  - concepts/process-layer.md
  - concepts/writers-and-holders.md
  - build/record-reasoning.md
  - build/ask-for-explanations.md
  - build/markets.md
  - build/withdraw-text.md
  - build/read-the-graph.md
  - build/seed-files.md
  - build/run-a-blueprint.md
  - build/the-appraiser.md
  - build/golden-set.md
  - deploy/deploy-on-ankka.md
  - deploy/operate.md
  - deploy/live.md
  - reference/http-api.md
  - reference/refusals.md
  - reference/graph-vocabulary.md
  - reference/configuration.md
  - reference/limitations.md
  - reference/glossary.md
---

# ankka-reasoning

ankka-reasoning is a reasoning graph: a record of how beliefs form and change. A writer sends records
over HTTP to one ankka service. Each record is checked against the records already held, kept or
refused by name, and published as a node with its edges to a compacted Kafka topic that ankka's
graph sink copies into Neo4j. A reader asks the service for answers, each a walk of that graph made of
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
   `service:<project>/<name>` for another ankka service, `machine:<organization>/<name>` for a
   registered machine, `person:<subject>@<issuer name>` for a person through the gateway with a token
   from an issuer the service lists in `ANKKA_AUTH_ISSUERS`, `local` outside a cluster, and `401` for
   a caller through the gateway without such a token. A writer states claims and beliefs only for a
   holder it speaks for, else `403` and `holder.writer.does-not-speak`.
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
13. **A run writes through tools, and every record it writes has an act.** `POST /process/runs`
    with `{blueprint: "appraiser", runId, input: {tranche, holder, question, follows?, observedAt?}}`
    starts a run and answers at once; wait for it with `GET /process/runs/{run}` until `ending`
    appears. The run's holder must be one the service registered for the blueprint
    (`REASONING_APPRAISER_HOLDERS`, `appraiser-a` and `appraiser-b` by default). A run's revision is
    `<run id>.<hypothesis id>`; the June run follows the May run's with `follows`. A record a person
    wrote has no act, and `GET /process/acts?record=claim:<id>` answers `{}` for it.
14. **Every answer has a twin under `/process`** with the same parameters, answering
    `{answer, acts}`; `answer` is byte for byte the lower route's. Use the twin when the reader
    wants to know which run, version, step or worker wrote a record. `GET /process/answers/run?run=`
    is what a run wrote, from the graph.
15. **A run needs `ANTHROPIC_API_KEY` and `TYPESAFE_API_KEY`.** Without them the service runs and
    every run ends `failed` with a `reason` naming the key. A failed run is not a refusal: it is
    held with its ending, and whatever it wrote before failing stays, with its acts.

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
- Expecting a person's token to work against a service that lists no issuer. Without
  `ANKKA_AUTH_ISSUERS`, every caller through the gateway is refused with `401`.
- Reading a run's acts, or an answer with acts, the instant the run ended. The acts' view and the
  graph lag a moment; read again, or `POST /graph/wait` on the act's own key.
- Expecting the two holders' claims under `both` in a comparison. A claim is one holder's, so two
  runs over the same documents share evidence and not claims; `both` is empty and each side's
  claims are under `onlyA` and `onlyB`.
- Starting a run of the appraiser with a June tranche observed before 20 June at 22:00: the
  rumour's evidence is refused as observed before it was published, and the critique fails the run.
