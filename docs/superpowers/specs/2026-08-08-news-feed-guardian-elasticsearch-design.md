# Robust news feed: Guardian + topic modeling + Elasticsearch, as its own page — design

**Status: Brainstormed and partially agreed 2026-08-08. One architectural fork (Elasticsearch
ownership) is deliberately left open below with a pros/cons analysis, not a locked decision. Not
approved for implementation until that fork is resolved.**

## Why

The existing Investments-page news card ([INVESTMENT_NEWS.md](../../INVESTMENT_NEWS.md)) is
intentionally light: Finnhub `/company-news` per held symbol, a value-weighted draw over a 48h
window, 7 slots, refreshed every 4h. It works, but it's shallow — one source, no understanding of
*why* an article is relevant beyond "it mentions a symbol I hold," and no way to grow into more
than a small card without becoming its own concern.

This spec is for a **second, richer news surface**: a dedicated page, sourced from the Guardian
API instead of Finnhub, that extracts the actual themes driving coverage of your holdings (via
topic modeling) and uses those themes to widen the search — so holding AAPL doesn't just surface
articles that say "Apple," it surfaces the smartphone-supply-chain or AI-chip story AAPL is
currently part of, which organically pulls in adjacent companies without maintaining a peer list.

**This is additive, not a replacement.** The Investments page keeps its current Finnhub-sourced
card exactly as implemented — different audience (glanceable, portfolio-anchored), different
budget (7 items, 4h cadence), no reason to touch a working, tested feature. The new page is where
the investment goes into a proper search/aggregation-backed corpus.

## Locked decisions (from spec Q&A)

1. **Guardian, not Finnhub, powers the new page.** Finnhub remains untouched on the Investments
   page. The two surfaces share no code path except reading the same held-symbol set.
2. **Two-pass query, themes bridge the passes — pass 1 is aggregated across the whole portfolio,
   not per symbol:**
   - **Pass 1** — a single Guardian query combining *all* held company names (one call, not one
     per symbol), pulling a substantial pooled corpus for the whole portfolio at once.
   - **Extract themes** from that pooled corpus via topic modeling (MALLET) — **top 7 themes**.
   - **Pass 2** — one Guardian query per extracted theme (7 queries), by the *theme*, not any symbol.
   - **Serve only pass-2 results.** Pass 1 is purely a theme-discovery step; nothing from it is
     shown directly. This is what gives the feed its "similar stocks surge too" property, and it's
     now structural rather than incidental: because pass 1 pools the *entire* portfolio before
     extracting themes, a theme like "AI chip export controls" isn't attributed back to any single
     symbol — pass 2 searches it portfolio-wide, so whoever else is currently in that story surfaces
     too, without the app ever maintaining a symbol→peers table.
3. **Topic modeling = MALLET**, run in-process (it's a Java library — no sidecar, no new runtime),
   over the pooled portfolio-wide corpus (resolves the per-symbol-vs-pooled question the first
   draft of this spec left open).
4. **New page, not a card.** Own route, own nav entry, backed by Elasticsearch instead of a
   singleton Mongo document — a real corpus with faceted search (by symbol, by theme), not just a
   fixed 7-item list.

## Open architectural fork: who owns Elasticsearch?

The pipeline naturally splits into two halves: **producing** articles (Guardian fetch → MALLET →
Guardian again → index) needs the held-symbol set, which only `investments-service` has; **serving**
them (search/filter for the News page) is a read path. The question is whether both halves live in
`investments-service`, or split across two services sharing one Elasticsearch cluster, or split
across two services with Elasticsearch owned by only one.

This matters because **every existing datastore in this system has exactly one owning service**
(Postgres↔backend, MongoDB↔investments-service) and **cross-service data flow is one-way messaging,
never a shared store** — see `CLAUDE.md`'s "no sync HTTP between backend and investments-service"
rule and its extension into `SYSTEM_DESIGN.md`. A shared Elasticsearch index would be the first
deviation from that pattern anywhere in the codebase.

### Option A — dedicated `news-service` owns Elasticsearch exclusively

```
investments-service ──(RabbitMQ, one-way, LWW)──▶ news-service ──owns──▶ Elasticsearch
                                                        │
                                                   REST API (/api/news)
                                                        │
                                                     frontend (direct, via gateway,
                                                     same pattern as investments-service today)
```

investments-service publishes a portfolio-symbols snapshot (see below); news-service is the only
thing that ever reads or writes Elasticsearch; the frontend's News page calls news-service's own
endpoint directly through the gateway, exactly as it calls investments-service directly today
without going through backend.

**Pros**
- Consistent with every other datastore boundary in the system — one owner, no exceptions to
  explain later.
- news-service can evolve its ES schema/mappings freely without coordinating a second consumer.
- Failure isolation: a news-service outage can't affect investments-service's read path (there
  isn't one) or vice versa.
- Matches the "becomes its own page" framing structurally, not just visually — it really is its own
  bounded context, module, deployable, and gateway route, the same shape as investments-service's
  original extraction from the backend.
- Mirrors an already-proven contract-ownership shape: investments-service owns the message
  contract, news-service holds a consumer-side mirror — same asymmetry as backend/investments-service
  today, just with the roles of "upstream" and "downstream" flipped.

**Cons**
- A third Spring Boot module, a third container, a third gateway route, a third set of docs to
  keep current — real ongoing maintenance cost for a single-user app.
- If investments-service ever needs to *display* something news-derived (e.g. "3 new articles"
  badge on a holding), it needs its own small subscription to a news-service event or a frontend
  composed from two calls — there's no shortcut through a shared table.

### Option B — shared Elasticsearch (investments-service reads, news-service writes)

```
investments-service ──resolves symbols──▶ (in-process pipeline OR triggers a sibling module)
                                                        │
                                                        ▼
                                          Elasticsearch (written by news pipeline,
                                                         read directly by investments-service)
```

**Pros**
- One fewer network hop for investments-service if it ever wants to read news data itself (e.g. to
  resurrect a "news" field on `HoldingResponse`) — a direct ES query instead of a message round trip.
- Marginally less to stand up if the "pipeline" and "index" halves are developed as one deployable
  with two logical responsibilities, rather than two deployables.

**Cons**
- Breaks the one-owner-per-datastore invariant that every doc in this repo treats as load-bearing
  ("don't widen the CORS allowlist," "don't add outbox machinery to snapshots," "don't fold
  investment_cash_flow back into balances" are all the same shape of warning: *don't collapse a
  boundary that was drawn on purpose*). This would be the first exception, and every future reader
  of `SYSTEM_DESIGN.md` would need a special case carved out for it.
- Two services now need to agree on ES index mappings and version them together — a schema change
  in the pipeline's indexing code can silently break investments-service's query code with no
  compile-time signal, unlike a RabbitMQ message shape change (which `InvestmentMessageContractTest`
  actually catches).
- Harder to reason about ownership under failure: if a query looks wrong, "which service wrote
  this document" is no longer answered by "there's only one writer."
- Doesn't actually save the infra lift — Elasticsearch still has to be stood up, configured, and
  documented either way; sharing it only removes the *service* boundary, not the *infrastructure*
  cost.

**Recommendation: Option A.** It costs one extra module but buys back the exact invariant this
system has protected everywhere else, and the messaging shape (investments-service → news-service,
one-way, last-write-wins) is a straight copy of the `ValueSnapshot` pattern already proven in
production. Left open per instruction to present both rather than lock it — **this must be resolved
before implementation starts**, since it decides whether news-service is a real module or a package
inside investments-service.

## Pipeline detail

### Trigger: what tells news-service which symbols to search

investments-service already knows exactly when the held-symbol set changes (`buy()` opening a new
symbol, `cashOut()` fully closing one) — the same two transitions that trigger the existing internal
`news.refresh` job. Reuse that detection, but publish outward too:

```jsonc
// routing key: investment.portfolio, exchange: investments.exchange (existing exchange, reused)
{
  "symbols": [
    { "symbol": "AAPL", "companyName": "Apple Inc" },
    { "symbol": "NVDA", "companyName": "NVIDIA Corp" }
  ],
  "asOf": "<Instant>"
}
```

- **Last-write-wins, not must-not-lose** — same reasoning as `ValueSnapshot`: this is a "here's the
  current world" fact, not a discrete event that must never be lost. A dropped message self-heals
  on the next publish. No outbox, no idempotency key.
- **Two publish triggers, same as the internal job**: (1) the held-set-change transitions, for
  promptness, and (2) a periodic full resync (proposed every 4h, matching the existing internal
  cadence) as a self-heal safety net independent of trigger delivery — exactly the belt-and-braces
  pattern `docs/SYSTEM_DESIGN.md` already documents for `ValueSnapshot`.
- **investments-service owns this contract**; news-service holds the consumer-side mirror record,
  matching the existing asymmetric-ownership convention (just with investments-service now playing
  the "upstream" role it doesn't play anywhere else today).

### Company name resolution (needed because Guardian doesn't index by ticker)

Guardian search works on natural-language text, not stock symbols — searching "AAPL" is much
weaker than searching "Apple." investments-service must resolve `companyName` before publishing.

- **Locked approach**: call Finnhub's `/stock/profile2?symbol=` once, at buy-time (when a symbol
  first becomes OPEN), and **cache the result on the `Holding` document** (`companyName` field,
  new). Company names don't change, so this is a one-time cost per symbol, not a per-publish cost,
  and it reuses the Finnhub key/rate-limiter already wired for pricing — Finnhub isn't gone from
  the picture, it's just no longer a *news* source.
- Existing holdings need a one-time backfill (fetch-and-set `companyName` for all OPEN holdings) —
  same shape as the `V4__backfill_investment_transactions.sql` precedent, but a Mongo backfill
  script rather than a Postgres migration since this field lives in `holdings`.

### Pass 1 — aggregated theme discovery

**One Guardian query for the whole portfolio**, not one per symbol: combine every held company
name into a single query (e.g. `q="Apple Inc" OR "NVIDIA Corp" OR "Microsoft Corp"`), ordered by
relevance/newest, over a lookback window (config, default 48h to match the existing internal news
job), requesting body/trail text (`show-fields=trailText,bodyText` or similar) since MALLET needs
actual text, not just headlines.

**Sizing "substantial."** Guardian's Content API supports up to `page-size=200` per call. Target a
single page-size=200 request as the default corpus size — comfortably larger than any one symbol's
share would be alone, and enough for MALLET's LDA to have a real chance at stable topics (see
caveat below). If a portfolio is small enough that 48h doesn't fill 200 articles, the corpus is
simply smaller — same honest-degradation stance the rest of this app takes (no padding with
irrelevant filler to hit a number).

### Theme extraction (MALLET)

Feed the entire pooled corpus from pass 1 into MALLET's topic-modeling pipeline in one run, extract
the **top 7 defining themes** (`MALLET_NUM_THEMES = 7`) across the whole portfolio at once — not
per symbol. This is what makes the "similar stocks surge" behavior structural rather than
incidental: a theme is never attributed back to the symbol that happened to surface it, so pass 2
searching that theme naturally pulls in whatever else the story touches.

**Caveat to flag honestly**: even pooled, MALLET's LDA (`ParallelTopicModel`) is built for corpora
larger than "≤200 short news articles over 48h." Pooling the whole portfolio (rather than one
symbol at a time, as the first draft of this spec proposed) is itself the primary mitigation — more
documents, more stable topics. If 7 stable themes still don't reliably emerge at this scale during
implementation, the fallback is simpler TF-IDF top-term extraction (MALLET supports this via its
pipe infrastructure without the full LDA machinery) rather than forcing LDA to work on too little
data.

### Pass 2 — theme-driven queries, served results

**One Guardian query per extracted theme (7 queries)**, using each theme's term(s) as `q=`, same
lookback window. **Only these results get indexed into Elasticsearch and served** — pass 1 is
discovery scaffolding, never shown. Unlike pass 1, pass-2 query volume is now **fixed at 7 per
pipeline run regardless of portfolio size**, which is a meaningful efficiency property: a
20-holding portfolio costs the same pass-2 budget as a 3-holding one.

### Elasticsearch index

One index (name TBD, e.g. `news_articles`), one document per served article:

```jsonc
{
  "themes":        ["AI chip export controls"],   // which of the 7 theme queries produced this
  "headline":      "…",
  "trailText":     "…",                    // Guardian's summary field
  "url":           "https://…",            // dedup key
  "source":        "The Guardian",
  "sectionName":   "Technology",
  "publishedAt":   "<Instant>",
  "ingestedAt":    "<Instant>"
}
```

No per-symbol attribution field (deliberately — see above: this feed is portfolio-wide by design,
not indexed back to "which holding caused this").

Elasticsearch earns its place here beyond "the user asked for it": native relevance scoring (BM25)
for pass-2 ranking, faceted filtering by symbol or theme on the News page, and a durable growing
corpus instead of the existing card's single overwritten singleton document — the News page can
support search/browse, not just a fixed list.

## Frontend

- **New route/page** (e.g. `/news`), own nav entry — separate from the Investments page, which is
  untouched.
- **Theme facets** (up to 7, one per pipeline run) as the primary filter — there's no per-symbol
  filter since the index carries no symbol attribution; the held-symbol set only ever influences
  pass 1 upstream, not what's shown.
- Backed by news-service's own `/api/news` endpoint (gateway-routed directly, no
  backend/investments-service proxying — same shape as investments-service's existing direct
  frontend access).
- Empty state per theme that returns nothing, same honest-empty-state philosophy as the existing
  card.

## Config (new, on whichever service ends up owning the pipeline per the resolved fork)

| Env | Default (proposed) | Meaning |
|-----|---------|---------|
| `GUARDIAN_API_KEY` | — | Guardian Open Platform key |
| `GUARDIAN_BASE_URL` | `https://content.guardianapis.com` | overridable for testing |
| `NEWS_PIPELINE_CRON` | `0 0 */4 * * *` | periodic full resync, mirrors existing internal cadence |
| `NEWS_LOOKBACK_HOURS` | `48` | pass-1 and pass-2 article window |
| `NEWS_CORPUS_SIZE` | `200` | pass-1 pooled corpus size (Guardian's max `page-size`) |
| `MALLET_NUM_THEMES` | `7` | themes extracted per pipeline run, and pass-2 query count |

## Testing (sketch — firms up once the ownership fork is resolved)

- Guardian adapter: `MockRestServiceServer`, matching the existing `FinnhubProvider`/
  `FinnhubNewsProvider` precedent (no embedded HTTP mock server — see `docs/TESTING.md`).
- MALLET theme extraction: unit tests over fixed input corpora with known expected top terms,
  isolated from any HTTP/DB dependency.
- Pipeline end-to-end: Testcontainers Elasticsearch + RabbitMQ (if Option A), asserting pass-1
  results never leak into the index and pass-2 results do.
- Contract test for the new `investment.portfolio` message, mirroring
  `InvestmentMessageContractTest`'s JSON-fixture approach.

## Deviations & notes

- Deviates from a literal reading of "Elasticsearch owned by both services" — see the fork above;
  Option A is recommended but not locked.
- The Investments-page card and this new page are **fully independent** after this change: two
  news sources (Finnhub vs Guardian), two refresh cadences, two storage shapes, sharing only "which
  symbols are currently held."

## Open questions

1. **Does pooled LDA actually stabilize at ≤200 documents?** The aggregated-corpus approach is the
   mitigation, not a guarantee — needs empirical validation once real Guardian responses are in
   hand; TF-IDF fallback is the documented escape hatch if 7 themes come out noisy.
2. **Elasticsearch ownership fork** — must be resolved before implementation (see above).
3. **Guardian rate limits/tier** — unlike Finnhub's documented 60/min free tier already reused for
   pricing, Guardian's free-tier quota hasn't been checked yet. Call volume is now cheap and
   portfolio-size-independent though: **1 pass-1 call + 7 pass-2 calls per pipeline run**, down
   from the first draft's per-symbol scaling.
4. **news-service's own persistence for pipeline state** (last-run timestamps, dead-letter/retry
   bookkeeping) — Elasticsearch alone may be sufficient (a small state document) or a dedicated
   store may be simpler; not decided.
5. **Company-name query construction at portfolio scale** — a single Guardian query OR-ing every
   held company name works cleanly for a handful of holdings; Guardian's query-length limits for a
   much larger portfolio aren't known yet (not a concern for a single-user app today, noted for
   completeness).
