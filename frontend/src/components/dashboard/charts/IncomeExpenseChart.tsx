import { formatCurrency, formatDate } from "../../../lib/format";
import { divergingBarLength, formatBucketLabel, pickAxisLabelIndices } from "./chartUtils";
import type { TimeBucket } from "../../../types/analytics";

interface IncomeExpenseChartProps {
  buckets: TimeBucket[];
}

const WIDTH = 600;
const HEIGHT = 260;
const LEFT = 50;
const RIGHT = WIDTH - 20;
const ZERO_Y = HEIGHT / 2;
const HALF_PLOT = ZERO_Y - 20; // leaves room top/bottom for axis labels
const PLOT_WIDTH = RIGHT - LEFT;

/**
 * "Income vs. expense over time" as a diverging bar chart, mirrored around a shared zero axis:
 * income extends up, expense extends down (magnitude only — not a negative number). Reads as
 * "money in vs. money out" at a glance; reuses the positive/negative tokens with no new token
 * needed. Shares the divergingBarLength helper with CategoryMoversChart (see chartUtils.ts) — the
 * two charts differ in orientation and in plotting two series vs. one signed delta, so that's the
 * one thing they actually share.
 */
export function IncomeExpenseChart({ buckets }: IncomeExpenseChartProps) {
  const n = buckets.length;
  if (n === 0) {
    return null;
  }
  const maxAbs = Math.max(...buckets.map((b) => Math.max(b.income, b.expense)), 1);

  const barWidth = (PLOT_WIDTH / n) * 0.6;
  const gap = (PLOT_WIDTH / n) * 0.4;
  const xFor = (i: number) => LEFT + i * (barWidth + gap) + gap / 2;

  const labelIndices = pickAxisLabelIndices(n);
  const ariaLabel = `Income versus expense from ${formatDate(buckets[0].start)} to ${formatDate(
    buckets[n - 1].start,
  )}`;

  return (
    <div>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={ariaLabel}
        style={{ width: "100%", height: "auto" }}
      >
        <title>Income vs. expense over time</title>

        <line x1={LEFT} y1={ZERO_Y} x2={RIGHT} y2={ZERO_Y} stroke="var(--color-border)" />

        {buckets.map((b, i) => {
          const incomeHeight = divergingBarLength(b.income, maxAbs, HALF_PLOT);
          const expenseHeight = divergingBarLength(b.expense, maxAbs, HALF_PLOT);
          const x = xFor(i);
          return (
            <g key={b.start}>
              <rect
                x={x}
                y={ZERO_Y - incomeHeight}
                width={barWidth}
                height={incomeHeight}
                fill="var(--color-positive)"
              />
              <rect x={x} y={ZERO_Y} width={barWidth} height={expenseHeight} fill="var(--color-negative)" />
            </g>
          );
        })}

        {labelIndices.map((i) => (
          <text
            key={i}
            x={xFor(i) + barWidth / 2}
            y={HEIGHT - 6}
            fill="var(--color-text-secondary)"
            fontSize={11}
            textAnchor="middle"
          >
            {formatBucketLabel(buckets[i].start)}
          </text>
        ))}
      </svg>

      <div className="chart-legend">
        <span>
          <i style={{ background: "var(--color-positive)" }} /> Income
        </span>
        <span>
          <i style={{ background: "var(--color-negative)" }} /> Expense
        </span>
      </div>

      <table className="visually-hidden">
        <caption>Income vs. expense over time — data table</caption>
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Income</th>
            <th scope="col">Expense</th>
          </tr>
        </thead>
        <tbody>
          {buckets.map((b) => (
            <tr key={b.start}>
              <td>{formatDate(b.start)}</td>
              <td>{formatCurrency(b.income)}</td>
              <td>{formatCurrency(b.expense)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
