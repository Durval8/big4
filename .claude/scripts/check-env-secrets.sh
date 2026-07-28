#!/usr/bin/env bash
# Flags staged .env* additions that look like a real secret value (20+ char assignment).
# Used by the PreToolUse secret-scan hook (.claude/settings.json) on `git commit`.
# Extracted from an inline hook command so it's independently testable/runnable.

HITS="$(git diff --cached --diff-filter=ACM -- '.env*' 2>/dev/null | grep -E '^\+[A-Za-z_][A-Za-z0-9_]*=.{20,}')"

if [ -n "$HITS" ]; then
  REASON="Staged .env* changes look like they add a real secret value (20+ char assignment): ${HITS//$'\n'/ | } -- confirm this isn't a live credential before committing."
  jq -n --arg reason "$REASON" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"ask",permissionDecisionReason:$reason}}'
fi
