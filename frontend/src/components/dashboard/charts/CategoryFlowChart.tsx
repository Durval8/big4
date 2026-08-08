import { formatCurrency } from "../../../lib/format";
import { sankeyLinkPath, topCategoriesWithOther } from "./chartUtils";
import type { CategoryTotal } from "../../../types/analytics";

interface CategoryFlowChartProps {
  categories: CategoryTotal[];
}

const WIDTH = 900;
const HEIGHT = 320;
const TOP = 20;
const BOTTOM = HEIGHT - 20;
const PLOT_HEIGHT = BOTTOM - TOP;
const SOURCE_X = 90;
const NODE_WIDTH = 16;
const TARGET_X = 640;
const LABEL_X = TARGET_X + NODE_WIDTH + 14;
const NODE_GAP = 8;

/**
 * "Spending by category" as a single-level flow diagram (Sankey): one "Total spending" source
 * node fans out into one link per category, thickness proportional to amount. Chosen over a donut
 * because thickness reads consistently across a skewed distribution where angle doesn't (see the
 * design spec's Chart types table).
 */
export function CategoryFlowChart({ categories }: CategoryFlowChartProps) {
  const slices = topCategoriesWithOther(categories);
  const total = slices.reduce((sum, s) => sum + s.amount, 0);
  if (total <= 0) {
    return null;
  }

  const gapBudget = NODE_GAP * (slices.length - 1);
  const availableForNodes = PLOT_HEIGHT - gapBudget;

  // Right-side (target) nodes: stacked with gaps between them.
  let targetY = TOP;
  const targets = slices.map((s) => {
    const height = (s.amount / total) * availableForNodes;
    const yTop = targetY;
    targetY += height + NODE_GAP;
    return { ...s, yTop, yBottom: yTop + height };
  });

  // Left-side (source) slices: same order/proportions, but contiguous (no gaps) — the source is
  // one node, so its outgoing slices simply divide the full height proportionally.
  let sourceY = TOP;
  const sources = slices.map((s) => {
    const height = (s.amount / total) * PLOT_HEIGHT;
    const yTop = sourceY;
    sourceY += height;
    return { yTop, yBottom: yTop + height };
  });

  const title = "Spending by category, shown as a flow from total spending into each category";
  const ariaLabel = slices.map((s) => `${s.label}: ${formatCurrency(s.amount)}`).join(", ");

  return (
    <svg
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      role="img"
      aria-label={ariaLabel}
      style={{ width: "100%", height: "auto" }}
    >
      <title>{title}</title>

      {/* The single source node. */}
      <rect x={SOURCE_X} y={TOP} width={NODE_WIDTH} height={PLOT_HEIGHT} fill="var(--color-border)" />

      {slices.map((slice, i) => (
        <path
          key={slice.label}
          d={sankeyLinkPath(
            SOURCE_X + NODE_WIDTH,
            sources[i].yTop,
            sources[i].yBottom,
            TARGET_X,
            targets[i].yTop,
            targets[i].yBottom,
          )}
          fill={`var(${slice.colorVar})`}
          opacity={0.5}
        />
      ))}

      {targets.map((t) => (
        <g key={t.label}>
          <rect
            x={TARGET_X}
            y={t.yTop}
            width={NODE_WIDTH}
            height={Math.max(t.yBottom - t.yTop, 1)}
            fill={`var(${t.colorVar})`}
          />
          <text
            x={LABEL_X}
            y={(t.yTop + t.yBottom) / 2 - 5}
            fill="var(--color-text-primary)"
            fontSize={15}
            fontWeight={600}
          >
            {t.label}
          </text>
          <text
            x={LABEL_X}
            y={(t.yTop + t.yBottom) / 2 + 13}
            fill="var(--color-text-secondary)"
            fontSize={13}
          >
            {formatCurrency(t.amount)}
          </text>
        </g>
      ))}
    </svg>
  );
}
