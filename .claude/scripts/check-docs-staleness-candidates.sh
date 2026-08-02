#!/usr/bin/env bash
# Deterministic pre-filter mirroring docs-sync.md's mapping table: for each pattern of changed
# source files, checks whether the matching doc was touched in the SAME staged diff, and reports
# only the candidates where it wasn't. Prints a "CANDIDATE:" block per hit.
#
# Called by .claude/scripts/docs-staleness-hook.sh, which greps for "CANDIDATE:" and turns any hits
# into a non-blocking advisory. This used to feed a `type: agent` hook that judged each candidate;
# that hook was removed on 2026-08-01 because it could not run without Bash access and then blocked
# every commit in the session. Consequence: nothing now distinguishes a genuine behavior change from
# a refactor/rename/test-only change -- this script matches filenames only, so the advisory it
# produces is a prompt to look, not a finding.
#
# If the mapping here changes, update docs-sync.md's table to match (and vice versa).

FILES="$(git diff --cached --name-only)"

if [ -z "$FILES" ]; then
  echo "Nothing staged."
  exit 0
fi

FOUND=0

report_case() {
  local src_pattern="$1" doc_pattern="$2" label="$3"
  local src_hit doc_hit
  src_hit="$(echo "$FILES" | grep -E "$src_pattern" || true)"
  if [ -n "$src_hit" ]; then
    doc_hit="$(echo "$FILES" | grep -E "$doc_pattern" || true)"
    if [ -z "$doc_hit" ]; then
      FOUND=1
      echo "CANDIDATE: $label"
      echo "  Source changed: $(echo "$src_hit" | tr '\n' ' ')"
      echo "  Doc NOT touched in this diff (expected match: $doc_pattern)"
      echo
    fi
  fi
}

report_case 'BalanceService\.java|BudgetService\.java' \
  'docs/DATA_MODEL\.md' \
  'balance/budget formula changed, DATA_MODEL.md not updated'

report_case 'Controller\.java|dto/.*Request\.java|dto/.*Response\.java' \
  'docs/API\.md' \
  'endpoint/DTO shape changed, API.md not updated'

report_case '/messaging/' \
  'docs/SYSTEM_DESIGN\.md|docs/INVESTMENTS_SERVICE\.md' \
  'messaging contract changed, SYSTEM_DESIGN.md/INVESTMENTS_SERVICE.md not updated'

report_case 'docker-compose\.yml|gateway/nginx\.conf' \
  'docs/SYSTEM_DESIGN\.md' \
  'compose/gateway routing changed, SYSTEM_DESIGN.md not updated'

if [ "$FOUND" -eq 0 ]; then
  echo "No candidates: no staged file matches a pattern this script checks, or the matching doc was already touched."
fi
