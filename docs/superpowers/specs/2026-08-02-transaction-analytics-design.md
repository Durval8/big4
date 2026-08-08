# Transaction analytics & Dashboard visualizations — design

**Status: Backend implemented and tested 2026-08-08** (`AnalyticsService`/`AnalyticsController`/
`BucketUnit` + unit/slice/integration tests, all green). **Frontend (the actual charts) not yet
started.** Brainstormed and agreed 2026-08-02, refined 2026-08-08 (chart types locked, see [Chart
types](#chart-types)).

## Why

The Dashboard shows five scalars (net worth, spending, net spending, net investment, and three
account balances) plus per-budget progress bars. Nothing on the page answers *how* spending is
divided — not by category, not over time. A user can see they spent €1,284 this month but not that
€412 of it was groceries, or that it was mostly one bad week.

There is also **no aggregation endpoint anywhere in the API**. The three controllers
(`TransactionController`, `BalanceController`, `BudgetController`) return either a single scalar
summary, a page of rows, or a per-budget list. `BudgetService.progress()` is the closest thing —
it groups `EXPENSE` sums by *budget* (a user-defined category set), not by category, and only for
budgets the user happens to have created.

## Scope

Backend transactions only (Postgres). Four charts, rendered in a new section on the Dashboard
**below** `BudgetSection`, driven by the Dashboard's existing shared time-range selector, and
**capped at a one-year window**.

**Investments are explicitly excluded.** The backend's `investment_valuation` projection is a
single current snapshot that is period-independent by design (documented v1 simplification in
`BalanceService`'s Javadoc), so investing value cannot be charted over time without first adding
valuation history. That's a separate, larger piece of work — see `docs/ARCHITECTURE.md`
follow-ups. This also means **net-worth-over-time is out of scope**: it would silently exclude
investing or show a flat line for it, either of which misleads.

## Decisions locked in during brainstorming

- **Four charts**, all in one section: spending by category; spending over time; income vs. expense
  over time; category change vs. the prior period.
- **Aggregation is server-side.** Not a preference — a constraint. `GET /api/transactions` caps
  `size` at 100 (`TransactionController.MAX_PAGE_SIZE`) and the frontend never sets `size` at all,
  so it defaults to 20. Computing any of these charts client-side would mean looping N sequential
  requests. The unpaginated repository method already exists and is explicitly reserved for exactly
  this: `TransactionRepository.findByTransactionDateBetweenOrderByTransactionDateDescIdDesc`, whose
  Javadoc names `BalanceService` and `BudgetService` as its aggregate consumers.
- **One year is the hard maximum window** (see below). `range=ALL` does not apply to analytics —
  it collapses to the one-year cap.
- **Income and expense both**, not expenses only. Enables the income-vs-expense chart and future
  net-cash-flow views without an API change. Direction comes from `transactionType` — the 17-value
  `Category` enum has no income/expense split and nothing in the codebase declares one.
- **Hand-rolled SVG, no charting library.** The frontend has exactly three runtime dependencies
  (`react`, `react-dom`, `react-router-dom`), zero `<svg>` anywhere today, and an existing
  hand-rolled bar primitive (`.progress-track` / `.progress-fill`). A line, a diverging bar, and a
  single-level flow diagram are each well under 150 lines of path math (see [Chart
  types](#chart-types) — the flow diagram is the most involved of the three, but a *single-level*
  Sankey (one source, N targets, no intermediate stages) doesn't need a general layout algorithm,
  just the same "sort by value, stack, draw a link" logic a stacked bar already needs). A library
  would also make theming *worse*, not better: the light/dark tokens are defined across three CSS
  blocks (see below), so library defaults would need overriding in all three.
- **Charts follow the Dashboard's shared range selector.** One time concept on the page — the
  cards, the budgets and the charts always describe the same window (modulo the one-year cap, which
  only bites on `ALL`).
- **Every chart has a minimum-data threshold and hides itself below it** (see
  [Render thresholds](#render-thresholds)). An uninsightful chart is worse than no chart: it
  occupies prime dashboard space and implies a pattern that isn't supported by the data.
- **The backend returns all non-zero categories; the frontend collapses to top-N + "Other".**
  A 17-slice donut is unreadable, but the top-N cut belongs in the view, not the API: the
  category-movers chart wants the *biggest movers*, which are not necessarily the biggest absolute
  spenders. The payload is at most 17 rows, so returning everything costs nothing and keeps the
  endpoint reusable.
- **One endpoint, not four.** Charts 1 and 4 share the per-category data (movers just needs a
  `previousAmount` alongside), and charts 2 and 3 share the per-bucket series (income-vs-expense
  just needs income alongside). Two arrays serve all four charts, so one request means one loading
  state and one error state for the whole section.

## Chart types

Each of the four charts gets a distinct, locked visualization type — resolving what was previously
an open question for chart 1 and previously-implicit choices for charts 2–4.

| # | Chart | Type | Why |
|---|---|---|---|
| 1 | Spending by category | **Flow diagram** (single-level Sankey: one "Total spending" source node, one link per category to a target node, link/node thickness ∝ amount) | Replaces the donut/horizontal-bar choice that was left open. A donut's angle encoding degrades with skewed, many-slice distributions (a €900 rent category next to six €20 ones is hard to compare by eye); a flow's thickness encoding reads the same way a bar does, but the source→category framing communicates "this is *where the money went*" more directly than a pie ever does, and it scales to the full top-N + "Other" set without the label-crowding a donut gets past ~6 slices. |
| 2 | Spending over time | **Line chart**, single series | Standard trend encoding; one line per bucket's expense total, matching `--color-accent` per the existing palette table. |
| 3 | Income vs. expense over time | **Diverging bar chart**, one bar-pair per bucket — income extends up from a shared zero axis, expense extends down (mirrored, magnitude only, not a negative number) | Two independent bars per bucket (grouped side-by-side) would work but reads as "compare two heights"; mirroring around zero reads as "money in vs. money out" at a glance and reuses the positive/negative tokens exactly as already assigned in the palette table below — no new token needed. |
| 4 | Category movers | **Horizontal diverging bar chart**, one bar per category, extending right (increase) or left (decrease) from a zero centerline | Already implied by the existing color-inversion note below; stated explicitly here as its own chart type. Horizontal orientation keeps category labels readable at up to 17 rows, which a vertical bar wouldn't at that count. |

All three non-flow charts share one **diverging-bar SVG helper**, parameterized by orientation
(vertical for chart 3, horizontal for chart 4) and by which token pair drives which direction —
avoiding two near-duplicate implementations of the same "bar extends from a baseline, color by
sign" logic.

## Semantics — what counts, and what it reconciles against

This is the part most likely to cause a "the numbers don't match" bug report, because the new
charts sit directly below cards computed a different way.

- **Category charts use `EXPENSE` only.** They therefore reconcile against
  `BalanceSummaryResponse.netSpending` (`= Σ EXPENSE`), **not** against `spending`
  (`= Σ EXPENSE + Σ TRANSFER into SAVINGS`). Transfers carry no category — `category` is null unless
  `transactionType` is `INCOME`/`EXPENSE` — so they *cannot* appear in a category breakdown. The
  section must not be labelled with the word "spending" in a way that implies it should equal the
  Spending card.
- **`TRANSFER` and `ADJUSTMENT` are excluded from every chart.** Transfers move money between the
  user's own accounts (no net income or expense), and adjustments are balance corrections, not
  economic activity. Both are also category-less.
- **Invariant worth testing directly:** for any window, `totalExpense` from this endpoint must equal
  `netSpending` from `GET /api/balances`, and must equal the sum of the `categories[].amount` array.
  The second half of that holds only because `category` is required for `EXPENSE` — but that's
  enforced at the API layer (`TransactionService`), while the DB column is nullable. A
  category-less expense inserted directly (e.g. by a future seeding script that bypasses the API)
  would count toward `totalExpense` but not toward any category row, silently breaking the
  invariant. The `AnalyticsIT` assertion therefore assumes API-created data; if `up-test-data`
  ever seeds via SQL, check it sets a category on every expense.
- Amounts are unsigned in storage; the response keeps them unsigned, with direction carried by which
  field they land in (`income` / `expense`).

## Window resolution and the one-year cap

The endpoint takes the same `range` / `from` / `to` params as balances and budgets and resolves
them through the same shared `dto/Period.resolve(...)`, then applies **two adjustments in order**:

1. **One-year cap.** If the resolved window exceeds one year, `from` is pushed forward to
   `to.minusYears(1).plusDays(1)`. This is what makes `range=ALL` meaningful here: rather than
   producing a series back to `1970-01-01`, `ALL` simply yields the maximum supported window — the
   last year — and is therefore *identical to `YEAR`* in the common case. The cap applies equally to
   an over-long explicit `from`/`to`.
2. **Earliest-transaction floor, for any named `range`.** If the user's first-ever transaction is
   more recent than the capped `from`, the window starts there instead. Without this, a two-month-old
   install selecting "All time" would render twelve monthly buckets, ten of them empty. Uses
   `findFirstByOrderByTransactionDateAsc()` — the repository method added for budget proration, so
   reuse rather than new query surface.

   The floor applies to **every `TimeRange` value, not just `ALL`** — `WEEK`, `MONTH` and `YEAR` are
   selector buttons too, and `YEAR` on a two-month-old install has exactly the same ten-empty-buckets
   problem. Only an **explicit `from`/`to`** escapes it: a caller who names specific dates gets those
   dates (capped), not a second-guessed window.

   This composes correctly with the render thresholds below rather than fighting them. A user three
   days into using the app who selects "Last week" gets a three-day window → 3 buckets → below the
   5-bucket threshold → the trend chart hides itself. That's the right outcome: three days genuinely
   is not a trend, and it beats drawing seven bars of which four are structurally empty.

`from` in the response always echoes the **final, adjusted** window — never `1970-01-01`. This
matters beyond cosmetics, because `buckets` is gap-filled: echoing the epoch would mean ~675 empty
monthly buckets.

**This diverges deliberately from `GET /api/budgets/progress`**, which takes the identical params:
there, `ALL` echoes the unchanged queried window (1970) and only the *proration anchor* moves,
because the spend query genuinely covers all history. Here the data window itself is capped and
re-anchored. A reader who assumes the two endpoints treat `ALL` the same way will be wrong — so it's
spelled out in both this spec and `docs/API.md`.

With no transactions at all, the response is empty and the section renders its empty state.

## Bucket granularity

Derived from the final window, never passed by the client. Because the window can't exceed a year,
the table is closed at both ends:

| Window length | Unit | Resulting buckets |
|---|---|---|
| ≤ 31 days | `DAY` | 1–31 |
| ≤ 26 weeks | `WEEK` | 5–26 |
| > 26 weeks (≤ 1 year) | `MONTH` | 6–13 |

**No step-down rule.** An earlier draft carried a "if fewer than 6 buckets, step down a unit" floor;
it was dropped because it is unreachable for every window ≥ 32 days (the minimum at each band is 5)
and *powerless* below that, since `DAY` has nothing to step down to. Don't reintroduce it. Short
windows are handled by the render thresholds instead, which is a view concern, not a bucketing one.

## Render thresholds

**Each chart decides independently whether it has enough data to be worth drawing**, and renders
nothing (not an empty axis, not a placeholder box) when it doesn't. A donut with one slice, or a
line through two points, actively misleads — it implies a distribution or a trend that the data
doesn't support.

| Chart | Renders only when |
|---|---|
| Spending by category | ≥ **3** categories with non-zero expense |
| Spending over time | ≥ **5** buckets **and** ≥ **3** non-empty buckets |
| Income vs. expense over time | the above, **and** ≥ **1** bucket with non-zero income |
| Category change vs. prior period | a prior period exists (below) **and** ≥ **3** categories non-zero in either window |

Rationale for the less obvious ones:

- **Income vs. expense needs actual income.** Without it the chart is just the spending-over-time
  chart with a permanently flat second series — visually busier and strictly less informative than
  the chart directly beside it. This is the threshold most likely to fire in real use, since many
  users record expenses diligently and income rarely (one salary row a month, or none if salary
  lands outside the app).
- **Movers needs a populated prior period.** If the prior window is empty, every category reads
  +100%, which is noise dressed as insight.
- **3 non-empty buckets, not 2.** Two points is a line segment; it has a direction but no shape, and
  a reader will over-read it.

Thresholds live in **one frontend module** (`components/dashboard/charts/thresholds.ts`) as named
constants, so they're tunable in one place once there's real usage to judge against. They are
deliberately *not* enforced in the backend: the endpoint always returns the full truthful
aggregate, and what's worth drawing is a presentation decision.

**If every chart falls below threshold**, the section renders a single empty state
("Not enough activity in this period to show trends yet.") rather than four separate empty boxes.
If some pass, only those render and the grid reflows around them.

### Expected consequence: the section fills in as the window widens

This is intended behaviour, not a bug to be reported later — **`range=WEEK` will frequently show
only one or two charts, or none at all.** Seven days is a small sample: it often contains fewer than
three distinct spend categories, and for anyone paid monthly it usually contains **zero** income
rows, which alone hides the income-vs-expense chart three weeks out of four.

Rough expectation for a typical active user:

| Range | Likely visible |
|---|---|
| `WEEK` | category donut sometimes; trend sometimes; income-vs-expense rarely; movers rarely |
| `MONTH` | all four, usually |
| `YEAR` / `ALL` | all four |

Treat the section as *progressive* — it earns its density as the window grows. The alternative,
window-aware thresholds (a lower bar for short windows), was considered and rejected: it makes the
thresholds harder to reason about and re-admits exactly the degenerate two-slice donuts they exist
to prevent. A uniform bar that a short window simply doesn't clear is the more honest design.

Browser verification must therefore include `WEEK` on a realistic dataset and confirm the section
degrades gracefully — not treat an empty WEEK view as a defect.

## Prior-period comparison (chart 4)

The comparison window is the immediately preceding window of equal length: for the final `[from,
to]`, `previousTo = from - 1 day` and `previousFrom = previousTo - (to - from)`. This needs a
second repository call over that earlier range.

**The prior period is omitted entirely when nothing precedes the window** — precisely, when `from`
is on or before the earliest transaction date. That covers `ALL` floored to the first transaction
(nothing can exist before it, by definition) and any custom `from` predating all data. In that case
`previousFrom`/`previousTo` are null, every `previousAmount` is null, and the movers chart hides
itself per its threshold above.

Note this is a *narrower* condition than "range is ALL". Since `ALL` is now capped to one year, a
user with two years of history who selects "All time" gets a real, populated prior year — and the
movers chart works. Only a user whose entire history fits inside the window loses it.

**Caveat between one and two years of history.** The prior window can partially predate the user's
first transaction — e.g. 18 months of history, `range=YEAR`: the window is the last 12 months, the
prior window is months 13–24, but only months 13–18 contain anything. Deltas are then computed
against an artificially low base, so most categories read as large increases. This is directionally
real (they *did* spend less back then, because they were not yet recording) but overstated. It is
not worth suppressing — the null condition would have to become "prior window fully covered by
history", which would hide the chart for a year longer than necessary — but the movers chart should
carry a short caption naming the comparison window (`vs. Aug 2024 – Jul 2025`) so the reader can
judge it.

### `ALL` and `YEAR` now usually behave identically

A direct consequence of the cap worth stating: once a user has more than a year of history, `ALL`
and `YEAR` resolve to the same window and produce byte-identical analytics responses. They diverge
only below a year, where the floor makes `ALL` start at the first transaction while `YEAR`… also
does, since the floor now applies to every named range. **So for the analytics section specifically,
the two buttons are equivalent.** They still differ for the balance cards and budgets above, so the
selector stays as is — but if this proves confusing in use, the cheap fix is a caption on the
section noting the one-year cap, not a second selector.

## API changes

### `GET /api/analytics`

| Param | Type | Default | Notes |
|---|---|---|---|
| `range` | `WEEK\|MONTH\|YEAR\|ALL` | — | `ALL` collapses to the one-year cap |
| `from` | ISO date | last month if no `range` | clamped so the window ≤ 1 year |
| `to` | ISO date | today | |

```jsonc
{
  "from": "2026-07-04",              // the FINAL window after cap + floor, never 1970-01-01
  "to": "2026-08-02",
  "previousFrom": "2026-06-04",      // null when nothing precedes the window
  "previousTo": "2026-07-03",        // null when nothing precedes the window
  "bucketUnit": "DAY",               // DAY | WEEK | MONTH
  "totalIncome": 3000.00,
  "totalExpense": 1284.55,
  "categories": [                    // EXPENSE only, desc by amount, zero-amount categories omitted
    { "category": "GROCERIES", "amount": 412.30, "previousAmount": 380.00 }
  ],
  "buckets": [                       // contiguous, gap-filled with zeros
    { "start": "2026-07-04", "income": 0.00, "expense": 42.10 }
  ]
}
```

`buckets` is **gap-filled**: every bucket in the window appears, including zero-activity ones, so
the frontend never has to reconstruct a continuous axis. `categories` is the opposite — categories
with no spend in the window are omitted, since a donut has no use for zero slices. A category
present in the prior period but not this one still appears (with `amount: 0`) so the movers chart
can show the drop.

## Backend changes

New `analytics` slice following the existing strict `controller/ → service/ → repository/` layering,
`dto/` records only, constructor injection, no `@Transactional`:

- `dto/AnalyticsResponse` — record, fields as above.
- `dto/CategoryTotal` — record: `category`, `amount`, `previousAmount`.
- `dto/TimeBucket` — record: `start`, `income`, `expense`.
- `dto/BucketUnit` — enum `DAY`, `WEEK`, `MONTH`, owning the window-length derivation as static
  logic on the enum (mirroring how `TimeRange.resolveFrom` owns its own date math).
- `service/AnalyticsService` — applies the cap and floor, then two calls to the existing unpaginated
  range finder (current window, prior window), filters to `INCOME`/`EXPENSE`, folds into the two
  aggregates.
- `controller/AnalyticsController` — `GET /api/analytics`, reuses `Period.resolve`, no
  controller-local `@ExceptionHandler` (mapping stays in `GlobalExceptionHandler`).

**No new repository methods, no migration.** Both queries it needs already exist. Read-only
endpoint, no schema change.

`TransactionRepository`'s unpaginated range finder gains a third aggregate consumer — its Javadoc
names the current two and should be updated to include `AnalyticsService`.

## Frontend changes

- `types/analytics.ts`, `api/analytics.ts` (single `get`), `hooks/useAnalytics.ts` (range →
  `Analytics`, same shape as the existing `useBalances`/`useBudgets` — plain `fetch` +
  `useState`/`useEffect`, no data-fetching library).
- `components/dashboard/AnalyticsSection.tsx` — section header + a responsive grid of whichever
  charts clear their thresholds, mirroring `BudgetSection`'s structure (loading / error-banner /
  empty-state handling). Slots into `DashboardPage` after `<BudgetSection range={range} />`, taking
  the same `range` prop.
- `components/dashboard/charts/` — `CategoryFlowChart.tsx`, `SpendingTrendChart.tsx`,
  `IncomeExpenseChart.tsx`, `CategoryMoversChart.tsx`, `thresholds.ts`, plus small shared helpers
  (axis ticks, currency-abbreviating label formatter, a `sankeyLinkPath` helper for the single-level
  flow diagram, and a shared `DivergingBar` primitive parameterized by orientation — used by both
  `IncomeExpenseChart` and `CategoryMoversChart`, see [Chart types](#chart-types)). These are the
  first `<svg>` in the codebase.
- Charts must be **responsive without JS measurement**: `viewBox` + `preserveAspectRatio` and a
  CSS-sized container, not a `useRef`/`ResizeObserver` width. The sole existing breakpoint is
  `@media (max-width: 800px)`; the grid collapses to one column there like `.budget-grid` does.
- **Accessibility**: each chart gets a `<title>` and `role="img"` with an `aria-label` summarizing
  the data, plus a visually-hidden data table fallback for the two most information-dense charts.
  Hover tooltips are nice-to-have; the label must not be the only way to read a value. Category
  labels are always rendered next to their flow — color is never the only channel carrying identity.

### Palette — iOS system colors

The existing tokens are already Apple-flavoured, and in two places reach for iOS system colors
outright: `--color-negative` in dark is `#ff453a` (systemRed dark) and `global.css` carries a
hardcoded `rgba(94,92,230,.14)` tint, i.e. `#5E5CE6` (systemIndigo dark). This spec makes that
explicit rather than ad hoc: **the series palette is Apple's iOS system colors, using their
documented light and dark variants.**

Only **one chart needs the categorical palette** — the flow diagram (one color per category
link/node, same role a donut's slice fill would have played). The other three map onto tokens that
already exist:

| Chart | Colors |
|---|---|
| Spending by category | the 8-color categorical palette below |
| Spending over time | single series → `--color-accent` |
| Income vs. expense | `--color-positive` (income) / `--color-negative` (expense) |
| Category movers | `--color-positive` / `--color-negative`, **direction inverted** — see below |

**Gotcha on the movers chart:** semantic direction runs opposite to the money. Spending *more* than
last period is the bad outcome, so a positive delta must render `--color-negative`. Getting this
backwards produces a chart that is confidently, cheerfully wrong.

New categorical tokens (`--chart-1` … `--chart-8`), ordered so adjacent donut slices land on
distant hues:

| Token | iOS name | Light | Dark |
|---|---|---|---|
| `--chart-1` | systemBlue | `#007AFF` | `#0A84FF` |
| `--chart-2` | systemOrange | `#FF9500` | `#FF9F0A` |
| `--chart-3` | systemPurple | `#AF52DE` | `#BF5AF2` |
| `--chart-4` | systemGreen | `#34C759` | `#30D158` |
| `--chart-5` | systemPink | `#FF2D55` | `#FF375F` |
| `--chart-6` | systemTeal | `#30B0C7` | `#40C8E0` |
| `--chart-7` | systemYellow | `#FFCC00` | `#FFD60A` |
| `--chart-8` | systemGray | `#8E8E93` | `#8E8E93` |

`--chart-8` (gray) is reserved for the aggregated **"Other"** node — the residual bucket reads
correctly as the muted one, and it means the top-7 real categories each get a distinct hue.
systemRed is deliberately absent: it's already `--color-negative`, and a red category slice
adjacent to a red "over budget" bar invites a false reading.

**Verify these hex pairs against Apple's current HIG colour reference before use.** The *choice* of
system colors and their ordering is the decision this spec is making; the literal values above are
written from recall and at least one is version-sensitive (systemTeal light is `#30B0C7` on iOS 15+
but `#5AC8FA` in older references). Treat the table as the intended palette, not as an authoritative
source of hex codes.

These must be added in **three** places, because dark mode is declared twice: `:root`,
`:root[data-theme="dark"]` (manual toggle via `useTheme`), and the
`@media (prefers-color-scheme: dark) { :root:not([data-theme]) }` fallback. Missing the third block
is the likely bug — it only shows up for a user who has never touched the theme toggle.

## Testing

- `AnalyticsServiceTest` (Mockito, fast tier) — the one-year cap on both `ALL` and an over-long
  explicit range; the earliest-transaction floor applying to **every named range** (`WEEK`/`MONTH`/
  `YEAR`/`ALL`) but *not* to explicit `from`/`to`; `ALL` and `YEAR` producing identical output when
  history exceeds a year; bucket-unit derivation at each boundary (31/32 days, 26/27 weeks); gap-filling
  produces contiguous buckets; `TRANSFER` and `ADJUSTMENT` excluded from both aggregates;
  category-less rows never reach the category array; prior-period window arithmetic; prior period
  nulled when `from` ≤ earliest transaction, and *populated* for `ALL` when ≥ 2 years of history
  exist; zero transactions returns empty rather than throwing; a category present only in the prior
  period appears with `amount: 0`.
- `AnalyticsControllerTest` (`@WebMvcTest`) — param resolution, the `range`-vs-`from`/`to`
  precedence, and the JSON field names (these are a frontend contract).
- `AnalyticsIT` (Testcontainers, `@SpringBootTest`) — seed a known transaction set and assert the
  full response end to end. **Include the reconciliation invariant**: hit `/api/analytics` and
  `/api/balances` over the same window and assert `totalExpense == netSpending`, and that
  `Σ categories[].amount == totalExpense`. Set
  `spring.rabbitmq.listener.simple.auto-startup=false` like the other backend ITs.
- Frontend has no test runner; `npm run build` (`tsc -b`) remains the only gate. Charts get
  browser-verified on the isolated test stack (`make up-test`, `localhost:9090`) across all four
  ranges, in both light and dark themes, and — importantly — **against a deliberately sparse
  dataset**, to confirm each threshold hides its chart rather than rendering a degenerate one.

## Docs

- `docs/API.md` — new "Analytics" section under Budgets, including the one-year cap and how `ALL`
  differs here from `/api/budgets/progress`.
- `docs/DATA_MODEL.md` — the aggregation semantics: what counts, the `netSpending` (not `spending`)
  reconciliation, window capping, bucket derivation, prior-period arithmetic.
- `docs/ARCHITECTURE.md` — add the new `dto`/`service`/`controller` members to the backend package
  listing; note the third aggregate consumer of the unpaginated range finder.

## Open questions

- **Seed data.** The charts are hard to evaluate against an empty or trivial dataset. `make
  up-test-data` exists (added for the pagination demos) — worth checking whether its generated
  transactions span enough months and categories to exercise all four charts, **and whether it
  generates any `INCOME` rows at all**, since the income-vs-expense chart hides itself without them.
  Not a blocker for implementation, but do it before browser verification rather than after.
- ~~**Chart type for "spending by category"**~~ — resolved: a single-level flow diagram (Sankey).
  See [Chart types](#chart-types). The categorical palette remains load-bearing (one color per
  category link/node, same role it would have played on a donut).
- **Threshold values are first guesses.** 3 categories / 5 buckets / 3 non-empty buckets / 1 income
  bucket are defensible but unvalidated. They're centralized in `thresholds.ts` specifically so they
  can be tuned after living with real data.

## Revision history

- 2026-08-02 — initial design. Chart set, hand-rolled SVG, income+expense scope, and
  shared-selector windowing confirmed with the user before writing.
- 2026-08-02 — spec review, three corrections: dropped the unreachable bucket step-down floor in
  favour of a short-window view state; pinned `ALL`'s `from` to the re-anchored earliest-transaction
  date and flagged the deliberate divergence from `/api/budgets/progress`; noted that the
  `Σ categories == totalExpense` invariant assumes API-created (non-null-category) data.
- 2026-08-02 — user refinements: hard one-year window cap with `ALL` collapsing to it (which also
  restores a usable prior period for `ALL`, so the null condition narrowed from "range is ALL" to
  "nothing precedes the window"); iOS system-color palette specified with light/dark pairs; per-chart
  minimum-data render thresholds added, replacing the earlier blanket short-window rule.
- 2026-08-02 — second spec review, four corrections: earliest-transaction floor widened from `ALL`
  only to every named range (`YEAR` on a short history had the identical empty-bucket problem);
  documented that `WEEK` will often show few or no charts and that this is intended, with a
  per-range expectation table, rather than leaving it to be found in a browser; flagged that a
  prior window partially predating the user's history inflates movers deltas, and required a
  comparison-window caption; noted `ALL` and `YEAR` are now equivalent for this section, and that
  the palette hex values are from recall and need verifying against Apple's current reference.
- 2026-08-08 — user locked chart 1 as a flow diagram (single-level Sankey), resolving the
  previously open donut-vs-bar question. Added an explicit [Chart types](#chart-types) table
  locking the other three charts too (line, and a shared diverging-bar primitive used vertically
  for income-vs-expense and horizontally for movers), since only chart 1 had been an open question
  but 2–4 had never been stated as explicit types either. Renamed the planned `CategoryDonut.tsx`
  component to `CategoryFlowChart.tsx` and swapped the `arcPath` helper for `sankeyLinkPath`.
- 2026-08-08 — backend implemented: `BucketUnit`, `AnalyticsResponse`/`CategoryTotal`/`TimeBucket`,
  `AnalyticsService`, `AnalyticsController`, plus `AnalyticsServiceTest`/`AnalyticsControllerTest`/
  `BucketUnitTest` (fast tier) and `AnalyticsIT` (Testcontainers, including the
  `totalExpense == netSpending` reconciliation invariant against `/api/balances`) — 128 backend
  tests green. One deviation from the spec text as written: `AnalyticsService` does not call
  `Period.resolve` or `LocalDate.now()` itself; `AnalyticsController` resolves the base window (as
  it already does for balances/budgets) and passes both the resolved window and the raw `from`
  param into the service, which needs the latter to distinguish "explicit dates" from "a named
  range" for the earliest-transaction floor. Also confirmed `make up-test-data`'s fixture already
  contains `INCOME` rows (10, across 5 categories) and spans enough months/categories to exercise
  all four charts for `MONTH`; `YEAR`/`ALL` will still hide the movers chart on that fixture alone,
  since it has under a year of history — expected per the render-thresholds section, not a fixture
  gap. Docs updated: `docs/API.md` (new Analytics section), `docs/DATA_MODEL.md` (new Analytics
  aggregation section), `docs/ARCHITECTURE.md` (backend package listing + shared period resolution
  note).
