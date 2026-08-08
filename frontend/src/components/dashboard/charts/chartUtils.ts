import { formatEnumLabel } from "../../../lib/format";
import type { CategoryTotal } from "../../../types/analytics";

/** The 8-color iOS-system categorical palette tokens — see tokens.css. --chart-8 is reserved for "Other". */
export const CHART_COLOR_VARS = [
  "--chart-1",
  "--chart-2",
  "--chart-3",
  "--chart-4",
  "--chart-5",
  "--chart-6",
  "--chart-7",
  "--chart-8",
] as const;

const MAX_DISTINCT_CATEGORIES = 7; // + "Other" as the 8th slot

export interface CategorySlice {
  label: string;
  amount: number;
  colorVar: string;
}

/**
 * Collapses the backend's full category list (up to 17 rows) to the top 7 by amount + an "Other"
 * slice summing the rest — the API returns everything, but a 17-slice chart is unreadable, so this
 * cut belongs in the view (see the design spec's "Locked decisions").
 */
export function topCategoriesWithOther(categories: CategoryTotal[]): CategorySlice[] {
  const nonZero = categories.filter((c) => c.amount > 0); // already desc-sorted by the API
  const top = nonZero.slice(0, MAX_DISTINCT_CATEGORIES);
  const rest = nonZero.slice(MAX_DISTINCT_CATEGORIES);

  const slices: CategorySlice[] = top.map((c, i) => ({
    label: formatEnumLabel(c.category),
    amount: c.amount,
    colorVar: CHART_COLOR_VARS[i],
  }));

  if (rest.length > 0) {
    const otherAmount = rest.reduce((sum, c) => sum + c.amount, 0);
    slices.push({ label: "Other", amount: otherAmount, colorVar: CHART_COLOR_VARS[7] });
  }

  return slices;
}

/** Abbreviates a currency amount for axis ticks: "$1.2k", "$450", "$3.4M". */
export function formatAxisCurrency(amount: number): string {
  const abs = Math.abs(amount);
  const sign = amount < 0 ? "-" : "";
  if (abs >= 1_000_000) {
    return `${sign}$${(abs / 1_000_000).toFixed(1)}M`;
  }
  if (abs >= 1_000) {
    return `${sign}$${(abs / 1_000).toFixed(1)}k`;
  }
  return `${sign}$${Math.round(abs)}`;
}

/**
 * Picks a sparse, evenly-spaced subset of indices (always including the first and last) to label
 * on a time axis with up to `maxLabels` — avoids crowding when there are up to 31 buckets.
 */
export function pickAxisLabelIndices(count: number, maxLabels = 6): number[] {
  if (count <= maxLabels) {
    return Array.from({ length: count }, (_, i) => i);
  }
  const step = (count - 1) / (maxLabels - 1);
  const indices = new Set<number>();
  for (let i = 0; i < maxLabels; i++) {
    indices.add(Math.round(i * step));
  }
  return Array.from(indices).sort((a, b) => a - b);
}

/** Short date label for a bucket start, e.g. "Jul 4". */
export function formatBucketLabel(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

/**
 * A single flow-diagram ribbon: a closed path from a source slice [x1, y1Top..y1Bottom] to a
 * target slice [x2, y2Top..y2Bottom], curved via cubic beziers. Shared by CategoryFlowChart only
 * (the other three charts share the diverging-bar helpers below) — kept here alongside the other
 * chart-geometry helpers.
 */
export function sankeyLinkPath(
  x1: number,
  y1Top: number,
  y1Bottom: number,
  x2: number,
  y2Top: number,
  y2Bottom: number,
): string {
  const midX = (x1 + x2) / 2;
  return [
    `M ${x1},${y1Top}`,
    `C ${midX},${y1Top} ${midX},${y2Top} ${x2},${y2Top}`,
    `L ${x2},${y2Bottom}`,
    `C ${midX},${y2Bottom} ${midX},${y1Bottom} ${x1},${y1Bottom}`,
    "Z",
  ].join(" ");
}

/**
 * Shared diverging-bar sizing: given a value and the max absolute value in the series, returns
 * the bar's length in SVG user units for a plot of `plotExtent` units. Used vertically (income
 * up / expense down from a shared zero axis) by IncomeExpenseChart and horizontally (increase
 * right / decrease left from a zero centerline) by CategoryMoversChart — the two charts differ in
 * axis orientation and in whether they plot two independent series or one signed delta, so this
 * helper covers the one thing they actually share: turning a magnitude into a bar length against
 * a common scale, never negative.
 */
export function divergingBarLength(value: number, maxAbsValue: number, plotExtent: number): number {
  if (maxAbsValue <= 0) {
    return 0;
  }
  return (Math.abs(value) / maxAbsValue) * plotExtent;
}
