import { formatCurrency, formatDate } from "../../../lib/format";
import { formatAxisCurrency, formatBucketLabel, pickAxisLabelIndices } from "./chartUtils";
import type { TimeBucket } from "../../../types/analytics";

interface SpendingTrendChartProps {
  buckets: TimeBucket[];
}

const WIDTH = 600;
const HEIGHT = 240;
const LEFT = 50;
const RIGHT = WIDTH - 20;
const TOP = 20;
const BOTTOM = HEIGHT - 40;
const PLOT_WIDTH = RIGHT - LEFT;
const PLOT_HEIGHT = BOTTOM - TOP;

/**
 * "Spending over time" as a single-series line chart — see the design spec's Chart types table.
 * One of the two charts with a visually-hidden data-table fallback: unlike the category charts,
 * which render each category's name as real DOM text next to its slice, a line has no per-point
 * textual representation otherwise.
 */
export function SpendingTrendChart({ buckets }: SpendingTrendChartProps) {
  const n = buckets.length;
  if (n === 0) {
    return null;
  }
  const maxVal = Math.max(...buckets.map((b) => b.expense), 1);

  const xFor = (i: number) => LEFT + (n === 1 ? 0 : (i / (n - 1)) * PLOT_WIDTH);
  const yFor = (value: number) => BOTTOM - (value / maxVal) * PLOT_HEIGHT;

  const linePath = buckets
    .map((b, i) => `${i === 0 ? "M" : "L"} ${xFor(i)},${yFor(b.expense)}`)
    .join(" ");

  const labelIndices = pickAxisLabelIndices(n);
  const ariaLabel = `Spending trend from ${formatDate(buckets[0].start)} to ${formatDate(
    buckets[n - 1].start,
  )}, ranging from ${formatCurrency(0)} to ${formatCurrency(maxVal)}`;

  return (
    <div>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={ariaLabel}
        style={{ width: "100%", height: "auto" }}
      >
        <title>Spending over time</title>

        <line x1={LEFT} y1={BOTTOM} x2={RIGHT} y2={BOTTOM} stroke="var(--color-border)" />
        <text x={0} y={TOP + 4} fill="var(--color-text-secondary)" fontSize={11}>
          {formatAxisCurrency(maxVal)}
        </text>
        <text x={0} y={BOTTOM + 4} fill="var(--color-text-secondary)" fontSize={11}>
          $0
        </text>

        <path d={linePath} fill="none" stroke="var(--color-accent)" strokeWidth={2} />

        {labelIndices.map((i) => (
          <text
            key={i}
            x={xFor(i)}
            y={HEIGHT - 12}
            fill="var(--color-text-secondary)"
            fontSize={11}
            textAnchor="middle"
          >
            {formatBucketLabel(buckets[i].start)}
          </text>
        ))}
      </svg>

      <table className="visually-hidden">
        <caption>Spending over time — data table</caption>
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Expense</th>
          </tr>
        </thead>
        <tbody>
          {buckets.map((b) => (
            <tr key={b.start}>
              <td>{formatDate(b.start)}</td>
              <td>{formatCurrency(b.expense)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
