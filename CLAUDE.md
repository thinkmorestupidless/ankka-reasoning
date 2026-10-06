# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## What this is

`ankka-reasoning` is a reasoning graph built as an ankka application: an event-sourced record of
evidence, claims, hypotheses and beliefs, published as graph deltas for ankka-flow's merge sink to
keep in a graph database. It is at the design stage. There is a spec, living features and a
glossary, and no code or build yet.

Start with `specs/001-belief-layer/spec.md`, then `features/` and `GLOSSARY.md`. The family map,
conventions and cross-repo decisions are in `../ankka-brain`; read `family.md` and
`repos/ankka-reasoning.md` there before cross-repo work.

## Rules

| Rule | What it means here |
|---|---|
| Nothing is edited | A record is never changed or removed once held. Thinking again is a new record linked to the earlier one. |
| Rules are checked on the write side | Whether a record may be held is decided from the service's own records. The graph database lags and is for reading only. |
| A link has one owner | Every link is stated by one record, the newer of the two, and published with it. No node or edge has two writers. |
| Links point backwards | A record links only to records already held and dated no later than itself. Cycles cannot be made, so nothing checks for them. |
| The belief layer names nothing above it | No market, and later no process, word in the belief layer. A layer adds kinds and edges from its own nodes and changes nothing beneath it. |
| Answers are records | An explanation is made of held records and links. No text is generated. |
| Missing platform features are requests | What ankka or ankka-flow cannot do is written down as a request to them, not worked around here. |

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
