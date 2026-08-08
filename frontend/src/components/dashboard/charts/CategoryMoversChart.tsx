import { formatCurrency, formatEnumLabel } from "../../../lib/format";
import { formatAxisCurrency, niceAxisTicks } from "./chartUtils";
import type { CategoryTotal } from "../../../types/analytics";

interface CategoryMoversChartProps {
  categories: CategoryTotal[];
}

const WIDTH = 900;
const ROW_HEIGHT = 40;
const HEADER_HEIGHT = 28;
const LABEL_WIDTH = 190;
const BAR_START_X = LABEL_WIDTH + 20;
const RIGHT_MARGIN = 110; // room for the value label past the longest bar
const BAR_MAX_LENGTH = WIDTH - BAR_START_X - RIGHT_MARGIN;
const MAX_ROWS = 8;

/**
 * "Category change vs. prior period" as a same-origin horizontal bar list, ranked by the size of
 * the change: every bar starts at the same x position, so comparing two categories' magnitudes is
 * a direct length comparison rather than judging two bars that grow from opposite sides of a
 * centerline. A light $-scale axis across the top turns "roughly this much bigger" into a
 * readable number. Bar color still runs opposite to the money: spending *more* (the bad outcome)
 * renders --color-negative — see the design spec's "Gotcha on the movers chart."
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

  const ticks = niceAxisTicks(Math.max(...withDelta.map((c) => Math.abs(c.delta)), 1), 4);
  const axisMax = ticks[ticks.length - 1];
  const height = HEADER_HEIGHT + withDelta.length * ROW_HEIGHT + 8;
  const lengthFor = (delta: number) => (Math.abs(delta) / axisMax) * BAR_MAX_LENGTH;

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

      {/* $-scale axis across the top, shared by every row's bar length. */}
      {ticks.map((t) => {
        const x = BAR_START_X + (t / axisMax) * BAR_MAX_LENGTH;
        return (
          <g key={t}>
            <line
              x1={x}
              y1={HEADER_HEIGHT}
              x2={x}
              y2={height}
              stroke="var(--color-border)"
              strokeDasharray={t === 0 ? undefined : "2,3"}
            />
            <text x={x} y={16} fill="var(--color-text-secondary)" fontSize={11} textAnchor="middle">
              {formatAxisCurrency(t)}
            </text>
          </g>
        );
      })}

      {withDelta.map((c, i) => {
        const y = HEADER_HEIGHT + i * ROW_HEIGHT + ROW_HEIGHT / 2;
        const barLength = Math.max(lengthFor(c.delta), 2);
        const increased = c.delta > 0;
        // Increase = spending more = bad = negative token. Decrease = good = positive token.
        const color = increased ? "var(--color-negative)" : "var(--color-positive)";

        return (
          <g key={c.label}>
            <text
              x={LABEL_WIDTH}
              y={y + 4}
              fill="var(--color-text-primary)"
              fontSize={14}
              textAnchor="end"
            >
              {c.label}
            </text>
            <rect x={BAR_START_X} y={y - 9} width={barLength} height={18} fill={color} rx={3} />
            <text
              x={BAR_START_X + barLength + 8}
              y={y + 4}
              fill="var(--color-text-secondary)"
              fontSize={13}
              fontWeight={600}
            >
              {increased ? "▲" : "▼"} {formatCurrency(Math.abs(c.delta))}
            </text>
          </g>
        );
      })}
    </svg>
  );
}
