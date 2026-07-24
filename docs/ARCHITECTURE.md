# Architecture

## Stack

| Layer     | Tech                                                  |
|-----------|--------------------------------------------------------|
| Backend   | Spring Boot 3.3.2, Java 21, Spring Data JPA, Hibernate |
| Database  | PostgreSQL 16 (`ddl-auto=update` — no migrations tool yet, see [Follow-ups](#follow-ups)) |
| Frontend  | React 18 + TypeScript, Vite 5, React Router 6 (no other runtime deps — no state/data-fetching library, just `fetch` + hooks) |
| Orchestration | Docker Compose — 3 containers (`postgres`, `backend`, `frontend`) |
| Testing   | JUnit 5, Mockito, MockMvc (Surefire); Testcontainers Postgres (Failsafe) — see [TESTING.md](TESTING.md) |

## Containers (`docker-compose.yml`)

```
postgres  (postgres:16-alpine)
  ├─ healthcheck: pg_isready                      ← added during implementation, not in the original plan
  └─ volume: postgres_data (persists across restarts)

backend   (build: ./backend)
  ├─ depends_on: postgres (condition: service_healthy)
  ├─ env: DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD
  └─ port: 8080

frontend  (build: ./frontend, build-arg VITE_API_BASE_URL)
  ├─ depends_on: backend
  └─ port: 5173
```

**Why the healthcheck was added:** the original plan listed healthchecks as
"later" (avoid gold-plating). In practice, the backend has no DB-connection
retry/backoff configured, so without `depends_on: condition: service_healthy`
it can start before Postgres accepts connections and fail. This one healthcheck
is load-bearing, not polish.

**Why `VITE_API_BASE_URL` exists:** the frontend's Docker image runs `vite
preview` (a static build), not the Vite dev server — so there's no dev proxy
to forward relative `/api/...` calls to the backend. The base URL is baked in
at *build time* (Dockerfile `ARG`/`ENV`, consumed by `src/api/client.ts` via
`import.meta.env.VITE_API_BASE_URL`) so the browser calls the backend's
published port directly. Locally (`npm run dev`), this is unset and Vite's
`server.proxy` (`vite.config.ts`) forwards `/api` to `http://localhost:8080`
instead — see `VITE_API_PROXY_TARGET` to override.

## Backend package layout

`backend/src/main/java/com/financedash/`
```
domain/       AccountType, TransactionType, Category (enums),
              Transaction (entity), Budget (entity)
repository/   TransactionRepository, BudgetRepository (Spring Data)
dto/          TransactionRequest/Response, BalanceSummaryResponse, AccountBalances,
              BudgetRequest/Response, BudgetProgressResponse,
              TimeRange, Period (shared range→window resolver), ErrorResponse
service/      TransactionService (CRUD + cross-field validation),
              BalanceService (metric formulas),
              BudgetService (CRUD + per-period spend computation)
controller/   TransactionController, BalanceController, BudgetController
exception/    ResourceNotFoundException, InvalidTransactionException,
              GlobalExceptionHandler (@RestControllerAdvice)
config/       WebConfig (CORS for the frontend origin),
              JpaConfig (@EnableJpaAuditing)
```

**Shared period resolution:** `dto/Period.resolve(range, from, to, today)` turns
the `range`/`from`/`to` query params into a `[from, to]` window. Both
`BalanceController` and `BudgetController` use it so the Dashboard's balances
and budgets always reflect the same window.

**Why JPA auditing is its own config:** `@EnableJpaAuditing` lives in
`config/JpaConfig`, not on `FinanceDashApplication`. If it sits on the
application class, `@WebMvcTest` slices (which don't load JPA) fail trying to
wire the auditing handler. Keeping it in a separate `@Configuration` means the
full context picks it up by scanning while the web slice ignores it — see
[TESTING.md](TESTING.md).
No `Account` or `User` entity — see [Data Model](DATA_MODEL.md#scope-decisions)
for why.

## Frontend structure

`frontend/src/`
```
types/                      transaction.ts (enums + Transaction/BalanceSummary),
                            budget.ts (Budget/BudgetInput/BudgetProgress)
api/                        client.ts (fetch wrapper + API_BASE), transactions.ts,
                            balances.ts, budgets.ts
hooks/                      useTransactions, useBalances,
                            useBudgets (progress for range + CRUD + reload)
lib/format.ts                formatCurrency / formatEnumLabel / formatDate helpers
components/
  layout/                   AppShell (nav), TimeRangeSelector
  dashboard/                BalanceCard, BalanceSummaryGrid (the 4 metrics),
                            AccountBalancesCard (per-account breakdown),
                            BudgetSection (Budgets on the Dashboard — list + add/
                            edit/delete under the same time range),
                            BudgetProgressCard (spent vs value, over-budget bar),
                            BudgetFormDrawer (name/value/multi-select categories)
  transactions/              TransactionTable, TransactionRow, TransactionFilters,
                            TransactionFormDrawer (create/edit — the field set
                            adapts to transactionType), DeleteConfirmDialog
  common/                   Button, Select, TextField, CurrencyInput, EmptyState
pages/                      DashboardPage, TransactionsPage
App.tsx                     react-router: "/" → Dashboard, "/transactions" → Transactions
```

Styling: `styles/tokens.css` (CSS custom properties, with a
`prefers-color-scheme: dark` override) + `styles/global.css` (component
classes). No CSS framework/component library — plain CSS chosen to keep the
Apple-inspired look (system font stack, generous whitespace, soft
shadows/rounded corners) fully under direct control.

## Follow-ups

Deliberately deferred, not forgotten (unchanged from the original plan):
Spring Security/auth, user-manageable accounts/categories, recurring
transactions, multi-currency, Flyway migrations, pagination.
