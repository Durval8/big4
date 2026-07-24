# Testing

The backend has a layered test suite (62 tests). Tests are split by cost so the
fast ones run on every `mvn test` and the Docker-dependent ones run on
`mvn verify`.

| Command      | Runs                                    | Needs Docker? |
|--------------|------------------------------------------|---------------|
| `mvn test`   | Unit + web-slice tests (`*Test`) — 51    | No            |
| `mvn verify` | Everything, incl. integration (`*IT`) — 62 | Yes         |

The split is done with the Surefire (`*Test`) / Failsafe (`*IT`) plugins in
`backend/pom.xml`.

## Layers

### Unit tests (Mockito, no Spring context) — fast
- **`TransactionServiceTest`** — every cross-field validation rule and its expected failure (category required/forbidden per type, TRANSFER destination required and distinct, linked-account forbidden for non-transfers), CRUD delegation, `findAll` filter-query routing, and the not-found paths for `findById`/`update`/`delete`.
- **`BalanceServiceTest`** — the metric formulas in isolation (repository mocked, so the balance/period lists are handed in directly). Covers empty ledger, adjustment-excluded-from-flows, income/expense, savings vs. investing transfers, investing withdrawals, the stock-vs-flow distinction (net worth uses the up-to-date ledger while flows use the in-period ledger), and a full worked scenario.
- **`TimeRangeTest`** — `WEEK`/`MONTH`/`YEAR`/`ALL` window resolution against a fixed date.

### Web-slice tests (`@WebMvcTest`, mocked services) — fast, no DB
- **`TransactionControllerTest`** — HTTP status codes (201/200/204/404), JSON contract, Bean Validation 400s (blank description, non-positive amount, missing required enum), service-thrown cross-field 400s, and filter-param pass-through.
- **`BalanceControllerTest`** — response JSON shape and the `range` vs `from`/`to` param resolution (including `range=ALL` → epoch and the default-to-last-month behavior), asserted via argument captors.

### Integration tests (`@SpringBootTest` / `@DataJpaTest` + Testcontainers Postgres) — need Docker
- **`TransactionRepositoryIT`** — the Spring Data derived queries against real Postgres: `Between` inclusivity on both bounds, `date DESC, id DESC` ordering, the optional account/account+category filters, `LessThanEqual` boundary, and that auditing timestamps get populated.
- **`FinanceDashApplicationIT`** — full stack through MockMvc + real Postgres: the CRUD lifecycle (create → get → update → delete → 404), validation rejections end-to-end, newest-first listing, and the worked-example balance scenario asserting all four metrics and the three account balances.

## Notes

- **Testcontainers** spins up one `postgres:16-alpine` container per test JVM (`AbstractPostgresContainerTest`), shared and reused for speed. Requires a running Docker daemon.
- Integration tests are `@Transactional` and roll back after each method, so they don't leak state between tests.
- JPA auditing lives in its own `config/JpaConfig` (`@EnableJpaAuditing`) rather than on the application class — otherwise `@WebMvcTest`, which doesn't load JPA, fails trying to wire the auditing handler. `@DataJpaTest` imports `JpaConfig` explicitly.
