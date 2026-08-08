import { formatCurrency, formatEnumLabel } from "../../../lib/format";
import { divergingBarLength } from "./chartUtils";
import type { CategoryTotal } from "../../../types/analytics";

interface CategoryMoversChartProps {
  categories: CategoryTotal[];
}

const WIDTH = 600;
const ROW_HEIGHT = 32;
const TOP = 10;
const CENTER_X = WIDTH / 2;
const HALF_PLOT = WIDTH / 2 - 130; // leaves room for the category label (left) and value (right)
const MAX_ROWS = 8;

/**
 * "Category change vs. prior period" as a horizontal diverging bar chart: one bar per category,
 * extending right (spent more — the bad outcome) or left (spent less — good) from a zero
 * centerline. Horizontal orientation keeps labels readable at up to 8 rows, which a vertical bar
 * wouldn't. Direction runs opposite to the money: a positive delta (spent more) renders
 * --color-negative, not --color-positive — see the design spec's "Gotcha on the movers chart."
 */
export function CategoryMoversChart({ categories }: CategoryMoversChartProps) {
  const withDelta = categories
    .map((c) => ({
      label: formatEnumLabel(c.category),
      delta: c.amount - (c.previousAmount ?? 0),
    }))
    .filter((c) => c.delta !== 0)
    .sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta))
    .slice(0, MAX_ROWS);

  if (withDelta.length === 0) {
    return null;
  }

  const maxAbsDelta = Math.max(...withDelta.map((c) => Math.abs(c.delta)), 1);
  const height = TOP * 2 + withDelta.length * ROW_HEIGHT;

  const ariaLabel = withDelta
    .map((c) => `${c.label}: ${c.delta > 0 ? "up" : "down"} ${formatCurrency(Math.abs(c.delta))}`)
    .join(", ");

  return (
    <svg
      viewBox={`0 0 ${WIDTH} ${height}`}
      role="img"
      aria-label={ariaLabel}
      style={{ width: "100%", height: "auto" }}
    >
      <title>Category change vs. the prior period</title>

      <line x1={CENTER_X} y1={0} x2={CENTER_X} y2={height} stroke="var(--color-border)" />

      {withDelta.map((c, i) => {
        const y = TOP + i * ROW_HEIGHT + ROW_HEIGHT / 2;
        const barLength = divergingBarLength(c.delta, maxAbsDelta, HALF_PLOT);
        const increased = c.delta > 0;
        // Increase = spending more = bad = negative token. Decrease = good = positive token.
        const color = increased ? "var(--color-negative)" : "var(--color-positive)";
        const barX = increased ? CENTER_X : CENTER_X - barLength;
        const valueX = increased ? CENTER_X + barLength + 8 : CENTER_X - barLength - 8;
        const valueAnchor = increased ? "start" : "end";

        return (
          <g key={c.label}>
            <text
              x={CENTER_X - HALF_PLOT - 10}
              y={y + 4}
              fill="var(--color-text-primary)"
              fontSize={13}
              textAnchor="end"
            >
              {c.label}
            </text>
            <rect x={barX} y={y - 8} width={Math.max(barLength, 1)} height={16} fill={color} />
            <text x={valueX} y={y + 4} fill="var(--color-text-secondary)" fontSize={12} textAnchor={valueAnchor}>
              {increased ? "+" : "−"}
              {formatCurrency(Math.abs(c.delta))}
            </text>
          </g>
        );
      })}
    </svg>
  );
}
