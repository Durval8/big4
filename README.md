# Personal Finance Dashboard (MVP)

Single-user personal finance tracker: log everyday transactions, set spending
budgets, track stock investments, and see net worth, spending, net spending,
net investment, and budget progress over selectable time windows.

Stack: Spring Boot 3 (Java 21) + PostgreSQL backend, React 18 + TypeScript
(Vite) frontend, Docker Compose orchestration.

**Status:** MVP built and verified end-to-end (backend CRUD + validation via
API calls, dashboard metrics hand-checked against a worked example, UI
exercised in-browser through Docker Compose).

## Documentation

- [docs/DATA_MODEL.md](docs/DATA_MODEL.md) — entities, enums, invariants, and the exact net worth / spending / net spending / net investment formulas
- [docs/API.md](docs/API.md) — REST endpoint reference with a worked example
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — container layout, backend/frontend structure, and why a couple of things deviate from the original plan
- [docs/TESTING.md](docs/TESTING.md) — the backend test suite (unit, web-slice, and Testcontainers integration) and how to run it
- [docs/INVESTMENTS.md](docs/INVESTMENTS.md) — the Investments feature: holdings, buy/edit/cash-out, position change, and how it folds into balances
- [docs/future/STATEMENT_IMPORT.md](docs/future/STATEMENT_IMPORT.md) — **shelved proposal (not built):** import bank-statement PDFs and extract transactions via Claude

In short:
- Every transaction has an `accountType` (CHECKING/SAVINGS — **not** INVESTING),
  a `transactionType` (INCOME/EXPENSE/TRANSFER/ADJUSTMENT), and, for
  INCOME/EXPENSE only, a `category`.
- TRANSFER moves money from `accountType` to `linkedAccountType`.
- ADJUSTMENT seeds an opening balance / corrects an account — it affects net
  worth only, not the flow metrics.
- **Investments** are their own entity (stock holdings). A buy debits a cash
  account and a cash-out credits savings; the INVESTING balance reflects total
  holdings value, and `netInvestment` is net cash moved into investments over
  the period. See [docs/INVESTMENTS.md](docs/INVESTMENTS.md).
- A **budget** has a name, a value (target), and a set of categories. Its spent
  total = expenses in those categories over the Dashboard's selected window;
  progress bars show on the Dashboard.

## Run with Docker Compose

```bash
cp .env.example .env
docker compose up --build
```

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080/api

## Run locally without Docker

Backend:
```bash
cd backend
mvn spring-boot:run
```
Requires a local Postgres reachable per `backend/src/main/resources/application.yml`
(env vars `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`).

Frontend:
```bash
cd frontend
npm install
npm run dev
```
Vite's dev server proxies `/api` to `http://localhost:8080` (override via
`VITE_API_PROXY_TARGET`).

## Tests

Fast unit + web-slice tests (no Docker):
```bash
cd backend
mvn test
```
Everything including the Testcontainers integration tests (needs a running Docker daemon):
```bash
cd backend
mvn verify
```
See [docs/TESTING.md](docs/TESTING.md) for the full breakdown.

## Out of scope for this MVP
Authentication, user-manageable accounts/categories, recurring transactions,
multi-currency, pagination, database migrations (Flyway).
