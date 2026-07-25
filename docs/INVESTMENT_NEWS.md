# Investment News Feed — Feature Spec

> **Status: IMPLEMENTED.** Spec'd and built 2026-07-25. A 7-entry, portfolio-relevant news list on
> the Investments page, sourced from Finnhub `/company-news`, refreshed every 4h and whenever the
> held-symbol set changes, living entirely in the [investments service](INVESTMENTS_SERVICE.md).
>
> **As-built notes:** value-weighted stock draw with a seeded RNG (`NewsSelector`); a singleton
> `news_feed` document + `GET /api/investments/news`; a `news.refresh` RabbitMQ queue driven by an
> `@Scheduled` producer and by buy/full-cash-out triggers (direct best-effort publish); best-effort
> rebuild that never blanks a good feed. The Finnhub news adapter parses the response **from bytes**
> (Jackson auto-detects UTF-8) so headlines/summaries with curly quotes, em-dashes, and ellipses are
> preserved. Verified live end-to-end (7 balanced items across AAPL/NVDA/MSFT, correct UTF-8 on the
> wire). Covered by unit (selector determinism/statistics, adapter incl. a UTF-8 regression test),
> slice, and Testcontainers (Mongo + RabbitMQ) tests.

## Summary

A **7-entry news list** on the Investments page. Each entry is a **headline + a 1–3 line summary**
(plus source, the symbol it relates to, and age), linking out to the article. News is **always
about current holdings**, pulled from Finnhub `/company-news`, refreshed **every 4 hours** and
**immediately when you buy a not-yet-held symbol**. The refresh runs on the service's existing
RabbitMQ job pattern and shares the Finnhub rate limiter with price quotes.

## Locked decisions (from spec Q&A)

1. **Slot selection = value-weighted stock draw.** Rank holdings by position value and give each a
   decreasing **odds of being drawn** (bigger position → higher odds: **#1 ≈ 20%, #2 ≈ 12.5%,
   #3 ≈ 7.5%, …**, ≈ 0.625× per rank). Each stock offers its **3 newest** articles. To fill the 7
   slots, repeatedly draw a stock by its odds and take its newest not-yet-used article; when a
   stock's 3 are used up, **drop it and renormalize** the remaining stocks' odds (the rebalance).
   Favors larger positions while still giving smaller ones a real chance — not a fixed round-robin.
2. **Refresh whenever the held-symbol set changes** *and* every 4h. Buying a not-yet-held symbol
   **or fully cashing one out** triggers an immediate feed rebuild — so a new holding's news shows
   promptly and a closed holding's news drops out, rather than either waiting up to 4h. (The user
   asked for both "show new buys without waiting" and "always related to my investments"; a
   full cash-out changes what "my investments" are, so it must trigger too.)
3. **Look-back = 48 hours.** "Most relevant" = newest within the last 2 days. Quiet stocks may
   contribute nothing; the list can be shorter than 7.

## Data source

- **Finnhub `/company-news?symbol=X&from=YYYY-MM-DD&to=YYYY-MM-DD&token=`** — North-American
  companies only; **draws from the same 60/min free-tier quota** as price quotes.
- Per article we keep: `headline` → title, `summary` → trimmed to ~200 chars / 2–3 lines,
  `url`, `source`, `datetime` (unix) → `publishedAt`, `id`/`url` for dedup. `related` gives the symbol.
- **Symbols with no coverage** (non-NA, or `UNRESOLVED` in the price sense) simply contribute no
  articles — handled silently, exactly like unpriceable symbols.
- **Summary-less articles (decided):** prefer articles that have a real `summary`; an article with
  an empty summary is used **only** as a last resort to avoid an under-filled list (rendered
  headline-only). So the list favors title+summary entries but never drops below what's available.

## MongoDB data model (investments service)

The service owns **three collections** in its MongoDB. `holdings` and `outbox` exist today (from the
extraction); `news_feed` is **new** for this feature. This supersedes the single-document sketch in
[INVESTMENTS_SERVICE.md](INVESTMENTS_SERVICE.md).

> **Conventions.** `_id` is a Mongo id (string) unless a fixed value is noted. Money is `BigDecimal`
> at 2 dp, share `quantity` at 6 dp, `price`/`avgCost` at 4 dp, percentages at 2 dp — enforced
> app-side by the `Precision` helper (Spring Data maps `BigDecimal` to `Decimal128`/string per
> config; the app is the source of truth for scale). Instants are UTC.

### `holdings` — one document per stock holding

```jsonc
{
  "_id":            "<ObjectId hex>",
  "stockSymbol":    "AAPL",              // uppercased; indexed; ≤ 1 OPEN doc per symbol (buys merge)
  "quantity":       "0.300282",          // shares (6 dp)
  "costBasis":      "100.00",            // accounting cost of shares still held (2 dp)
  "avgCost":        "333.0203",          // costBasis / quantity (4 dp); null when quantity = 0
  "netCashInvested":"100.00",            // Σ FUND.amount − Σ CASH_OUT.amount (2 dp)
  "realizedGain":   "0.00",              // Σ (proceeds − shares_sold × avgCost) (2 dp)
  "latestPrice":    "333.0200",          // last known price/share (4 dp); null before first price
  "priceAsOf":      "<Instant>",         // provider quote time; null before first price
  "priceStatus":    "OK",                // OK | STALE | UNRESOLVED
  "status":         "OPEN",              // OPEN | CASHED_OUT
  "events":         [ /* HoldingEvent, embedded, append-only */ ],
  "version":        3,                   // @Version optimistic lock
  "createdAt":      "<Instant>",         // @CreatedDate
  "updatedAt":      "<Instant>"          // @LastModifiedDate
}
```

`HoldingEvent` (embedded — the investing cash-flow ledger):

```jsonc
{ "eventId": "<uuid>",        // idempotency key; also the id carried on the cash-leg message
  "type":    "FUND",          // FUND | CASH_OUT
  "amount":  "100.00",        // cash moved (2 dp)
  "shares":  "0.300282",      // shares bought/sold (6 dp)
  "price":   "333.0200",      // price/share at the trade (4 dp)
  "account": "CHECKING",      // FUND: source (CHECKING|SAVINGS); CASH_OUT: always SAVINGS
  "date":    "2026-07-25" }   // LocalDate
```

### `outbox` — reliable outbound messages (unchanged)

```jsonc
{ "_id": "<ObjectId hex>",
  "exchange":    "investments.exchange",
  "routingKey":  "investment.cashleg",     // or investment.value
  "payloadJson": "{…}",                     // serialized CashLegCommand or ValueSnapshot
  "createdAt":   "<Instant>",
  "published":   false,
  "publishedAt":  null }                     // set when the relay publishes
```

### `news_feed` — the rendered feed (new; single document)

One precomputed document, not per-holding `news[]`: the page renders a single ranked 7-item list, so
computing it once at refresh keeps the read trivial and the write atomic.

```jsonc
{
  "_id":       "SINGLETON",               // there is only ever one document
  "updatedAt": "<Instant>",               // when the feed was last rebuilt
  "items": [                              // ≤ NEWS_MAX_ITEMS (7), in selected (most-relevant) order
    { "symbol":      "AAPL",              // the holding this article represents
      "headline":    "…",                 // title
      "summary":     "…",                 // trimmed to ~200 chars / 2–3 lines; may be "" (headline-only)
      "url":         "https://…",         // article link; also the dedup key
      "source":      "Reuters",
      "publishedAt": "<Instant>" }        // from Finnhub `datetime`
  ]
}
```

Served by a new endpoint **`GET /api/investments/news`** (already routed to the service by the
gateway) → `{ updatedAt, items[] }`. News is **not** a dashboard number, so — unlike cash legs and
valuations — it produces **no cross-service messages**; it stays entirely inside the service.

### Indexes

`holdings.stockSymbol` (exists). Recommended additions: `holdings.status` (the price/news jobs and
summary query by status) and a compound `outbox.{published, createdAt}` (the relay sweep).
`news_feed` needs none — it's a single document fetched by `_id`.

## Selection algorithm (value-weighted stock draw)

The odds attach to **stocks**, not articles; each drawn stock contributes one article at a time, and
a stock leaves the draw once its 3 articles are used up (the "rebalance" trigger).

Given the OPEN holdings that have a resolvable symbol:

1. **Order the stocks** by current position value, descending → `S1, S2, …, Sn` (biggest first).
   This is the "balanced ordering of owned stocks." **Ties (equal position value) are broken
   randomly** (via the same seeded RNG), so equally-sized holdings don't get a fixed precedence.
2. **Per-stock candidate pool.** For each `Si`, take its **up to 3 newest** articles from the last
   48h, newest first: `[Si.a1, Si.a2, Si.a3]`.
3. **Base odds by stock rank** ("more odds for higher portfolios"): stock `i` gets weight
   `w_i = decay^(i−1)`, default **`decay = 0.625`** (configurable `NEWS_WEIGHT_DECAY`) — i.e. each
   stock ≈ 5/8 the odds of the one above it, reproducing the target shape **#1 ≈ 20%, #2 ≈ 12.5%,
   #3 ≈ 7.5%, …**. Absolute values don't matter; only the ratios do (odds are normalized live).
4. **Draw 7 slots.** Repeat until 7 filled or no stock has an unused article:
   - among stocks that still have an unused, non-duplicate article, probability of `Si`
     = `w_i ÷ Σ(w of those stocks)`;
   - draw a stock by that probability, take its **newest unused** article (skip any whose `url` is
     already in the feed — dedup), append it to the feed;
   - if that stock now has **no articles left** (all 3 drawn, or its pool was smaller), **drop it and
     renormalize** the remaining stocks' odds. ← this is the rebalance step.
5. Store the drawn items in draw order (most-relevant first), stamped `updatedAt = now`.

**Worked example** — holdings AAPL (€600), NVDA (€400), MSFT (€100). Stock odds (×0.625 per rank,
normalized over the three): **AAPL ≈ 50%, NVDA ≈ 31%, MSFT ≈ 19%** (the "20 / 12.5 / 7.5" shape is the
raw curve before normalizing over how many stocks you actually hold). Each draw picks a stock by
those odds and takes its next-newest article; whenever a stock's 3 are exhausted it's dropped and the
others' odds scale up — so a 3-stock portfolio fills all 7 slots (3 + 3 + 1) with AAPL most likely to
supply the earliest/most slots.

**Non-determinism note:** because the stock draw is random, the exact 7 (and their order) can differ
between refreshes even when the underlying news is unchanged — intended variety. Tests inject a
**seeded RNG** (like the injected `Clock`/`nanoClock` elsewhere) so selection is deterministic under
test.

Degrades cleanly: one holding → up to its 3 newest; a stock with no recent news → never drawn; fewer
than 7 candidate articles in total → a shorter list.

## Refresh job (on the existing RabbitMQ pattern)

Mirrors the price-refresh job — a **new intra-service work queue** `news.refresh` alongside
`price.refresh`.

```
@Scheduled(NEWS_REFRESH_CRON = every 4h)      ─┐
buy() adds a new OPEN symbol                  ─┤─▶ publish "news.refresh" ─▶ news.refresh queue
cashOut() fully closes a holding (→CASHED_OUT)─┘                              │
                                                        NewsRefreshConsumer (rate-limited)
                                                                              │
                                   for each OPEN resolvable symbol: companyNews(symbol, now-48h, now)
                                                                              │
                                        round-robin rank → top 7 → overwrite news_feed (updatedAt=now)
```

- **Single "rebuild the feed" message**, not one-per-symbol: the artifact is a portfolio-wide ranked
  list, so the consumer loops the held symbols itself (it knows them from Mongo) and rebuilds
  atomically. (A per-symbol-message variant is noted under open questions for when the portfolio is
  large.)
- **Trigger delivery is a direct, best-effort `RabbitTemplate` publish — not the outbox.** News is
  non-financial and the rebuild is idempotent, so a lost trigger is harmless: the next 4h tick
  self-heals it. No outbox machinery (that's reserved for the must-not-lose cash legs).
- **Overlapping rebuilds are safe.** Two quick buys, or a buy next to a scheduled tick, enqueue
  redundant full rebuilds; because a rebuild is idempotent (it recomputes from current holdings),
  the extra runs just reproduce the same feed. A single-in-flight guard (skip if a rebuild is
  already running) is available if the extra Finnhub calls ever matter for quota — not needed now.
- **Rate limiting:** every provider call — including these — goes through the shared token bucket,
  so news and quotes can't jointly exceed the free tier. Volume is tiny: one call per held symbol
  every 4h, plus a rebuild per held-set change.
- **Best-effort, never blank:** if a symbol's fetch fails transiently, that symbol contributes
  nothing this round (logged) and the rebuild proceeds with the rest. If **every** fetch fails, the
  **previous feed is left intact** (don't overwrite a good feed with an empty one). Listener retry +
  DLQ as with price refresh; a dead-lettered rebuild just waits for the next tick — no per-holding
  STALE state is needed since news is non-financial.
- **Held-set-change detection:** `buy()` already distinguishes creating a new OPEN holding from
  merging into an existing one, and `cashOut()` detects the full-close transition — only those two
  transitions publish `news.refresh` (a buy that merges into an existing holding, or a partial
  cash-out, does not).

## Provider integration

- A **`StockNewsProvider`** port: `companyNews(symbol, from, to) → List<NewsArticle>`, implemented by
  a Finnhub adapter that **reuses the same `RateLimiter` bean** as `FinnhubProvider`. Keeps the
  provider swappable and lets tests stub it via `MockRestServiceServer`.
- Non-NA / unknown symbols return an empty list (Finnhub replies `[]`), so no special error path.

## Frontend

- A **"News" card** on the Investments page (below the holdings table). Up to 7 rows: **headline**
  (links to `url`, opens in a new tab), the trimmed **summary**, and a small footer line with the
  **symbol chip · source · relative time** ("3h ago"). A header shows **"Updated Nh ago"** from
  `updatedAt`.
- **Empty state:** "No recent news for your holdings" when there are no items (no holdings, or no
  coverage in the window).
- New API + hook (`newsApi.list()`, `useInvestmentNews()`). The list fetches on page load. After a
  **held-set change** (new-symbol buy / full cash-out) the rebuild is **asynchronous** — it publishes
  a trigger, then makes rate-limited Finnhub calls — so an immediate refetch on the POST response
  would return the *stale* feed. Instead, the hook **polls `updatedAt` until it advances** (a few
  short retries, then give up), and the UI states the honest expectation: **"updating… new holdings'
  news appears within about a minute"** — not instantly, since the rate limiter makes exact timing
  unguaranteed.

## Config

| Env | Default | Meaning |
|-----|---------|---------|
| `NEWS_REFRESH_CRON` | `0 0 */4 * * *` | every 4 hours |
| `NEWS_LOOKBACK_HOURS` | `48` | per-symbol article window |
| `NEWS_PER_STOCK` | `3` | candidate articles taken per holding |
| `NEWS_MAX_ITEMS` | `7` | feed size (slots to draw) |
| `NEWS_WEIGHT_DECAY` | `0.625` | rank odds ratio (#k+1 ÷ #k); lower = steeper front-load |

Wired through `docker-compose.yml` like the pricing knobs.

## Testing

- **Selection** (unit, **seeded RNG**): with a fixed seed the draw is deterministic — assert the
  expected 7; value-order drives base odds; 3-per-stock candidate cap; dedup by url; renormalization
  after each pick (and the rebalance when a stock's 3 are exhausted); graceful degradation
  (1 symbol, a 0-article symbol, <7 candidates → shorter list). A statistical test (many seeds) can
  assert bigger holdings are drawn first more often.
- **Provider adapter** (unit, `MockRestServiceServer`): parse Finnhub company-news JSON; empty array
  for unknown symbol; summary trimming.
- **Feed rebuild** (Testcontainers Mongo + RabbitMQ, provider mocked): publish `news.refresh` →
  assert `news_feed` has the expected ≤7 ranked items; all-fail keeps the previous feed; empty
  holdings → empty feed.
- **New-symbol buy** triggers a rebuild (assert the message is published / the feed updates); buying
  an already-held symbol does **not**.
- Listener isolation: `auto-startup=false` on non-consuming ITs (same shared-broker lesson as pricing).

## Deviations & notes

- Deviates from the embedded `news[]` sketch in favor of a **singleton feed doc** (simpler read,
  single ranked list).
- News is **best-effort and non-financial** — failures degrade the list, never the dashboard or
  balances.
- Opening an article is **user-initiated external navigation** (a normal link), not an automated
  fetch of third-party content by the app.

## Open questions

1. **Per-symbol-message variant.** For a large portfolio, split into one `news.refresh.<symbol>`
   message each writing a per-symbol shortlist, with a separate rank step — more scalable but more
   moving parts. Not needed for a single-user app now.
2. **Caching window vs quota.** 48h + every-4h is comfortably within quota; revisit if the portfolio
   grows large or the cadence tightens.
3. **Article images / read-time** — out of scope for the "title + summary" ask.
