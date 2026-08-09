import type { BucketUnit, TimeBucket } from "../../types/analytics";

/** Coarseness order, so a regroup can refuse to "split" a bucket it can only merge. */
const GRANULARITY: Record<BucketUnit, number> = { DAY: 0, WEEK: 1, MONTH: 2 };

const DAYS_PER_WEEK = 7;

/**
 * How far back to ask for, per granularity. These are chosen to land inside the backend's own
 * `BucketUnit.forWindow` thresholds (≤31 days → DAY, ≤26 weeks → WEEK, else MONTH), so asking for
 * the right window is enough to get the right bucket size back — no new API parameter needed.
 *
 * `regroupBuckets` still runs on the result as a safety net: if those thresholds ever move, the
 * chart quietly re-aggregates to the granularity the user picked instead of silently showing
 * something finer than the label on the control claims.
 */
export const WINDOW_DAYS: Record<BucketUnit, number> = { DAY: 31, WEEK: 182, MONTH: 364 };

/** How many periods to plot once the series is trimmed to real activity. */
export const TARGET_PERIODS: Record<BucketUnit, number> = { DAY: 14, WEEK: 12, MONTH: 12 };

export const GRANULARITY_LABEL: Record<BucketUnit, string> = {
  DAY: "Daily",
  WEEK: "Weekly",
  MONTH: "Monthly",
};

/** Local-time `YYYY-MM-DD`. Not `toISOString`, which converts to UTC and can shift the date. */
export function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

/**
 * Trims leading and trailing buckets with no activity, then keeps the most recent `limit`.
 *
 * Both ends matter, for different reasons. Leading empties are the gap between the requested
 * window and the first transaction — showing them wastes the axis on a period that predates the
 * data. Trailing empties are the gap between the last transaction and today; keeping them ends the
 * chart on a run of flat nothing, which reads as "the data stops working" rather than "nothing has
 * happened yet". Trimming both makes the axis show the latest activity, which is the point.
 *
 * Interior empty buckets are always kept — a quiet week *between* two active ones is real
 * information, and dropping it would compress the time axis into a lie.
 */
export function trimToRecentActivity(buckets: TimeBucket[], limit: number): TimeBucket[] {
  const active = (b: TimeBucket) => b.income > 0 || b.expense > 0;
  const first = buckets.findIndex(active);
  if (first === -1) {
    return [];
  }
  let last = buckets.length - 1;
  while (last > first && !active(buckets[last])) {
    last--;
  }
  return buckets.slice(first, last + 1).slice(-limit);
}

/**
 * Merges gap-filled buckets up to a coarser unit, summing income and expense. Returns the input
 * untouched when the target is the same or finer than what the API sent — buckets can be combined
 * but never subdivided, and silently returning something finer than requested would be a lie.
 *
 * Relies on the series being contiguous and gap-filled (which the API guarantees): weekly groups
 * are chunks of seven consecutive buckets anchored at the window start, matching the backend's own
 * "WEEK buckets anchor at `from`" convention rather than snapping to calendar weeks. Monthly groups
 * use the calendar month, matching the backend there too.
 */
export function regroupBuckets(
  buckets: TimeBucket[],
  source: BucketUnit,
  target: BucketUnit,
): TimeBucket[] {
  if (buckets.length === 0 || GRANULARITY[target] <= GRANULARITY[source]) {
    return buckets;
  }

  const groups: TimeBucket[][] =
    target === "WEEK" ? chunk(buckets, DAYS_PER_WEEK) : groupByCalendarMonth(buckets);

  return groups.map((group) => ({
    start: group[0].start,
    income: group.reduce((sum, b) => sum + b.income, 0),
    expense: group.reduce((sum, b) => sum + b.expense, 0),
  }));
}

function chunk(buckets: TimeBucket[], size: number): TimeBucket[][] {
  const out: TimeBucket[][] = [];
  for (let i = 0; i < buckets.length; i += size) {
    out.push(buckets.slice(i, i + size));
  }
  return out;
}

/**
 * Groups by the `YYYY-MM` prefix of the ISO date string rather than via `new Date`, which parses a
 * date-only string as UTC midnight and can therefore report the previous month in a negative-offset
 * timezone — putting a transaction in the wrong bar for anyone west of Greenwich.
 */
function groupByCalendarMonth(buckets: TimeBucket[]): TimeBucket[][] {
  const out: TimeBucket[][] = [];
  let currentKey = "";
  buckets.forEach((b) => {
    const key = b.start.slice(0, 7);
    if (key !== currentKey) {
      out.push([]);
      currentKey = key;
    }
    out[out.length - 1].push(b);
  });
  return out;
}
