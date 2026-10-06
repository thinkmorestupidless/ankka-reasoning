# Thin wrappers: every recipe is one command, and everything works without `just`.

# Every suite. Docker is required; the graph suites start Kafka, Neo4j and the sink.
test:
    sbt test

# The service on :9000, against the compose file's Postgres, Kafka and Neo4j.
run:
    ANKKA_DB_PORT=${REASONING_POSTGRES_PORT:-5432} ANKKA_KAFKA_BOOTSTRAP_SERVERS=localhost:9094 REASONING_NEO4J_URI=bolt://localhost:7687 REASONING_NEO4J_USERNAME=neo4j REASONING_NEO4J_PASSWORD=reasoning-local-password sbt service/run

# Postgres with ankka's schema, Kafka with the compacted topic, Neo4j and the sink.
up:
    sbt schema && docker compose up -d

down:
    docker compose down -v

# Post seed/<name>.json to a running service.
seed name url="http://localhost:9000":
    sbt "seed/runMain reasoning.seed.Seed seed/{{name}}.json {{url}}"

# Time to the graph and time to an answer, for SC-007.
measure url="http://localhost:9000":
    sbt "seed/runMain reasoning.seed.Measure {{url}}"

# Write seed/ten-questions.json again from its generator.
generate:
    sbt "seed/runMain reasoning.seed.Generate"

# Empty the graph database and fill it again from the delta topic alone.
rebuild:
    ./compose/rebuild.sh

images:
    sbt service/Docker/publishLocal

descriptors:
    sbt deployDescriptors

hooks:
    git config core.hooksPath .githooks

# The living features, the glossary and the specs that name them.
features:
    uvx --from "git+https://github.com/thinkmorestupidless/speckit-bdd@v0.2.0#subdirectory=checker" speckit-bdd check --root . --glossary GLOSSARY.md --features features --specs specs

# Check every page, then build the site, llms.txt, llms-full.txt, docs-index.json and the skill
docs:
    uv run --project tools/docs docs build

# Refresh included samples, the generated table and the rendered skill from their sources
docs-sync:
    uv run --project tools/docs docs sync

# The site with live reload, while writing
docs-serve:
    uv run --project tools/docs docs serve
