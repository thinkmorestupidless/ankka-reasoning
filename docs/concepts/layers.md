---
title: Layers and the vocabulary
description: How the graph's vocabulary is divided into a belief layer, a market layer and a process layer, what a layer may and may not do, and how the vocabulary is enforced.
kind: concept
layers: [belief, market, process]
related: [concepts/records-and-links.md, concepts/process-layer.md, reference/graph-vocabulary.md, build/read-the-graph.md]
---

# Layers and the vocabulary

The vocabulary is the complete list of what may be in the graph: every kind of node, every kind of
edge, the properties of each, and the kinds of holder. It is divided into layers, and each layer can
be used without the ones above it. There are three: the belief layer, the market layer on top of it,
and the process layer on top of that, which records which run of a blueprint wrote a record. The
process layer's kinds are in the [graph vocabulary](../reference/graph-vocabulary.md#process-layer).

## What each layer is for

| Layer | Adds | For |
|---|---|---|
| belief | question, hypothesis, holder, source, evidence, claim, belief revision, and the eleven kinds of edge between them | reasoning about anything not yet known |
| market | market and resolution, seven kinds of edge that leave them, and the holder kind `market` | a question that is traded |
| process | blueprint version, run, run ending and act, and five kinds of edge that leave them | how a record came to be written: by which run of which blueprint version, in which step, as which worker |

The belief layer names nothing of markets. It has no outcome, no price and no venue, and it would
serve a forecaster with no market in sight, or any other domain where beliefs rest on claims and
claims on evidence. Neither of the two beneath names anything of runs: a record written by a person
and one written by a run of a blueprint are the same kind of record, and the process layer says
afterwards which was which. [The process layer](process-layer.md) is its own page.

## What a layer may do

A layer may add kinds of node, add kinds of edge that leave its own nodes, and add kinds of holder. It
may not change anything beneath it: no new property on a lower layer's node, no new edge leaving one.

The market layer keeps to this by reusing what the belief layer has. A market does not get a price
property; it speaks as a holder, and its prices are that holder's belief revisions. A resolution does
not mark a hypothesis as true; it states an edge, `RESOLVES_TO`, from itself to the hypothesis. So
every answer the belief layer gives works on a market's beliefs unchanged, and a graph with the market
layer's nodes removed is a complete belief-layer graph.

The process layer keeps to it the same way. An act is a node of its own with an edge, `WROTE`, to the
record it names; the record gains no property and states no edge back. A run writes as a holder of
the belief layer's, and the layer adds no kind of holder.

This is held by the build, not by convention. The belief layer's code is a module that does not depend
on the market layer's, and neither depends on the process layer's, so a market word in the belief layer
or a process word in either does not compile. The service's test suite runs the belief layer's
scenarios against a service with no market layer in it, and every scenario of both against a service
with no process layer.

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

The reply is an excerpt: one kind of node and one of edge from the first layer; the reply has three
layers, `belief`, `market` and `process`. A reader who has this page of JSON can write any query
against the graph without reading the service's code.
[Graph vocabulary](../reference/graph-vocabulary.md) is the whole of it, and
[Read the graph](../build/read-the-graph.md) uses it.

## The layers as libraries

Each layer is a library as well as a part of the service, so another ankka application can host the
belief layer, or more, beside components of its own. A release publishes four modules to Maven
Central under `com.thinkmorestupidless`:

| Module | Holds |
|---|---|
| `ankka-reasoning-graph` | the vocabulary as a value, the only way to build an element, and the reader of the graph database |
| `ankka-reasoning-belief` | the belief layer: `BeliefLayer.components`, `BeliefLayer.graphComponents(topic)` and `BeliefLayer.endpoints(clock, config, graph, acl)` |
| `ankka-reasoning-market` | the market layer, the same three entry points on `MarketLayer` |
| `ankka-reasoning-process` | the process layer, on `ProcessLayer`; its components need ankka's agent runtime registered beside them |

```scala
libraryDependencies += "com.thinkmorestupidless" %% "ankka-reasoning-belief" % "<version>"
```

Each depends on the one before it, so naming the process layer brings all four. The service is the
example of composing them:
[`ReasoningService`](https://github.com/thinkmorestupidless/ankka-reasoning/blob/main/modules/service/src/main/scala/reasoning/service/Main.scala)
adds the three layers' components and endpoints together, registers the process layer's blueprints
with ankka's agent runtime, and nothing else. Three things are the service's and are not in the
libraries: the `/graph` routes, the rule for who a caller is as a writer, which an application
supplies as the `acl` it hands each layer, and the model and judgment providers a run asks, read from
the environment.

The first three are on Maven Central from release 0.1.1; the process layer's module is published with
the first release that carries it. [Limitations](../reference/limitations.md) says which that is.

## What the layers leave out

The process layer records which run wrote a record, and not the conversation that led to it: no
goal, no task, no model call and no session is a record. Scoring, which would set a version or a
holder against the outcome a market resolved to, is above all three and is not built.
[Limitations](../reference/limitations.md) lists what else is not there.
