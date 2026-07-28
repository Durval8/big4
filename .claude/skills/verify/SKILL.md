---
name: verify
description: Verify a code change in big4 (backend, investments-service, or frontend) actually works — picks the right test tier for what changed instead of always running the full Testcontainers suite.
---

# Verifying a change in big4

This repo has three independently-verifiable areas (`backend/`, `investments-service/`,
`frontend/`) plus a cross-cutting messaging contract between the two Java services. Don't
reflexively run the whole `mvn verify` matrix — scope the check to what actually changed.

## 1. Figure out what changed and what tier that needs

```bash
git diff --stat HEAD   # or against the target branch
```

| Touched | Minimum verification |
|---|---|
| `backend/**` (not messaging) | `cd backend && mvn test -Dtest=<RelevantTest>` (fast, no Docker) |
| `investments-service/**` (not messaging) | `cd investments-service && mvn test -Dtest=<RelevantTest>` |
| Anything under `**/messaging/` or `**/messaging/contract/` in **either** module | Also run the contract test: `cd backend && mvn test -Dtest=InvestmentMessageContractTest` — this is the one test whose entire job is catching drift between the investments-service's canonical message records and the backend's mirror records |
| A balance/budget formula (`BalanceService`, `BudgetService`) | `BalanceServiceTest` / `BudgetServiceTest` plus check whether `docs/DATA_MODEL.md`'s formulas still match |
| A buy/cash-out/pricing/news code path | The corresponding `*IT` in `investments-service` (`HoldingServiceIT`, `PriceRefreshIT`, `NewsServiceIT`) — these need Docker (Testcontainers Mongo + RabbitMQ), see step 2 |
| `frontend/**` | `cd frontend && npm run build` — this repo has **no lint script and no test runner configured**, so `tsc -b && vite build` succeeding is the only automated gate that exists. For behavior, run it in a browser (see the `run` skill) |
| Both services' `pom.xml` / module wiring | `mvn test` from the repo root (both modules, fast tier only) |

Only reach for the **full** `mvn verify` (both modules, all Testcontainers ITs) when the change
is broad (e.g. touches shared messaging contracts or module wiring) or when you're about to hand
off/merge — it's slow and needs Docker up. For a scoped change, a targeted `-Dtest=`/`-Dit.test=`
run is faster and just as conclusive.

## 2. Before anything needing `mvn verify` or a Testcontainers `*IT`

Check the Docker daemon is actually up first — half this repo's test suite silently depends on it:

```bash
docker info > /dev/null 2>&1 && echo "Docker OK" || echo "Docker daemon not running — mvn verify / *IT tests will fail/hang"
```

## 3. Running it

```bash
# Fast tier (no Docker) — always safe, always fast
cd backend && mvn test -Dtest=<Test>[#<method>]
cd investments-service && mvn test -Dtest=<Test>[#<method>]

# Testcontainers tier (needs Docker) — scope to one IT when possible
cd investments-service && mvn verify -Dit.test=<NameIT>
```

If you need the **entire** `mvn verify` across both modules, delegate it to a subagent rather
than running it inline — Testcontainers/Failsafe output is large and mostly noise, and dumping it
into the main conversation wastes context for no benefit. Report back pass/fail plus any failure
stack traces, not the full log.

## 4. Frontend behavior (not just the build)

`npm run build` only proves the TypeScript compiles. To actually verify a UI change works:
start the dev server (`npm run dev`, proxies to whichever backend/investments-service you have
running — see the `run` skill) and exercise the golden path plus the edge case the change targets
in a browser. Don't claim a frontend fix is verified from `npm run build` passing alone.

## 5. Known noise to ignore

- Shared-broker test isolation: if a Testcontainers IT you didn't touch fails intermittently with
  a message-consumption assertion, check whether it's the known cross-context `@RabbitListener`
  contention (see `docs/TESTING.md`) before assuming your change broke it.
- The provider adapter tests (`FinnhubProviderTest`, `FinnhubNewsProviderTest`) use
  `MockRestServiceServer`, not a real HTTP server or live Finnhub — no network/API-key needed to
  run them.
