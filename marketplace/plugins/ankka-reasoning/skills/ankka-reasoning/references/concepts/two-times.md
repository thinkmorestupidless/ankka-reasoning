# Two times on every record

> The difference between when a record is dated and when it was recorded, and how asOf and asRecordedBy use each to answer a question about the past.

Source: https://reasoning.ankka.cloud/concepts/two-times/
Every record carries two times. `dated` is when the thing it describes happened: when evidence was
observed, when a claim was stated, when a belief was held. `recordedAt` is when the service accepted
the record. They differ whenever history is entered after the fact, and a question about the past has
to say which one it means.

## Dated: the writer's time

`dated` is set by the writer, and is the time the record is about. It may be left out, and is then the
time the record was accepted. Evidence and price observations call it `observedAt` in a request,
because what a writer knows about them is when they were seen.

A record cannot be dated in the future, and cannot be dated before a record it links to. So the dates
alone tell a consistent story: a claim of 4 May derives only from evidence observed by 4 May.

## Recorded: the service's time

`recordedAt` is set by the service from its own clock when it accepts the record, and a writer cannot
supply it. It says when the service came to know something, whatever date the record carries.

The launch example is about May and may be entered in October. Its evidence is dated 4 May and 11 May
and recorded in October:

```json
{
  "id": "fcc1d4a2479250e20568ecdf842752940e6927dfca110d39ac7d0585ea2f9b5f",
  "source": "regulator",
  "excerpt": "Notice 1187: Product Y is approved for sale.",
  "dated": "2026-05-11T09:00:00Z",
  "recordedAt": "2026-10-06T12:11:18.542Z"
}
```

## Two questions about the past

Every answer takes two optional bounds, and they ask different questions.

| Parameter | Counts a record when | The question it answers |
|---|---|---|
| `asOf` | `dated` is at or before the time | What was true of the world, as now understood, at that time? |
| `asRecordedBy` | `recordedAt` is at or before the time | What did this service know at that time? |

`asOf` uses everything the service knows today, including history entered late. If evidence observed
on 6 May is recorded on 20 May, an answer as of 7 May includes it.

`asRecordedBy` ignores everything entered since. The same answer as recorded by 12 May does not
include that evidence, because on 12 May the service had not been told. An answer as recorded by a
time never changes afterwards, whatever is entered later, so it is the one to use for an audit: what
could have been known when a decision was made.

The two can be given together, and a record then has to satisfy both.

## How a time applies to links

A record that does not count is absent, and so is every link it states, because a link belongs to the
record that states it. As of 6 May in the launch example:

- the belief is its first revision, 0.38, because the second is dated 11 May;
- that revision rests on the claim "approval is pending and the launch date is uncertain";
- that claim is not a revised claim, because the claim that revises it is dated 11 May.

Nothing is computed to make this so. Links point backwards, so everything a counted record links to
also counts, and the graph as of any time is whole.

A revision named by its identifier has to count too. Asking why a belief changed between two
revisions, as of a time before the second was stated, is refused with
`answer.revision.later-than-asked`.

## Sources and holders are the exception

A source and a holder are registered, not stated, so each is dated when it was registered. An answer
as of a past time shows a source beside evidence that counts, and a holder beside a claim or a
revision that counts, whenever the source or holder itself was registered. In the launch example the
regulator is registered in October and appears as the source of evidence dated 11 May.

`asRecordedBy` has no such exception. A record is always accepted after the records it links to, so
nothing in an answer as recorded by a time was recorded later.
