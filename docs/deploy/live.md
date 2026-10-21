---
title: Live, against a real model
description: Give the service the two keys a run of the appraiser needs, know what a run does without them, run the live suite against the real model and judgment, and set the keys as secrets on a cluster.
kind: guide
layers: [process]
related: [build/the-appraiser.md, build/golden-set.md, build/run-a-blueprint.md, deploy/deploy-on-ankka.md, reference/configuration.md, reference/limitations.md]
---

# Live, against a real model

A run of [the appraiser](../build/the-appraiser.md) asks two things of the outside world: a text
model, for its two workers, and a judgment, for its critique. Each comes from a provider ankka
supplies, and each needs a key. Everything else the service does needs neither.

## The two keys

| Variable | Gives | Used by |
|---|---|---|
| `ANTHROPIC_API_KEY` | ankka's Anthropic model provider | the `reader` and `reviser` workers, in the `read` and `revise` steps |
| `TYPESAFE_API_KEY` | ankka's judgment provider | the `critique` step, once per claim |

Both are ankka's own variables, read when the service starts. Set them in the environment of
`just run` on a laptop, or as a secret on a cluster, below. The build sets neither, and nothing in
the repository or its tests carries a key.

## Without a key

The service starts and serves every route with neither key set. A run starts too, and fails at the
first step that needs the missing provider, naming the variable:

```bash
curl -s localhost:9000/process/runs -d '{"blueprint":"appraiser","runId":"no-key-may","input":{"tranche":"may","holder":"appraiser-a","question":"launch"}}'
curl -s localhost:9000/process/runs/no-key-may
```

```json
{"run":{"id":"no-key-may","blueprint":"appraiser","version":1,"startedBy":"local","dated":"2026-10-21T16:02:46.536Z","recordedAt":"2026-10-21T16:02:46.582Z","writer":"service:reasoning"},"ending":{"run":"no-key-may","status":"failed","reason":"read: item 1: none call failed: the model key ANTHROPIC_API_KEY is not set","dated":"2026-10-21T16:02:46.657Z","recordedAt":"2026-10-21T16:02:47.131Z","writer":"service:reasoning"},"acts":[]}
```

The run is held with its ending, as any run is; it wrote nothing, so it has no acts. With one key
and not the other, the run fails at the first step that needs the missing one, naming it; whatever
the steps before it wrote stays held, with its acts.

## Run it on a laptop

With both keys in the environment, [the golden set](../build/golden-set.md)'s four runs are the
same four requests. A run over the May tranche reads five documents and takes a few minutes; each
reader makes two tool calls and the reviser one, and the critique asks one judgment per claim.

```bash
ANTHROPIC_API_KEY=… TYPESAFE_API_KEY=… just run
just seed golden-set
curl -s localhost:9000/process/runs -d '{"blueprint":"appraiser","runId":"appraiser-a-may","input":{"tranche":"may","holder":"appraiser-a","question":"launch","observedAt":"2026-05-31T09:00:00Z"}}'
```

What a real model writes is its own: the excerpts it chooses, the statements it makes and the
weights it gives. What the set fixes is the direction, below one half in May and above it in June,
and the critique, which fails the run on any claim that does not derive from evidence recorded from
a document of the tranche.

## The live suite

The repository carries a suite that runs the appraiser against the real providers, for one holder
over both tranches, and asserts what the golden set says: the first probability below one half, the
second above it, every record held with its act, and every claim deriving from the tranche. It is
part of `sbt test` and is skipped, not failed, when either key is missing from the environment,
so a build without keys is green and says so.

```bash
ANTHROPIC_API_KEY=… TYPESAFE_API_KEY=… sbt 'service/testOnly *LiveAppraiserSuite'
```

Without the keys:

```text
==> s reasoning.LiveAppraiserSuite.the appraiser for appraiser-a over May and then June, against the real providers skipped 0.008s
[info] Passed: Total 1, Failed 0, Errors 0, Passed 0, Skipped 1
```

The suite allows a run fifteen minutes and itself forty. It runs against the same service, Kafka,
Neo4j and sink the other graph suites use, with the two providers switched to the real ones for
its duration, so that the records it writes can be traced in the graph like any other suite's.

## On a cluster

Set the keys as a project secret and name them in the service descriptor's `env`, beside the
Neo4j credentials [Deploy on ankka](deploy-on-ankka.md) sets:

```bash
ankka projects secrets set reasoning-models ANTHROPIC_API_KEY=- TYPESAFE_API_KEY=- -p reasoning
```

```json
{ "name": "ANTHROPIC_API_KEY", "secretKeyRef": { "name": "reasoning-models", "key": "ANTHROPIC_API_KEY" } },
{ "name": "TYPESAFE_API_KEY", "secretKeyRef": { "name": "reasoning-models", "key": "TYPESAFE_API_KEY" } }
```

`=-` reads each value from standard input, one at a time, so neither is left in the shell's
history. A run through the gateway is then the same request as on a laptop, with a token, and
`startedBy` is the person or service that sent it. This has not been run on an installation;
[Limitations](../reference/limitations.md) says what is unproven.
