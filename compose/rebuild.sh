#!/bin/sh
# Rebuilds the graph database from the delta topic alone. The service is not involved.
# The sink reads the topic again from its start at a version one higher, kept in .env for compose.
set -eu
docker compose stop sink
docker compose exec -T neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (n) CALL (n) { DETACH DELETE n } IN TRANSACTIONS OF 10000 ROWS"
current=$(sed -n 's/^REASONING_SINK_VERSION=//p' .env 2>/dev/null || true)
next=$(( ${current:-1} + 1 ))
if [ -f .env ] && grep -q '^REASONING_SINK_VERSION=' .env; then
  sed -i.bak "s/^REASONING_SINK_VERSION=.*/REASONING_SINK_VERSION=$next/" .env && rm -f .env.bak
else
  echo "REASONING_SINK_VERSION=$next" >> .env
fi
echo "rebuild: the sink reads the topic again at version $next"
docker compose up -d sink
