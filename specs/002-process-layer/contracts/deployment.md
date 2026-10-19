# Contract: deployment, process layer and the sink

What changes around the service: the sink that fills the graph database (research R2), the model
and judgment providers, and the appraiser's holders. Spec 001's
[deployment.md](../../001-belief-layer/contracts/deployment.md) holds for everything else; its
"The pipeline" section is replaced by "The sink" below.

## Settings

| Variable | Default | Meaning |
|---|---|---|
| `ANTHROPIC_API_KEY` | none | ankka's own. The model the appraiser's workers call. Without it the service starts and every run fails at its first model call, saying so |
| `TYPESAFE_API_KEY` | none | ankka's own. The judgment the critique asks. Without it, as above |
| `REASONING_APPRAISER_HOLDERS` | `appraiser-a,appraiser-b` | The holders the appraiser speaks for, registered at start, kind `agent` |
| `REASONING_GOLDEN_SET` | the packaged files | A directory holding `question.json`, `may.json` and `june.json`, to run the appraiser over other documents in the same shape |

- **D10.** With no model key or no judgment key, every route works and every run fails at the step
  that needs the missing one, with a reason naming the variable. The build sets neither.
- **D11.** At start the service registers the appraiser (ankka refuses a bad one and the service
  does not start), its holders (a repeat after the first start) and the golden set's sources.
- **D12.** The service is the only writer of its process records; the writer of a copied run or
  version is the service's own identity and of an act the blueprint's.

## The sink

Replaces the ankka-flow pipeline. The image is ankka-contrib's; the version is pinned in
`project/Dependencies.scala` (`V.sinkImage`) and used by the graph suites, the compose file and the
deploy descriptor alike.

| | |
|---|---|
| Image | `ghcr.io/thinkmorestupidless/ankka-graph-sink-neo4j:<version>` |
| Descriptor | `deploy/graph-sink.json`: `"http": false`, `"database": "none"`, env `ANKKA_GRAPH_SINK_TOPIC=reasoning-graph`, `ANKKA_GRAPH_SINK_VERSION`, `NEO4J_URI`, `NEO4J_USERNAME` and `NEO4J_PASSWORD` from the project secret `reasoning-graph`, `NEO4J_DATABASE` |
| Topic | declared on the project before the service publishes: `ankka projects topics set reasoning-graph --partitions 3 --cleanup compact --max-compaction-lag 1d -p reasoning` |
| Rebuild | empty the store (or point the sink at an empty one), apply the descriptor with `ANKKA_GRAPH_SINK_VERSION` one higher; locally `just rebuild` |

- **D13.** The order on a cluster: create the project, declare the topic, create the secret, apply
  the sink, apply the service. The topic exists compacted before anything publishes (spec 001 D8),
  and the sink keeps no state, so the order of the last two does not matter.
- **D14.** The compose file runs the same image as the service `sink`, from the same environment
  variables, against the compose's Neo4j; `compose/flow-graph/` and `compose/neo4j-secret/` go.
- **D15.** The sink's status (`ankka services get reasoning-graph-sink`) shows how far behind the
  topic it is and, while a delta is refused, which; nothing in the service reads it.
