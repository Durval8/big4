#!/usr/bin/env bash
# Bounded, filtered log pull across big4's Docker Compose services -- an alternative to
# `make logs`/`make logs-test`, which run `docker compose logs -f` unbounded and unfiltered across
# all 7 services (postgres, mongodb, rabbitmq, backend, investments-service, frontend, gateway).
# Used by the `logs` skill so a debugging session gets a digest instead of raw, interleaved,
# multi-thousand-line output (same reasoning as .claude/agents/test-runner.md for Surefire/
# Failsafe output). Always non-follow (no -f), so it terminates on its own.
#
# Usage: fetch-service-logs.sh [--test] [--since <duration>] [--all] [service...]
#   --test          Target the big4-test Compose project/env instead of prod (big4, reads .env)
#   --since <dur>   Passed straight to `docker compose logs --since` (default: 15m)
#   --all           Print full raw output instead of filtering to error/warning lines
#   service...      Restrict to these services (default: all services in the project)

set -euo pipefail

PROJECT="big4"
ENV_ARGS=()
SINCE="15m"
FILTER_ONLY=1
SERVICES=()

while [ $# -gt 0 ]; do
  case "$1" in
    --test) PROJECT="big4-test"; ENV_ARGS=(--env-file .env.test) ;;
    --since) SINCE="$2"; shift ;;
    --all) FILTER_ONLY=0 ;;
    *) SERVICES+=("$1") ;;
  esac
  shift
done

COMPOSE=(docker compose -p "$PROJECT" "${ENV_ARGS[@]}")

if ! RAW="$("${COMPOSE[@]}" logs --no-color --timestamps --since "$SINCE" "${SERVICES[@]}" 2>&1)"; then
  echo "docker compose logs failed for project=$PROJECT -- is that stack up (\`docker compose -p $PROJECT ps\`)?"
  echo "If using --test, confirm .env.test exists (it's gitignored, so a fresh checkout won't have it)."
  echo "$RAW"
  exit 1
fi

if [ -z "$RAW" ]; then
  echo "No log output for project=$PROJECT since=$SINCE${SERVICES:+ services=${SERVICES[*]}}."
  exit 0
fi

if [ "$FILTER_ONLY" -eq 0 ]; then
  echo "$RAW"
  exit 0
fi

MATCHED="$(echo "$RAW" | grep -E 'ERROR|WARN|Exception|Caused by' || true)"

if [ -z "$MATCHED" ]; then
  TOTAL=$(echo "$RAW" | wc -l | tr -d ' ')
  echo "No ERROR/WARN/Exception/Caused-by lines in the last $SINCE for project=$PROJECT ($TOTAL total log lines suppressed -- rerun with --all to see everything)."
  exit 0
fi

echo "-- matched-line counts by service --"
echo "$MATCHED" | awk -F'|' '{gsub(/^[ \t]+|[ \t]+$/,"",$1); print $1}' | sort | uniq -c | sort -rn
echo
echo "-- matched lines (ERROR/WARN/Exception/Caused by) --"
echo "$MATCHED"
