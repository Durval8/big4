#!/usr/bin/env bash
# PreToolUse hook entrypoint for the docs-staleness check.
#
# Replaces an earlier `type: agent` hook that had three failure modes, all hit on 2026-08-01:
#
#   1. FAILED CLOSED. The agent needed Bash to run the pre-filter and `git diff --cached`. In a
#      permission mode where the sub-agent's Bash/PowerShell is denied, it could not run the check
#      at all and conservatively returned "ask" -- blocking *every* commit in the session with a
#      message about docs staleness that had nothing to do with the actual diff. A doc check must
#      never be able to wedge committing.
#   2. IGNORED ITS OWN `if` GATE. Configured with `if: Bash(git commit *)`, it still fired on
#      ordinary calls like `cd ... && ls -R`, spawning an agent per Bash call. This script therefore
#      gates itself on the command text instead of trusting the `if` condition.
#   3. WAS NOT BYPASSABLE THE OBVIOUS WAY. Being a PreToolUse hook rather than a git hook, it is
#      unaffected by `git commit --no-verify`, which is the first thing anyone reaches for. The
#      advisory design below removes the need for a bypass at all.
#
# Design: deterministic, self-gating, and FAIL-OPEN. It never returns a permissionDecision, so it
# cannot block or auto-approve a commit -- it only emits a systemMessage advisory when a staged
# source change looks like it should have come with a doc update. Exits 0 unconditionally.

set -uo pipefail

# Always allow, whatever happens below.
trap 'exit 0' EXIT

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
[ -n "$REPO_ROOT" ] || exit 0
cd "$REPO_ROOT" 2>/dev/null || exit 0

RAW_INPUT="$(cat 2>/dev/null || true)"

# Extract the command being run. Works for both the Bash and PowerShell tools, since a commit made
# through either must be checked -- scoping to Bash alone left PowerShell commits unchecked.
COMMAND=""
if [ -n "$RAW_INPUT" ] && command -v jq >/dev/null 2>&1; then
  COMMAND="$(printf '%s' "$RAW_INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || true)"
fi
# If the payload could not be parsed, fall back to the raw text rather than silently skipping.
[ -n "$COMMAND" ] || COMMAND="$RAW_INPUT"

# Self-gate: only real commits are interesting. `git commit --help`/`-h` is not a commit.
printf '%s' "$COMMAND" | grep -Eq '(^|[;&|[:space:]])git[[:space:]]+([^;&|]*[[:space:]])?commit([[:space:]]|$)' || exit 0
printf '%s' "$COMMAND" | grep -Eq 'commit[[:space:]]+(--help|-h)([[:space:]]|$)' && exit 0

PREFILTER="$REPO_ROOT/.claude/scripts/check-docs-staleness-candidates.sh"
[ -x "$PREFILTER" ] || [ -f "$PREFILTER" ] || exit 0

CANDIDATES="$(bash "$PREFILTER" 2>/dev/null || true)"
printf '%s' "$CANDIDATES" | grep -q '^CANDIDATE:' || exit 0

# Candidates found -> advise, don't block. Keep it terse; the model reads this and can act.
MESSAGE="Docs-staleness advisory (not blocking) -- a staged source change has no matching doc update in the same diff:

$CANDIDATES
If this is a genuine behavior change, update the named doc (or run the docs-sync subagent) before or right after this commit. If it is a refactor, rename, or test-only change with no external effect, ignore this -- the check matches filenames and cannot tell the difference."

if command -v jq >/dev/null 2>&1; then
  jq -n --arg msg "$MESSAGE" '{systemMessage: $msg}' 2>/dev/null || true
else
  # No jq: still surface something useful on stderr rather than staying silent.
  printf '%s\n' "$MESSAGE" >&2
fi

exit 0
