---
title: Nothing is edited
description: Why a record is never changed once held, how thinking again is recorded instead, and the one exception, withdrawing text, with what it leaves behind.
kind: concept
related: [concepts/records-and-links.md, concepts/two-times.md, build/withdraw-text.md]
---

# Nothing is edited

A record is never changed or removed once the service holds it. A holder who thinks again states a new
record linked to the old one, and both are kept. History is therefore not reconstructed when someone
asks about the past: it was never overwritten.

## Thinking again is a new record

There are three ways to think again, one for each kind of record that can be superseded, and each is a
link from the new record to the old.

| To think again about | State | The link |
|---|---|---|
| a claim | a new claim that names the old one in `revises` | `REVISES`, from the new claim |
| a belief | a new belief revision that names the current one in `follows` | `FOLLOWS`, from the new revision |
| a market's resolution | a new resolution that names the current one in `revises` | `REVISES_RESOLUTION`, from the new resolution |

The old record does not change in any way. A claim that has been revised is recognised by the link
that arrives at it: it is a revised claim as soon as, and for as long as, a claim that revises it
counts. Asked about a time before the revision, the same claim is not a revised claim, with no
bookkeeping to make that true.

In the launch example, the claim of 4 May, "approval is pending and the launch date is uncertain", is
revised on 11 May by "the approval barrier has gone". The first claim is still held, still derives from
the filing, and is still what the belief of 4 May rested on. It was right for its time.

## A belief is one line

A belief's revisions form one line, each following the one before, and the service refuses anything
that would fork it. A revision states which revision it follows; if that is not the current one, the
write is refused with `belief.follows.not-current`, and the refusal names the current revision so the
writer can read it and decide again.

```json
{
  "rule": "belief.follows.not-current",
  "status": 409,
  "error": "A revision follows the belief's current revision, which is 'agent-a.launch.2'.",
  "names": { "revision": "agent-a.launch.3", "current": "agent-a.launch.2", "follows": "agent-a.launch.1" }
}
```

Two writers revising one belief at the same moment therefore cannot both succeed against the same
revision. The second is told what it missed. A market's resolutions are one line by the same rule.

A claim is different: one claim may be revised by several later claims, because two holders may each
think again about it.

## What can be added

Three things grow, and only by addition: a question can be given another hypothesis, a holder another
writer who speaks for it, and a market another resolution. Nothing is ever removed from any of the
three, which is what lets the service check a rule once and rely on it afterwards.

## The one exception: withdrawing text

The text of a piece of evidence or of a claim can be taken out for good. This exists because text a
writer supplied may turn out to be something that must not be kept: a passage quoted at more length
than was allowed, a statement naming a person who asked not to be named.

A withdrawal removes the evidence's locator, excerpt and author, or the claim's statement, and records
who withdrew it, when and why. Everything else stays: the record, its identifier, its dates and every
link to and from it. A belief that rested on a withdrawn claim still rests on it, and the trace through
it to a source still holds; the reader sees that the text was withdrawn and not what it said.

It is done once and cannot be undone. Recording the same evidence again answers with the withdrawn
record, so the text does not come back by being re-sent.

The text is gone from each place it was kept, on that place's own schedule:

| Where | When it is gone |
|---|---|
| the service's database | at once: the record's row is overwritten |
| the graph database | when the sink applies the new delta, a few seconds later |
| the delta topic | when the broker compacts the element's key, within the topic's compaction bound, one day as deployed |

This is why every record that carries text a writer supplied is a key value entity and not an event.
ankka never removes an event from a journal, so text in an event would be permanent; a key value
entity's one row is overwritten. Only a belief's line is event sourced, and its events carry numbers
and identifiers, never text.

[Withdraw text](../build/withdraw-text.md) is the request, and says who may make it.
