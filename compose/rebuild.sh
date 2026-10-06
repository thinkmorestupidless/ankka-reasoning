#!/bin/sh
# Rebuilds the graph database from the delta topic alone. The service is not involved.
set -eu
docker compose stop sink
docker compose exec -T neo4j cypher-shell -u neo4j -p reasoning-local-password \
  "MATCH (n) CALL (n) { DETACH DELETE n } IN TRANSACTIONS OF 10000 ROWS"
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
  --group reasoning-graph.graph.in --reset-offsets --to-earliest --all-topics --execute
docker compose start sink
