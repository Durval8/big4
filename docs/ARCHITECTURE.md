# Architecture (module internals)

The system-level view — services, data stores, routing, messaging, and runtime flows — lives in
[SYSTEM_DESIGN.md](SYSTEM_DESIGN.md). This doc covers the **internal structure of each module**:
package/file layouts and the few non-obvious wiring decisions.

## Stack

| Layer | Tech |
|---|---|
| Backend | Spring Boot 3.3.2, Java 21, Spring Data JPA + Hibernate, Spring AMQP |
| Investments service | Spring Boot 3.3.2, Java 21, Spring Data MongoDB, Spring AMQP, Spring `RestClient` |
| Databases | PostgreSQL 16 (backend, Flyway-managed — `ddl-auto: validate`, versioned migrations under `backend/src/main/resources/db/migration/`), MongoDB 7 (investments service) — Mongo is still schema-on-write, no migration tool yet |
| Messaging | RabbitMQ 3.13 |
| Gateway | nginx (single-origin reverse proxy) |
| Frontend | React 18 + TypeScript, Vite 5, React Router 6 (no state/data lib — `fetch` + hooks) |
| Orchestration | Docker Compose — 7 services (`postgres`, `mongodb`, `rabbitmq`, `backend`, `investments-service`, `frontend`, `gateway`) |
| Testing | JUnit 5, Mockito, MockMvc, `MockRestServiceServer` (Surefire); Testcontainers Postgres / Mongo / RabbitMQ (Failsafe) — see [TESTING.md](TESTING.md) |

## Backend package layout

`backend/src/main/java/com/financedash/`
```
domain/       AccountType, TransactionType, Category (enums), Transaction, Budget (entities);
              CashLegType (enum), InvestmentCashFlow, InvestmentValuation
                — message-fed projections of investing data (NOT the source of truth)
repository/   TransactionRepository, BudgetRepository,
              InvestmentCashFlowRepository, InvestmentValuationRepository
dto/          TransactionRequest/Response, BalanceSummaryResponse, AccountBalances,
              BudgetRequest/Response, BudgetProgressResponse, TimeRange, Period, ErrorResponse
service/      TransactionService (CRUD + cross-field validation; rejects INVESTING),
              BalanceService (dashboard metrics; folds the cash-flow projection into cash
                balances, reads the valuation projection for the INVESTING balance),
              BudgetService (CRUD + per-period spend)
controller/   TransactionController, BalanceController, BudgetController
messaging/    InvestmentsMessaging (mirror of the broker names), CashLegCommand, ValueSnapshot
                (consumer-side mirror records), InvestmentCashLegConsumer (idempotent),
              InvestmentValuationConsumer (last-write-wins)
config/       WebConfig (CORS), JpaConfig (@EnableJpaAuditing), RabbitConfig (exchange/queues/bindings
                + Jackson message converter)
exception/    ResourceNotFoundException, InvalidTransactionException, GlobalExceptionHandler
```

The backend **no longer has an `Investment` entity/service/controller** — investing moved to the
service. What remains is the two projections it consumes over RabbitMQ, which `BalanceService` folds
into the dashboard exactly as before (same math, message-sourced).

**Shared period resolution:** `dto/Period.resolve(range, from, to, today)` turns the query params
into a `[from, to]` window; `BalanceController` and `BudgetController` both use it so balances and
budgets share one window.

**Why JPA auditing is its own config:** `@EnableJpaAuditing` lives in `config/JpaConfig`, not on the
application class — otherwise `@WebMvcTest` slices (no JPA) fail wiring the auditing handler. See
[TESTING.md](TESTING.md).

## Investments service package layout

`investments-service/src/main/java/com/financedash/investments/`
```
domain/       Holding (@Document) + HoldingEvent (embedded), HoldingStatus, PriceStatus,
              InvestmentEventType, CashAccount; OutboxMessage; NewsFeed + NewsItem; Precision
repository/   HoldingRepository, OutboxRepository, NewsFeedRepository (Spring Data Mongo)
dto/          BuyRequest, HoldingUpdateRequest, CashOutRequest, ManualPriceRequest,
              HoldingResponse, SummaryResponse, NewsResponse, ErrorResponse
provider/     StockPriceProvider + FinnhubProvider (quotes), StockNewsProvider + FinnhubNewsProvider
                (company-news), NewsArticle, Quote; ProviderException hierarchy
ratelimit/    RateLimiter (token bucket, injectable clock — the real Finnhub throttle)
service/      HoldingService (buy/cash-out/correction/manual price; writes outbox; triggers news),
              OutboxWriter + OutboxRelay (transactional outbox → RabbitMQ),
              PriceRefreshScheduler/Consumer + StalePriceHandler (the price job),
              NewsService + NewsSelector (value-weighted draw) + NewsRefreshScheduler/Publisher/Consumer
controller/   InvestmentController (/api/investments), NewsController (/api/investments/news)
messaging/    InvestmentsMessaging (canonical broker names) + contract/{CashLegCommand, ValueSnapshot}
config/       MongoConfig (@EnableMongoAuditing + Clock), RabbitConfig (all exchanges/queues),
              PricingConfig (RestClient + RateLimiter + provider beans)
exception/    InvalidInvestmentException, ProviderUnavailableException, ResourceNotFoundException,
              GlobalExceptionHandler
```

This service **owns the message contract**; the backend's `messaging/` records mirror it.

## Frontend structure

`frontend/src/`
```
types/        transaction.ts, budget.ts, investment.ts (share-based + PriceStatus), news.ts
api/          client.ts (fetch wrapper; relative base URL), transactions.ts, balances.ts,
              budgets.ts, investments.ts, news.ts
hooks/        useTransactions, useBalances, useBudgets, useInvestments, useInvestmentNews
                (feed + updatedAt polling), useTheme (light/dark, persisted)
lib/format.ts formatCurrency / formatPercent / formatShares / formatPrice / formatRelativeTime / …
components/
  layout/     AppShell (nav + theme toggle), TimeRangeSelector
  dashboard/  BalanceCard, BalanceSummaryGrid (the 4 metrics),
              AccountBalancesCard (prominent grouped accounts panel), BudgetSection,
              BudgetProgressCard, BudgetFormDrawer
  transactions/ TransactionTable/Row/Filters, TransactionFormDrawer, DeleteConfirmDialog
  investments/  InvestmentTable/Row, InvestmentFormDrawer (buy / correction),
              CashOutDialog, ManualPriceDialog, NewsCard
  common/     Button, Select, TextField, CurrencyInput, EmptyState
pages/        DashboardPage, TransactionsPage, InvestmentsPage
```

**Theming:** `styles/tokens.css` defines CSS custom properties for light and dark. Dark applies via
`:root[data-theme="dark"]`; with no manual choice it still follows `prefers-color-scheme`. An inline
script in `index.html` applies the saved/OS theme before first paint (no flash); `useTheme` toggles
and persists it. Plain CSS (`styles/global.css`), no component library.

**Why `VITE_API_BASE_URL` is empty in Docker:** the frontend is served behind the gateway, so it uses
**relative** `/api/...` URLs against the gateway origin (one host). Locally (`npm run dev`),
Vite's `server.proxy` splits `/api/investments` → `:8081` and `/api` → `:8080` (override via
`VITE_INVESTMENTS_PROXY_TARGET` / `VITE_API_PROXY_TARGET`).

**One origin still needs a CORS allowlist.** Browsers attach an `Origin` header to every non-GET
request even same-origin, and Spring CORS-checks anything carrying that header, so `WebConfig`'s
allowlist gates all writes. It defaults to `http://localhost:*`; a deployment on a real domain must
add its public origin via `CORS_ALLOWED_ORIGIN_PATTERNS` or every POST/PUT/DELETE returns
`403 Invalid CORS request` while reads keep working. The investments-service has no CORS config at
all, so its writes are unaffected — which is why a misconfiguration presents as "budgets and
transactions are broken but investments are fine".

## Follow-ups

Deferred, not forgotten: Spring Security/auth, user-manageable accounts/categories, recurring
transactions, multi-currency, valuation history, a Mongo migration tool, pagination, and a
dynamic-resolver gateway config (see [SYSTEM_DESIGN.md](SYSTEM_DESIGN.md#deployment)).
