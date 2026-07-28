# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Single-user personal finance dashboard (MVP): transactions, budgets, and dashboard metrics in a
Spring Boot + Postgres **backend**; stock investments (share-based holdings, automated Finnhub
pricing, a news feed) in a separate Spring Boot + MongoDB **investments-service**; RabbitMQ as the
one-way messaging bridge between them; a React + TypeScript (Vite) **frontend**; an nginx
**gateway** fronting everything as one origin. All orchestrated via Docker Compose.

This is deliberately more complex than a single-user app strictly needs — the investments-service
extraction (separate DB, broker, gateway) was an explicit architecture-exploration choice, not
accidental scope creep. Don't "simplify" it back into the monolith without being asked.

**Start with `docs/SYSTEM_DESIGN.md`** for the authoritative system-level view (topology, data
ownership, routing, messaging, key flows) before making cross-cutting changes. Then:
`docs/ARCHITECTURE.md` (module-internal package layouts), `docs/DATA_MODEL.md` (entities +
exact balance/metric formulas), `docs/API.md` (endpoint reference), `docs/TESTING.md` (test
breakdown). Feature-specific design records: `docs/INVESTMENTS_SERVICE.md`,
`docs/INVESTMENT_PRICING.md`, `docs/INVESTMENT_NEWS.md`. The docs are kept current — treat them as
ground truth, and update the relevant doc when a change alters the architecture or a formula.

## The one rule that shapes everything: no sync HTTP between backend and investments-service

The backend **never calls** the investments service. All cross-service data flows one way over
RabbitMQ (cash-leg commands + last-write-wins value snapshots). The backend keeps two **message-fed
projections** (`investment_cash_flow`, `investment_valuation` in Postgres) — it never queries Mongo.
If a task seems to need the backend to read live investment data, the answer is a new/adjusted
message, not an HTTP call. See `docs/SYSTEM_DESIGN.md#inter-service-messaging`.

The investments-service **owns the message contract** (`InvestmentsMessaging` + `contract` records
in that module); the backend's `messaging/` package holds consumer-side mirror records, guarded by
`InvestmentMessageContractTest` (JSON-fixture contract test). Changing a message shape means
updating both sides and that test.

## Commands

Everything is a Maven multi-module reactor at the root (`pom.xml` lists `backend` and
`investments-service` as modules but is **not** a shared parent — each keeps its own
`spring-boot-starter-parent` and stays independently buildable/deployable).

```bash
# Both services, from repo root
mvn test                          # fast unit + web-slice tests, no Docker
mvn verify                        # + Testcontainers integration tests (needs a running Docker daemon)

# Per module (each independently buildable)
cd backend && mvn test            # or mvn verify
cd investments-service && mvn test

# A single test class / method
mvn test -Dtest=BalanceServiceTest
mvn test -Dtest=BalanceServiceTest#comprehensiveScenarioMatchesAllFormulas
mvn verify -Dit.test=HoldingServiceIT    # a single Failsafe integration test

# Frontend
cd frontend && npm install
npm run dev          # Vite dev server on :5173, proxies /api/investments -> :8081, /api -> :8080
npm run build        # tsc -b && vite build (this is the closest thing to a typecheck/lint gate)

# Full stack via Docker Compose (production-style, one origin at :8090)
cp .env.example .env
docker compose up --build

# Isolated local test stack (separate Compose project/volumes, ports offset ~1000)
make build-test && make up-test     # app at http://localhost:9090
make down-test / make logs-test / make clean-test (clean-test wipes volumes)

# Production stack (Cloudflare tunnel target)
make build && make up               # / make down / make logs / make clean (destructive)
```

Run locally without Docker: needs Postgres, MongoDB, RabbitMQ (`docker compose up postgres mongodb
rabbitmq` covers just those three), then `mvn spring-boot:run` in each of `backend` (:8080) and
`investments-service` (:8081, needs `FINNHUB_API_KEY`).

**No lint/format command exists yet** in either the Java modules or the frontend (`package.json`
has no `lint` script) — don't invent one or assume a config file for it exists.

### Testcontainers notes (see `docs/TESTING.md` for full detail)

- `mvn verify` needs a running Docker daemon; `mvn test` does not.
- Provider adapters (`FinnhubProvider`, `FinnhubNewsProvider`) are tested with
  `MockRestServiceServer`, not an embedded HTTP mock server — this environment can't open the
  loopback selector pipe an embedded WireMock/Jetty needs.
- Shared-broker isolation: Spring caches one context per test class, so a stray `@RabbitListener`
  from one IT's context can steal messages meant for another IT on the shared broker. ITs that
  don't need consumers set `spring.rabbitmq.listener.simple.auto-startup=false`.

## Architecture notes that aren't obvious from any single file

- **`AccountType` has three values (`CHECKING`, `SAVINGS`, `INVESTING`) but `INVESTING` is not a
  legal value on a `Transaction`** — `TransactionService` rejects it. `INVESTING` survives purely
  as the dashboard's reflection of investments-service holdings value. If you're touching
  transaction validation, don't relax this.
- **Investing math has two separate accumulators that must not be collapsed**: `netCashInvested`
  (cash flow: Σ FUND − Σ CASH_OUT; drives the backend's `netInvestment` and cash balances) vs.
  `costBasis`/`avgCost` (accounting cost of shares still held; drives `positionChangePct`). Mixing
  these back together reintroduces the %-skew bug the share-based rework fixed — see
  `docs/INVESTMENT_PRICING.md#average-cost-accounting-fixes-the-skew`.
- **Cash-leg commands vs. value snapshots are different delivery guarantees, on purpose**: cash-leg
  commands are must-not-lose (outbox + idempotent consumer keyed by `eventId`); value snapshots are
  last-write-wins by `asOf` (a lost intermediate self-heals on the next one). Don't "fix" the
  snapshot path by adding outbox/idempotency machinery to it — that's a deliberate simplification.
- **`@EnableJpaAuditing` lives in its own `config/JpaConfig`** in the backend, not on the
  application class, because `@WebMvcTest` slices (no JPA) would otherwise fail wiring the auditing
  handler.
- **Budget categories are fetched EAGER deliberately** (`@ElementCollection`) — services aren't
  `@Transactional` and `open-in-view` is off, so a lazy collection would fail to load once the
  session closes during response mapping.
- **`.env.test` is gitignored** (added alongside `.env`) — never let a real API key land in it since
  historically it had no gitignore protection. `.env.example` is the only template file meant to be
  committed with placeholder values.
- **Gateway resolves upstream hostnames at startup**: after rebuilding a backend/investments-service
  container (new IP), the gateway may need `docker compose restart gateway` (or `make restart[-test]`)
  to pick it up. A dynamic-resolver nginx config would remove this; not yet done.
- **Frontend uses relative `/api/...` URLs everywhere** (`VITE_API_BASE_URL` is empty in Docker) so
  it works unmodified behind the gateway with no CORS. Locally, Vite's `server.proxy` splits
  `/api/investments` → investments-service, `/api` → backend
  (override via `VITE_INVESTMENTS_PROXY_TARGET` / `VITE_API_PROXY_TARGET`).
- **Two isolated Docker Compose environments share one `docker-compose.yml`**: production
  (`-p big4`, `.env`) and a local test stack (`-p big4-test`, `.env.test`, ports offset ~1000,
  distinct `RABBITMQ_ERLANG_COOKIE`) — fully separate volumes/networks, safe to run simultaneously.
- **Every DTO in both modules is a Java `record`, never a class** — request/response shapes
  (`TransactionRequest`, `HoldingResponse`, `ErrorResponse`, etc.) are all immutable records. Don't
  introduce a DTO class as a one-off.
- **Exception-to-HTTP mapping lives only in each module's `GlobalExceptionHandler`** — no controller
  anywhere has its own `@ExceptionHandler`. Add a new exception type there, not locally on a
  controller.
- **Every service/component uses constructor injection (`private final` fields + a constructor)** —
  there is no `@Autowired` field injection anywhere in either module. Keep new classes consistent.
- **No service class is `@Transactional`** — ties to the eager-fetch/open-in-view note above; don't
  add `@Transactional` to a new service without first re-checking whether that assumption still
  holds for what you're building.

## Continuous improvement of Claude Code workflows

This repo actively uses skills (`.claude/skills/`), hooks (`.claude/settings.json`), and subagents
(`.claude/agents/`) to streamline development — e.g. `run`/`verify`/`deploy`/`endpoints` skills,
`test-runner`/`docs-sync` subagents, the secret-scan and docs-staleness hooks. When you notice a
repeated manual step, a workflow done by hand more than once, or friction that a new skill/hook/
subagent could remove, proactively flag it and suggest the addition — don't wait to be asked.
</content>
