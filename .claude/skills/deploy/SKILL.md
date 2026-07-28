---
name: deploy
description: Deploy big4 to the live production stack (https://big4finance.online) behind the Cloudflare tunnel — pre-flight checks, the actual deploy, and post-deploy verification.
---

# Deploying big4 to production

Production is a real, live, publicly-reachable stack (`https://big4finance.online`, Cloudflare
tunnel → this same host's nginx gateway) run with Docker Compose project `big4` and `.env`. This
is **not** the isolated `-test` stack — mistakes here are visible to whoever uses the live app.

**This skill does not run `make up`/`make build` unprompted.** Walk through the checks, show the
user what's about to happen, and only run the deploy commands after they explicitly confirm — the
same bar as any other hard-to-reverse, shared-state action.

## 1. Pre-flight checks (do these first, report results before deploying)

```bash
git status --short                 # working tree must be clean — no stray uncommitted changes
git branch --show-current          # confirm this is the branch meant to go live (usually main)
git log origin/main..HEAD --oneline # anything local not yet pushed? anything pushed not yet merged?
```

Run the relevant tests for whatever changed since the last deploy (see the `verify` skill) —
don't deploy on the strength of "it compiled." At minimum, run `mvn test` from the repo root
(fast tier, both modules) if any backend/investments-service code changed since the last known-good
deploy.

Check `.env` (not `.env.test`) has a real `FINNHUB_API_KEY` if pricing/news features are expected
to work live — without one, buys are blocked and holdings go `STALE`. `.env` is gitignored, so this
has to be checked on the host directly, not via git.

## 2. The deploy

Only after pre-flight checks pass and the user has confirmed:

```bash
make build   # docker compose -p big4 build
make up      # docker compose -p big4 up -d
```

`make build && make up` is the whole deploy — there's no separate migration step to run by hand
(Flyway migrations run automatically on backend startup, per `6bb9992`).

## 3. Post-deploy verification

```bash
make ps                              # all 7 services healthy/running?
curl -sf https://big4finance.online/ -o /dev/null && echo "gateway OK"
```

For startup errors, prefer the `logs` skill (`bash .claude/scripts/fetch-service-logs.sh --since 15m`)
over `make logs` — it's bounded and pre-filtered to ERROR/WARN/Exception instead of an unbounded
raw `-f` tail you'd have to read through and Ctrl-C out of.

If `backend` or `investments-service` were rebuilt, nginx may still be routing to the old
container's IP (it resolves upstream hostnames at its own startup, not per-request):

```bash
docker compose -p big4 restart gateway   # or: make restart
```

Spot-check the actual feature that changed — hit the relevant page/endpoint through
`https://big4finance.online`, not just a health check. A green `make ps` doesn't prove the feature
works.

## Explicitly out of scope for this skill

`make clean` (wipes production volumes — Postgres + Mongo data, irrecoverable) is **never** part
of a routine deploy. If a clean reset is genuinely needed, that's a separate, explicitly-requested,
explicitly-confirmed action — never bundle it into "deploying."

## If something looks wrong post-deploy

The system is designed to fail gracefully: if investments-service or RabbitMQ has an issue,
the backend still serves the dashboard from its last message-fed projection, and only the
Investments page / buys / sells degrade (see `docs/SYSTEM_DESIGN.md#consistency--resilience`).
A bad backend deploy is more serious — it's the single source of truth for every dashboard number.
Roll back with `git checkout <last-good-ref>` on the host, then repeat steps 2–3.
