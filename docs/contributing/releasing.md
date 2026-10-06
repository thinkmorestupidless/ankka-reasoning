---
title: Releasing
description: What pushing a version tag publishes, what each part needs outside the repository, how to prove the release path locally first, and what to check afterwards.
kind: contributing
related: [contributing/documentation.md, deploy/deploy-on-ankka.md, concepts/layers.md]
---

# Releasing

A release is a tag. Pushing `v0.1.0` to the repository publishes everything a version of
ankka-reasoning consists of, and nothing else ever publishes: not a merge, not a commit to `main`,
not a command run by hand. The workflow is
[`.github/workflows/release.yml`](https://github.com/thinkmorestupidless/ankka-reasoning/blob/main/.github/workflows/release.yml).

## What a tag publishes

| Job | Publishes | To |
|---|---|---|
| `release-page` | the release page, with generated notes | GitHub releases |
| `libraries` | `ankka-reasoning-graph`, `ankka-reasoning-belief`, `ankka-reasoning-market`, signed, with sources and documentation | Maven Central, group `com.thinkmorestupidless` |
| `image` | the service's image, tagged with the version and `latest`; then the service descriptor naming that image and the pipeline's blueprint, attached to the release page | `ghcr.io/thinkmorestupidless/ankka-reasoning` |
| `marketplace` | the documentation as a Claude Code plugin, at this version | the repository `thinkmorestupidless/ankka-marketplace` |

The documentation site is not part of a release. It is published from `main` by `docs.yml`, so it
describes `main`, and the plugin a release publishes is the documentation as it stood at the tag.

A tag with a hyphen, such as `v0.2.0-rc.1`, is a pre-release. It gets the release page, marked as a
pre-release, and nothing reaches a registry.

## The version is the tag

The version comes from git and from nowhere else. sbt-dynver reads the nearest tag: on the commit
tagged `v0.1.0` the build's version is `0.1.0`, and a commit past it or a tree with a changed file
is a `-SNAPSHOT`. Two rules follow.

- `version` is never set in the build. Setting it overrides the tag without saying so.
- No tracked file holds the version. The plugin's manifest says `0.0.0` in the tree, and the release
  job writes the version into its own checkout before copying the plugin out.

Every job that builds refuses a version that is not the tag's, so a dirty checkout fails by name and
does not publish a snapshot.

## What nothing can undo

A version on Maven Central cannot be replaced or removed, and the three module names are permanent
once published. So:

- never move or re-push a tag that has published;
- never publish by hand;
- a mistake in a release is fixed by the next version.

A re-run of the workflow for a tag is safe: the `libraries` job asks Maven Central whether the
version is there and uploads nothing a second time, the image is pushed again under the same tag,
and the `marketplace` job finds nothing to commit.

## What has to exist outside the repository

Five repository secrets, and one setting made after the first release.

| Needed | For | What it is |
|---|---|---|
| `PGP_SECRET` | `libraries` | the signing key: `gpg --armor --export-secret-keys <id> \| base64` |
| `PGP_PASSPHRASE` | `libraries` | that key's passphrase |
| `SONATYPE_USERNAME`, `SONATYPE_PASSWORD` | `libraries` | a user token from the Central Portal, its username and password; the namespace `com.thinkmorestupidless` is already claimed there |
| `MARKETPLACE_REPO_TOKEN` | `marketplace` | a token with `contents: write` on `thinkmorestupidless/ankka-marketplace` |
| the package made public | `image` | a package on `ghcr.io` is private when first pushed; set `ankka-reasoning` to public in the package's settings, once |

The image is pushed with the workflow's own token and needs no secret. A job whose secret is missing
fails and says which; the other jobs are not held up by it.

## Prove the path before the tag

Everything a release does except the upload can be run on a laptop.

What the `libraries` job would publish, into a directory in place of Maven Central:

```bash
sbt -Dreasoning.release.local=/tmp/reasoning-release publish
find /tmp/reasoning-release -name '*.pom'
```

```text
/tmp/reasoning-release/com/thinkmorestupidless/ankka-reasoning-belief_3/<version>/ankka-reasoning-belief_3-<version>.pom
/tmp/reasoning-release/com/thinkmorestupidless/ankka-reasoning-graph_3/<version>/ankka-reasoning-graph_3-<version>.pom
/tmp/reasoning-release/com/thinkmorestupidless/ankka-reasoning-market_3/<version>/ankka-reasoning-market_3-<version>.pom
```

Three modules and no fourth: the job checks the same of the bundle it uploads. The image and the
descriptor as the `image` job builds them, without pushing:

```bash
DOCKER_REPOSITORY=ghcr.io/thinkmorestupidless sbt service/Docker/publishLocal deployDescriptors
grep '"image"' target/deploy/service.json
```

```text
    "image": "ghcr.io/thinkmorestupidless/ankka-reasoning:<version>",
```

And the plugin the `marketplace` job would copy is current when the documentation check passes:

```bash
just docs
```

## Cut a release

From `main`, with the pull request that leads to it merged and green:

```bash
git switch main && git pull
git tag v0.1.0
git push origin v0.1.0
```

Then check each thing it published:

- the workflow run for the tag, every job green;
- the release page, with `ankka-reasoning-0.1.0-service.json` and `ankka-reasoning-0.1.0-blueprint.conf` attached;
- `docker pull ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.0`, from a machine that is not logged in to `ghcr.io`;
- `https://repo1.maven.org/maven2/com/thinkmorestupidless/ankka-reasoning-belief_3/0.1.0/`, which
  follows the upload by minutes to an hour;
- the `ankka-reasoning` entry in ankka-marketplace's manifest, at the version.

The first release also needs the image's package made public, as
[What has to exist outside the repository](#what-has-to-exist-outside-the-repository) says.
