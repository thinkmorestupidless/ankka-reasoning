---
title: Records and links
description: The kinds of record ankka-reasoning holds, the links each one states, and the three rules that keep the graph a history that cannot loop or be rewritten.
kind: concept
layers: [belief, market]
related: [concepts/nothing-is-edited.md, concepts/layers.md, reference/graph-vocabulary.md, build/record-reasoning.md]
---

# Records and links

Everything ankka-reasoning holds is a record, and every relationship between two records is a link
stated by one of them. This page says what the records are, which links each states, and the rules
that hold for every link.

![The kinds of node in the graph and the links between them, in two layers. In the belief layer: a hypothesis answers a question; evidence is from a source; a claim derives from evidence, supports or contradicts a hypothesis, is stated by a holder, and may revise an earlier claim; a belief revision is in a hypothesis with a probability, rests on claims each with an optional weight, is held by a holder, and follows the belief revision before it. In the market layer: a market is about a question, offers an outcome for a hypothesis, and speaks as a holder; a resolution is of a market, resolves to a hypothesis, is on evidence, and may revise an earlier resolution. Every arrow is a link stated by the record it leaves and points to a record that was already held. No record is ever edited.](../assets/diagrams/graph.svg)

## The records of the belief layer

The belief layer has seven kinds of record, enough to reason about anything not yet known.

| Record | What it is | Links it states |
|---|---|---|
| Question | something not yet known, with the hypotheses that compete to answer it | none |
| Hypothesis | one possible answer; held in its question and named `<question>/<hypothesis>` | answers its question |
| Holder | whoever holds beliefs and states claims: an agent, a person, a model | none |
| Source | where evidence comes from: a publication, a register, a feed | none |
| Evidence | something observed: a locator, an excerpt, and when it was published and observed | from its source |
| Claim | a statement a holder makes on the strength of evidence | stated by its holder; derives from evidence; supports or contradicts hypotheses; may revise an earlier claim |
| Belief revision | one holder's probability for one hypothesis at a time | held by its holder; in its hypothesis; rests on claims, each with an optional weight; follows the revision before it |

A belief is not a record of its own. It is the line of revisions one holder has stated for one
hypothesis, each following the one before, and the last of them is what the holder believes now.

Evidence belongs to no question and no holder. It is identified by the SHA-256 of its source, locator
and excerpt, so the same passage recorded twice, by anyone, about anything, is one piece of evidence
that any number of claims can derive from.

## The records of the market layer

The market layer adds two kinds of record for a question that is traded.

| Record | What it is | Links it states |
|---|---|---|
| Market | a place a question is traded: a venue, the outcomes it offers, its criteria and closing time | about its question; offers an outcome for a hypothesis; speaks as a holder |
| Resolution | which outcome a market came to, on what evidence and on whose authority; none means void | of its market; resolves to a hypothesis; on evidence; may revise an earlier resolution |

A market is a holder too. Opening a market registers a holder of kind `market`, and each price
observation is a revision of that holder's belief in the outcome's hypothesis, resting on no claims: a
market states a probability and gives no reasons. That is why a market can be compared with any other
holder using nothing the belief layer does not already have.
[Markets and resolutions](../build/markets.md) records one.

## A link has one owner

Every link is stated by exactly one record, the newer of the two it joins, when that record is made.
A claim states what it derives from; evidence never lists the claims derived from it. A belief
revision states what it rests on; a claim never lists who rests on it.

This is why nothing held ever has to change when something new arrives, and it is the rule ankka's
graph deltas need: each node and each edge has one writer, so the latest delta for an element is the
whole truth about it. Questions asked the other way round, such as which claims derive from this
evidence, are answered by walking the graph against the direction of its edges.

## Links point backwards

A record may link only to records that are already held, and that are dated no later than itself. A
claim cannot derive from evidence observed after the claim was stated. A belief revision cannot rest
on a claim stated after it.

Two things follow without any code to enforce them. A cycle cannot be made, because every link runs
from a later record to an earlier one, so nothing checks for cycles. And no record can rest on
something that had not happened yet, so the graph as of any past time is closed: everything a record
of that time links to is also of that time.

A holder and a source are the exception to the date half of the rule. They are registered, not
stated: each is dated when it was registered, and evidence observed last May may name a source
registered today. They are still held before anything names them.
[Two times on every record](two-times.md) says what that means for an answer about the past.

## Identifiers

A writer chooses the identifier of every record except evidence, whose identifier is derived. An
identifier is 1 to 64 characters of letters, digits, `.`, `_` and `-`, starting with a letter or a
digit; a market's is at most 32 and an outcome name at most 16, so that the identifiers derived from
them stay within 64.

The identifier is what makes a write safe to repeat. The same record sent again under its identifier
is answered with the record already held; a different record under an identifier that is taken is
refused. In the graph a node's `id` is the record's identifier after its kind, as in
`claim:launch.pending`; the [graph vocabulary](../reference/graph-vocabulary.md) lists each form.
