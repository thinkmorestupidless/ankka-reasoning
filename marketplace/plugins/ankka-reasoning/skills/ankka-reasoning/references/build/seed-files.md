# Seed files

> Write a seed file of records to post in order, name a record so a later step can refer to it, send the file with just seed, and use the two files the repository ships.

Source: https://reasoning.ankka.cloud/build/seed-files/
A seed file is a list of requests to post to a running service, in order. It is how a worked example,
a test set or a piece of history is recorded without writing a client: the seed tool reads the file,
sends each step as a `POST`, and stops at the first refusal. It is a client of the HTTP API and nothing
more, so everything on [Record reasoning](record-reasoning.md) applies to what a file sends.

```bash
just seed launch-example
```

`just seed <name>` sends `seed/<name>.json` to `http://localhost:9000`. A second argument is the
service's address: `just seed launch-example http://localhost:9001`.

## The format

A seed file is a JSON object with a `steps` array. Each step has the path to post to and the body to
send:

```json
{
  "name": "gazette",
  "about": "A question of its own, to show a seed file's three devices. Every name in it is made up.",
  "steps": [
    {
      "post": "/questions",
      "body": {
        "id": "gazette",
        "statement": "Will the Harbour Gazette print a Sunday edition this year?",
        "hypotheses": [
          { "id": "yes", "statement": "it does" },
          { "id": "no", "statement": "it does not" }
        ]
      }
    },
    { "post": "/holders", "body": { "id": "gazette-reader", "kind": "person", "name": "a reader" } },
    { "post": "/sources", "body": { "id": "harbour-gazette", "name": "the Harbour Gazette" } },
    {
      "post": "/evidence",
      "as": "notice",
      "body": {
        "source": "harbour-gazette",
        "locator": "https://gazette.example/notices/sunday",
        "excerpt": "From June the Gazette will be printed seven days a week."
      }
    },
    {
      "post": "/claims",
      "body": {
        "id": "gazette.seven-days",
        "holder": "gazette-reader",
        "statement": "the Gazette has said it will print on Sundays",
        "derivesFrom": ["@notice"],
        "stances": [{ "hypothesis": "gazette/yes", "stance": "supports" }]
      }
    },
    { "post": "/evidence/{@notice}/withdrawal", "body": { "note": "the notice was printed in error" } }
  ]
}
```

| Key | In | Meaning |
|---|---|---|
| `steps` | the file | the requests, sent first to last |
| `post` | a step | the path the step is posted to |
| `body` | a step | the JSON body, exactly as the route takes it |
| `as` | a step, optional | a name under which the `id` of the record in the reply is remembered |
| `name`, `about` | the file, optional | for a reader; the tool does not read them |

The steps are in the order the records have to arrive in: a record names only records already held, so
a question comes before a claim about it and evidence before a claim that derives from it. The tool
sends no credentials, so the writer of every record is whoever the service takes the caller to be:
`local` outside a cluster.

## Refer to a record an earlier step made

A step with `as` names the record it made, and a later step refers to that record's identifier in one
of two ways:

| Written | Where | Becomes |
|---|---|---|
| `"@notice"` | a whole string value anywhere in a body | the identifier remembered as `notice` |
| `{@notice}` | inside any string, a path included | the same identifier, in place |

The names exist for evidence. A claim names evidence by its identifier, and the identifier of evidence
is the digest of what it says, which nobody knows until the service has recorded it. The step that
records the evidence takes `"as": "notice"`, the claim says `"derivesFrom": ["@notice"]`, and a
withdrawal posts to `/evidence/{@notice}/withdrawal`. Every other record has an identifier the file
chose and is referred to by it directly.

A name is letters, digits, `.`, `_` and `-`. It is remembered from the `id` of the reply, so `as`
belongs on a step whose reply is a record with an `id`. Two consequences of the syntax: a string in a
body that begins with `@` is always read as a reference, so no statement or excerpt in a seed file can
begin with one; and a reference to a name no earlier step gave stops the tool with an error before
anything more is sent.

## What the tool prints

The tool prints a line for each step, with the status it was answered with and the path, and then a
count. Sending the file from [the format](#the-format) for the first time prints:

```text
201 /questions                   
201 /holders                     
201 /sources                     
201 /evidence                    
201 /claims                      
200 /evidence/f5677d682e09b168fa51ae6370bdf6af8ee48cf59753bf26a38cb9c02b89013f/withdrawal 
6 sent, 5 new, 1 already held
```

The last path shows `{@notice}` replaced by the evidence's identifier. `new` counts the steps answered
`201` and `already held` those answered `200`; a withdrawal answers `200` the first time too, so it is
counted with the second. Run through `just`, each line carries sbt's `[info]` in front of it.

## A file sent twice is recorded once

A seed file is safe to send again. Every write answers `200` with the record already held when the same
record is sent a second time, so the second run records nothing and changes nothing:

```text
200 /questions                   
200 /holders                     
200 /sources                     
200 /evidence                    
200 /claims                      
200 /evidence/f5677d682e09b168fa51ae6370bdf6af8ee48cf59753bf26a38cb9c02b89013f/withdrawal 
6 sent, 0 new, 6 already held
```

The fourth step is worth a look. It sends the excerpt again after the withdrawal took it out, and the
service answers with the withdrawn record: the text does not come back. A file that failed part way
through is put right and sent whole; the steps that had succeeded answer `200` and the rest are
recorded.

## The first refusal stops the file

The tool stops at the first step the service refuses, prints the refusal, and exits with status 1. No
later step is sent, because the records after a refused one usually name it. This file's second step
registers a holder of a kind the vocabulary does not name:

```json
{
  "steps": [
    { "post": "/sources", "body": { "id": "almanac", "name": "the almanac" } },
    { "post": "/holders", "body": { "id": "oracle", "kind": "oracle", "name": "an oracle" } },
    { "post": "/sources", "body": { "id": "never-sent", "name": "a source the file never reaches" } }
  ]
}
```

```text
201 /sources                     
422 /holders                     {"rule":"holder.kind.not-in-vocabulary","status":422,"error":"'oracle' is not a kind of holder the vocabulary names.","names":{"holder":"oracle","kind":"oracle"}}
2 sent, 1 new, 0 already held
```

The first source is held and the third was never sent:

```bash
curl -s localhost:9000/sources/never-sent
```

```json
{"rule":"record.not-held","status":404,"error":"No source 'never-sent' is held.","names":{"source":"never-sent"}}
```

The refusal's `rule` says what to correct; every rule is on [Refusals](../reference/refusals.md).

## The files the repository ships

Two seed files are in the repository's `seed/` directory:

| File | Steps | Holds |
|---|---|---|
| [`launch-example.json`](https://github.com/thinkmorestupidless/ankka-reasoning/blob/main/seed/launch-example.json) | 10 | the worked example this documentation shares: one question, one holder, two sources, two pieces of evidence, two claims of which the second revises the first, and two revisions of one belief, 0.38 then 0.61 |
| [`ten-questions.json`](https://github.com/thinkmorestupidless/ankka-reasoning/blob/main/seed/ten-questions.json) | 557 | ten questions and the reasoning about them: 555 records and two withdrawals |

`just seed launch-example` is the quickest way to have something to ask about. Sent to a service that
does not hold it yet, it prints `201` for each of its ten steps and ends `10 sent, 10 new, 0 already
held`; sent again it prints:

```text
200 /questions                   
200 /holders                     
200 /sources                     
200 /sources                     
200 /evidence                    
200 /claims                      
200 /beliefs/revisions           
200 /evidence                    
200 /claims                      
200 /beliefs/revisions           
10 sent, 0 new, 10 already held
```

[Your first explanation](../get-started/first-explanation.md) asks why the belief it records changed.

`ten-questions.json` is a set large enough to try every answer on and to measure with. Four holders
and six sources are shared by ten questions. Each question has fourteen pieces of evidence over six
weeks, twelve claims by three of the holders, four of which revise an earlier claim by the same holder,
and seven revisions of each of those three holders' beliefs, some resting on another holder's claim.
Three of the questions have a market with twenty price observations, and one of those markets is
resolved and its resolution revised. The file ends by withdrawing the text of one piece of evidence and
of one claim. Every date in it is in the past, between March and May 2026, so it is also an example of
entering history after the fact. Every name, number and date in both files is made up.

## The larger file is generated

`ten-questions.json` is written by a generator, and is not edited by hand. The generator is
deterministic: it writes the same file every time, so the committed file is its output and a test fails
when the two differ.

```bash
just generate
```

That rewrites `seed/ten-questions.json` from
[`Generate.scala`](https://github.com/thinkmorestupidless/ankka-reasoning/blob/main/modules/seed/src/main/scala/reasoning/seed/Generate.scala).
To change the set, change the generator and run it. The seed tool itself is
[`Seed.scala`](https://github.com/thinkmorestupidless/ankka-reasoning/blob/main/modules/seed/src/main/scala/reasoning/seed/Seed.scala),
about a hundred lines: a file that needs more than `as` and two forms of reference is better sent by a
client of its own.
