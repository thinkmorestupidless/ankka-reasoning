---
title: Limitations
description: Check what ankka-reasoning does not do yet, what is unproven, and which bounds a writer, a reader or an operator will meet.
kind: reference
layers: [belief, market, process]
related: [reference/http-api.md, deploy/deploy-on-ankka.md, deploy/live.md, build/withdraw-text.md, concepts/process-layer.md, concepts/two-times.md, concepts/writers-and-holders.md]
---

# Limitations

The honest list: what is not built, what is built and not yet proven, and the bounds that are
deliberate. Each entry says how the system behaves today, so a reader can plan round it.

## What is not built

- **One blueprint, over documents given to it.** The service carries [the appraiser](../build/the-appraiser.md)
  and no other, and a blueprint is not registered over HTTP: only the appraiser's second
  instruction set is. It reads the documents of [the golden set](../build/golden-set.md), or of a
  directory in the same shape named by `REASONING_GOLDEN_SET`; nothing in the service gathers
  evidence from anywhere else.
- **The process layer records who wrote a record, not the conversation.** An act names the run,
  the step and the worker. No goal, task, model call, prompt or session is a record, and a run
  cannot be replayed from the graph.
- **A tool cannot fail a run.** A tool refuses a record and tells the model; the run goes on. The
  appraiser's critique fails the run from a step after the one that found the fault, so the records
  written before the verdict stay held, with their acts.
- **A tool takes at most three parameters**, so `record_evidence` names a document of the tranche
  and reads its source, locator and publication date from there; evidence of a document the
  tranche does not hold cannot be recorded by a run.
- **No scoring.** Once a market is resolved the service can say what each holder believed at the
  time. It does not say which holders or which evidence were worth listening to.
- **A belief does not think again by itself.** `GET /answers/resting-on-revised` lists the beliefs
  whose current revision rests on a claim that has since been revised. Nothing marks them or
  revises them: a holder does that by stating a new revision.
- **Nothing checks that probabilities add up.** A belief is one holder's probability for one
  hypothesis. A holder's probabilities for the hypotheses of one question are not required to sum
  to one.
- **The process layer is in no release yet.** Release 0.1.1 publishes the service's image, the
  belief, market and graph modules as libraries and the documentation as a plugin, without the
  process layer, the appraiser or the `/process` routes. Until the next release, those run from
  the repository.

## What is unproven

- **The deployment on a cluster is unproven.** The steps on
  [Deploy on ankka](../deploy/deploy-on-ankka.md), the graph sink as a service of the project and its
  descriptor, have not been run on any installation, local or cloud. Everything else in this
  documentation was run on a laptop, with Postgres, Kafka, Neo4j and the graph sink in containers.
- **Time to the graph is measured on a laptop only.** Under a burst of 555 writes, nine in ten
  records were in the graph within 3.8 seconds of their `201` and the slowest took 4.4. Over the
  golden set's four runs with a scripted model, nine in ten records a run wrote were in the graph
  with their acts within 1.6 seconds of the run's end, and the whole set was answered in 8.5. The
  floor is a poll: the service asks its database what has changed once a second, and a record
  cannot reach the graph sooner than the next poll.
- **The appraiser against a real model is proved once at a time, by hand.** The live suite runs
  only with `ANTHROPIC_API_KEY` and `TYPESAFE_API_KEY` in the environment and is skipped
  otherwise, so the build never runs it. How often a real model reads the golden set the way the
  set expects has not been measured over repeated runs.
- **A model key is needed for any run.** Without both keys the service starts and serves every
  other route, and every run fails at its first model call, naming the key. The two keys are
  ankka's providers' and no other provider is wired in.

## Who can write

- **A person needs a token from an issuer the service lists.** A caller through the gateway is a
  writer only with a token an issuer in `ANKKA_AUTH_ISSUERS` signed; a service that lists none
  answers every such caller `401`. A person's token has not been tried through an installation's
  gateway; it is verified in the service's own suites against an issuer of their own.
- **Outside a cluster every caller is `local`.** "Speaks for" therefore refuses nobody on a
  laptop: every caller is the same writer. That is for a laptop only.
- **A holder's writers are added and never removed.** There is no route that takes a writer away
  from a holder.
- **Stewards are set at start.** The writers who may withdraw any record's text are listed in
  `REASONING_STEWARDS` and read once; changing the list is a restart.

## Runs

- **A record can be held without its act for a moment.** A tool writes the record, then the act,
  and answers the model after both. If the service stops between the two, the record is held with
  no act until ankka runs the step again after a restart and the tool is called again, when the
  record is a repeat and the act is written. No run that has ended leaves a record without its
  act.
- **A run's acts and a record's act are read from a view** that may lag the write by a moment. An
  answer under `/process` reads the graph, which lags too; `POST /graph/wait` on the act's own
  identifier says when it is there.
- **A run id is at most 47 characters** and may not start `schedule:`, which ankka reserves. An
  identifier anywhere else is at most 64.
- **A run is of the version current when it started.** There is no way to run an earlier version
  again, and no version is ever removed.
- **The holders a blueprint may be run for are set at start**, in `REASONING_APPRAISER_HOLDERS`,
  each registered of kind `agent`; a run for any other holder is refused. The blueprint's writer,
  `blueprint:appraiser`, cannot be given to a holder over HTTP.

## Reading

- **Records are read one at a time, by identifier.** No route lists the questions, holders,
  sources, evidence or claims the service holds. A list is a query of the graph database, as
  [Read the graph](../build/read-the-graph.md) shows.
- **Answers are not paged.** An answer under `/answers` returns everything it found in one reply.
  Only `GET /beliefs/line` takes a `limit`. An explanation over a question of a thousand records
  was measured at 14 milliseconds, so this is a bound on reply size and not on time.
- **Every answer needs the graph database.** With none configured, or one that cannot be reached,
  each route that reads it answers `503`. Writes and read-back by identifier are unaffected.
- **The graph lags a write.** A record is held when its write answers; its node and edges arrive
  after the next poll and the sink's write. A client that must read its own write from the graph
  calls `POST /graph/wait` first.
- **A source or a holder registered later still appears in an answer as of an earlier time.** An
  answer with `asOf` holds no stated record dated later. A source and a holder are registered and
  not stated, and are dated when registered, so each is shown beside evidence or a claim that
  counts whenever it was registered itself. [Two times on every record](../concepts/two-times.md)
  explains the distinction.
- **`revisedBy` on `GET /claims/{claim}` may lag a moment.** It comes from a view. The answers
  under `/answers` read the graph and do not use it.

## Withdrawing text

- **A withdrawal is for good and cannot be undone.** The same evidence recorded again is answered
  with the withdrawn record, so the text does not come back under that source, locator and
  excerpt.
- **Withdrawn text is not gone from everywhere at once.** It leaves the service's own row when the
  withdrawal is accepted and the graph database when the sink applies the new delta. The earlier
  delta stays on the topic until the broker compacts it, which a topic declared with
  `max.compaction.lag.ms` of one day bounds at one day.
- **Backups are not reached.** A backup of the database or the broker taken before the withdrawal
  still holds the text, and so does Postgres's own storage until its next vacuum of the row,
  which no query can read. A deployment with a deadline for erasure sets its backup retention to
  meet it.
- **The topic must have been created compacted.** The service creates no topics. A topic that came
  into being uncompacted keeps every delta, withdrawn text included.
- **Only text is withdrawn.** The excerpt, locator and author of evidence and the statement of a
  claim can be taken out. A question's or a hypothesis's statement, a name, a venue, resolution
  criteria and a withdrawal's own note cannot.

## Bounds on what is recorded

- **An identifier is at most 64 characters** of `[A-Za-z0-9._-]`, starting with a letter or a
  digit. A market's is at most 32 and an outcome name at most 16, so the identifiers derived from
  them stay within 64.
- **A hypothesis is named `<question>/<hypothesis>` in a body or a query, not in a path.** A route
  takes at most two path parameters.
- **Text has limits.** A question's or hypothesis's statement is at most 500 characters, a claim's
  1,000, a locator 2,000, an excerpt `REASONING_EXCERPT_LIMIT` (2,000 by default), a name 200 and
  a note 500. The original of a piece of evidence is not kept: only its locator and an excerpt.
- **A record cannot be dated in the future**, nor before a record it links to. History can be
  entered late; it cannot be entered out of order.
- **A belief's line cannot be back-filled.** A revision follows the current one and is dated no
  earlier, so a revision dated before the current one is refused.
- **A market's price observations state no reasons.** Each is a revision resting on no claims, and
  an observation later than the market's closing time is refused.

## Running it

- **The graph sink's image is built for `linux/amd64` only.**
  `ghcr.io/thinkmorestupidless/ankka-graph-sink-neo4j:0.1.0` runs on Apple silicon under Docker's
  emulation, more slowly than it would natively.
- **One graph database, and the service only reads it.** The service never writes to the graph
  database and creates nothing in it; the graph sink is its only writer. Neo4j 5.26 or later is what
  it is tested against.
- **A setting is read once at start.** Changing one is a restart.
