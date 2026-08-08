import type { Analytics } from "../../../types/analytics";

/**
 * Minimum-data render thresholds — see
 * docs/superpowers/specs/2026-08-02-transaction-analytics-design.md#render-thresholds.
 *
 * Centralized here, deliberately not enforced backend-side: the endpoint always returns the full
 * truthful aggregate; what's worth drawing is a presentation decision. An uninsightful chart is
 * worse than no chart — it occupies prime dashboard space and implies a pattern the data doesn't
 * support. Tune these once there's real usage to judge against; they're first guesses.
 */
export const MIN_CATEGORIES_FOR_FLOW = 3;
export const MIN_BUCKETS_FOR_TREND = 5;
export const MIN_NON_EMPTY_BUCKETS_FOR_TREND = 3;
export const MIN_NON_ZERO_INCOME_BUCKETS = 1;
export const MIN_CATEGORIES_FOR_MOVERS = 3;

function nonEmptyBucketCount(analytics: Analytics): number {
  return analytics.buckets.filter((b) => b.income > 0 || b.expense > 0).length;
}

function nonZeroIncomeBucketCount(analytics: Analytics): number {
  return analytics.buckets.filter((b) => b.income > 0).length;
}

/** ≥3 categories with non-zero expense. */
export function canShowCategoryFlow(analytics: Analytics): boolean {
  return analytics.categories.filter((c) => c.amount > 0).length >= MIN_CATEGORIES_FOR_FLOW;
}

/** ≥5 buckets and ≥3 non-empty buckets. */
export function canShowTrend(analytics: Analytics): boolean {
  return (
    analytics.buckets.length >= MIN_BUCKETS_FOR_TREND &&
    nonEmptyBucketCount(analytics) >= MIN_NON_EMPTY_BUCKETS_FOR_TREND
  );
}

/** The trend threshold, plus ≥1 bucket with non-zero income. */
export function canShowIncomeExpense(analytics: Analytics): boolean {
  return canShowTrend(analytics) && nonZeroIncomeBucketCount(analytics) >= MIN_NON_ZERO_INCOME_BUCKETS;
}

/** A prior period exists, and ≥3 categories are non-zero in either window. */
export function canShowMovers(analytics: Analytics): boolean {
  if (analytics.previousFrom == null) {
    return false;
  }
  const nonZeroInEither = analytics.categories.filter(
    (c) => c.amount > 0 || (c.previousAmount ?? 0) > 0,
  ).length;
  return nonZeroInEither >= MIN_CATEGORIES_FOR_MOVERS;
}
