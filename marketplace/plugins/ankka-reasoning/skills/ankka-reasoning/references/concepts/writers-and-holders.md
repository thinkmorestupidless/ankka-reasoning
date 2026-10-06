# Writers, holders and stewards

> Who a caller is taken to be, how a writer comes to speak for a holder, what that permits, and what a steward may do that other writers may not.

Source: https://reasoning.ankka.cloud/concepts/writers-and-holders/
A holder is who a belief belongs to. A writer is who sent the request. They are different things: an
agent holds a belief, and the service that runs the agent is the writer that states it. The service
decides who the writer is, records it on every record, and lets a writer state claims and beliefs only
for the holders it speaks for.

## Who the writer is

The writer is decided by the service from the connection, never from anything in the request. Nothing
in a body or a header names it.

| The caller is | The writer is |
|---|---|
| another ankka service, identified by its certificate | `service:<project>/<name>` |
| anything, when the service runs outside a cluster | `local` |
| a caller arriving through the gateway | refused with `401` |

A caller through the gateway is refused because a person's token cannot be verified yet: the verifier
ankka provides for one is not in a released version of ankka. Until it is, the writers of a deployed
service are other ankka services in the installation.

Outside a cluster there are no certificates, so every caller is the one writer `local`. That is for a
laptop: with a single writer, "speaks for" refuses nobody.

Every record carries its `writer` and the service returns it when the record is read back. It is not
published to the graph.

## Speaking for a holder

A writer speaks for a holder when it is one of the holder's writers. The writer who registers a holder
is its first, and any of a holder's writers can add another:

```bash
curl -s localhost:9000/holders/agent-a/writers -d '{"writer":"service:forecasting/second-agent"}'
```

Writers are never removed from a holder.

Speaking for a holder is what permits stating a claim as that holder, stating a revision of its
belief, and adding a writer to it. A writer that does not is refused with `403` and
`holder.writer.does-not-speak`, and the refusal names the holder:

```json
{
  "rule": "holder.writer.does-not-speak",
  "status": 403,
  "error": "The writer does not speak for the holder 'agent-a'.",
  "names": { "holder": "agent-a", "writer": "service:forecasting/other" }
}
```

A market's holder is spoken for by the writer who opened the market, so only that writer, and those
it adds, can record the market's price observations and resolutions.

## What needs no holder

Opening a question, adding a hypothesis, registering a source and recording evidence are open to any
writer the service can identify. None of them is a statement of belief: evidence is what was observed,
and it belongs to no holder. What a holder makes of it is a claim, and that is where "speaks for"
begins.

## Stewards

A steward is a writer who may withdraw the text of any record. Stewards are named in the service's
configuration, in `REASONING_STEWARDS`, as whole writer strings separated by commas:

```bash
REASONING_STEWARDS=service:compliance/steward,service:ops/console
```

Without being a steward, a writer may withdraw the text of evidence it recorded itself and of a claim
whose holder it speaks for. A steward exists for the case where that writer is gone or will not: text
that has to come out still can. A steward can do nothing else that other writers cannot; it does not
speak for any holder by being one.

[Withdraw text](../build/withdraw-text.md) is the request.
