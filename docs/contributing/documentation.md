---
title: Writing documentation
description: How ankka-reasoning's documentation is built with ankka's docs tool, where a page goes, the rules every page follows, and how the skill is rendered.
kind: contributing
related: [reference/glossary.md]
---

# Writing documentation

ankka-reasoning's documentation is one tree of plain Markdown under `docs/`, and every way of reading
it is a rendering of that tree: the site at `https://reasoning.ankka.cloud/`, `llms.txt`,
`llms-full.txt`, a Markdown copy of each page, `docs-index.json`, and the agent skill in the
marketplace plugin. The tool that renders and checks it is ankka's, taken as a dependency, and the
rules are ankka's too; the page
[Writing documentation](https://docs.ankka.cloud/contributing/documentation/) in ankka's documentation
states every rule with its reason. This page says what is particular to this repository.

## The build

```bash
uv run --project tools/docs docs check    # every rule; exits 1 on a problem
uv run --project tools/docs docs sync     # refresh the generated table and the skill
uv run --project tools/docs docs build    # check, build the site, write the machine renderings
uv run --project tools/docs docs serve    # the site with live reload, while writing
```

`just docs`, `just docs-sync` and `just docs-serve` are the same commands. The site lands in
`target/docs-site`. The tool comes from ankka's repository, named in `tools/docs/pyproject.toml`, and
what it needs to know about this repository is `extra.docs` in `mkdocs.yml`: the frontmatter
vocabulary, where the skill is rendered, and which file the configuration table is generated from.

The site is published to GitHub Pages from `main` by `.github/workflows/docs.yml`, which also runs the
build as a check on every pull request that touches the documentation or the code it is generated
from.

## Where a page goes

| Kind | Directory | The reader wants to |
|---|---|---|
| `tutorial` | `get-started/` | be walked from nothing to something working |
| `concept` | `concepts/` | understand how something works and why |
| `guide` | `build/`, `deploy/` | get one task done |
| `reference` | `reference/` | look one fact up |
| `contributing` | `contributing/` | change ankka-reasoning itself |

A new page is added to the `nav` in `mkdocs.yml` and, unless it is a contributing page, to the skill's
`pages:` list, or the check fails.

## Frontmatter

Every page starts with `title`, `description` and `kind`. One optional key is this repository's:
`layers`, any of `belief` and `market`, for a page about the records of a layer. `related` lists the
pages a reader most often needs next.

## The words

A page uses the words of [the glossary](../reference/glossary.md) in the one sense it gives them:
holder and writer are different things, a belief is a line of revisions, and a claim is revised, never
edited or updated. The glossary page is written from `GLOSSARY.md` at the root of the repository,
which is what the living features under `features/` are checked against, so a term changes there
first.

## The example

Every page that needs an example uses the same one: whether Company X will launch Product Y this
year, with the holder `agent-a`, the claims `launch.pending` and `launch.barrier-gone`, and the belief
moving from 0.38 to 0.61. It is
[`seed/launch-example.json`](https://github.com/thinkmorestupidless/ankka-reasoning/blob/main/seed/launch-example.json),
so any reply shown on a page can be reproduced with `just seed launch-example` and the request beside
it. Replies shown are the service's own output, shortened where the page says it is an excerpt.

## The generated table

The table on [Configuration](../reference/configuration.md) is generated from the service's
`application.conf` by `docs sync`, and the prose around it must mention every environment variable the
table lists. Adding a setting is therefore a failed build until someone writes a sentence about it.

## The skill

One skill, `ankka-reasoning`, is curated in `tools/docs/skill/ankka-reasoning/SKILL.md` and rendered
into `marketplace/plugins/ankka-reasoning/skills/`, which is committed and checked. Its body holds the
rules an agent must hold to write to or read from the service; the pages themselves are generated
into it from `docs/`.
