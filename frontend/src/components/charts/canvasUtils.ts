import { formatEnumLabel } from "../../lib/format";
import type { BucketUnit, CategoryTotal } from "../../types/analytics";

/** The categorical palette tokens — see tokens.css. --chart-8 is reserved for "Other". */
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
 * Collapses the backend's full category list (up to 17 rows) to the top N by amount + an "Other"
 * slice summing the rest. The API returns everything; a 17-slice chart is unreadable, so the cut
 * belongs in the view.
 *
 * `limit` is a parameter because the two charts that use this have different capacity: the donut
 * reads fine at 8 slices, but the Sankey also has to fit a stacked, non-overlapping *label* per
 * slice, so it asks for fewer.
 */
export function topCategoriesWithOther(
  categories: CategoryTotal[],
  limit: number = MAX_DISTINCT_CATEGORIES,
): CategorySlice[] {
  const nonZero = categories.filter((c) => c.amount > 0); // already desc-sorted by the API
  const top = nonZero.slice(0, limit);
  const rest = nonZero.slice(limit);

  const slices: CategorySlice[] = top.map((c, i) => ({
    label: formatEnumLabel(c.category),
    amount: c.amount,
    colorVar: CHART_COLOR_VARS[i % CHART_COLOR_VARS.length],
  }));

  if (rest.length > 0) {
    slices.push({
      label: "Everything else",
      amount: rest.reduce((sum, c) => sum + c.amount, 0),
      colorVar: CHART_COLOR_VARS[7],
    });
  }

  return slices;
}

/**
 * Applies an alpha to a resolved CSS color. Handles the `#rrggbb` and `#rgb` forms the theme
 * tokens actually use; anything else (a named color, an already-rgb() value) is returned as-is
 * rather than mangled, which degrades to a fully opaque fill instead of an invalid one.
 */
export function withAlpha(color: string, alpha: number): string {
  const hex = color.trim();
  if (!hex.startsWith("#")) {
    return hex;
  }
  const body = hex.slice(1);
  const full =
    body.length === 3
      ? body
          .split("")
          .map((ch) => ch + ch)
          .join("")
      : body;
  if (full.length !== 6) {
    return hex;
  }
  const r = parseInt(full.slice(0, 2), 16);
  const g = parseInt(full.slice(2, 4), 16);
  const b = parseInt(full.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/** Abbreviates a currency amount for axis ticks: "€1.2k", "€450", "€3.4M", "€0.20" below €10. */
export function formatAxisCurrency(amount: number): string {
  const abs = Math.abs(amount);
  const sign = amount < 0 ? "-" : "";
  if (abs >= 1_000_000) {
    return `${sign}€${(abs / 1_000_000).toFixed(1)}M`;
  }
  if (abs >= 1_000) {
    return `${sign}€${(abs / 1_000).toFixed(1)}k`;
  }
  if (abs < 10 && abs !== Math.round(abs)) {
    return `${sign}€${abs.toFixed(2)}`;
  }
  return `${sign}€${Math.round(abs)}`;
}

/**
 * Short label for a bucket start: "Jul 4" for a day or a week (a week is named by the day it
 * starts on), "Jul" alone for a month — where a day number would imply the bar covers just that
 * date rather than the whole month.
 */
export function formatBucketLabel(iso: string, unit: BucketUnit = "DAY"): string {
  const date = new Date(iso);
  if (unit === "MONTH") {
    return date.toLocaleDateString(undefined, { month: "short" });
  }
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

/**
 * Picks a sparse, evenly-spaced subset of indices (always including the first and last) to label
 * on a time axis — avoids crowding when there are up to 31 buckets.
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

/**
 * Labels every index by default — a chart whose x-axis *is* the chosen granularity (one bar per
 * week, one label per week) should tick at that same rate, not at some visually-convenient
 * subset. Only thins when the labels would actually collide: given the pixel width each index
 * gets and the widest label's measured width, it computes the largest stride that keeps
 * consecutive labels from overlapping, then walks the axis at that stride — always including the
 * last index, so the axis still ends on a labelled point rather than mid-stride.
 *
 * A regular stride (every 2nd, every 3rd) rather than a fixed label *count* matters here: it keeps
 * the ticks periodic in the chosen unit ("every other week") instead of collapsing to an unrelated
 * handful spread evenly by index, which is what a fixed max-label-count would do.
 *
 * The stride walks backward from the last index, not forward from the first. Anchoring forward and
 * then force-adding the last index (to keep the axis ending on the latest period) can place that
 * forced label closer to its neighbor than the stride allows — the two literally overlap — because
 * "count - 1" isn't guaranteed to fall on the stride. Walking from the end guarantees the last
 * period is always labelled *and* every gap is a full stride, at the cost of the first index
 * sometimes going unlabelled instead. That trade fits this page anyway: the recent-activity
 * trimming already anchors these charts on the newest data, not the oldest.
 */
export function pickIndicesAtGranularity(
  count: number,
  widthPerIndex: number,
  labelWidthPx: number,
  horizontalPad = 12,
): number[] {
  if (count === 0) {
    return [];
  }
  const stride = Math.max(1, Math.ceil((labelWidthPx + horizontalPad) / Math.max(widthPerIndex, 1)));
  const indices: number[] = [];
  for (let i = count - 1; i >= 0; i -= stride) {
    indices.push(i);
  }
  return indices.reverse();
}

/**
 * "Nice" axis ticks: searches increasing 1/2/5×10ⁿ step sizes for the smallest one that keeps the
 * resulting tick count close to `targetTickCount`, then returns evenly-spaced values from 0 up to
 * that step's ceiling of `maxValue`.
 *
 * Deriving the step from `maxValue / targetTickCount` and rounding *that* up to the nearest
 * 1/2/5 overshoots badly whenever the raw step lands just above a rounding boundary — e.g.
 * maxValue=425, targetTickCount=4 gives a raw step of 106.25, which rounds up to 200, nearly
 * doubling the axis ceiling to 800 for data that peaks at 425. Searching for the step against the
 * tick *count* it produces keeps the ceiling tight to the data.
 */
export function niceAxisTicks(maxValue: number, targetTickCount = 4): number[] {
  if (maxValue <= 0) {
    return Array.from({ length: targetTickCount + 1 }, (_, i) => i);
  }
  const minCount = Math.max(targetTickCount - 1, 2);
  const maxCount = targetTickCount + 2;
  const startMagnitude = Math.pow(10, Math.floor(Math.log10(maxValue / maxCount)));

  for (let magnitude = startMagnitude; magnitude <= maxValue * 2; magnitude *= 10) {
    for (const mantissa of [1, 2, 5]) {
      const step = mantissa * magnitude;
      const count = Math.ceil(maxValue / step);
      if (count >= minCount && count <= maxCount) {
        return Array.from({ length: count + 1 }, (_, i) => i * step);
      }
    }
  }
  const step = Math.pow(10, Math.ceil(Math.log10(maxValue / targetTickCount)));
  const count = Math.ceil(maxValue / step);
  return Array.from({ length: count + 1 }, (_, i) => i * step);
}

/**
 * Nudges label positions apart so stacked text never overlaps, without touching the geometry the
 * labels annotate. Forward pass enforces `minGap` between consecutive positions; the backward pass
 * pulls the run back inside `[min, max]` if the forward pass pushed it past the end.
 *
 * When the labels simply cannot all fit (`minGap × n > max - min`), no ordering satisfies the
 * constraint — the caller must size its canvas to the label count instead. `labelCapacity` below
 * is how callers check that up front; this function degrades to evenly-spaced-but-tight rather
 * than producing a scrambled order.
 */
export function declutterPositions(
  positions: number[],
  minGap: number,
  min: number,
  max: number,
): number[] {
  if (positions.length === 0) {
    return positions;
  }
  if (!fitsWithin(positions.length, minGap, min, max)) {
    const step = (max - min) / Math.max(positions.length - 1, 1);
    return positions.map((_, i) => min + i * step);
  }

  const out = positions.slice();
  for (let i = 1; i < out.length; i++) {
    out[i] = Math.max(out[i], out[i - 1] + minGap);
  }
  if (out[out.length - 1] > max) {
    out[out.length - 1] = max;
    for (let i = out.length - 2; i >= 0; i--) {
      out[i] = Math.min(out[i], out[i + 1] - minGap);
    }
  }
  if (out[0] < min) {
    out[0] = min;
    for (let i = 1; i < out.length; i++) {
      out[i] = Math.max(out[i], out[i - 1] + minGap);
    }
  }
  return out;
}

function fitsWithin(count: number, minGap: number, min: number, max: number): boolean {
  return minGap * (count - 1) <= max - min;
}

/** How many labels fit in `extent` px at `minGap` spacing — callers size the canvas from this. */
export function labelCapacity(extent: number, minGap: number): number {
  return Math.max(1, Math.floor(extent / minGap) + 1);
}

/** A rounded rectangle path. `radius` is clamped so it can't invert on short bars. */
export function roundedRectPath(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
) {
  const r = Math.max(0, Math.min(radius, width / 2, height / 2));
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + width - r, y);
  ctx.quadraticCurveTo(x + width, y, x + width, y + r);
  ctx.lineTo(x + width, y + height - r);
  ctx.quadraticCurveTo(x + width, y + height, x + width - r, y + height);
  ctx.lineTo(x + r, y + height);
  ctx.quadraticCurveTo(x, y + height, x, y + height - r);
  ctx.lineTo(x, y + r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.closePath();
}

/**
 * A Sankey ribbon: a closed shape between a source edge [y1Top, y1Bottom] at x1 and a target edge
 * [y2Top, y2Bottom] at x2, with both long edges as cubic beziers whose control points sit at the
 * horizontal midpoint. That control-point placement is what gives the flat-then-turn-then-flat
 * profile; moving them toward either end makes the ribbon look kinked.
 */
export function ribbonPath(
  ctx: CanvasRenderingContext2D,
  x1: number,
  y1Top: number,
  y1Bottom: number,
  x2: number,
  y2Top: number,
  y2Bottom: number,
) {
  const midX = (x1 + x2) / 2;
  ctx.beginPath();
  ctx.moveTo(x1, y1Top);
  ctx.bezierCurveTo(midX, y1Top, midX, y2Top, x2, y2Top);
  ctx.lineTo(x2, y2Bottom);
  ctx.bezierCurveTo(midX, y2Bottom, midX, y1Bottom, x1, y1Bottom);
  ctx.closePath();
}

/** The font stack the charts draw with, matching the app's `--font-sans`. */
export function chartFont(weight: number, size: number): string {
  return `${weight} ${size}px -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif`;
}
