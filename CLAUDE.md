# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## What this is

`ankka-reasoning` is a reasoning graph built as an ankka application: a record of evidence,
claims, hypotheses and beliefs, each an ankka entity, published as graph deltas for ankka-flow's
merge sink to keep in a graph database. The first feature, the belief layer with the market layer
on it, is built: five sbt modules, one deployable service, and a suite for every scenario.

Start with `specs/001-belief-layer/spec.md` and `plan.md`, then `features/` and `GLOSSARY.md`.
`research.md` there says why each decision was made, R1 first: text a writer supplied is never
written to an event, because ankka never removes one. Its "Found during implementation" says what
building it changed. The family map, conventions and cross-repo decisions are in `../ankka-brain`;
read `family.md` and `repos/ankka-reasoning.md` there before cross-repo work.

## Modules

`graph` ← `belief` ← `market` ← `service`, and `seed` apart. The first three are published as
libraries (`ankka-reasoning-graph`, `-belief`, `-market`); `service` is published as an image. The direction is the layering: a
market word in `belief` does not compile. `service` composes the layers and holds every suite that
needs the whole service. `seed` is a client over HTTP and depends on none of them.

Pinned: ankka 0.10.0, ankka-flow 0.3.0 (the sink's image), Scala 3.9.0, in
`project/Dependencies.scala`.

## Commands

```bash
sbt graph/test belief/test market/test          # components alone: seconds
sbt 'service/testOnly *QuestionsFeatures'       # one feature's suite; no Docker
sbt 'service/testOnly *SeededSetSuite'          # a graph suite: starts Kafka, Neo4j and the sink
sbt test                                        # everything; Docker required
sbt scalafmtAll scalafmtSbt                     # format; CI checks it
just up && just run                             # the stack and the service on :9000
just seed launch-example                        # post seed/<name>.json to a running service
just measure                                    # time to the graph, time to an answer (SC-007)
just generate                                   # rewrite seed/ten-questions.json from its generator
just rebuild                                    # empty Neo4j and fill it from the topic
just features                                   # the features, the glossary and the specs agree
just docs                                       # check every page, build the site and the skill
just docs-sync                                  # refresh the generated table and the rendered skill
```

Use `sbt --client` for repeated commands. The suites that start containers run one at a time
(`Tags.limit(Tags.Test, 1)`); leave it so. A graph suite needs the sink's image under the name in
`Dependencies.scala`, which today has to be built from ankka-flow's `v0.3.0` tag (research F8). A
test switch passed to sbt reaches the test JVM only if `build.sbt` forwards it.

One service fixture is shared by every suite in a test JVM, and so is one graph. A suite keeps out
of the others' way by naming its records after itself, and puts the shared clock back when it
moves it.

## Documentation

`docs/` is the public documentation, published at `https://reasoning.ankka.cloud/` from `main` and
built by ankka's docs tool (`tools/docs`, navigation and `extra.docs` in `mkdocs.yml`), as in ankka,
ankka-flow and satisfactory. A behaviour change is a docs change. A new page goes in the `nav` and in
the skill's `pages:` (`tools/docs/skill/ankka-reasoning/SKILL.md`); run `just docs-sync` after
touching the skill, a page or `application.conf`, and commit what it renders under `marketplace/`.
A page tells no history: no `specs/` paths, no feature, requirement or task numbers. Every reply
shown on a page is the service's real output for the launch example; `docs/contributing/documentation.md`
has the rest.

## Release

Push a `v*` tag from `main`; only a tag publishes anything, and `.github/workflows/release.yml` is
the whole of it: the three layer modules to Maven Central, the image to
`ghcr.io/thinkmorestupidless/ankka-reasoning`, the plugin to ankka-marketplace, and a release page
with the descriptor and blueprint attached. A tag with a hyphen is a pre-release and gets the page
only. The version comes from the tag through sbt-dynver: never set `version` in the build, and
never write the version into a tracked file (the plugin's `0.0.0` is rewritten by the release job).
Published names are immutable and a version cannot be replaced: never re-tag, never publish by
hand. `docs/contributing/releasing.md` has the checks and the secrets.

## Rules

| Rule | What it means here |
|---|---|
| Nothing is edited | A record is never changed or removed once held. Thinking again is a new record linked to the earlier one. The one exception: the text of evidence or of a claim can be withdrawn, and the record, its links and its dates stay. |
| Rules are checked on the write side | Whether a record may be held is decided from the service's own records. The graph database lags and is for reading only. |
| A link has one owner | Every link is stated by one record, the newer of the two, and published with it. No node or edge has two writers. |
| Links point backwards | A record links only to records already held and dated no later than itself. Cycles cannot be made, so nothing checks for them. |
| The belief layer names nothing above it | No market, and later no process, word in the belief layer. A layer adds kinds and edges from its own nodes and changes nothing beneath it. |
| Answers are records | An explanation is made of held records and links. No text is generated. |
| Missing platform features are requests | What ankka or ankka-flow cannot do is written down as a request to them. Where a feature cannot wait, the way round is named in its plan's Complexity Tracking beside the request that would remove it, and never reaches into the platform's tables or internals. |

## Specs and living features

spec-kit 0.14.1 with the [speckit-bdd](https://github.com/thinkmorestupidless/speckit-bdd) extension
and preset, as in ankka and ankka-flow. A spec's acceptance scenarios name scenarios under
`features/<area>/`; none is written in the spec. Every word a step uses is a term in `GLOSSARY.md`
or one of its everyday words. Check with:

```bash
uvx --from "git+https://github.com/thinkmorestupidless/speckit-bdd@v0.2.0#subdirectory=checker" \
  speckit-bdd check --root . --glossary GLOSSARY.md --features features --specs specs
```

`research.md` is the per-feature decision record once a plan exists.
