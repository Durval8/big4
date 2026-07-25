# Personal Finance Dashboard (MVP)

Single-user personal finance tracker: log everyday transactions, set spending
budgets, track stock investments, and see net worth, spending, net spending,
net investment, and budget progress over selectable time windows.

Stack: Spring Boot 3 (Java 21) + PostgreSQL **backend** (transactions, budgets,
dashboard); a Spring Boot + **MongoDB** **investments service** (holdings, automated
pricing); **RabbitMQ** for inter-service messaging; React 18 + TypeScript (Vite)
frontend; an **nginx gateway** fronting them as one origin; all via Docker Compose.

**Status:** MVP + budgets + investments built. Investments have been **extracted into
their own service** with its own MongoDB and an automated Finnhub price job, talking to
the backend only over RabbitMQ (see
[docs/INVESTMENTS_SERVICE.md](docs/INVESTMENTS_SERVICE.md)). Each module is covered by
unit, web-slice, and Testcontainers integration tests.

## Documentation

- [docs/DATA_MODEL.md](docs/DATA_MODEL.md) — entities, enums, invariants, and the exact net worth / spending / net spending / net investment formulas
- [docs/API.md](docs/API.md) — REST endpoint reference with a worked example
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — container layout, backend/frontend structure, and why a couple of things deviate from the original plan
- [docs/TESTING.md](docs/TESTING.md) — the backend test suite (unit, web-slice, and Testcontainers integration) and how to run it
- [docs/INVESTMENTS.md](docs/INVESTMENTS.md) — the original Investments feature: holdings, buy/cash-out, position change, and how it folds into balances
- [docs/INVESTMENTS_SERVICE.md](docs/INVESTMENTS_SERVICE.md) — **the microservice extraction (built):** Mongo-backed service, RabbitMQ messaging (cash-leg commands + value snapshots), outbox, backend projections, gateway
- [docs/INVESTMENT_PRICING.md](docs/INVESTMENT_PRICING.md) — **automated pricing (built):** share-based holdings, average-cost position change, and the rate-limited Finnhub refresh job
- [docs/future/STATEMENT_IMPORT.md](docs/future/STATEMENT_IMPORT.md) — **shelved proposal (not built):** import bank-statement PDFs and extract transactions via Claude

In short:
- Every transaction has an `accountType` (CHECKING/SAVINGS — **not** INVESTING),
  a `transactionType` (INCOME/EXPENSE/TRANSFER/ADJUSTMENT), and, for
  INCOME/EXPENSE only, a `category`.
- TRANSFER moves money from `accountType` to `linkedAccountType`.
- ADJUSTMENT seeds an opening balance / corrects an account — it affects net
  worth only, not the flow metrics.
- **Investments** live in a separate service (share-based holdings priced from
  Finnhub). A buy debits a cash account and a cash-out credits savings — the service
  emits those as RabbitMQ cash-leg commands the backend folds into balances, plus a
  value snapshot the backend shows as the INVESTING balance. `netInvestment` is net
  cash moved into investments over the period. See
  [docs/INVESTMENTS_SERVICE.md](docs/INVESTMENTS_SERVICE.md).
- A **budget** has a name, a value (target), and a set of categories. Its spent
  total = expenses in those categories over the Dashboard's selected window;
  progress bars show on the Dashboard.

## Run with Docker Compose

```bash
cp .env.example .env
# Optional: set FINNHUB_API_KEY in .env for live prices (free key at https://finnhub.io).
docker compose up --build
```

- **App (via gateway): http://localhost:8090** — open this one; it fronts the frontend and both APIs on a single origin.
- RabbitMQ management UI: http://localhost:15672 (guest/guest)
- Without a `FINNHUB_API_KEY`, buys are blocked and the price job marks holdings STALE — add a key, or add a holding with a manual price for an "unresolved" symbol.

## Run locally without Docker

Needs local Postgres, MongoDB, and RabbitMQ running (or start just those three with
`docker compose up postgres mongodb rabbitmq`).

```bash
cd backend            && mvn spring-boot:run    # :8080  (Postgres + RabbitMQ)
cd investments-service && mvn spring-boot:run    # :8081  (MongoDB + RabbitMQ, FINNHUB_API_KEY)
cd frontend           && npm install && npm run dev   # :5173
```
Vite's dev server proxies `/api/investments` to `http://localhost:8081` and everything
else `/api` to `http://localhost:8080` (override via `VITE_INVESTMENTS_PROXY_TARGET` /
`VITE_API_PROXY_TARGET`).

## Tests

Each module has fast unit + web-slice tests (no Docker) and Testcontainers integration
tests (needs a running Docker daemon):
```bash
cd backend             && mvn test     # fast;  mvn verify  → + Postgres/RabbitMQ ITs
cd investments-service && mvn test     # fast;  mvn verify  → + Mongo/RabbitMQ ITs
```
See [docs/TESTING.md](docs/TESTING.md) for the full breakdown.

## Out of scope for this MVP
Authentication, user-manageable accounts/categories, recurring transactions,
multi-currency, pagination, database migrations (Flyway).
