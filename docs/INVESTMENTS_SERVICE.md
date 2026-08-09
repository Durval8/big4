# Investments Service Extraction — Architecture Spec

> **Status: IMPLEMENTED.** Spec'd and built 2026-07-24. Investments are a standalone
> Spring Boot service (`investments-service/`) with its own MongoDB, hosting the
> price-fetch job, communicating with the backend **only via RabbitMQ (no
> synchronous inter-service HTTP)**. Supersedes the *infrastructure* parts of
> [INVESTMENT_PRICING.md](INVESTMENT_PRICING.md) (the share/avg-cost accounting and
> provider integration there also live in this service now).

## As-built notes (deviations from the original spec)

- **Contract ownership (open q #7):** the **investments service owns** the canonical
  message shapes (`InvestmentsMessaging` + the `contract` records); the backend keeps
  consumer-side mirror records guarded by a JSON-fixture contract test
  (`InvestmentMessageContractTest`). No shared Maven module (would re-couple the builds).
- **Gateway also serves the frontend** (single origin) rather than only partitioning the
  API — removes CORS and the two-base-URL problem. nginx: `/api/investments`→service,
  `/api`→backend, `/`→frontend.
- **Manual pricing** endpoint (`POST /api/investments/{id}/price`) added for UNRESOLVED
  holdings (pricing open q #9); UNRESOLVED symbols are excluded from the price job.
- **Realized gain** is tracked per holding, totalled across *every* holding (open and
  cashed-out — a fully-closed position must keep counting, since that's the one moment
  the figure matters most), and surfaced as its own "Realized Gains" card on the
  Investments page (pricing open q #5, now resolved in favor of showing it).
- **Snapshot tie-break:** the backend accepts snapshots with an equal `asOf` (last write
  wins) and rejects only strictly-older ones, so a buy + immediate refresh in the same
  millisecond can't drop the newer value.
- **Testing:** the provider adapter is tested with `MockRestServiceServer` (this
  environment can't open the loopback selector pipe an embedded WireMock/Jetty needs);
  everything else uses Testcontainers (Mongo + RabbitMQ, Postgres on the backend).

## The shift

- A new **Investments Service** owns everything investment-related (holdings, shares, value, position change, buy/sell events, and — later — per-stock **news** for a unified feed on the Investments page).
- Its store is **MongoDB**: one document per holding aggregates heterogeneous, read-mostly data (stock, value, change, embedded news) — a better fit than several relational tables for the roadmap, and what the page renders.
- **`/investments` is served entirely by this service** (CRUD + summary); the frontend talks to it, not the backend.
- The stock-price provider is **encapsulated in this service** — nothing else calls it.
- The backend and the service exchange data **only over RabbitMQ** — no HTTP between them (a deliberate decision to avoid synchronous coupling).

## Target topology

```
        ┌───────────┐   /api/investments/*   ┌──────────────────────┐
Browser │  Frontend │ ─────────────────────▶ │  Investments Service  │
        └───────────┘   (via gateway)        │  (Spring Boot)        │
              │  everything else              │  ├─ Mongo (holdings + │
              ▼                               │  │   events + news)   │
        ┌───────────┐                         │  ├─ price-fetch job    │
        │  Backend  │                         │  ├─ provider adapter   │
        │ (Postgres)│ ◀── RabbitMQ ────────── │  └─ outbox relay       │
        └───────────┘   cash-leg commands     └──────────┬────────────┘
                         + value snapshots                │ quote()
        (backend NEVER calls the service)        ┌────────▼────────┐
                                                 │  Stock price API │
                                                 └─────────────────┘
   RabbitMQ: (1) price-refresh work queue — intra-service;
             (2) investments → backend — cash-leg commands + value snapshots.
```

## Service boundaries & data ownership

| Concern | Owner | Store |
|---|---|---|
| Cash accounts (CHECKING/SAVINGS), transactions, budgets, dashboard metrics | Backend | Postgres |
| Holdings (shares, avgCost, value, position change), buy/sell events, news | Investments Service | MongoDB |
| Stock-price provider calls, price-refresh job, buy-time quote | Investments Service | — |
| Investing net value **for the dashboard** (shallow copy) | Backend | Postgres (message-fed) |
| Investing net value **for the Investments page** (live) | Investments Service | MongoDB |

MongoDB document sketch (one per holding):
```jsonc
{ "_id", "stockSymbol", "quantity", "costBasis", "avgCost", "latestPrice", "priceAsOf",
  "priceStatus": "OK|STALE|UNRESOLVED", "status": "OPEN|CASHED_OUT",
  "events": [ { "type": "FUND|CASH_OUT", "amount", "shares", "price", "account", "date" } ],
  "news":   [ { "headline", "url", "source", "publishedAt" } ] }   // news populated later
```
The `events` array is the investing cash-flow ledger; accounting (separate
`netCashInvested` vs `costBasis`) is unchanged from
[INVESTMENT_PRICING.md](INVESTMENT_PRICING.md#average-cost-accounting-fixes-the-skew).
The service also keeps an **outbox collection** for reliable publish.

## Cross-service contract — messaging only, no HTTP

The backend never calls the service. Everything flows one way, over RabbitMQ, in
**two streams of different nature**:

| Stream | Nature | Trigger | Backend effect | On loss |
|---|---|---|---|---|
| **Cash-leg command** (`InvestmentFunded {account, amount, date}` / `InvestmentCashedOut {amount→SAVINGS, date}`) | **incremental command** | buy / cash-out | record a cash movement → CHECKING/SAVINGS balance **and** `netInvestment` computed locally | must not be lost → **outbox + idempotent consumer** |
| **Value snapshot** (`InvestmentNetValue {netValue, asOf}`) | **state snapshot** (full current value) | buy / cash-out / price refresh | overwrite the **shallow-copy** used for the dashboard investing balance | **self-healing** — the next snapshot overwrites; a lost intermediate is harmless |

So the backend still owns and computes every dashboard number: cash balances and
`netInvestment` from the cash-leg commands it records, and the investing balance
from the latest snapshot. "Other values from the backend" holds — the service
just *feeds* the projections.

**Why messaging both ways instead of a sync `GET /net-value`:** avoids
runtime HTTP coupling and a hard backend→service dependency; the backend serves
the dashboard entirely from local (message-fed) state and stays up if the service
is down. The cost — eventual consistency — is analyzed next and is acceptable.

### Backend projections (Postgres, message-fed)

- **`investment_cash_flow`** `{ id (source event id — dedupe), type FUND|CASH_OUT, amount, accountType, date }`. `BalanceService` folds these into CHECKING/SAVINGS and `netInvestment` — **same math as today**, source changes from local `InvestmentEvent`s to message rows.
- **`investment_valuation`** singleton `{ netValue, asOf }` → the INVESTING balance in net worth.
- The backend's `Investment`/`InvestmentEvent` entities, `InvestmentService`, `InvestmentController`, repos and tests are **removed** (moved to the service).

### Buy flow (eventual consistency, outbox)

```
frontend → investments svc  POST /api/investments
  svc: quote(symbol) [own integration] → shares = amount/price
  write holding (Mongo) + outbox {CashLegCommand(debit source, amount),
                                  ValueSnapshot(new total)}   [one Mongo tx]
  → 201 to frontend (Investments page reflects it immediately, live)
  relay → RabbitMQ → backend (idempotent):
      • investment_cash_flow(FUND, amount, source)  → checking drops
      • investment_valuation(netValue)              → dashboard investing value updates
```
On a **sell** the service also updates its own data first (e.g. **stops tracking
news once a holding zeroes out**), then emits the same two messages.

## Dashboard value: shallow copy (chosen) vs frontend composition

Analyzed per request.

- **X — backend shallow copy (chosen).** Dashboard = one backend call (cash + message-fed investing value). Net worth is **stale-but-coherent**: both parts move on the same message stream, so it's only ever "old" or "new," never contradictory. No inter-service HTTP; resilient to the service being down.
- **Y — frontend composition.** Dashboard = backend cash + a **live** read of the service's net value, summed in the browser. Fresher, but mixes a fresh investing read with **lagging** backend cash right after a buy → **net worth transiently inflated** (you appear to hold both the spent cash and the new stock). Also two origins per view and worse resilience.

**Pick X.** For the headline number, coherent-stale beats fresh-contradictory, and
freshness where it matters is preserved — the **Investments page** reads the
service live. This also fixes the routing question below.

## Frontend routing

Per-page partition: **Dashboard/Transactions/Budgets → backend; Investments page
→ investments service.** A **reverse proxy / gateway** (nginx or Traefik in
compose) fronts both so the browser sees one origin: `/api/investments/**` →
service, else → backend. Avoids CORS/two-base-URL sprawl. The dashboard's
investing number comes from the backend's shallow copy (not a live cross-service
read), consistent with choice X.

## Scheduling — self-contained (confirmed)

Producer **and** consumer both in the investments service; the broker sits between
them. The service's `@Scheduled` clock emits a tick; the service fans out one
work-queue message per held symbol (it knows its own symbols from Mongo — no
cross-service call); the rate-limited consumer drains them.

| Option | Scheduler location | Verdict |
|---|---|---|
| **Self-contained (chosen)** | inside the service | Highest cohesion; zero new coupling; backend stays ignorant of cadence. Needs a leader lock (ShedLock) only if the service runs >1 instance. |
| Dedicated producer container | standalone → broker → service | Extra deployable for a "tick"; justified only when scheduling becomes its own operational concern. |
| Producer in the backend | backend → broker → service | **Rejected** — re-couples the backend to the investment domain the extraction just removed. |

Job internals (rate limiter = the real throughput governor, retry/backoff → DLQ,
freshness guard, market-hours cadence, `STALE`/`UNRESOLVED`, buy-time quote
sharing the limiter) are unchanged from
[INVESTMENT_PRICING.md](INVESTMENT_PRICING.md).

## Consistency & failure

- **End-to-end eventual consistency** (accepted): after a buy, the dashboard briefly shows the prior coherent net worth; the Investments page shows the new value; the backend catches up when it processes the messages. Windows are seconds — fine for a single user.
- **Outbox** on the service guarantees at-least-once publish even across broker/restart hiccups; **idempotent** backend consumers (dedupe cash-leg commands by event id) make it effectively-once. Snapshots are idempotent by overwrite.
- **Service down:** backend serves the last snapshot (stale) and all non-investment features work; the Investments page and buys/sells are unavailable.
- **Broker down:** outbox retains messages; delivery resumes on reconnect.
- **DLQ:** poison cash-leg commands are parked for inspection; the value snapshot self-heals.
- **Net worth never craters** on an investments hiccup.

## Deployment

`docker-compose.yml` gains `mongodb`, `rabbitmq`, `investments-service`, and a
`gateway` (nginx/Traefik). Backend gains RabbitMQ consumers. Broker/DB creds via
env; add to `.env.example`.

## Migration

Investment data leaves Postgres for the service's MongoDB; the backend loses its
Investment entity/repo/controller/tests and its event fold-in (replaced by the two
message-fed projections). Pre-1.0, disposable data → **reset both stores**
(`docker compose down -v`); no cross-store migration.

## Testing

- **Contract test** the message schemas (a shared contract so backend and service don't drift — Spring Cloud Contract or a hand-rolled JSON-schema check). No sync endpoint to contract-test anymore.
- Service: Testcontainers **MongoDB** + **RabbitMQ**, WireMock for the provider; outbox → publish → consume path.
- Backend: idempotent cash-leg consumer, snapshot overwrite, and the cached/stale dashboard value when no snapshot has arrived; `BalanceService` fold-in fed by test messages.

## Trade-offs (be honest)

A second service, a second datastore (polyglot persistence), a broker, and a
gateway for a single-user app is a large jump in operational and cognitive
complexity, and it trades a monolith's transaction for cross-service eventual
consistency. The justification is the **news-feed roadmap** (a document store fits
the per-stock aggregate) and deliberate architecture exploration — named so the
cost is a choice, not a surprise. A monolith module with a Mongo-backed repository
would deliver the same features with far less machinery if that roadmap slips.

## Open questions

1. ~~Cash legs & `netInvestment`~~ — **decided:** messaging-only; backend records cash-leg commands (owns `netInvestment`) + a value-snapshot shallow copy; **outbox** + idempotent consumers.
2. ~~Scheduler placement~~ — **decided:** self-contained in the service.
3. ~~Dashboard value source~~ — **decided:** backend shallow copy (X), not frontend composition (Y).
4. **Snapshot cadence.** Emit a `ValueSnapshot` on every change *and* every price refresh (recommended), or throttle to avoid chatty snapshots on large portfolios?
5. **Gateway choice.** nginx vs Traefik in compose (or, as a fallback, frontend calls two origins with CORS)?
6. **Broker layout.** One RabbitMQ for both the intra-service price queue and the service→backend streams, or separate vhosts/queues (recommended: one broker, separate exchanges/queues)?
7. **Message-contract ownership.** Shared schema module + versioning so the two services agree on payloads.
8. **Backend consumer placement.** In the existing backend process (recommended) vs a separate backend worker.
9. **News feed shape now or later.** Model the `news` array now even though population is a later feature?
10. **Inter-service auth.** Trust the compose network, or a shared secret / network policy on the broker?
11. Everything still open in [INVESTMENT_PRICING.md](INVESTMENT_PRICING.md) (provider, cadence, fractional shares, symbol validation, realized-gain tracking).
