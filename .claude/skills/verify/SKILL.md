---
name: verify
description: Verify a code change in big4 (backend, investments-service, or frontend) actually works — runs the right test tier for what changed, reads the diff, checks no test was weakened just to force a pass, and reports pass/fail with evidence. See reference.md for the full tier-routing table, weakened-test red flags, and known test-suite noise.
---

# Verifying a change in big4

Four steps, every time, in this order. Don't skip step 3 — a test suite that's green because an
assertion was loosened is not a passing verification.

## 1. Run the test suite

Pick the right tier for what changed — don't reflexively run the whole `mvn verify` matrix. See
`reference.md#tier-routing` for the full table (which test class per area, when Docker/Testcontainers
is needed). Before anything needing `mvn verify` or a Testcontainers `*IT`, confirm the Docker daemon
is actually up: `docker info > /dev/null 2>&1`. For the full `mvn verify` across both modules,
delegate to the `test-runner` subagent instead of running it inline — its Surefire/Failsafe output is
large and mostly noise.

## 2. Read the diff

`git diff --stat HEAD` (or against the target branch) to see what changed and route step 1's tier.
Then, separately, get the **full** diff for anything that touched a test file — you need the actual
content, not just the file list, to do step 3:

```bash
git diff HEAD -- '**/*Test.java' '**/*IT.java' '**/*.test.ts' '**/*.test.tsx'
```

## 3. Check that no test was weakened just to make things pass

For every test file touched in the diff, confirm assertions got **stricter or equal**, never
looser, and that nothing was disabled, skipped, or deleted to reach green. See
`reference.md#weakened-test-red-flags` for the specific patterns to scan for (loosened assertions,
`@Disabled`/`@Ignore`, expected values edited to match new output instead of the code being fixed,
mocks introduced around the exact thing under test, narrowed exception assertions, removed edge
cases). If you find one, treat it as a finding — report it the same as a failing test, don't let it
pass silently just because the suite went green.

## 4. Report pass or fail, with evidence

Every verification ends with: **PASS or FAIL**, the exact command(s) run, and the evidence — test
names + counts for a pass, the failing assertion/exception for a fail, the specific diff hunk for a
weakened-test finding. "It works" without the command and its output isn't a verification.

## Frontend note

`npm run build` (`tsc -b && vite build`) only proves the TypeScript compiles — this repo has no
lint script and no test runner configured for the frontend, so it's the only automated gate that
exists. For an actual behavior check, start the dev server (see the `run` skill) and exercise the
change in a browser; don't report a frontend fix as verified from the build alone.

See `reference.md#known-noise` for test flakiness that isn't your change's fault before assuming a
failure is a regression.
