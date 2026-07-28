#!/usr/bin/env bash
# Deterministic pre-filter for the `verify` skill's step 3 ("no test weakened just to pass").
# Flags mechanical red flags in a test-file diff. Does NOT replace reading the diff yourself --
# subtler cases (an expected value quietly edited to match new output, with no stated reason) need
# real judgment about whether the accompanying production-code change actually justifies it. See
# .claude/skills/verify/reference.md#weakened-test-red-flags for the full list this only partially
# automates.
#
# Usage: check-weakened-tests.sh [<git-diff-ref>]
#   Default ref is HEAD (working-tree diff). Pass --cached for the staged diff instead.

REF="${1:-HEAD}"

DIFF="$(git diff "$REF" -- '*Test.java' '*IT.java' '*.test.ts' '*.test.tsx' 2>/dev/null)"

if [ -z "$DIFF" ]; then
  echo "No test files changed against $REF."
  exit 0
fi

FLAGS=""

flag() {
  local pattern="$1" label="$2"
  local hits
  hits="$(echo "$DIFF" | grep -E "^\+.*${pattern}" || true)"
  if [ -n "$hits" ]; then
    FLAGS="${FLAGS}
[$label]
${hits}
"
  fi
}

flag '@Disabled|@Ignore' 'disabled/ignored test added'
flag '\.skip\(|\bxit\(|\bxdescribe\(' 'frontend test skip added'

ADDED_ASSERTS=$(echo "$DIFF" | grep -cE '^\+.*\b(assert[A-Za-z]*|expect)\(')
REMOVED_ASSERTS=$(echo "$DIFF" | grep -cE '^-.*\b(assert[A-Za-z]*|expect)\(')

if [ "$REMOVED_ASSERTS" -gt "$ADDED_ASSERTS" ]; then
  FLAGS="${FLAGS}
[assertion count dropped]
Removed ${REMOVED_ASSERTS} assert/expect line(s), added only ${ADDED_ASSERTS} -- check whether a
check was deleted rather than a test being cleanly rewritten.
"
fi

if [ -n "$FLAGS" ]; then
  echo "WEAKENED-TEST RED FLAGS FOUND (mechanical check only -- still read the diff yourself):"
  echo "$FLAGS"
  exit 1
else
  echo "No mechanical red flags found in the test-file diff. Still read the full diff yourself for"
  echo "subtler cases (expected values quietly edited to match new output, tautological mocking,"
  echo "narrowed exception assertions) -- see reference.md#weakened-test-red-flags."
  exit 0
fi
