# Use-cases for the process layer

Two use-cases to shape what comes after the belief layer and the market layer. Neither is specified
here yet. What they need from the platform is request 13 in [`ankka-requests.md`](ankka-requests.md).

## The idea, and where it lives

A reasoning pattern (a loop of thinking and acting, a plan of dependent tasks, several answers and a
vote, a draft and a critique, branches scored and pruned) is a few shapes of control flow: loop
until done, fan out and gather, map and reduce, propose and critique, branch and prune, each started
by a request, a schedule or a record. A **blueprint** chooses and parameterises the shapes: its roles
(instructions, tools, model), its steps, what each step reads and what it writes. It has no
conditions or loops of its own; those are in the shapes.

A blueprint is held, not deployed. Registering one is a write; changing one is a new version linked
to the earlier one. A **run** is held too, and links to the version it ran. Only code is deployed:
the service that runs blueprints, once, and any tool a blueprint names that is not already reachable.

**None of that is about reasoning.** A service that triages tickets, or writes a weekly digest, wants
blueprints and runs without a reasoning graph, Kafka or Neo4j. So the shapes, blueprints and runs
belong in ankka, as the next step up from an autonomous agent's task, which is already a record of
its own. Building them here would be the largest way round a missing platform feature this
repository has.

What belongs here is what only a reasoning graph gives a run:

- **Tools that write reasoning records**: record evidence, state a claim or a finding, revise a
  belief, each as a holder a role speaks for. A blueprint gets them by naming them.
- **The process layer as a projection**: ankka's blueprint versions and runs published as nodes, and
  every record a run writes linked to its run, so any record traces to the run and the blueprint
  version that wrote it. Since a layer adds edges only from its own nodes, a belief-layer record is
  linked to its run through a process-layer record that names both.
- **Triggers that read the reasoning**: "a new claim on a hypothesis of an open question", which
  ankka cannot know about.
- **Blueprints scored**: once markets resolve, the belief revisions a run wrote trace to its
  blueprint version, so versions can be compared by outcome.

Blueprints compose through records rather than calls: one run's records are what another reads.

## 1. A watch, and a weekly research digest

Given search criteria and a cadence, a service searches for the latest research, reads what it
finds, and writes a podcast script that summarises and discusses what appeared in the last period.
On a weekly cadence the script is ready on Sunday night for someone to listen to on Monday.

It is two blueprints:

- **The watch**: on its cadence, search each source for the criteria and keep one entry per paper,
  with where it was found and an excerpt.
- **The digest**: on its cadence, read what the watch kept in the period, note what each paper
  finds, relate papers that agree, disagree or build on each other, and write a script in which
  every statement traces to a paper. A critique checks that.

| Step | Shape | What is kept |
|---|---|---|
| The cadence comes round | scheduled start | a run |
| Search each source | fan out and gather | one entry per paper |
| Read each paper | map, one reader per paper | what each paper finds |
| Group, rank, relate | reduce | relations between papers |
| Write the script | propose and critique | the script, linked to what it uses |

It needs no reasoning graph, so it is **ankka's example** for blueprints and runs. What it asks of
them:

- **Starts** other than a request: a cadence, and records that match a filter.
- **The period** is of the time a paper was found, not published: a paper indexed days after it was
  published still reaches exactly one episode.
- **One paper, many sources**: a preprint, its journal version and its index entry are one paper,
  found by an identifier such as a DOI.
- **Sources** for life sciences are bioRxiv, PubMed or Europe PMC, and OpenAlex or Semantic Scholar
  more than arXiv. Each is a tool, an MCP server or a service the role calls.

Run on ankka-reasoning instead, with its tools, the same digest keeps each paper as evidence with
its provenance, each finding linked to the evidence it comes from, and the script linked to the
findings, all in the graph. A claim must take a stance on a hypothesis, which a digest has none of,
so what a paper finds is a record of the process layer, not a claim.

## 2. Designing an experiment for a cultivated food

A lab that designs experiments for others is asked whether it can make a food in the lab: "Can you
make foie gras?" The service searches the literature, finds the current best approaches, recommends
three experimental approaches with their trade-offs, and a person chooses one. The service then
writes a proposal for a proof of concept. When new research bears on the food, the recommendations
are assessed again.

| Step | Shape | Records |
|---|---|---|
| The request | plan: experiment type, requirements, constraints | a question |
| Literature search | fan out, a loop per source | evidence |
| Current best approaches | map and reduce: agreements, conflicts, gaps | claims; a hypothesis per approach |
| Three recommended, with trade-offs | assess each approach on each criterion | assessments |
| A person chooses | a human gate (ankka's approvals) | a decision linked to what was shown |
| Proof-of-concept proposal | propose and critique | a document in sections, each linked to its claims |
| New research arrives | started by the watch's evidence, then a relevance judgment | claims, belief revisions; the recommendation challenged |

This one is about reasoning, so it is **this repository's example**. What it asks beyond the first:

- **Trade-offs are not probabilities.** A belief spreads a probability over hypotheses; cost, time,
  maturity of evidence and risk are scores per approach per criterion. Criteria, assessments and the
  decision a person makes from them look like a decision layer beside the market layer.
- **A decision is a record**, linked to the assessments shown when it was made, so "why this
  approach?" is answered as of that date.
- **Assessing again follows links.** New evidence cannot link to old claims, since links point
  backwards; it yields new claims about hypotheses already held. From a hypothesis the graph gives
  the assessments, the decision and the sections of the proposal that rest on it, and only those
  are written again. The earlier recommendation is not edited: it is challenged, and choosing again
  is a new decision.
- **A document** (the proposal) is text a writer, here an agent, supplied: withdrawable like any
  other, linked to the claims it rests on. It is not an explanation; explanations stay made of
  records.
- **Private material**, such as the lab's earlier experiments, is evidence whose text may have to be
  withdrawn, which the belief layer already allows.

## Order

1. ankka: blueprints and runs, with the watch and the digest as the example.
2. Here: the process layer as a projection of ankka's runs, and the tools that write reasoning
   records, once ankka's feature has a plan.
3. Here: the decision layer, challenges and assessing again, with the cultivated food as the example.
