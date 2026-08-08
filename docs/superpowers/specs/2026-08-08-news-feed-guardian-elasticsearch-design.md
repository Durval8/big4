# Robust news feed: Guardian + topic modeling + Elasticsearch, as its own page — design

**Status: Approved design 2026-08-08, not yet implemented.** The Elasticsearch-ownership fork is
resolved — **Option A**, below: `news-service` owns Elasticsearch exclusively and serves the News
page's API directly. The pros/cons analysis is kept for the record (it's the reasoning the decision
rests on), not because the choice is still open.

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

## Elasticsearch ownership (locked: Option A)

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

**Decision: Option A.** It costs one extra module but buys back the exact invariant this system has
protected everywhere else, and the messaging shape (investments-service → news-service, one-way,
last-write-wins) is a straight copy of the `ValueSnapshot` pattern already proven in production.
`news-service` owns Elasticsearch exclusively; the News page is served **exclusively** by
`news-service`'s own API — investments-service never queries Elasticsearch, and nothing about the
News page routes through investments-service or backend.

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

**A theme is a keyword set, not a phrase — correcting an imprecision in earlier examples above.**
LDA doesn't produce readable phrases like "AI chip export controls"; it produces, per topic, a
ranked list of terms by weight (e.g. `chip(0.09) export(0.07) china(0.06) semiconductor(0.05)
restriction(0.04) tariff(0.03) nvidia(0.03)`). What this spec calls a "theme" is really **that
keyword set**; anywhere above reads "theme" as a tidy phrase, treat it as shorthand for "the
keyword set that theme represents." The **display label** shown as a filter chip is a cheap
derivation — join the top 2–3 terms (`"chip / export / china"`) — not a separate modeling step. A
genuinely readable auto-label (e.g. an LLM call per topic) is a plausible future enhancement, not
part of this spec's locked scope. See [Browsing tags](#browsing-tags-content-type-facets-and-theme-keyword-drill-down)
below for where the full keyword set (not just the top 2–3 used for the label) gets used.

### Pass 2 — theme-driven queries, served results

**One Guardian query per extracted theme (7 queries)**, using that theme's **top terms** (not just
the display label — the fuller keyword set, e.g. top 5–8 by weight) OR'd together as `q=`, same
lookback window. Querying on the actual keyword weights MALLET found, rather than the 2–3-term
display label, is closer to what the topic model actually determined and gives pass 2 better
recall. **Only these results get indexed into Elasticsearch and served** — pass 1 is discovery
scaffolding, never shown. Unlike pass 1, pass-2 query volume is now **fixed at 7 per pipeline run
regardless of portfolio size**, which is a meaningful efficiency property: a 20-holding portfolio
costs the same pass-2 budget as a 3-holding one.

### Elasticsearch design

`news-service` owns two indices — a growing article corpus, and a tiny singleton document
recording what the *current* pipeline run considers relevant. They're separated because they
answer different questions and change at different rates: articles accumulate across many runs;
"what are today's 7 themes" is replaced wholesale every run.

#### `news_articles` — the corpus, one document per distinct article

**Document identity is the whole design problem here.** Every 4h (or on-trigger) pipeline run does
its own pass 1 → MALLET → pass 2, and the same real-world article can legitimately resurface across
runs — a still-developing story stays in Guardian's "recent" window for days, and consecutive runs
may re-derive an overlapping (not identical) set of themes. Three requirements follow directly:

1. **The same article must never become two documents**, no matter how many runs re-surface it.
2. **An article's theme list should accumulate, not flip-flop** — if run N tags it
   `"AI chip export controls"` and run N+3 re-surfaces it under `"chipmaker earnings"` too, both
   themes are genuinely true of that article; losing the first on overwrite would make the corpus
   less useful over time, not more.
3. **Recency of *when this was last relevant* must survive independently of `publishedAt`** — a
   week-old article resurfacing today is a different signal than one nobody's queried back into
   view since it was published.

**Resolution: use Guardian's own article `id` as the Elasticsearch `_id`**, and **upsert** on every
pass-2 write via a script update (or read-modify-write, given expected volume is a few hundred
documents per run at most) that unions the incoming theme(s) into the existing `themes` array
instead of replacing it, and bumps `lastSeenAt`. Guardian's `id` (e.g.
`technology/2026/aug/03/apple-earnings-report`) is already a stable, globally unique key — no
hashing or URL-normalization needed, and it doubles as the natural idempotency key the same way
`eventId` does for cash-leg commands elsewhere in this system, just for a different reason (dedup
across runs, not exactly-once delivery).

**Mapping:**

```jsonc
PUT /news_articles
{
  "settings": {
    "number_of_shards": 1,      // single-node ES for a single-user app; no sharding need
    "number_of_replicas": 0,    // no HA requirement locally or in the current deployment shape
    "refresh_interval": "5s"    // write volume is a few hundred docs every 4h — no need for near-realtime
  },
  "mappings": {
    "properties": {
      "headline":      { "type": "text", "analyzer": "english" },   // free-text search, stemmed
      "trailText":     { "type": "text", "analyzer": "english" },   // Guardian's summary field
      "url":           { "type": "keyword" },                       // exact-match, display link
      "source":        { "type": "keyword" },                       // "The Guardian" today; future-proofs a 2nd source
      "sectionName":   { "type": "keyword" },                       // e.g. "Technology", "Business"
      "themes":        { "type": "keyword" },                       // array; exact-match facet + filter, union'd on upsert
      "contentTags":   { "type": "keyword" },                       // array; keyword-classifier tags, see Browsing tags
      "publishedAt":   { "type": "date" },                          // Guardian's publication instant
      "firstIndexedAt":{ "type": "date" },                          // set once, never overwritten
      "lastSeenAt":    { "type": "date" }                           // bumped every run that re-surfaces this article
    }
  }
}
```

`bodyText` (fetched alongside `trailText` for MALLET's input) is deliberately **not** a mapped
field — it's read transiently during the pipeline run (for topic modeling and for content-tag
keyword matching, below) and discarded, keeping the index lean. Only the derived signals
(`themes`, `contentTags`) and the display-worthy `trailText` summary persist.

`headline`/`trailText` are `text` (analyzed, stemmed via the `english` analyzer) because they're
genuinely searched, not just displayed. Everything else is `keyword` — exact-match filtering and
aggregation, never full-text matched. `themes` is `keyword`, not `text`: theme strings are
MALLET-extracted labels, treated as tags to filter/facet by, not prose to tokenize.

**Write path:** for each pass-2 result, run the content-tag keyword classifiers (below) against its
`headline`/`trailText`/transient `bodyText` to compute `contentTags`, then bulk-upsert the
deduplicated result set via the `_bulk` API using `_id = guardianArticleId` for each doc, with a
partial-update script: `ctx._source.themes = (ctx._source.themes + params.newThemes) as a set;
ctx._source.contentTags = (ctx._source.contentTags + params.newTags) as a set;
ctx._source.lastSeenAt = params.now`, falling back to a plain index (with `firstIndexedAt =
lastSeenAt = now`) when the document doesn't exist yet.

**Retention.** An accumulating corpus without a cap eventually just becomes storage cost with no
UX benefit — old, no-longer-relevant articles won't win the theme-facet filter (see query patterns
below) but would still bloat a `match_all`/recency browse. Locked default: a periodic delete-by-query
job (piggybacking on the same scheduler as the pipeline) purges documents whose `lastSeenAt` is
older than `NEWS_RETENTION_DAYS` (proposed default **90**) — pruning on *last relevance*, not
`publishedAt`, so a genuinely long-running story that keeps resurfacing under new themes is never
purged out from under itself.

#### `news_pipeline_state` — singleton document, "what's current"

```jsonc
{
  "_id": "current",
  "themes": [
    {
      "label":    "chip / export / china",                              // top 2-3 terms, for the chip
      "keywords": ["chip", "export", "china", "semiconductor",
                   "restriction", "tariff", "nvidia"]                    // full weighted term set (top 5-8)
    }
    // … up to 7
  ],
  "runAt": "<Instant>"
}
```

This exists because the theme facet chips the News page shows should reflect **the latest run's 7
themes**, not every theme ever seen across the corpus's lifetime (which would grow to dozens as
`news_articles` accumulates history and become a useless, ever-lengthening filter list). Keeping
"today's 7" as its own singleton — the same shape as the old Mongo `news_feed` singleton this
feature is superseding in ambition, just now living in Elasticsearch since news-service has no
other datastore — cleanly separates "what to filter by" from "what's in the corpus." This also
folds in what an earlier draft of this spec flagged as an open question (news-service's own
pipeline-state persistence) — no second datastore needed; it's a second small index in the same ES.
Carrying the full `keywords` list per theme (not just `label`) is what makes theme-specific
keyword browsing possible — see below.

#### Query patterns (serving the News page)

- **Default view** (no filter selected): `GET /news_articles/_search` sorted `publishedAt desc`,
  paginated (`from`/`size`; `search_after` if deep pagination ever matters, unlikely at this scale).
- **Theme chip selected**: `{"query": {"terms": {"themes": ["<selected theme label>"]}}}`, same sort.
- **Theme keyword drill-down** (see Browsing tags below): `{"query": {"match": {"trailText":
  "<one keyword from that theme's set>"}}}` **AND**-ed with the theme filter above — narrows an
  already-theme-filtered view by one of its underlying terms, computed live rather than stored
  per-article (see rationale below).
- **Content tag selected** (e.g. "Future moves"): `{"query": {"terms": {"contentTags":
  ["future-moves"]}}}`, composable with a theme filter (both are `terms` filters, so they `AND`
  naturally in a `bool` query).
- **Chip labels**: read once from `GET /news_pipeline_state/_doc/current` — not a `terms`
  aggregation over `news_articles.themes`, precisely to avoid surfacing stale historical themes as
  filter options. Content-tag chip labels are static (they're a fixed, code-defined list, not
  derived per run).
- **(Later, optional) free-text search box**: `multi_match` over `headline^2` and `trailText`,
  boosting headline matches — not part of this spec's locked scope, noted as a natural extension
  the index shape already supports for free.

No aggregation is needed to compute "top 7" at read time — that work already happened once, in the
pipeline, and is cached verbatim in `news_pipeline_state`.

Elasticsearch earns its place here beyond "the user asked for it": native relevance scoring (BM25)
for pass-2 ranking, faceted filtering by symbol or theme on the News page, and a durable growing
corpus instead of the existing card's single overwritten singleton document — the News page can
support search/browse, not just a fixed list.

## Browsing tags: content-type facets and theme-keyword drill-down

Themes answer "what story is this." A second, orthogonal question is useful too: "what *kind* of
coverage is this" — is it forward-looking speculation, a results announcement, a regulatory
development? That's a different axis, and it doesn't need MALLET — simple keyword/phrase spotting
against `headline`/`trailText`/transient `bodyText` is enough, computed per-article at pipeline
write time (same point as content-tag assignment in the write path above), stored in `contentTags`.

### Content-type tags (keyword-classifier mechanism)

A **config-driven list of `{tagName: [phrases]}`** — a static resource file in news-service (not
env vars; the list is long and structural, not a per-deployment tunable), checked against each
pass-2 article's text. A tag applies if **any** of its phrases match (case-insensitive, simple
substring or phrase match — no NLP needed for this pass). An article can carry multiple tags.

| Tag | Example phrases | What it surfaces |
|---|---|---|
| `future-moves` | "expected to", "plans to", "forecast", "set to", "poised to", "guidance", "anticipated" | Forward-looking coverage — what's coming, not what happened |
| `earnings` | "quarterly results", "EPS", "revenue beat", "revenue miss", "reported earnings" | Results/reporting coverage |
| `regulatory` | "antitrust", "regulator", "lawsuit", "investigation", "fine", "compliance" | Legal/regulatory developments |
| `mergers-acquisitions` | "acquire", "acquisition", "merger", "buyout", "deal to buy" | M&A activity |
| `leadership-change` | "steps down", "resign", "named CEO", "appoint", "succeed as" | Executive/leadership moves |
| `analyst-rating` | "price target", "upgrade", "downgrade", "analyst rating", "outperform" | Sell-side opinion, not company action |

This table is a starting point, not a locked final list — same "not decided" status as the
retention window default. The mechanism (keyword-classifier → `contentTags` array) is the locked
part; the specific tag set is expected to be tuned once real Guardian output is in hand (a phrase
list that's too broad over-tags everything, too narrow tags nothing — this needs iteration against
real data, not guesswork).

**Why keyword-spotting and not another MALLET pass**: these are binary/categorical distinctions
("is this forward-looking, yes/no"), not clustering problems — topic modeling finds latent
groupings in a corpus, it doesn't answer a yes/no question about one document. A rule-based
classifier is the right-sized tool, and it's ordinary Java string matching, not a second ML
dependency.

### Theme-specific keyword browsing

Formalizes what "theme" already carries once `news_pipeline_state.themes[].keywords` exists (see
above): each of the 7 themes is a **set of keywords**, not just a display label. Two consumers of
that keyword set, already reflected above:

1. **Pass 2 query construction** uses the full keyword set (top 5–8 terms), not just the 2–3-term
   label — better recall than querying on the label alone.
2. **Frontend drill-down**: selecting a theme chip can expose its underlying keyword list (e.g. as
   secondary sub-chips: `chip` · `export` · `china` · `semiconductor` · …); clicking one **narrows**
   the already-theme-filtered view to articles also matching that specific term, computed as a live
   `match` query rather than a stored per-article field — storing "which keyword(s) of its theme
   matched this article" per document would need to happen at index time per theme per article
   (multiplicative bookkeeping for no real benefit, since the query-time version is one extra
   clause and just as fast at this corpus size).

### Other ideas considered, and why they're scoped in or out

- **Section facet (`sectionName`) — scoped in, effectively free.** Already indexed as `keyword` for
  display; promoting it to a third filter dimension (Technology / Business / …) alongside themes
  and content tags costs nothing new to build.
- **Recency lane ("Just In") — scoped in, effectively free.** `publishedAt` is already indexed and
  sorted on; a "published in the last N hours" quick-filter is a `range` query on data already
  there, no pipeline change needed.
- **Sentiment tag (bullish/bearish keyword spotting: "surge", "plunge", "rally", "selloff") —
  plausible, same keyword-classifier mechanism as content tags above.** Not included in the locked
  tag table because sentiment words are far more ambiguous out of context than "expected to" or
  "antitrust" (a headline can use "surge" ironically or about volume, not price) — worth a
  dedicated accuracy check before shipping, not a same-day add.
- **Named-entity tagging (e.g. tag which companies are actually *mentioned*, not just the
  seed-query symbols) — the most direct realization of "similar stocks," explicitly scoped
  out for now.** True NER is a real NLP dependency this spec hasn't introduced (MALLET does topic
  modeling, not entity recognition); a cheap approximation — matching against a static list of
  known company names — would work but needs that list maintained and is a meaningfully different
  scope decision than reusing MALLET. Flagged as the most promising *next* enhancement, not part of
  this spec.

## Frontend

- **New route/page** (e.g. `/news`), own nav entry — separate from the Investments page, which is
  untouched.
- **Three filter dimensions**, all composable (`AND`ed):
  - **Theme facets** (up to 7, one per pipeline run) — the primary filter, each expandable into its
    underlying keyword sub-chips for further drill-down (see Browsing tags above).
  - **Content-type tag chips** (future moves, earnings, regulatory, M&A, leadership, analyst
    rating) — a fixed, code-defined list, independent of any given run's themes.
  - **Section** and **"Just In"** quick-filters (Guardian's `sectionName`; a recent-hours toggle).
  - No per-symbol filter — the index carries no symbol attribution; the held-symbol set only ever
    influences pass 1 upstream, not what's shown.
- Backed by news-service's own `/api/news` endpoint (gateway-routed directly, no
  backend/investments-service proxying — same shape as investments-service's existing direct
  frontend access).
- Empty state per filter combination that returns nothing, same honest-empty-state philosophy as
  the existing card.

## Config (new, on whichever service ends up owning the pipeline per the resolved fork)

| Env | Default (proposed) | Meaning |
|-----|---------|---------|
| `GUARDIAN_API_KEY` | — | Guardian Open Platform key |
| `GUARDIAN_BASE_URL` | `https://content.guardianapis.com` | overridable for testing |
| `NEWS_PIPELINE_CRON` | `0 0 */4 * * *` | periodic full resync, mirrors existing internal cadence |
| `NEWS_LOOKBACK_HOURS` | `48` | pass-1 and pass-2 article window |
| `NEWS_CORPUS_SIZE` | `200` | pass-1 pooled corpus size (Guardian's max `page-size`) |
| `MALLET_NUM_THEMES` | `7` | themes extracted per pipeline run, and pass-2 query count |
| `NEWS_RETENTION_DAYS` | `90` | `news_articles` docs purged once `lastSeenAt` is older than this |
| *(resource file, not env)* | `content-tags.yml` or similar | the `{tagName: [phrases]}` list — structural, versioned in code, not per-deployment config |

## Testing (sketch — firms up once the ownership fork is resolved)

- Guardian adapter: `MockRestServiceServer`, matching the existing `FinnhubProvider`/
  `FinnhubNewsProvider` precedent (no embedded HTTP mock server — see `docs/TESTING.md`).
- MALLET theme extraction: unit tests over fixed input corpora with known expected top terms,
  isolated from any HTTP/DB dependency.
- Pipeline end-to-end: Testcontainers Elasticsearch + RabbitMQ, asserting pass-1 results never leak
  into the index and pass-2 results do.
- Upsert semantics: re-running the pipeline against an article already in `news_articles` merges
  themes (union, not overwrite) and bumps `lastSeenAt` without creating a duplicate document or
  losing a previously-attached theme.
- Retention: a document whose `lastSeenAt` predates `NEWS_RETENTION_DAYS` is purged by the sweep;
  one that resurfaces (bumping `lastSeenAt`) the day before the cutoff survives.
- `news_pipeline_state`: a full pipeline run overwrites the singleton wholesale (not a merge, unlike
  `news_articles`) — assert stale themes from a prior run don't linger as chip options.
- Content-tag classifier: unit tests per tag over fixed sentences (a "plans to launch" sentence
  tags `future-moves`; a sentence with none of the configured phrases tags nothing); assert an
  article can carry multiple tags; assert tags union (not overwrite) on upsert like themes do.
- Contract test for the new `investment.portfolio` message, mirroring
  `InvestmentMessageContractTest`'s JSON-fixture approach.

## Deviations & notes

- Deviates from the user's original "Elasticsearch owned by both services" framing — resolved to
  Option A (see above) after an explicit pros/cons request; `news-service` is the sole ES
  reader/writer and the sole thing that serves the News page.
- The Investments-page card and this new page are **fully independent** after this change: two
  news sources (Finnhub vs Guardian), two refresh cadences, two storage shapes, sharing only "which
  symbols are currently held."
- `news_articles` documents carry no symbol attribution (by design, per the aggregated-pass-1
  decision) and no run identifier either — an article belongs to the corpus, not to any one
  pipeline run. `news_pipeline_state` is what's run-scoped.

## Open questions

1. **Does pooled LDA actually stabilize at ≤200 documents?** The aggregated-corpus approach is the
   mitigation, not a guarantee — needs empirical validation once real Guardian responses are in
   hand; TF-IDF fallback is the documented escape hatch if 7 themes come out noisy.
2. **Guardian rate limits/tier** — unlike Finnhub's documented 60/min free tier already reused for
   pricing, Guardian's free-tier quota hasn't been checked yet. Call volume is now cheap and
   portfolio-size-independent though: **1 pass-1 call + 7 pass-2 calls per pipeline run**, down
   from the first draft's per-symbol scaling.
3. **Company-name query construction at portfolio scale** — a single Guardian query OR-ing every
   held company name works cleanly for a handful of holdings; Guardian's query-length limits for a
   much larger portfolio aren't known yet (not a concern for a single-user app today, noted for
   completeness).
4. **`NEWS_RETENTION_DAYS` default (proposed 90)** is a guess, not a measured value — revisit once
   real corpus growth rate (articles/run after dedup) is observed.
5. **Content-tag phrase lists (the table above) are a first guess**, not tuned against real
   Guardian output — expect false positives/negatives until iterated on actual articles.
6. **Sentiment and named-entity tagging** are noted as plausible next steps, deliberately not
   speced here (see Browsing tags → "Other ideas considered").

## V1 implementation slice — the simplest version that's still correct

Everything above is the target design. This section is the **first buildable increment** — enough
to prove the real pipeline end-to-end (real Guardian calls, real MALLET topics, real Elasticsearch,
a real page) while deliberately deferring every enhancement that isn't load-bearing for that proof.
Each simplification below is explicit and reversible — nothing here contradicts the design above,
it just sequences what's built first.

### What v1 cuts, and why each cut is safe

| Full design | v1 simplification | Why it's safe to defer |
|---|---|---|
| Company-name resolution (Finnhub lookup + `Holding.companyName` + backfill) | Query Guardian with **raw stock symbols** | Weaker relevance, zero schema/migration risk; upgrading to company names later is an isolated change to one query-building method |
| Held-set-change trigger (buy/cash-out → immediate republish) | **Periodic publish only**, on `NEWS_PIPELINE_CRON` | Same self-healing property the design already leans on elsewhere — worst case, a new holding's news is up to one cron tick late |
| Two ES indices (`news_articles` + `news_pipeline_state`) | **One ES index**; "current themes" held in an **in-memory bean** | A restart loses the current theme labels until the next scheduled run repopulates them — acceptable for a single-instance personal deployment; nothing is lost from `news_articles` itself |
| Upsert-merge script (union themes across runs) | **Plain overwrite-index** per run | An article's `themes` reflects only its most recent run instead of accumulating across runs — a real regression from the target design, but not a correctness bug, and it's a one-method change to fix later |
| `contentTags`, `sectionName`, retention job | **Omitted entirely** | None of these affect whether the pipeline or page works — pure additive scope |
| Theme-keyword drill-down UI | **Chips only**, no sub-keyword expansion | The keyword sets already exist in the theme extraction output either way; the UI for them is a later increment |

### news-service module (new)

```
news-service/                                  # new Maven module, registered in root pom.xml
  src/main/java/com/financedash/news/
    domain/       GuardianArticle (record: guardianId, headline, trailText, bodyText, url,
                    publishedAt) — the transient pass-1/pass-2 shape, not an ES entity
    document/     NewsArticleDocument (@Document(indexName="news_articles"))
    repository/   NewsArticleRepository (Spring Data Elasticsearch — consistent with this repo's
                    existing Spring Data JPA/Mongo pattern, not a hand-rolled REST client)
    dto/          ArticleResponse, ThemeResponse, ErrorResponse — records, per repo convention
    provider/     GuardianProvider (+ GuardianSearchException) — RestClient adapter, mirrors
                    FinnhubProvider's shape (rate-limit awareness deferred — Guardian's free tier
                    limit isn't quota-shared with anything else, unlike Finnhub)
    service/      ThemeExtractor (wraps MALLET — input: List<GuardianArticle>, output:
                    List<Theme(label, keywords)>), NewsPipelineService (orchestrates pass 1 →
                    ThemeExtractor → pass 2 → index), CurrentThemesHolder (in-memory, v1 only)
    messaging/    PortfolioSymbolsConsumer (consumer-side mirror record + @RabbitListener,
                    triggers NewsPipelineService synchronously — no internal queue in v1)
    controller/   NewsController (GET /api/news, GET /api/news/themes)
    config/       ElasticsearchConfig, RabbitConfig, WebConfig (CORS — copy investments-service's
                    WebConfig verbatim; same unauthenticated-API reasoning applies)
    exception/    GlobalExceptionHandler
  pom.xml         spring-boot-starter-web, spring-boot-starter-data-elasticsearch,
                    spring-boot-starter-amqp, mallet
```

Port **8082** (backend=8080, investments-service=8081, news-service=8082 — next in sequence).

### investments-service change (v1 scope: publish only)

One new `@Scheduled` producer, same shape as `PriceRefreshScheduler` but simpler (no
due/not-due filtering — every tick publishes the full current OPEN set):

```java
@Scheduled(cron = "${news.pipeline-cron:0 0 */4 * * *}")
public void publishPortfolioSymbols() {
    List<String> symbols = holdingRepository.findByStatus(HoldingStatus.OPEN)
        .stream().map(Holding::getStockSymbol).distinct().toList();
    rabbitTemplate.convertAndSend(InvestmentsMessaging.INVESTMENTS_EXCHANGE,
        "investment.portfolio", new PortfolioSnapshot(symbols, clock.instant()));
}
```

No `companyName` field, no buy()/cashOut() hook changes — this is the entire investments-service
diff for v1.

### Elasticsearch v1 index

```java
@Document(indexName = "news_articles")
@Setting(shards = 1, replicas = 0)
public class NewsArticleDocument {
    @Id
    private String id;                                    // = Guardian's own article id

    @Field(type = FieldType.Text, analyzer = "english")
    private String headline;

    @Field(type = FieldType.Text, analyzer = "english")
    private String trailText;

    @Field(type = FieldType.Keyword)
    private String url;

    @Field(type = FieldType.Keyword)
    private String source;                                // "The Guardian", hardcoded for now

    @Field(type = FieldType.Keyword)
    private List<String> themes;                           // this run's themes only (v1: overwritten, not merged)

    @Field(type = FieldType.Date)
    private Instant publishedAt;
}
```

`docker-compose.yml` gains one service:

```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.15.0
  environment:
    discovery.type: single-node
    xpack.security.enabled: "false"   # matches this app's no-auth stance on Postgres/Mongo too
    ES_JAVA_OPTS: "-Xms512m -Xmx512m"
  ports:
    - "${ELASTICSEARCH_PORT:-9200}:9200"
  volumes:
    - es_data:/usr/share/elasticsearch/data
```

### REST API v1

```
GET /api/news/themes
→ { "themes": [ { "label": "chip / export / china", "keywords": ["chip","export","china",…] } ],
    "runAt": "2026-08-08T12:00:00Z" }
    (served from CurrentThemesHolder; empty themes[] + runAt=null before the first run completes)

GET /api/news?theme=<label>&page=0&size=20
→ { "items": [ { "headline", "trailText", "url", "source", "themes", "publishedAt" } ],
    "page": 0, "size": 20, "hasMore": true }
    (theme param optional; omitted = unfiltered, publishedAt desc)
```

Both gateway-routed directly (`/api/news/**` → news-service:8082), same pattern as
`/api/investments/**` → investments-service today. No backend or investments-service involvement
in serving either endpoint.

### Frontend v1

```
frontend/src/
  types/newsFeed.ts        Theme, Article, NewsFeedResponse (kept separate from the existing
                              types/news.ts, which is the unrelated Investments-page card's types)
  api/newsFeed.ts           newsFeedApi.themes(), newsFeedApi.list(theme?, page?)
  hooks/useNewsFeed.ts      fetches themes once on mount; fetches articles on mount and whenever
                              the selected theme changes; exposes { themes, articles, selectedTheme,
                              setSelectedTheme, loading, error }
  components/newsfeed/
    ThemeChipBar.tsx        renders theme chips + an "All" chip; highlights the selected one
    ArticleCard.tsx         headline (links to url, new tab), trailText, source + relative time
    ArticleList.tsx         maps ArticleCard, renders EmptyState when items is empty
  pages/NewsFeedPage.tsx    composes ThemeChipBar + ArticleList
```

- New route `/news` in the router; new nav entry in `AppShell.tsx` alongside the existing GitHub
  docs link.
- Empty states: no themes yet (pipeline hasn't run) vs. a selected theme with zero articles — two
  distinct messages, same honest-empty-state philosophy as the rest of the app.
- Styling: plain CSS against `tokens.css`, same as every other page — no new dependency.

### "Corresponding frontend test" — what that means in this repo

**This codebase has no frontend test runner** — `frontend/package.json` has exactly three runtime
dependencies (react, react-dom, react-router-dom) and no test framework; `npm run build` (`tsc -b`
+ `vite build`) is the only automated gate (see `CLAUDE.md`). Every prior frontend feature in this
project (analytics charts, the investment cash-leg UI changes) was verified by **manual browser
walkthrough**, not an automated suite. This section follows that same convention rather than
silently introducing new tooling.

**Manual verification script for the News page:**

1. Seed at least one OPEN holding (any symbol) so pass 1 has something to query.
2. Add a **manual trigger endpoint for testing** — `POST /api/news/refresh` on news-service,
   calling `NewsPipelineService` directly — so verification doesn't require waiting up to
   `NEWS_PIPELINE_CRON`'s full interval. (Dev/test convenience only; not part of the public
   contract above.)
3. Trigger it, then `GET /api/news/themes` directly and confirm 7 (or fewer, if the corpus was
   thin) themes with non-empty `keywords`.
4. Load `/news` in the browser: confirm the same theme labels render as chips, matching step 3
   exactly.
5. Click a chip: confirm the article list changes and every visible article's `themes` (check via
   network tab / `GET /api/news?theme=`) actually contains the selected label.
6. Click "All": confirm the list returns to the unfiltered, `publishedAt`-descending view.
7. Pick a theme with zero matches (or temporarily filter on a nonsense value via the API) and
   confirm the empty state renders rather than a blank list.
8. Click an article headline: confirm it opens the Guardian article in a new tab
   (`target="_blank" rel="noreferrer noopener"`, matching the existing GitHub docs link pattern).
9. Resize to <800px (this repo's one breakpoint) and confirm the chip bar and article cards reflow
   without horizontal scroll.
10. Toggle dark mode: confirm chips and cards pick up `tokens.css` dark values with no
    hardcoded-light-color regressions.

If real automated frontend tests are wanted instead of/alongside this script, that's a separate,
explicit decision (introducing Vitest + Testing Library or similar) — not assumed here, since it's
a tooling change affecting the whole frontend, not just this feature.
