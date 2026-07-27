# Personal Finance Dashboard (MVP)

Single-user personal finance tracker: log everyday transactions, set spending
budgets, track stock investments, and see net worth, spending, net spending,
net investment, and budget progress over selectable time windows.

Stack: Spring Boot 3 (Java 21) + PostgreSQL **backend** (transactions, budgets,
dashboard); a Spring Boot + **MongoDB** **investments service** (holdings, automated
pricing); **RabbitMQ** for inter-service messaging; React 18 + TypeScript (Vite)
frontend; an **nginx gateway** fronting them as one origin; all via Docker Compose.

**Status:** MVP + budgets + investments built. Investments run in **their own service** (MongoDB)
with an automated **Finnhub price job** and a **portfolio news feed**, talking to the backend only
over RabbitMQ, behind an nginx gateway; the UI has a **light/dark theme switch**. See
[docs/SYSTEM_DESIGN.md](docs/SYSTEM_DESIGN.md). Both modules are covered by unit, web-slice, and
Testcontainers integration tests.

## Documentation

- [docs/SYSTEM_DESIGN.md](docs/SYSTEM_DESIGN.md) — **start here:** system architecture — topology, services, data ownership, routing, messaging, and runtime flows (with diagrams)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — module internals: backend / investments-service / frontend package layouts and wiring
- [docs/DATA_MODEL.md](docs/DATA_MODEL.md) — entities, enums, invariants, and the exact net worth / spending / net spending / net investment formulas
- [docs/API.md](docs/API.md) — REST endpoint reference with a worked example
- [docs/TESTING.md](docs/TESTING.md) — both modules' test suites (unit, web-slice, Testcontainers) and how to run them
- [docs/INVESTMENTS_SERVICE.md](docs/INVESTMENTS_SERVICE.md) — the microservice extraction: Mongo-backed service, RabbitMQ messaging (cash-leg commands + value snapshots), outbox, backend projections, gateway
- [docs/INVESTMENT_PRICING.md](docs/INVESTMENT_PRICING.md) — automated pricing: share-based holdings, average-cost position change, the rate-limited Finnhub refresh job
- [docs/INVESTMENT_NEWS.md](docs/INVESTMENT_NEWS.md) — the portfolio news feed: Finnhub company-news, the value-weighted stock-draw selection, and its 4h / held-set-change refresh
- [docs/INVESTMENTS.md](docs/INVESTMENTS.md) — the original (pre-service) Investments feature record
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

## Live deployment

The app is publicly available at **https://big4finance.online**, served via a Cloudflare tunnel
pointing at the production Docker Compose stack on the host machine.

## Environments

Two isolated environments can run simultaneously — production (behind the Cloudflare tunnel) and a
local test instance with shifted ports so they never conflict. Both use the same
`docker-compose.yml`; isolation is achieved via Docker Compose project namespacing (`-p`) and a
separate `.env.test` that offsets every host-bound port.

| | Production | Test |
|---|---|---|
| Public URL | https://big4finance.online | — |
| Gateway | host port from `.env` (default 8090) | `9090` |
| Frontend | host port from `.env` (default 5173) | `6173` |
| Backend | host port from `.env` (default 8080) | `9080` |
| Investments | host port from `.env` (default 8081) | `9081` |
| Postgres | host port from `.env` (default 5432) | `6432` |
| MongoDB | host port from `.env` (default 27017) | `28017` |
| RabbitMQ | host port from `.env` (default 5672 / 15672) | `6672 / 16672` |
| Docker project | `big4` | `big4-test` |
| Volumes/networks | `big4_*` | `big4-test_*` — fully separate |

Use the `Makefile` targets:

```bash
# Production (Cloudflare tunnel — uses .env)
make build   # build images
make up      # start detached
make down    # stop
make logs    # tail logs
make clean   # stop + wipe volumes (destructive)

# Test environment (browser: http://localhost:9090)
make build-test
make up-test
make down-test
make logs-test
make clean-test  # stop + wipe test volumes (destructive)
```

Without a `FINNHUB_API_KEY`, buys are blocked and the price job marks holdings STALE — add a key
to `.env` / `.env.test`, or add a holding with a manual price for an "unresolved" symbol.

## Run with Docker Compose (manual)

```bash
cp .env.example .env
# Optional: set FINNHUB_API_KEY in .env for live prices (free key at https://finnhub.io).
docker compose up --build
```

- **App (via gateway): http://localhost:8090** — open this one; it fronts the frontend and both APIs on a single origin.
- RabbitMQ management UI: http://localhost:15672 (guest/guest)

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
tests (needs a running Docker daemon). A root aggregator `pom.xml` makes the two services a
Maven multi-module build, so one command from the repo root builds/tests both:
```bash
mvn test      # both services, fast;  mvn verify  → + all Testcontainers ITs
```
Or per module (each stays independently buildable):
```bash
cd backend             && mvn test     # fast;  mvn verify  → + Postgres/RabbitMQ ITs
cd investments-service && mvn test     # fast;  mvn verify  → + Mongo/RabbitMQ ITs
```
The aggregator only lists the modules (reactor) — it doesn't couple their dependencies. See
[docs/TESTING.md](docs/TESTING.md) for the full breakdown.

## Out of scope for this MVP
Authentication, user-manageable accounts/categories, recurring transactions,
multi-currency, pagination, database migrations (Flyway).
