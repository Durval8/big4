---
name: logs
description: Pull and digest big4's Docker Compose logs across its 7 services (postgres, mongodb, rabbitmq, backend, investments-service, frontend, gateway) for debugging — bounded and filtered, unlike `make logs`/`make logs-test`'s unbounded raw `-f` tail. Use when diagnosing a cross-service issue, checking post-deploy health, or investigating an error without dumping thousands of interleaved log lines into context.
---

# Reading big4's container logs

`make logs` and `make logs-test` run `docker compose logs -f` — no time bound, no filtering, all 7
services interleaved. That's fine for a human watching a terminal, but wrong for an agent: `-f`
never terminates on its own, and a raw dump buries the two or three lines that matter in noise.
Use this skill instead for anything scripted/investigative.

## The script

```bash
bash .claude/scripts/fetch-service-logs.sh [--test] [--since <duration>] [--all] [service...]
```

- No flags: last 15 minutes, **prod** project (`big4`), all services, filtered to lines matching
  `ERROR|WARN|Exception|Caused by`, grouped with a per-service count summary before the matched
  lines.
- `--test`: target the `big4-test` project/env instead (`docker compose -p big4-test --env-file
  .env.test`) — use this for anything on the isolated local test stack, never prod, unless you're
  specifically checking prod health (see the `deploy` skill).
- `--since <duration>`: any value `docker compose logs --since` accepts (`10m`, `2h`, an RFC3339
  timestamp). Widen this if the default 15-minute window comes back empty but you know the issue
  happened earlier.
- `--all`: skip the error/warning filter and print everything in the window — use sparingly, this
  is the raw-dump case the filter exists to avoid.
- Trailing args restrict to specific services, e.g. `backend investments-service` to skip
  postgres/mongodb/rabbitmq noise when you already know which side owns the bug.

Confirm the Docker daemon is up first (`docker info > /dev/null 2>&1`) if you haven't already —
the session-start hook reports this once per session, but it can go down mid-session.

## When to run it inline vs. delegate

- Default scope (15m, filtered) is cheap — run the script directly and read its output.
- A wide `--since` window or `--all` can still produce a large pull. In that case, delegate to the
  `log-digest` subagent instead of running the script yourself and pasting the output back — same
  reasoning as delegating a full `mvn verify` to `test-runner` rather than dumping Surefire output
  inline.

## Reading the output

- The script's own filter already narrows to `ERROR|WARN|Exception|Caused by` — an empty result
  means genuinely nothing matched in that window, not that the script failed silently (it says so
  explicitly, including how many lines it suppressed).
- Known non-issues to recognize before treating a match as a real bug: the shared-broker
  `@RabbitListener` cross-context contention (`docs/TESTING.md`), and investments-service falling
  back to `STALE` pricing when `FINNHUB_API_KEY` is unset or rate-limited — both look like errors
  in a raw grep but are documented, expected behavior in those specific circumstances.
- If `backend` or `investments-service` was just rebuilt and the gateway still shows connection
  errors to it, that's the known stale-upstream-IP gotcha (`run`/`deploy` skills) — restart the
  gateway rather than treating it as an application bug.

## What this skill is (and isn't) for

- **Bounded, filtered log inspection** for debugging or a post-deploy sanity check.
- **Not** a replacement for `make logs`/`make logs-test` when a human wants to watch logs live in
  their own terminal — those are still the right tool for interactive tailing.
- **Not** a metrics/observability system — there's no log aggregation here, this only reads
  whatever Docker still has buffered for the running containers.
