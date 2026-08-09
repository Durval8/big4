import { formatCurrency } from "../../lib/format";
import { useCanvasChart } from "./useCanvasChart";
import {
  chartFont,
  formatAxisCurrency,
  formatBucketLabel,
  niceAxisTicks,
  pickAxisLabelIndices,
  withAlpha,
} from "./canvasUtils";
import type { TimeBucket } from "../../types/analytics";

interface SpendingTrendChartProps {
  buckets: TimeBucket[];
}

const HEIGHT = 260;

/**
 * Spending over time as a filled area with a smoothed line. The curve is drawn through
 * quadratic segments anchored on the midpoint between each pair of points: that keeps the line
 * passing exactly through every data point (a cardinal spline would overshoot below zero on a
 * sharp drop to a zero-spend day, which is both wrong and visible).
 */
export function SpendingTrendChart({ buckets }: SpendingTrendChartProps) {
  const values = buckets.map((b) => b.expense);
  const maxValue = Math.max(...values, 0);

  const canvasRef = useCanvasChart(
    ({ ctx, width, height, color }) => {
      if (buckets.length < 2) {
        return;
      }
      const padTop = 12;
      const padBottom = 30;
      const padLeft = 54;
      const padRight = 14;
      const plotW = width - padLeft - padRight;
      const plotH = height - padTop - padBottom;
      if (plotW <= 0 || plotH <= 0) {
        return;
      }

      const ticks = niceAxisTicks(maxValue);
      const axisMax = ticks[ticks.length - 1] || 1;
      const xAt = (i: number) => padLeft + (i / (buckets.length - 1)) * plotW;
      const yAt = (v: number) => padTop + plotH - (v / axisMax) * plotH;

      // Gridlines + y labels.
      ctx.strokeStyle = color("--color-border");
      ctx.lineWidth = 1;
      ctx.textAlign = "right";
      ctx.textBaseline = "middle";
      ctx.font = chartFont(600, 11);
      ticks.forEach((t) => {
        const y = Math.round(yAt(t)) + 0.5; // half-pixel so a 1px line lands on one row, not two
        ctx.beginPath();
        ctx.moveTo(padLeft, y);
        ctx.lineTo(padLeft + plotW, y);
        ctx.stroke();
        ctx.fillStyle = color("--color-text-tertiary");
        ctx.fillText(formatAxisCurrency(t), padLeft - 10, y);
      });

      const accent = color("--color-negative");
      const traceLine = () => {
        ctx.beginPath();
        ctx.moveTo(xAt(0), yAt(values[0]));
        for (let i = 0; i < values.length - 1; i++) {
          const x0 = xAt(i);
          const y0 = yAt(values[i]);
          const x1 = xAt(i + 1);
          const y1 = yAt(values[i + 1]);
          const mx = (x0 + x1) / 2;
          const my = (y0 + y1) / 2;
          ctx.quadraticCurveTo(x0, y0, mx, my);
          ctx.quadraticCurveTo(mx, my, x1, y1);
        }
      };

      const fill = ctx.createLinearGradient(0, padTop, 0, padTop + plotH);
      fill.addColorStop(0, withAlpha(accent, 0.28));
      fill.addColorStop(1, withAlpha(accent, 0.02));
      traceLine();
      ctx.lineTo(xAt(values.length - 1), padTop + plotH);
      ctx.lineTo(xAt(0), padTop + plotH);
      ctx.closePath();
      ctx.fillStyle = fill;
      ctx.fill();

      traceLine();
      ctx.strokeStyle = accent;
      ctx.lineWidth = 2.25;
      ctx.lineJoin = "round";
      ctx.lineCap = "round";
      ctx.stroke();

      // Emphasised endpoint — the "where it stands now" marker.
      const lastX = xAt(values.length - 1);
      const lastY = yAt(values[values.length - 1]);
      ctx.beginPath();
      ctx.arc(lastX, lastY, 4, 0, Math.PI * 2);
      ctx.fillStyle = accent;
      ctx.fill();
      ctx.beginPath();
      ctx.arc(lastX, lastY, 7, 0, Math.PI * 2);
      ctx.strokeStyle = withAlpha(accent, 0.3);
      ctx.lineWidth = 2;
      ctx.stroke();

      // X labels.
      ctx.textAlign = "center";
      ctx.textBaseline = "top";
      ctx.fillStyle = color("--color-text-tertiary");
      ctx.font = chartFont(600, 11);
      pickAxisLabelIndices(buckets.length).forEach((i) => {
        ctx.fillText(formatBucketLabel(buckets[i].start), xAt(i), padTop + plotH + 10);
      });
    },
    [maxValue, buckets.length, JSON.stringify(values)],
  );

  if (buckets.length < 2) {
    return null;
  }

  return (
    <div className="chart-canvas-wrap" style={{ height: HEIGHT }}>
      <canvas
        ref={canvasRef}
        role="img"
        aria-label={`Spending over time across ${buckets.length} periods, peaking at ${formatCurrency(maxValue)}`}
        className="chart-canvas"
      />
      <table className="visually-hidden">
        <caption>Spending per period</caption>
        <tbody>
          {buckets.map((b) => (
            <tr key={b.start}>
              <th scope="row">{formatBucketLabel(b.start)}</th>
              <td>{formatCurrency(b.expense)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
