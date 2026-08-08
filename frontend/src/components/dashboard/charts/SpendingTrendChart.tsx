import { formatCurrency, formatDate } from "../../../lib/format";
import { formatAxisCurrency, formatBucketLabel, niceAxisTicks, pickAxisLabelIndices } from "./chartUtils";
import type { TimeBucket } from "../../../types/analytics";

interface SpendingTrendChartProps {
  buckets: TimeBucket[];
}

const WIDTH = 900;
const HEIGHT = 280;
const LEFT = 64;
const RIGHT = WIDTH - 20;
const TOP = 16;
const BOTTOM = HEIGHT - 36;
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
  const rawMax = Math.max(...buckets.map((b) => b.expense), 1);
  const ticks = niceAxisTicks(rawMax, 4);
  const axisMax = ticks[ticks.length - 1];

  const xFor = (i: number) => LEFT + (n === 1 ? 0 : (i / (n - 1)) * PLOT_WIDTH);
  const yFor = (value: number) => BOTTOM - (value / axisMax) * PLOT_HEIGHT;

  const linePath = buckets
    .map((b, i) => `${i === 0 ? "M" : "L"} ${xFor(i)},${yFor(b.expense)}`)
    .join(" ");
  const areaPath = `${linePath} L ${xFor(n - 1)},${BOTTOM} L ${xFor(0)},${BOTTOM} Z`;

  const labelIndices = pickAxisLabelIndices(n);
  const ariaLabel = `Spending trend from ${formatDate(buckets[0].start)} to ${formatDate(
    buckets[n - 1].start,
  )}, ranging from ${formatCurrency(0)} to ${formatCurrency(rawMax)}`;

  return (
    <div>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={ariaLabel}
        style={{ width: "100%", height: "auto" }}
      >
        <title>Spending over time</title>

        {ticks.map((t) => {
          const y = yFor(t);
          return (
            <g key={t}>
              <line x1={LEFT} y1={y} x2={RIGHT} y2={y} stroke="var(--color-border)" strokeWidth={1} />
              <text x={LEFT - 10} y={y + 4} fill="var(--color-text-secondary)" fontSize={11} textAnchor="end">
                {formatAxisCurrency(t)}
              </text>
            </g>
          );
        })}
        <line x1={LEFT} y1={BOTTOM} x2={LEFT} y2={TOP} stroke="var(--color-border)" strokeWidth={1} />

        <path d={areaPath} fill="var(--color-accent)" opacity={0.12} stroke="none" />
        <path d={linePath} fill="none" stroke="var(--color-accent)" strokeWidth={2.5} />
        {buckets.map((b, i) => (
          <circle key={b.start} cx={xFor(i)} cy={yFor(b.expense)} r={3} fill="var(--color-accent)" />
        ))}

        <line x1={LEFT} y1={BOTTOM} x2={RIGHT} y2={BOTTOM} stroke="var(--color-text-secondary)" strokeWidth={1} />
        {labelIndices.map((i) => (
          <g key={i}>
            <line x1={xFor(i)} y1={BOTTOM} x2={xFor(i)} y2={BOTTOM + 4} stroke="var(--color-text-secondary)" />
            <text
              x={xFor(i)}
              y={HEIGHT - 12}
              fill="var(--color-text-secondary)"
              fontSize={11}
              textAnchor="middle"
            >
              {formatBucketLabel(buckets[i].start)}
            </text>
          </g>
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
