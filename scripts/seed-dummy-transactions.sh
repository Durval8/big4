#!/usr/bin/env bash
# Seeds dummy transactions into a running big4 stack for pagination/sorting demos and
# manual testing. Not a DB-level load: it waits for the gateway to answer, then fires real
# POST /api/transactions requests via curl, one per line of scripts/dummy-transactions.jsonl.
# Data goes through the app's normal validation path exactly like a user typing it in, and
# nothing is baked into any Docker volume or migration -- the only artifact is this repo file.
#
# Usage: bash scripts/seed-dummy-transactions.sh [test|prod]
#   test (default) -> reads GATEWAY_PORT from .env.test, falls back to 9090
#   prod           -> reads GATEWAY_PORT from .env,      falls back to 8090
#
# Called by `make up-test-data` / `make up-data`, which bring the stack up first.

set -euo pipefail

STACK="${1:-test}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURE="$SCRIPT_DIR/dummy-transactions.jsonl"

case "$STACK" in
  test)
    ENV_FILE="$SCRIPT_DIR/../.env.test"
    DEFAULT_PORT=9090
    ;;
  prod)
    ENV_FILE="$SCRIPT_DIR/../.env"
    DEFAULT_PORT=8090
    ;;
  *)
    echo "Usage: $0 [test|prod]" >&2
    exit 1
    ;;
esac

if [ ! -f "$FIXTURE" ]; then
  echo "ERROR: fixture not found: $FIXTURE" >&2
  exit 1
fi

GATEWAY_PORT="$DEFAULT_PORT"
if [ -f "$ENV_FILE" ]; then
  FILE_PORT="$(grep -E '^GATEWAY_PORT=' "$ENV_FILE" 2>/dev/null | tail -1 | cut -d= -f2-)"
  [ -n "$FILE_PORT" ] && GATEWAY_PORT="$FILE_PORT"
fi

BASE_URL="http://localhost:${GATEWAY_PORT}"
echo "Seeding dummy transactions into the '$STACK' stack at $BASE_URL"

# Bounded readiness wait: the stack was likely just started by `make up`/`make up-test` and
# needs time to boot (Postgres, Flyway migrations, Spring context). Poll instead of hanging
# forever or firing requests at a container that isn't listening yet.
READY=0
for _ in $(seq 1 30); do
  if curl -sf -o /dev/null --max-time 3 "$BASE_URL/api/transactions?size=1"; then
    READY=1
    break
  fi
  sleep 2
done
if [ "$READY" -ne 1 ]; then
  echo "ERROR: $BASE_URL/api/transactions did not respond within 60s." >&2
  echo "Is the '$STACK' stack up? (make up / make up-test)" >&2
  exit 1
fi

CREATED=0
FAILED=0
while IFS= read -r line; do
  [ -z "$line" ] && continue
  if curl -sf -o /dev/null -X POST "$BASE_URL/api/transactions" \
      -H "Content-Type: application/json" \
      --data-raw "$line"; then
    CREATED=$((CREATED + 1))
  else
    FAILED=$((FAILED + 1))
    echo "WARN: failed to create: $line" >&2
  fi
done < "$FIXTURE"

echo "Seeded $CREATED dummy transaction(s) into the '$STACK' stack ($FAILED failed)."
[ "$FAILED" -eq 0 ]
