# Testing

Both Spring modules — the **backend** and the **investments service** — carry a layered suite, split
by cost with the Surefire (`*Test`) / Failsafe (`*IT`) plugins: fast Docker-free tests on
`mvn test`, Testcontainers integration on `mvn verify`.

| Module | `mvn test` (no Docker) | `mvn verify` (needs Docker) |
|---|---|---|
| `backend` | unit + web-slice | + Testcontainers **Postgres + RabbitMQ** |
| `investments-service` | unit + web-slice | + Testcontainers **MongoDB + RabbitMQ** |

Roughly **105** backend and **62** service tests, all green. (Off-VPN, build with the
Maven-Central settings override — see the repo memory note.)

## Backend

**Unit (Mockito, no Spring):**
- `TransactionServiceTest` — cross-field validation (category per type, TRANSFER destination
  required/distinct, `INVESTING` rejected as account/linked), CRUD delegation, filter routing.
- `BalanceServiceTest` — dashboard formulas with repos mocked, now over the **message-fed
  projections**: cash-flow fold-in (FUND debits source, CASH_OUT credits savings), the valuation
  snapshot as the INVESTING balance, stock-vs-flow net worth, a full worked scenario.
- `BudgetServiceTest` — validation + spent computation (expenses only, category match, over-budget).
- `InvestmentCashLegConsumerTest` / `InvestmentValuationConsumerTest` — **idempotent** cash-leg apply
  (dedupe by `eventId`) and **last-write-wins** valuation (reject strictly-older, accept ties).
- `InvestmentMessageContractTest` — the JSON fixtures the service emits bind to the backend's mirror
  records (guards against contract drift).
- `TimeRangeTest` / `PeriodTest` — window resolution.

**Web slice (`@WebMvcTest`, mocked services):** `TransactionControllerTest`, `BalanceControllerTest`,
`BudgetControllerTest` — status codes, JSON contract, validation/400 shapes (incl. malformed / invalid-enum bodies).

**Integration (`@SpringBootTest` / `@DataJpaTest` + Testcontainers):**
- `TransactionRepositoryIT` — derived queries against real Postgres (range inclusivity, ordering, auditing).
- `FinanceDashApplicationIT` — full-stack CRUD, validation rejections, worked-example balances, INVESTING-transfer rejection.
- `BudgetIT` — CRUD + period-scoped progress.
- `InvestmentMessagingIT` — a **raw JSON message on the exchange is converted and persisted** into
  `investment_cash_flow` / `investment_valuation` **and the user-visible `transactions` ledger row**
  (the end-to-end consumer path + Jackson conversion). Also covers the symbol-less message shape an
  older service build emits. Not `@Transactional` (the listener commits on its own thread), so it
  cleans the shared container on the way out as well as in — otherwise its ledger rows leak into
  `TransactionRepositoryIT`'s counts.
- `InvestmentBackfillIT` — acceptance test for `V4__backfill_investment_transactions.sql`. Executes
  **the migration file itself** (not a transcription) against a seeded legacy state and asserts
  balances are unchanged across it, cash-outs stay out of `spending`, the generated rows carry the
  right shape, and re-running doesn't duplicate.

## Investments service

**Unit (Mockito / plain, no Spring):**
- `NewsSelectorTest` — the value-weighted stock draw under a **seeded RNG**: determinism, dedup,
  cap-at-7, exhaustion/renormalization, graceful degradation, and a statistical "bigger holding
  drawn first more often" check.
- `HoldingResponseTest` — position-change % (avg-cost vs latest price) with divide-by-zero guards.
- `RateLimiterTest` — token-bucket refill/cap under an injectable fake clock.
- `FinnhubProviderTest` / `FinnhubNewsProviderTest` — adapter contract via **`MockRestServiceServer`**
  (no sockets): quote/news parsing, unknown-symbol → not-found/empty, 429/5xx → transient, a
  rate-limit-exhausted short-circuit, and a **UTF-8 regression test** (curly quotes/ellipses preserved).
- `NewsRefreshConsumerTest` — the trigger delegates to a full rebuild.

**Web slice (`@WebMvcTest`):** `InvestmentControllerTest` (buy 201, validation 400, provider-down 503,
404) and `NewsControllerTest` (feed JSON, empty feed).

**Integration (`@SpringBootTest` + Testcontainers Mongo + RabbitMQ):**
- `SmokeIT` — context loads with both containers wired.
- `HoldingServiceIT` — buy/merge/cash-out accounting incl. the worked +50% example, UNRESOLVED +
  manual price, provider-failure blocks the buy, and the **news-refresh triggers** (fire on new
  symbol / full cash-out, not on merge / partial).
- `OutboxRelayIT` — outbox → relay → RabbitMQ delivers both streams to the backend queues, marks published.
- `PriceRefreshIT` — producer selection (freshness/UNRESOLVED skip), consumer applies a fresh quote,
  unknown → UNRESOLVED, and **persistent failure → DLQ → STALE** (last price kept).
- `NewsServiceIT` — feed rebuild (balanced items, dedup), empty holdings → empty feed, stale-window
  drop, summary trimming, and **all-fetches-fail keeps the previous feed** (never blank).

## Notes

- **Testcontainers** starts one container per store per test JVM, shared/reused for speed
  (`AbstractPostgresContainerTest`, `AbstractMessagingContainerTest`, `AbstractContainersTest`).
  Requires a running Docker daemon.
- **Shared-broker isolation:** Spring caches a context per test class, so a `@RabbitListener` from one
  IT's context can compete for messages on the shared broker. ITs that don't need consumers set
  `spring.rabbitmq.listener.simple.auto-startup=false`; only the IT asserting consumption keeps
  listeners on. (Learned the hard way — a flaky cross-context consumer.)
- **No embedded HTTP mock:** this environment can't open the loopback selector pipe an embedded
  WireMock/Jetty (or a default JDK `HttpClient`) needs, so provider adapters use `MockRestServiceServer`
  and the production `RestClient` uses a blocking `SimpleClientHttpRequestFactory`.
- JPA auditing lives in `config/JpaConfig` so `@WebMvcTest` (no JPA) doesn't fail wiring the auditing handler.
