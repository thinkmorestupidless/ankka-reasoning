# ankka-reasoning

A reasoning graph on [ankka](https://github.com/thinkmorestupidless/ankka): a record of how beliefs
form and change. Evidence is kept with where it came from, claims are derived from evidence,
hypotheses compete to answer a question, and a belief is one holder's probability for one
hypothesis. Nothing is ever edited, so any belief can be traced to its sources, read back as it
stood on any past day, and compared with a belief that disagrees.

The records live in an ankka service. Each is published as a node, and each link as an edge, to a
topic that [ankka-flow](https://github.com/thinkmorestupidless/ankka-flow)'s merge sink applies to a
graph database.

## Status

Designing. There is a specification and no code.

- [`specs/001-belief-layer/spec.md`](specs/001-belief-layer/spec.md): the first feature.
- [`features/`](features): what the service does, as scenarios. The spec names them.
- [`GLOSSARY.md`](GLOSSARY.md): the words the scenarios use, each in one sense.

## Layers

The vocabulary is layered so that each layer can be used without the ones above it.

| Layer | Holds | Feature |
|---|---|---|
| Belief | questions, hypotheses, holders, sources, evidence, claims, beliefs | 001 |
| Market | markets, outcomes, price observations, resolutions | 001 |
| Process | goals, tasks, workers, recorded calls, replay | later |

## The example the features share

Will Company X launch Product Y this year? On 4 May a company filing says approval is pending, and
an agent puts the launch at 0.38. On 11 May a regulator's notice says the approval barrier has
gone; the agent states a new claim that revises the earlier one and moves to 0.61. The first claim
and its source are kept: it was right for its time. Asked why the belief changed, the graph answers
with those records and nothing else. Every name and number in it is made up.
