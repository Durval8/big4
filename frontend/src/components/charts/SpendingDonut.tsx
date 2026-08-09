import { formatCurrency } from "../../lib/format";
import { useCanvasChart } from "./useCanvasChart";
import { chartFont, formatAxisCurrency, topCategoriesWithOther } from "./canvasUtils";
import type { CategoryTotal } from "../../types/analytics";

interface SpendingDonutProps {
  categories: CategoryTotal[];
}

const SIZE = 220;

/**
 * Spending split as a donut with the total in the hole, beside a legend that carries the actual
 * numbers. The ring answers "is one category dominating?" at a glance; the legend answers "how
 * much, exactly?" — which is why the amounts live there and not as slice labels, where they'd
 * collide on any thin slice.
 */
export function SpendingDonut({ categories }: SpendingDonutProps) {
  const slices = topCategoriesWithOther(categories);
  const total = slices.reduce((sum, s) => sum + s.amount, 0);

  const canvasRef = useCanvasChart(
    ({ ctx, width, height, color }) => {
      if (total <= 0) {
        return;
      }
      const cx = width / 2;
      const cy = height / 2;
      const outerR = Math.min(width, height) / 2 - 2;
      const innerR = outerR * 0.66;
      // A gap between slices, expressed in radians at the *outer* edge so it looks even. Skipped
      // when it would consume more than a third of a slice, or slivers would vanish entirely.
      const gapPx = 2.5;

      let angle = -Math.PI / 2;
      slices.forEach((s) => {
        const sweep = (s.amount / total) * Math.PI * 2;
        const gap = Math.min(gapPx / outerR, sweep / 3);
        ctx.beginPath();
        ctx.arc(cx, cy, outerR, angle, angle + sweep - gap);
        ctx.arc(cx, cy, innerR, angle + sweep - gap, angle, true);
        ctx.closePath();
        ctx.fillStyle = color(s.colorVar);
        ctx.fill();
        angle += sweep;
      });

      ctx.textAlign = "center";
      ctx.textBaseline = "alphabetic";
      ctx.fillStyle = color("--color-text-primary");
      ctx.font = chartFont(700, 22);
      ctx.fillText(formatAxisCurrency(total), cx, cy + 2);
      ctx.fillStyle = color("--color-text-tertiary");
      ctx.font = chartFont(600, 11);
      ctx.fillText("Total", cx, cy + 20);
    },
    [total, JSON.stringify(slices)],
  );

  if (total <= 0) {
    return null;
  }

  const ariaLabel = `Spending by category, ${formatCurrency(total)} total: ${slices
    .map((s) => `${s.label} ${formatCurrency(s.amount)}`)
    .join(", ")}`;

  return (
    <div className="donut-layout">
      <div className="chart-canvas-wrap donut-layout__ring" style={{ width: SIZE, height: SIZE }}>
        <canvas ref={canvasRef} role="img" aria-label={ariaLabel} className="chart-canvas" />
      </div>
      <ul className="donut-legend">
        {slices.map((s) => (
          <li key={s.label} className="donut-legend__item">
            <span className="donut-legend__dot" style={{ background: `var(${s.colorVar})` }} />
            <span className="donut-legend__name">{s.label}</span>
            <span className="donut-legend__value">
              {formatCurrency(s.amount)}
              <span className="donut-legend__pct">
                {" "}
                ({((s.amount / total) * 100).toFixed(1)}%)
              </span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
