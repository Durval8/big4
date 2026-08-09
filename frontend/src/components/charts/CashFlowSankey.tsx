import { formatCurrency } from "../../lib/format";
import { useCanvasChart } from "./useCanvasChart";
import {
  chartFont,
  declutterPositions,
  ribbonPath,
  topCategoriesWithOther,
  withAlpha,
} from "./canvasUtils";
import type { CategoryTotal } from "../../types/analytics";

interface CashFlowSankeyProps {
  incomeCategories: CategoryTotal[];
  categories: CategoryTotal[];
  totalIncome: number;
  totalExpense: number;
}

/** Slices beyond this collapse into "Everything else" — see the height note below. */
const MAX_EXPENSE_SLICES = 7;
/** Vertical px between two-line label centres. Drives the canvas height, so it isn't cosmetic. */
const LABEL_GAP = 40;
/**
 * Height per label, and the floor. Both are deliberately well above `LABEL_GAP`, because fitting
 * the labels is a weaker requirement than *placing* them: real spending is heavily skewed (an 88%
 * savings slice leaves 12% of the height for six categories), so the small nodes cluster into a
 * thin band and their labels all get displaced onto leader lines. Extra height doesn't change the
 * proportions — it scales that band up until the labels land near the nodes they name.
 */
const HEIGHT_PER_LABEL = 58;
const MIN_HEIGHT = 480;
const PAD_Y = 14;
/** Smallest a node may render, so a sub-1% category still has something its label can point at. */
const MIN_NODE_HEIGHT = 24;

interface FlowNode {
  label: string;
  amount: number;
  color: string;
  /** Resolved at draw time — a CSS custom-property name, not a literal color. */
  isSemantic?: boolean;
}

/**
 * Cash flow as a three-column Sankey: income sources fan into a single "Income" node, which fans
 * back out into spending categories plus whatever is left over.
 *
 * **It balances in both directions.** When income exceeds spending the surplus leaves as a
 * "Savings" target; when spending exceeds income the shortfall enters as a "From savings" source.
 * Without that second case the two sides wouldn't sum to the same total and the ribbons would
 * silently misrepresent their own proportions — a real scenario here, not a hypothetical, since a
 * one-week window can easily contain rent and no paycheck.
 *
 * The canvas height is derived from the label count rather than fixed: a Sankey's constraint isn't
 * the ribbons (which can be a pixel tall) but the stacked text beside them, which cannot overlap.
 */
export function CashFlowSankey({
  incomeCategories,
  categories,
  totalIncome,
  totalExpense,
}: CashFlowSankeyProps) {
  const net = totalIncome - totalExpense;

  const sources: FlowNode[] = topCategoriesWithOther(incomeCategories, MAX_EXPENSE_SLICES).map(
    (s) => ({ label: s.label, amount: s.amount, color: s.colorVar }),
  );
  if (net < 0) {
    sources.push({
      label: "From savings",
      amount: -net,
      color: "--color-negative",
      isSemantic: true,
    });
  }

  const targets: FlowNode[] = [];
  if (net > 0) {
    targets.push({ label: "Savings", amount: net, color: "--color-positive", isSemantic: true });
  }
  targets.push(
    ...topCategoriesWithOther(categories, MAX_EXPENSE_SLICES).map((s) => ({
      label: s.label,
      amount: s.amount,
      color: s.colorVar,
    })),
  );

  // Both sides sum to this by construction (see the surplus/shortfall nodes above).
  const flowTotal = Math.max(totalIncome, totalExpense);
  const height = Math.max(
    MIN_HEIGHT,
    Math.max(sources.length, targets.length) * HEIGHT_PER_LABEL + PAD_Y * 2,
  );

  const canvasRef = useCanvasChart(
    ({ ctx, width, height: h, color }) => {
      if (flowTotal <= 0 || sources.length === 0 || targets.length === 0) {
        return;
      }
      const resolve = (node: FlowNode) => color(node.color);

      const top = PAD_Y;
      const bottom = h - PAD_Y;
      const plotH = bottom - top;
      const nodeW = 13;

      // Column positions are measured, not guessed. Fixed percentages meant a long category name
      // ("Investment Income", "$5,586.02 (88.5%)") either overflowed the canvas or forced the
      // ribbons narrow enough to be unreadable, depending on the card width. Measuring the widest
      // label on each side and sizing the gutters to it makes every name fit at any width, and
      // hands whatever is left over to the ribbons.
      const measureWidest = (nodes: FlowNode[]) => {
        let widest = 0;
        nodes.forEach((n) => {
          ctx.font = chartFont(600, 12.5);
          widest = Math.max(widest, ctx.measureText(n.label).width);
          ctx.font = chartFont(600, 11);
          const pct = ((n.amount / flowTotal) * 100).toFixed(1);
          widest = Math.max(widest, ctx.measureText(`${formatCurrency(n.amount)} (${pct}%)`).width);
        });
        return widest;
      };

      const LABEL_PAD = 14; // between a node and its text
      const EDGE_MARGIN = 10; // between the text and the canvas boundary
      // A gutter has to cover *both* — sizing it to `widest + LABEL_PAD` alone puts the far end of
      // the text exactly on the canvas edge, which clips the last character or two.
      // Cap each gutter at a share of the canvas so a pathological label truncates rather than
      // collapsing the ribbons to nothing.
      const gutterFor = (nodes: FlowNode[], cap: number) =>
        Math.min(measureWidest(nodes) + LABEL_PAD + EDGE_MARGIN, width * cap);
      const sourceGutter = gutterFor(sources, 0.32);
      const targetGutter = gutterFor(targets, 0.36);

      const sourceX = sourceGutter;
      const targetX = width - targetGutter - nodeW;
      const aggX = sourceX + nodeW + (targetX - sourceX - nodeW) / 2;
      const targetLabelX = targetX + nodeW + LABEL_PAD;
      const sourceLabelRight = sourceX - LABEL_PAD;

      // Node stacks: gaps between nodes, so the visible bars separate. The aggregate node's two
      // edges use contiguous (gapless) slices instead, because a ribbon has to meet the full
      // height of its share of that node — gaps would leave the ribbons not adding up.
      //
      // Every node also gets a floor of MIN_NODE_HEIGHT, with only the *remainder* split
      // proportionally. Without it, real data makes this chart unusable: an 88%-savings month
      // leaves ~50px for six categories however tall the canvas is, so those nodes are hairlines
      // and every label is displaced onto a leader line. The floor is a deliberate, bounded
      // distortion of the outer node heights — the ribbons still *leave* the aggregate node at
      // strictly proportional widths, which is where the eye actually compares them, and each one
      // tapers to meet a node big enough to carry a label.
      const stack = (nodes: FlowNode[], gap: number) => {
        const usable = plotH - gap * (nodes.length - 1);
        const reserved = MIN_NODE_HEIGHT * nodes.length;
        // If the floors would dominate, drop them — proportionality matters more than legibility
        // once there are so many nodes that nothing is readable either way.
        const floor = reserved <= usable * 0.5 ? MIN_NODE_HEIGHT : 0;
        const proportional = usable - floor * nodes.length;
        let y = top;
        return nodes.map((n) => {
          const nodeH = floor + (n.amount / flowTotal) * proportional;
          const seg = { node: n, top: y, bottom: y + nodeH };
          y += nodeH + gap;
          return seg;
        });
      };
      const contiguous = (nodes: FlowNode[]) => {
        let y = top;
        return nodes.map((n) => {
          const nodeH = (n.amount / flowTotal) * plotH;
          const seg = { top: y, bottom: y + nodeH };
          y += nodeH;
          return seg;
        });
      };

      const sourceSegs = stack(sources, 8);
      const targetSegs = stack(targets, 10);
      const aggLeft = contiguous(sources);
      const aggRight = contiguous(targets);

      // The aggregate node.
      ctx.fillStyle = color("--color-border");
      ctx.fillRect(aggX, top, nodeW, plotH);

      const neutral = color("--color-text-tertiary");

      // Column 1 -> aggregate.
      sourceSegs.forEach((seg, i) => {
        const c = resolve(seg.node);
        const grad = ctx.createLinearGradient(sourceX + nodeW, 0, aggX, 0);
        grad.addColorStop(0, withAlpha(c, 0.6));
        grad.addColorStop(1, withAlpha(neutral, 0.3));
        ribbonPath(
          ctx,
          sourceX + nodeW,
          seg.top,
          seg.bottom,
          aggX,
          aggLeft[i].top,
          aggLeft[i].bottom,
        );
        ctx.fillStyle = grad;
        ctx.fill();

        ctx.fillStyle = c;
        ctx.fillRect(sourceX, seg.top, nodeW, Math.max(seg.bottom - seg.top, 1.5));
      });

      // Aggregate -> column 3.
      targetSegs.forEach((seg, i) => {
        const c = resolve(seg.node);
        const grad = ctx.createLinearGradient(aggX + nodeW, 0, targetX, 0);
        grad.addColorStop(0, withAlpha(neutral, 0.3));
        grad.addColorStop(1, withAlpha(c, 0.6));
        ribbonPath(
          ctx,
          aggX + nodeW,
          aggRight[i].top,
          aggRight[i].bottom,
          targetX,
          seg.top,
          seg.bottom,
        );
        ctx.fillStyle = grad;
        ctx.fill();

        ctx.fillStyle = c;
        ctx.fillRect(targetX, seg.top, nodeW, Math.max(seg.bottom - seg.top, 1.5));
      });

      // The aggregate node's own label, above the column.
      ctx.textAlign = "left";
      ctx.fillStyle = color("--color-text-secondary");
      ctx.font = chartFont(700, 10);
      ctx.fillText("INCOME", aggX + nodeW + 8, top + 11);

      const drawLabels = (
        segs: { node: FlowNode; top: number; bottom: number }[],
        x: number,
        align: CanvasTextAlign,
        leaderTowardRight: boolean,
      ) => {
        const centres = segs.map((s) => (s.top + s.bottom) / 2);
        const placed = declutterPositions(centres, LABEL_GAP, top + 8, bottom - 8);
        segs.forEach((seg, i) => {
          const y = placed[i];
          const nodeMid = centres[i];

          // A leader line only when the label had to move off its node — the common case stays
          // clean, and a displaced label is still unambiguously attributable.
          if (Math.abs(y - nodeMid) > 2) {
            const from = leaderTowardRight ? x - 10 : x + 10;
            ctx.beginPath();
            ctx.moveTo(from, nodeMid);
            ctx.lineTo(x, y);
            ctx.strokeStyle = color("--color-border");
            ctx.lineWidth = 1;
            ctx.stroke();
          }

          const pct = ((seg.node.amount / flowTotal) * 100).toFixed(1);
          ctx.textAlign = align;
          ctx.fillStyle = color("--color-text-primary");
          ctx.font = chartFont(600, 12.5);
          ctx.fillText(seg.node.label, x, y - 3);
          ctx.fillStyle = color("--color-text-secondary");
          ctx.font = chartFont(600, 11);
          ctx.fillText(`${formatCurrency(seg.node.amount)} (${pct}%)`, x, y + 12);
        });
      };

      drawLabels(sourceSegs, sourceLabelRight, "right", true);
      drawLabels(targetSegs, targetLabelX, "left", false);
    },
    [flowTotal, height, sources.length, targets.length, JSON.stringify([sources, targets])],
  );

  if (flowTotal <= 0 || sources.length === 0 || targets.length === 0) {
    return null;
  }

  const ariaLabel = [
    `Cash flow: ${formatCurrency(totalIncome)} income into ${formatCurrency(totalExpense)} of spending.`,
    `Sources: ${sources.map((s) => `${s.label} ${formatCurrency(s.amount)}`).join(", ")}.`,
    `Destinations: ${targets.map((t) => `${t.label} ${formatCurrency(t.amount)}`).join(", ")}.`,
  ].join(" ");

  return (
    <div className="chart-canvas-wrap" style={{ height }}>
      <canvas ref={canvasRef} role="img" aria-label={ariaLabel} className="chart-canvas" />
      <table className="visually-hidden">
        <caption>Cash flow by source and destination</caption>
        <tbody>
          {sources.map((s) => (
            <tr key={`in-${s.label}`}>
              <th scope="row">{s.label} (in)</th>
              <td>{formatCurrency(s.amount)}</td>
            </tr>
          ))}
          {targets.map((t) => (
            <tr key={`out-${t.label}`}>
              <th scope="row">{t.label} (out)</th>
              <td>{formatCurrency(t.amount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
