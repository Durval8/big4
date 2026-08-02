#!/usr/bin/env bash
# Shared gate for the PreToolUse commit hooks. Source this, then:
#
#   [ "$HOOK_IS_COMMIT" = 1 ] || exit 0
#
# Consumes stdin (the hook's JSON payload) and sets HOOK_IS_COMMIT=1 when the intercepted command
# is a real `git commit`, otherwise 0.
#
# Why this exists rather than settings.json's `if: Bash(git commit *)` condition: on 2026-08-01 that
# condition was observed firing on commands containing no `git commit` at all (e.g. `cd ... && ls -R`),
# so it cannot be relied on to scope a hook. Gating in the script is deterministic.
#
# Two deliberate behaviors:
#   - No payload on stdin (script run by hand from a terminal) => HOOK_IS_COMMIT=1, so the scripts
#     stay independently runnable for testing, which is why they were extracted in the first place.
#   - Unparseable payload => fall back to matching the raw text, rather than silently skipping the
#     check and giving a false sense that it ran.

HOOK_IS_COMMIT=0

_hook_raw_input=""
# Only read stdin if something is actually piped in; a bare terminal run must not hang here.
if [ ! -t 0 ]; then
  _hook_raw_input="$(cat 2>/dev/null || true)"
fi

if [ -z "$_hook_raw_input" ]; then
  HOOK_IS_COMMIT=1
else
  _hook_command=""
  if command -v jq >/dev/null 2>&1; then
    # .tool_input.command covers both the Bash and PowerShell tools, so a commit made through
    # either is gated identically -- scoping to Bash alone left PowerShell commits unchecked.
    _hook_command="$(printf '%s' "$_hook_raw_input" | jq -r '.tool_input.command // empty' 2>/dev/null || true)"
  fi
  [ -n "$_hook_command" ] || _hook_command="$_hook_raw_input"

  if printf '%s' "$_hook_command" \
      | grep -Eq '(^|[;&|[:space:]])git[[:space:]]+([^;&|]*[[:space:]])?commit([[:space:]]|$)'; then
    HOOK_IS_COMMIT=1
  fi
  # `git commit --help` / `-h` opens documentation, it does not create a commit.
  if printf '%s' "$_hook_command" | grep -Eq 'commit[[:space:]]+(--help|-h)([[:space:]]|$)'; then
    HOOK_IS_COMMIT=0
  fi
fi

unset _hook_raw_input _hook_command
