# Testing

The backend has a layered test suite (117 tests). Tests are split by cost so the
fast ones run on every `mvn test` and the Docker-dependent ones run on
`mvn verify`.

| Command      | Runs                                     | Needs Docker? |
|--------------|-------------------------------------------|---------------|
| `mvn test`   | Unit + web-slice tests (`*Test`) — 97     | No            |
| `mvn verify` | Everything, incl. integration (`*IT`) — 117 | Yes         |

The split is done with the Surefire (`*Test`) / Failsafe (`*IT`) plugins in
`backend/pom.xml`.

## Layers

### Unit tests (Mockito, no Spring context) — fast
- **`TransactionServiceTest`** — cross-field validation rules and expected failures (category per type, TRANSFER destination required/distinct, non-transfer linked-account forbidden, **INVESTING rejected** as account/linked), CRUD delegation, `findAll` filter routing, not-found paths.
- **`BalanceServiceTest`** — metric formulas in isolation (repos mocked): empty ledger, adjustment-excluded-from-flows, income/expense, transfer-to-savings, the **investment-event fold-in** (fund debits source, cash-out credits savings, mark-to-market raises net worth), stock-vs-flow net worth, and a full worked scenario.
- **`BudgetServiceTest`** — validation, and the spent-computation decisions (expenses only, category matching, over-budget negative remaining).
- **`InvestmentServiceTest`** — source-account validation, new vs merged holding, mark-to-market **position change (+50% / −20%)**, partial vs full cash-out (→ `CASHED_OUT`, pct null), cash-out-exceeds rejection, summary aggregation.
- **`TimeRangeTest` / `PeriodTest`** — window resolution and the shared `range`/`from`/`to` resolver.

### Web-slice tests (`@WebMvcTest`, mocked services) — fast, no DB
- **`TransactionControllerTest`** — status codes, JSON contract, Bean-Validation and service-thrown 400s (incl. malformed/invalid-enum bodies), filter pass-through.
- **`BalanceControllerTest`** — response shape and `range` vs `from`/`to` resolution via argument captors.
- **`BudgetControllerTest`** / **`InvestmentControllerTest`** — HTTP contract + validation 400s for each resource.

### Integration tests (`@SpringBootTest` / `@DataJpaTest` + Testcontainers Postgres) — need Docker
- **`TransactionRepositoryIT`** — derived queries against real Postgres: `Between` inclusivity, `date DESC, id DESC` ordering, filters, `LessThanEqual` boundary, auditing timestamps.
- **`FinanceDashApplicationIT`** — full-stack CRUD, validation rejections, newest-first listing, worked-example balances, and INVESTING-transfer rejection.
- **`BudgetIT`** — CRUD + progress, proving period-scoping against real Postgres.
- **`InvestmentIT`** — full lifecycle: buy folds into balances, mark-to-market position change, partial + full cash-out to savings, duplicate-symbol merge, cash-out-exceeds rejection.

## Notes

- **Testcontainers** spins up one `postgres:16-alpine` container per test JVM (`AbstractPostgresContainerTest`), shared and reused for speed. Requires a running Docker daemon.
- Integration tests are `@Transactional` and roll back after each method, so they don't leak state between tests.
- JPA auditing lives in its own `config/JpaConfig` (`@EnableJpaAuditing`) rather than on the application class — otherwise `@WebMvcTest`, which doesn't load JPA, fails trying to wire the auditing handler. `@DataJpaTest` imports `JpaConfig` explicitly.
