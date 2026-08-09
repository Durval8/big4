# Reports redesign — canvas charts, a sidebar shell, and a cash-flow view

**Status: Implemented 2026-08-09.** Backend (`incomeCategories` on `AnalyticsResponse`) and
frontend (sidebar `AppShell`, new `/reports` route, five reworked charts) both landed on
`feat/analytics-redesign`. Browser-verified against the full Docker test stack with seeded data.

Supersedes the presentation half of
[the transaction-analytics design](2026-08-02-transaction-analytics-design.md); that document's
backend semantics remain authoritative and unchanged.

## Why

Three complaints drove this, in the order they were raised:

1. **"Don't use SVG as the quality is very poor."** The original charts were hand-authored SVG
   scaled through a `viewBox`. Vector geometry scales cleanly in principle, but at the sizes these
   charts actually render — 1px gridlines, 11px axis text — the result looked soft and unevenly
   weighted against the crisp DOM text beside it.
2. **The flow chart only showed spending.** "It is not just for spending, it is overall
   transactions." A diagram titled *cash flow* that starts at "total spending" answers half the
   question.
3. **Labels overlapped**, particularly on thin Sankey slices.

The visual target was Monarch's Reports surface: a persistent left rail, a row of headline figures
above tabbed analysis, and charts that carry their numbers inline rather than deferring to a
tooltip.

## Locked decisions

### Canvas, not SVG — and still no charting library

Every chart is drawn imperatively on `<canvas>` through one shared hook. The decisive property is
that the backing store is sized to `width × devicePixelRatio` and the context scaled to match, so
strokes and text rasterize at the display's real density. That is the actual fix for complaint 1;
"SVG is a vector format" is true and beside the point when a 1px stroke lands across two physical
pixel rows.

No library was added. This is consistent with the project's existing zero-dependency stance for
charts, and it avoids taking a dependency for a Sankey — the one chart here that no mainstream
chart library ships in core anyway.

The cost is real and worth stating: canvas has no DOM, so nothing is selectable, inspectable, or
accessible for free. Every chart therefore carries `role="img"` with a data-bearing `aria-label`,
plus a `.visually-hidden` table of the underlying numbers. **A new canvas chart without both is a
regression**, not a stylistic choice.

### Three things the shared hook owns

`useCanvasChart(draw, deps)` exists because each of these was independently easy to get wrong:

- **Hi-DPI**: as above.
- **Resize**: a `ResizeObserver` on the container, *not* a `window` resize listener. In a SPA the
  card reflows on tab switches, sidebar layout and data-driven height changes with the window never
  changing size.
- **Theme**: canvas resolves colors once, at draw time. A theme flip must force a redraw.

That last point has a trap that cost a debugging cycle and is documented in the hook: the redraw is
driven by a `MutationObserver` on `<html data-theme>`, **not** by consuming `useTheme()`. `useTheme`
holds per-component `useState`, so a second caller never learns about `AppShell`'s toggle — the
charts kept their old palette across a theme change and looked, at a glance, like they had simply
rendered correctly. Verified by hashing canvas pixels across a light↔dark↔light round trip.

A second failure in the same area: an undefined CSS custom property resolves to `""`, and
`addColorStop("")` **throws**, which unmounted the entire React tree over one wrong token name
(`--color-text-tertiary`, which the mockup had but `tokens.css` did not). The token was added to all
three theme blocks, *and* the hook's resolver now falls back rather than propagating the throw —
belt and braces, because the failure mode is catastrophically out of proportion to the mistake.

### Chart types

| # | Chart | Type | Why |
|---|---|---|---|
| 1 | Cash flow | **Three-column Sankey**: income sources → one "Income" node → spending categories + savings | Answers complaint 2. Thickness encoding survives a skewed distribution where a donut's angle doesn't, and the source→destination framing is what "cash flow" means. |
| 2 | Spending by category | **Donut + legend carrying amounts and percentages** | The ring answers "is one category dominating"; the legend answers "how much, exactly". Amounts live in the legend precisely because slice labels collide on thin slices. |
| 3 | Spending over time | **Smoothed area** with an emphasized endpoint | Unchanged in intent from the line it replaces; the fill gives the series weight against the gridlines and the endpoint marker answers "where does it stand now". |
| 4 | Income vs. expenses | **Grouped bars from a shared baseline**, with each period's savings rate above the pair | Replaces mirrored diverging bars. The question asked here is "which is taller", and that comparison is only reliable when both bars start from the same line — mirroring around zero makes it a two-step judgement. |

Two rules keep the bars compact, both learned from the first version reading as sparse. **The pair
is the unit**: the two bars sit 3px apart and the whitespace goes *between* groups — with a wide
gap inside the pair, five buckets read as ten unrelated bars rather than five comparisons. And **a
group never exceeds `MAX_GROUP_W` (108px)**: spreading four monthly buckets across the full width
left each pair marooned in ~150px of nothing, so past the cap the groups keep their size and the
whole run centres instead. Gridlines still span the full plot, since they're the scale.
| 5 | Biggest movers | **DOM rows**, not canvas | The content is six labelled numbers. The browser lays that out better than canvas can, and it gets text selection and screen-reader access for free. Reaching for canvas here would be consistency for its own sake. |

The movers row leads with the **change**, not the balance. Leading with the balance made the
biggest drop on the page render as "Groceries $0.00", which reads as an empty category rather than
as the headline it is; the current amount moved to a subordinate line ("now $90.00", or "nothing
this period" when it went to zero).

### Income vs. expenses is detached from the page's time range

Every other chart on Reports answers a question *about the selected window*. This one doesn't: it
asks whether a normal period covers itself, and the useful answer is the last N periods at a
granularity the reader chooses — not whatever the rest of the page happens to be showing. Bound to
the shared range, its bar count lurched between 7 and 31 for reasons unrelated to the comparison,
and "last week" reduced it to a single pair, which answers nothing.

So `IncomeExpenseCard` owns a **Daily / Weekly / Monthly** switch, fetches its own window through
`useIncomeExpenseSeries`, and ignores the range selector entirely. Consequences worth knowing:

- **It issues a second `/api/analytics` request.** Accepted deliberately — the alternative is
  deriving it from the shared response, which is exactly the coupling being removed.
- **Its totals will disagree with the KPI row, by design.** With the page on "Last week" the KPIs
  can read $0.00 while this card reports twelve weeks of activity. The card's own summary line
  states the totals *over its plotted periods* and names the period count, so the two numbers are
  never presented as the same claim.

**The granularity picks the window, and the window picks the bucket size.** `WINDOW_DAYS`
(31 / 182 / 364) is chosen to land inside the backend's existing `BucketUnit.forWindow` thresholds
(≤31 days → `DAY`, ≤26 weeks → `WEEK`, else `MONTH`), so asking for the right window is enough —
no new API parameter. `regroupBuckets` still runs on the response as a safety net: if those
thresholds ever move, the chart re-aggregates to what the control claims rather than silently
plotting something finer.

**The axis is trimmed to real activity.** `trimToRecentActivity` drops leading and trailing empty
buckets, then keeps the most recent N (14 / 12 / 12). Leading empties waste the axis on time that
predates the data; trailing empties end the chart on flat nothing, which reads as broken rather
than as "nothing has happened yet". **Interior** empties are always kept — a quiet week between two
active ones is real, and dropping it would compress the time axis into a lie.

`regroupBuckets` merges but never splits, and returns its input untouched when asked for something
finer. Month grouping keys off the ISO string's `YYYY-MM` prefix rather than
`new Date(iso).getMonth()`: a date-only string parses as UTC midnight, which reports the previous
month for anyone west of Greenwich and would put a transaction in the wrong bar. `isoDate` formats
in local time for the same reason.

The spending trend keeps the API's own buckets. It's an area chart, where 30 daily points read as
a *shape* that coarsening to four weekly points would throw away — the opposite of what the paired
bars need.

### The x-axis ticks at the chosen granularity, not a fixed count

Choosing "Weekly" means the bars are one per week; the ticks should be too, rather than the six or
so evenly-index-spaced labels the trend chart uses. `pickIndicesAtGranularity` labels every bucket
by default and only thins once labels would actually collide — measured against that unit's own
label text (a month abbreviation like "Jan" is far narrower than a day-and-date one like "Jul 19"),
not a guess.

When it does thin, it walks a **regular stride** (every 2nd, every 3rd) anchored from the **last**
index backward, not the first forward. That direction matters and was the one bug here: anchoring
forward and then force-appending the last index to keep the axis current can place that forced
label closer to its neighbor than the stride allows, since `count - 1` isn't guaranteed to land on
the stride — the two overlap outright. Caught on a 375px viewport (14 daily buckets, ~17px each):
the forward version produced overlapping ink; walking backward instead gave five labels at a
uniform 52px, still ending on the latest period. The trade is the *first* bucket sometimes goes
unlabelled instead — acceptable here, since `trimToRecentActivity` already anchors this chart on
the newest data.

At this page's actual desktop width (~977px), no thinning triggers at all: all 14/12/8 buckets get
their own label at Daily/Weekly/Monthly.

### The Sankey balances in both directions

Income and spending rarely match, and the difference has to go somewhere or the two sides of the
diagram won't sum to the same total — at which point the ribbon thicknesses are quietly lying.

- Income > spending → the surplus leaves as a **"Savings"** target.
- Spending > income → the shortfall enters as a **"From savings"** source.

Both sides then total `max(totalIncome, totalExpense)`, and every percentage is against that one
baseline. The deficit case is not hypothetical: a one-week window containing rent and no paycheck
produces it.

### The Sankey's columns are measured, not proportional

Column positions come from `ctx.measureText` on the actual labels, not from fixed fractions of the
canvas width. Percentages meant a long name ("Investment Income", "$5,586.02 (88.5%)") either
overflowed or squeezed the ribbons, depending on card width; measuring the widest label per side
and sizing the gutter to it fits every name at any width and hands the remainder to the ribbons.
Each gutter is capped at a share of the canvas so a pathological label truncates against the edge
rather than collapsing the diagram.

The gutter must cover **both** the node-to-text gap *and* a trailing margin. Sizing it to
`widest + LABEL_PAD` alone lands the far end of the text exactly on the canvas boundary and clips
the last character or two — caught by scanning the canvas's edge pixel columns for ink, not by
eye.

To give the labels more room to begin with, the sidebar narrowed to 208px, `app-main`'s max width
grew to 1440px, and chart cards use a `.card--chart` modifier with tighter horizontal padding.
That modifier **must stay defined after `.card`** in `global.css`: same specificity, so source
order is the only thing deciding it, and its first home up in the shell rules silently lost.

### Every Sankey node has a minimum height — a deliberate, bounded distortion

Real spending is heavily skewed. An 88%-savings month leaves ~12% of the plot for six categories,
so those nodes render as hairlines clustered in a thin band, every label gets displaced onto a
leader line, and no amount of extra canvas height fixes it: the band scales with everything else.
Placing six labels 40px apart inside a 12% band would need a canvas roughly 1700px tall.

So each node reserves `MIN_NODE_HEIGHT` (24px) and only the *remainder* is split proportionally.
This is a real distortion of the outer node heights and worth naming as such — but the ribbons
still **leave the aggregate node at strictly proportional widths**, which is where the eye actually
compares them, and each one tapers to meet a node large enough to carry a label. The floor is
dropped entirely if it would consume more than half the plot, since past that many nodes nothing is
readable either way and proportionality is the more valuable property.

Measured on the seeded month: worst-case label displacement fell from **95px to 5px**, and labels
sitting exactly on their node went from 1/6 to 3/6 with the rest within a line height. Canvas height
is `max(480, labels × 58)`.

### Label collision is a height problem, not a nudging problem

Labels are decluttered by a two-pass sweep (forward pass enforces a minimum gap; backward pass pulls
the run back inside bounds), and a leader line is drawn only for labels that actually moved — so the
common case stays clean and a displaced label is still attributable.

But nudging cannot create space that isn't there. Once `minGap × labelCount` exceeds the plot
height, no ordering satisfies the constraint. So **the Sankey's canvas height is derived from its
label count**, not fixed. `declutterPositions` detects the impossible case and degrades to even
spacing rather than emitting a scrambled order.

### `incomeCategories` is a separate list

The one backend change. `categories` stays EXPENSE-only; `incomeCategories` is the INCOME twin,
produced by the same `buildCategoryTotals` pass with the transaction type as a parameter.

Merging them behind a type discriminator was rejected: every existing consumer of `categories`
reads it as "spending", so a merged list would render salary as an expense in the spending
breakdown and as a mover in the movers list. The cash-flow diagram is the only view wanting both,
and it puts them on opposite sides of the flow — so it gains nothing from a single list either.

### Sidebar, and where the charts live

`AppShell` moved from a top nav to a persistent left rail (Dashboard / Transactions / Reports /
Investments), collapsing to a horizontal strip under the existing 800px breakpoint.

Charts moved from `components/dashboard/charts/` to `components/charts/`, since they are now shared
by two routes. The dashboard's `AnalyticsSection` was cut back to the two questions worth answering
without leaving the dashboard — what was spent on, and what changed — and links to `/reports` for
the rest rather than duplicating it.

## Deliberately not in scope

- **Realized gains.** Proposed during design and cut. Realized gain is proceeds − cost basis of
  shares sold; cost basis lives in Mongo, and per `CLAUDE.md` the backend can never query it. Doing
  this properly means a new message contract on both sides, an `InvestmentMessageContractTest`
  fixture update, a Flyway projection table and a consumer — its own epic, not a card on a redesign.
- **The Accounts screenshot** (net-worth area chart, account groups, asset/liability summary bars).
  Different data sources (`/api/balances` + investments), separate lift.
- **Category groups.** Monarch's Sankey has a middle taxonomy level (Housing → Mortgage + Home
  Improvement). `Category` here is a flat enum; adding a parent level is a data-model change, not a
  chart change.

## Verification

- `mvn -s ~/.m2/personal-settings.xml verify` — both modules.
- `npm run build` (`tsc -b && vite build`) — the frontend's only static gate; there is no test
  runner or linter.
- Full Docker test stack (`make build-test && make up-test`, app on `:9090`) with seeded data.
  Screenshots are unavailable in this environment, so verification used the accessibility tree plus
  `getBoundingClientRect`/`getComputedStyle`/`getImageData` probes: canvases painted (non-zero
  alpha), correct real figures in the KPI row and aria-labels, both tabs rendering, no console
  errors, no horizontal body overflow, and the light↔dark pixel-hash round trip described above.
- **The deficit branch was verified explicitly, not assumed.** The seeded fixture is net-positive in
  every window the range selector can reach, so the "From savings" source node and the
  `flowTotal = totalExpense` path would otherwise have shipped as dead code — the one branch the
  design calls out as "not hypothetical." Exercised by pointing the analytics fetch at
  `from=2026-07-25&to=2026-07-31` (income $411.00, spending $604.98) and confirming both sides sum
  to $604.98: sources `Gifts $200 + Investment Income $151 + Other Income $60 + From savings
  $193.98`, destinations `Healthcare $400 + Entertainment $179.98 + Housing $25`, with no "Savings"
  target present. **Any future change to the balancing logic needs this window re-run** — the
  default fixture cannot catch a regression in it.

## Revision history

- 2026-08-09 (fifth pass) — x-axis now ticks at the chosen granularity (`pickIndicesAtGranularity`
  in `canvasUtils`), replacing the trend chart's fixed-count `pickAxisLabelIndices` for this chart
  only. One bug caught and fixed before it shipped: the initial version anchored the stride forward
  and force-appended the last index, which could overlap that label with its neighbor since
  `count - 1` isn't guaranteed to land on the stride — reproduced at 375px (14 daily buckets) and
  fixed by anchoring the stride from the last index backward instead. Verified at both the mobile
  width that triggers thinning (uniform 52px gaps, no overlap, latest period still labelled) and
  this page's actual desktop width (no thinning at all — all 14/12/8 buckets individually
  labelled at Daily/Weekly/Monthly).

- 2026-08-09 (fourth pass) — income vs. expenses detached from the shared time range. New
  `IncomeExpenseCard` with a Daily/Weekly/Monthly switch, `useIncomeExpenseSeries` (own window, own
  fetch), and `trimToRecentActivity`; `comparisonBucketUnit` deleted, since granularity is now a
  user choice rather than a function of the page range. Verified: each granularity plots the
  expected periods (14 daily ending on the last active day, 12 weekly, 8 monthly — 8 rather than 12
  because the fixture's history is shorter, with the leading empties trimmed), and the card is
  byte-identical across all four page ranges including "Last week", where the KPI row reads $0.00
  and the card still shows its own twelve weeks.

- 2026-08-09 (third pass) — Sankey lengthened (`max(480, labels × 58)`) and given a 24px minimum
  node height, cutting worst-case label displacement from 95px to 5px; without the floor the extra
  height alone changed nothing, since the skew scales with it. Comparison bars tightened: 3px inside
  the pair, 38px wide, group width capped at 108px with the run centred beyond that — verified from
  rendered pixels (axis labels exactly 108px apart on the year view). Bar-chart height 300 → 260.
  Note for future probes: this browser reports `devicePixelRatio` 1.25, so canvas backing-store
  coordinates are *not* CSS pixels — an earlier measurement pass silently found nothing because it
  mixed the two.
- 2026-08-09 (second pass) — Sankey column layout switched from fixed fractions to measured label
  widths; sidebar narrowed to 208px, `app-main` max width raised to 1440px, `.card--chart` added.
  Income-vs-expenses regrouped per range via the new `bucketUtils` (day / week / month), with
  unit-aware axis labels and captions. Two bugs caught by probing rather than by eye: `.card--chart`
  losing to `.card` on source order, and the target-side gutter clipping labels against the canvas
  edge (`rightSlack: 0`). Verified across all four ranges — `WEEK` → 7 daily bars, `MONTH` → 5
  weekly, `YEAR`/`ALL` → 4 monthly — with 10–13px of edge slack on the Sankey in every case.

- 2026-08-09 — written and implemented in one pass on `feat/analytics-redesign`. Backend:
  `incomeCategories` added to `AnalyticsResponse`, `expenseByCategory` generalized to
  `byCategory(transactions, type)`, two new `AnalyticsServiceTest` cases asserting income and
  expense never appear in the same list, `AnalyticsControllerTest` extended. Frontend:
  `components/charts/` (`useCanvasChart`, `canvasUtils`, `CashFlowSankey`, `SpendingDonut`,
  `SpendingTrendChart`, `IncomeExpenseChart`, `CategoryMoversChart`, `thresholds`), `ReportsPage`,
  `components/reports/KpiCard`, sidebar `AppShell`; the five SVG chart files and `chartUtils.ts`
  deleted. `--color-text-tertiary` added to all three theme blocks in `tokens.css`. Two bugs found
  and fixed during browser verification, both documented above: the missing token crashing the tree
  via `addColorStop`, and `useTheme`'s per-component state preventing canvas theme redraws.
