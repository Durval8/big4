#!/usr/bin/env bash
# Flags staged .env* additions that look like a real secret value (20+ char assignment).
# Used by the PreToolUse secret-scan hook (.claude/settings.json) on `git commit`.
# Extracted from an inline hook command so it's independently testable/runnable.

# Self-gate on "is this actually a commit" rather than relying on settings.json's `if` condition,
# which was observed on 2026-08-01 firing for commands containing no `git commit` at all. Sourcing
# the shared gate also means commits made through the PowerShell tool are scanned, not just Bash.
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -n "$REPO_ROOT" ] && [ -f "$REPO_ROOT/.claude/scripts/lib/commit-gate.sh" ]; then
  # shellcheck source=lib/commit-gate.sh
  . "$REPO_ROOT/.claude/scripts/lib/commit-gate.sh"
  [ "${HOOK_IS_COMMIT:-1}" = 1 ] || exit 0
fi

HITS="$(git diff --cached --diff-filter=ACM -- '.env*' 2>/dev/null | grep -E '^\+[A-Za-z_][A-Za-z0-9_]*=.{20,}')"

if [ -n "$HITS" ]; then
  REASON="Staged .env* changes look like they add a real secret value (20+ char assignment): ${HITS//$'\n'/ | } -- confirm this isn't a live credential before committing."
  jq -n --arg reason "$REASON" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"ask",permissionDecisionReason:$reason}}'
fi
