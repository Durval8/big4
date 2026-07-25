# Automated Investment Pricing — Feature Spec

> **Status: IMPLEMENTED** (inside the investments service — see
> [INVESTMENTS_SERVICE.md](INVESTMENTS_SERVICE.md)). Built 2026-07-24. Replaces manual
> mark-to-market edits with prices fetched from **Finnhub** (free tier, 60 calls/min) by
> a periodic, rate-limited job every 15 minutes. Resolved open questions: Finnhub as the
> provider (rate limiter sized to 50/min for headroom); buy blocked on transient failure;
> unknown symbols flagged UNRESOLVED and priced manually (never fetched); STALE keeps the
> last-known price with a warning; realized gain tracked (UI viz deferred); dev-volume
> reset for migration.

## Summary

Today a holding's value is a single `currentValue` a user edits by hand
([INVESTMENTS.md](INVESTMENTS.md)). This feature makes value **API-driven**:

- Holdings become **share-based** (`quantity`), because a price API returns
  **price-per-share**, not position value. Value = `quantity × latestPrice`.
- A **periodic job** fetches the latest price for each held symbol, respecting
  the provider's rate limits, and updates cached prices. Position change is then
  computed from **average cost vs. latest price** — real gain/loss, no edits.
- The job runs off a **message broker** (RabbitMQ) work queue, with a token-bucket
  rate limiter, retry/backoff, and a dead-letter queue.

> **Proportionality note.** For a single-user app the broker is heavier than
> strictly required; its value here is **durability + retry + DLQ + decoupling**,
> not horizontal scale. Crucially, the broker does **not** relax the provider's
> rate limit — the **rate limiter on the consumer is the real throughput
> governor**. This choice was made deliberately (see decision 4); a simpler
> in-process `@Scheduled` + rate-limited executor would also satisfy the
> functional need.

## Locked decisions (from spec Q&A)

1. **Shares × live price.** Add `quantity`; `currentValue = quantity × latestPrice`. Enables average-cost, real gain/loss, and a proper realized/unrealized split — fixing the partial-cash-out %-skew flagged in [INVESTMENTS.md](INVESTMENTS.md) (open question #10).
2. **Prices are the source of truth.** The manual value edit is removed; editing is limited to symbol/quantity corrections.
3. **Buy by money amount.** Enter € + source account; `shares = amount ÷ priceAtBuy` (fetched synchronously at buy time, which also captures cost basis).
4. **External broker (RabbitMQ) work queue** for the periodic fetch.

## Model changes

### `Investment` (evolves)

| Field           | Change | Notes                                                        |
|-----------------|--------|--------------------------------------------------------------|
| `quantity`      | **new** `BigDecimal` | shares held (fractional allowed — buy-by-amount yields fractions) |
| `currentValue`  | **derived** | `quantity × latestPrice`; no longer user-editable/stored as truth |
| `latestPrice`   | **new** `BigDecimal` | last fetched price-per-share (cached)              |
| `priceAsOf`     | **new** `Instant`    | when `latestPrice` was fetched (staleness)         |
| `priceStatus`   | **new** enum | `OK` / `STALE` (provider failing, last-known kept) / `UNRESOLVED` (symbol not found) |
| `stockSymbol`, `status`, timestamps | unchanged | symbol validated/resolved against the provider on add |

`InvestmentEvent` (`FUND` / `CASH_OUT`) is unchanged and still drives the cash
fold-in. It additionally records the **shares** and **price** of each buy/sell so
cost basis is reconstructable.

### Average-cost accounting (fixes the %-skew)

**Two distinct per-holding accumulators — keep them separate; do not collapse
into one field:**

- **`netCashInvested` (cash flow)** = Σ FUND.amount − Σ CASH_OUT.amount (a CASH_OUT amount is **proceeds**). This is what the shipped `BalanceService` folds into cash balances, what `netInvestment` sums, and what the "Money Invested" tile shows. **Meaning unchanged from what shipped.**
- **`costBasis` (new, drives avgCost)** = Σ buy cash − Σ (shares_sold × avgCost). This is the accounting cost of the shares still held.

```
avgCost           = costBasis / quantity
currentValue      = quantity × latestPrice
positionChangePct = (latestPrice − avgCost) / avgCost × 100      // unrealized return; see guard below
```

- **Buy** (amount A at fetched price P): `shares = A/P`; `quantity += shares`; FUND event (A debits source); `netCashInvested += A`; `costBasis += A`. `avgCost` recomputed.
- **Cash-out** (amount A at latest price P): `shares = A/P`; `proceeds = A` → SAVINGS (CASH_OUT event, **amount = proceeds**); `quantity −= shares`; `netCashInvested −= A`; `costBasis −= shares × avgCost`; **realized gain = A − shares × avgCost** (tracked for history — see open questions). `avgCost` unchanged; fully sold (`quantity → 0`) → `CASHED_OUT`.

**`positionChangePct` guard:** return `null` / "—" when `quantity = 0` (cashed out)
or there is no price yet (`UNRESOLVED` or pre-first-fetch) — never divide by zero
or NaN the `currentValue`/tiles.

Position change's baseline thus **moves from `netCashInvested` (shipped) to
`avgCost`**, which is why partial cash-outs no longer distort the percentage.

**Worked example** — buy 10 shares @ €10, price rises to €15, cash out €75:
`quantity 10→5`, `netCashInvested 100→25` (cash flow), `costBasis 100→50`,
`avgCost = 50/5 = €10`. `positionChangePct = (15 − 10)/10 = +50%` ✓ (using the
cost-basis number, **not** the cash-flow 25 which would give a bogus +200%).
`currentValue = 5 × 15 = €75`; realized gain on the sale = 75 − 5×10 = €25.

## Price provider integration

- **Port/adapter.** A `StockPriceProvider` interface (`quote(symbol) → {price, asOf}`, `resolve(symbol) → boolean`) with one adapter for the chosen provider. Keeps the provider swappable and mockable.
- **Candidates (free tiers — verify current limits when picking):** Finnhub (higher req/min, good for intraday), Alpha Vantage (low daily quota → EOD-only), Twelve Data. The provider's rate limit **drives the refresh cadence and the rate limiter** (see below).
- **Secrets/config.** API key via env/secret (never in code); base URL and cadence configurable. Add to `.env.example` / compose env.
- **Symbol resolution.** On add, resolve the symbol against the provider; reject or flag `UNRESOLVED` if unknown (open question).
- **Currency.** Providers quote in the instrument's currency (usually USD); the app is single-currency — assume one currency for v1 (note as a limitation).

## Periodic job & task queue (the core of this feature)

### Message flow

```
@Scheduled(cron, market-hours-aware)
        │  (producer: enqueue one message per distinct OPEN symbol)
        ▼
 RabbitMQ queue  price.refresh  ──▶  @RabbitListener consumer(s)
        │                                   │  prefetch + shared token-bucket rate limiter
        │                                   ▼
        │                         StockPriceProvider.quote(symbol)
        │                                   │
        │                     update holdings with that symbol:
        │                     latestPrice, priceAsOf, priceStatus=OK
        │
   on failure ── transient (429/5xx/timeout) ▶ retry w/ exponential backoff
                └ exhausted ▶ dead-letter queue  price.refresh.dlq ; priceStatus=STALE (keep last price)
                └ 4xx unknown symbol ▶ ack, priceStatus=UNRESOLVED (no retry)
```

### Design points

- **Rate limiting is mandatory even with the broker.** A shared token bucket (Bucket4j / Resilience4j) on the consumer paces `quote()` calls under the provider's req/min; broker `prefetch`/`concurrency` alone won't enforce the API limit. The broker gives durability, retry, DLQ, and back-pressure — not rate relief.
- **Producer = enqueue only.** The scheduler publishes distinct OPEN symbols and returns immediately; it does no fetching. Overlapping runs are harmless because the consumer is the throttle, but add a **freshness guard** (skip enqueue if `priceAsOf` is within the interval) to avoid wasting a scarce daily quota.
- **Retry/DLQ.** Spring AMQP retry (`RetryInterceptor`, exponential backoff) → DLQ after N attempts. A DLQ item means "provider persistently failing for this symbol"; keep the last-known price and mark `STALE`. Alert/inspect via the DLQ.
- **Idempotent updates.** Updating `latestPrice`/`priceAsOf` is naturally idempotent; a duplicate message just re-fetches (wasting quota — hence the freshness guard).
- **Market-hours / cadence.** Prices move only during market hours and free tiers are often delayed or EOD. Cadence is provider-dependent and configurable (e.g. every 15 min during market hours for an intraday tier, or once daily EOD for a low-quota tier).
- **Circuit breaker.** On sustained provider outage, open a breaker to stop hammering (and burning quota); hold last-known prices, mark `STALE`.

### Infra

- Add a `rabbitmq` service to `docker-compose.yml` (management UI optional); backend depends on it. `spring-boot-starter-amqp`.
- Broker credentials via env; queues/bindings declared in config (durable queue + DLQ + DLX).

### Buy-time synchronous fetch

Because buys are entered as a money amount (decision 3), the **add path does a single synchronous `quote(symbol)`** to convert amount → shares and capture the buy price — this is separate from the periodic job. It must go **through the same token bucket** as the job: it hits the same provider under the same limit, so a buy racing a refresh run can 429 or eat a scarce daily quota. Handle failure explicitly (retry once, or fall back to letting the user enter shares — open question). This tightens the provider-choice constraint (a low daily-quota tier makes buy-time fetches expensive).

## Metrics & net worth impact

- INVESTING balance (the reflection) = `Σ quantity × latestPrice` of OPEN holdings — still "current," now automatic. `netInvestment` (period net cash in) is unchanged (still from FUND/CASH_OUT).
- The "investing component is always current regardless of period" simplification stays; true historical net worth would need a **price-history table** (future).
- Position change is now real unrealized return (avg-cost vs latest price), computed server-side — no manual input.

## Migration

Existing holdings have `currentValue` but no `quantity`/`latestPrice`, and shares
can't be derived from a value without a price. Options: **dev-volume reset**
(recommended, consistent with the investments rollout — pre-1.0, disposable data),
or a one-off backfill prompting re-entry. Do not fabricate a quantity.

## Failure modes & guardrails

- Provider down / rate-limited → keep last-known price, mark `STALE`, never zero out (net worth must not crater on an API hiccup).
- Unknown/delisted symbol → `UNRESOLVED`, excluded from valuation (counts as last-known or zero — decide), surfaced in the UI.
- Buy-time fetch failure → don't silently create a shareless holding; block or fall back to manual shares.
- Never block a user request on the periodic job; all fetching is async off the queue (except the deliberate buy-time quote).

## Testing

- Mock `StockPriceProvider` for unit tests of valuation, average-cost, buy/sell mechanics, and position change.
- **WireMock** to stub the provider's HTTP for adapter tests (don't hit the real API in CI).
- Consumer/rate-limiter tests with a fake provider + fake clock.
- Integration: **Testcontainers RabbitMQ** module + WireMock to exercise enqueue → consume → holding update → DLQ-on-failure, alongside the existing Postgres container.

## Out of scope (later)

Live intraday streaming/websockets, price-history table + true historical net worth, realized-gain reporting UI, dividends/splits/corporate actions, multi-currency, watchlists/quotes for non-held symbols.

## Open questions

1. **Provider + cadence.** Which free provider, and therefore intraday (e.g. every 15 min in market hours) vs EOD-only (low daily quota)? Drives the rate limiter and schedule — and note both the periodic refresh **and** buy-time quotes draw from the same quota, so a low daily limit constrains how often users can buy.
2. **Broker specifics.** RabbitMQ + Spring AMQP (recommended — native listener/retry/DLQ) vs Redis Streams (lighter, if Redis is wanted for caching too).
3. **Buy-time fetch failure.** Block the buy, or fall back to manual share entry?
4. **Cash-out input.** By money amount (symmetric with buy — recommended) or by share count?
5. **Realized-gain tracking.** Record realized gain on sells now (for history), or defer the reporting and just keep balances correct?
6. **Symbol validation on add.** Reject unknown symbols, or accept and flag `UNRESOLVED`?
7. **`UNRESOLVED`/`STALE` in net worth.** Value at last-known price, or exclude, when a symbol can't be priced?
8. **Migration.** Dev-volume reset (recommended) vs backfill/re-entry.
9. **Manual override.** Truly none (decision 2), or a break-glass manual price for un-resolvable symbols?
10. **Fractional shares** confirmed (buy-by-amount implies them) — any rounding/precision policy for share quantity?
