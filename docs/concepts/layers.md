---
title: Layers and the vocabulary
description: How the graph's vocabulary is divided into a belief layer and a market layer, what a layer may and may not do, and how the vocabulary is enforced.
kind: concept
layers: [belief, market]
related: [concepts/records-and-links.md, reference/graph-vocabulary.md, build/read-the-graph.md]
---

# Layers and the vocabulary

The vocabulary is the complete list of what may be in the graph: every kind of node, every kind of
edge, the properties of each, and the kinds of holder. It is divided into layers, and each layer can
be used without the ones above it. There are two: the belief layer, and the market layer on top of it.

## What each layer is for

| Layer | Adds | For |
|---|---|---|
| belief | question, hypothesis, holder, source, evidence, claim, belief revision, and the eleven kinds of edge between them | reasoning about anything not yet known |
| market | market and resolution, seven kinds of edge that leave them, and the holder kind `market` | a question that is traded |

The belief layer names nothing of markets. It has no outcome, no price and no venue, and it would
serve a forecaster with no market in sight, or any other domain where beliefs rest on claims and
claims on evidence.

## What a layer may do

A layer may add kinds of node, add kinds of edge that leave its own nodes, and add kinds of holder. It
may not change anything beneath it: no new property on a lower layer's node, no new edge leaving one.

The market layer keeps to this by reusing what the belief layer has. A market does not get a price
property; it speaks as a holder, and its prices are that holder's belief revisions. A resolution does
not mark a hypothesis as true; it states an edge, `RESOLVES_TO`, from itself to the hypothesis. So
every answer the belief layer gives works on a market's beliefs unchanged, and a graph with the market
layer's nodes removed is a complete belief-layer graph.

This is held by the build, not by convention. The belief layer's code is a module that does not depend
on the market layer's, so a market word in it does not compile, and the service's test suite runs the
belief layer's scenarios against a service with no market layer in it.

## The vocabulary is a value

The vocabulary is data in the code, and it is the only way to build an element for the graph. A graph
consumer asks the vocabulary for a node of a kind with these properties, and the vocabulary refuses a
label, an edge type or a property it does not declare. Each kind also names the one kind of record
that publishes it, which is how "a link has one owner" is checked.

The service publishes its vocabulary, composed from the layers it runs with:

```bash
curl -s localhost:9000/graph/vocabulary | python3 -m json.tool
```

```json
{
    "layers": [
        {
            "name": "belief",
            "nodes": [
                {
                    "label": "Question",
                    "id": "question:<identifier>",
                    "properties": [
                        { "name": "statement", "type": "text", "optional": false }
                    ],
                    "publishedBy": "question"
                }
            ],
            "edges": [
                { "type": "ANSWERS", "from": "Hypothesis", "to": "Question", "properties": [], "publishedBy": "question" }
            ],
            "holderKinds": ["agent", "person", "model"]
        }
    ]
}
```

The reply is an excerpt: one kind of node and one of edge from the first layer. A reader who has this
page of JSON can write any query against the graph without reading the service's code.
[Graph vocabulary](../reference/graph-vocabulary.md) is the whole of it, and
[Read the graph](../build/read-the-graph.md) uses it.

## Later layers

A process layer is planned above these two: goals, tasks, workers and the calls they made, so that how
a piece of reasoning was carried out can be replayed. It does not exist yet, and nothing in the belief
or market layer will change to make room for it. [Limitations](../reference/limitations.md) lists what
else is not there.
