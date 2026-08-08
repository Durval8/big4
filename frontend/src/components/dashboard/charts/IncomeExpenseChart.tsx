import { formatCurrency, formatDate } from "../../../lib/format";
import {
  divergingBarLength,
  formatAxisCurrency,
  formatBucketLabel,
  niceAxisTicks,
  pickAxisLabelIndices,
} from "./chartUtils";
import type { TimeBucket } from "../../../types/analytics";

interface IncomeExpenseChartProps {
  buckets: TimeBucket[];
}

const WIDTH = 900;
const HEIGHT = 320;
const LEFT = 64;
const RIGHT = WIDTH - 20;
const TOP = 16;
const LABEL_ROW_Y = HEIGHT - 12;
const ZERO_Y = (TOP + (HEIGHT - 36)) / 2;
const HALF_PLOT = ZERO_Y - TOP;
const PLOT_WIDTH = RIGHT - LEFT;

/**
 * "Income vs. expense over time" as a diverging bar chart, mirrored around a shared zero axis:
 * income extends up, expense extends down (magnitude only — not a negative number). A tick's
 * dollar value labels both the income gridline above zero and the mirrored expense gridline
 * below it, since both read as "this much," just in opposite directions.
 */
export function IncomeExpenseChart({ buckets }: IncomeExpenseChartProps) {
  const n = buckets.length;
  if (n === 0) {
    return null;
  }
  const rawMaxAbs = Math.max(...buckets.map((b) => Math.max(b.income, b.expense)), 1);
  const ticks = niceAxisTicks(rawMaxAbs, 3); // [0, step, 2*step, axisMax]
  const axisMax = ticks[ticks.length - 1];

  const barWidth = (PLOT_WIDTH / n) * 0.6;
  const gap = (PLOT_WIDTH / n) * 0.4;
  const xFor = (i: number) => LEFT + i * (barWidth + gap) + gap / 2;

  const labelIndices = pickAxisLabelIndices(n);
  const ariaLabel = `Income versus expense from ${formatDate(buckets[0].start)} to ${formatDate(
    buckets[n - 1].start,
  )}, up to ${formatCurrency(rawMaxAbs)} per bucket`;

  return (
    <div>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={ariaLabel}
        style={{ width: "100%", height: "auto" }}
      >
        <title>Income vs. expense over time</title>

        {/* Mirrored gridlines: each non-zero tick labels both the income side (above) and the
            expense side (below) — same magnitude, opposite direction. */}
        {ticks.map((t) => {
          if (t === 0) return null;
          const dy = (t / axisMax) * HALF_PLOT;
          return (
            <g key={t}>
              <line x1={LEFT} y1={ZERO_Y - dy} x2={RIGHT} y2={ZERO_Y - dy} stroke="var(--color-border)" />
              <line x1={LEFT} y1={ZERO_Y + dy} x2={RIGHT} y2={ZERO_Y + dy} stroke="var(--color-border)" />
              <text x={LEFT - 10} y={ZERO_Y - dy + 4} fill="var(--color-text-secondary)" fontSize={11} textAnchor="end">
                {formatAxisCurrency(t)}
              </text>
              <text x={LEFT - 10} y={ZERO_Y + dy + 4} fill="var(--color-text-secondary)" fontSize={11} textAnchor="end">
                {formatAxisCurrency(t)}
              </text>
            </g>
          );
        })}
        <line x1={LEFT} y1={TOP} x2={LEFT} y2={ZERO_Y + HALF_PLOT} stroke="var(--color-border)" />

        {/* X-axis guides: a faint vertical line through the plot for every labeled bucket, so a
            date at the bottom clearly ties back to its bar group above. */}
        {labelIndices.map((i) => (
          <line
            key={`guide-${i}`}
            x1={xFor(i) + barWidth / 2}
            y1={TOP}
            x2={xFor(i) + barWidth / 2}
            y2={ZERO_Y + HALF_PLOT}
            stroke="var(--color-border)"
            strokeDasharray="2,3"
          />
        ))}

        {buckets.map((b, i) => {
          const incomeHeight = divergingBarLength(b.income, axisMax, HALF_PLOT);
          const expenseHeight = divergingBarLength(b.expense, axisMax, HALF_PLOT);
          const x = xFor(i);
          return (
            <g key={b.start}>
              <rect
                x={x}
                y={ZERO_Y - incomeHeight}
                width={barWidth}
                height={incomeHeight}
                fill="var(--color-positive)"
                rx={2}
              />
              <rect x={x} y={ZERO_Y} width={barWidth} height={expenseHeight} fill="var(--color-negative)" rx={2} />
            </g>
          );
        })}

        <line x1={LEFT} y1={ZERO_Y} x2={RIGHT} y2={ZERO_Y} stroke="var(--color-text-secondary)" strokeWidth={1.5} />
        <text x={LEFT - 10} y={ZERO_Y + 4} fill="var(--color-text-secondary)" fontSize={11} textAnchor="end">
          $0
        </text>

        {labelIndices.map((i) => (
          <text
            key={i}
            x={xFor(i) + barWidth / 2}
            y={LABEL_ROW_Y}
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
