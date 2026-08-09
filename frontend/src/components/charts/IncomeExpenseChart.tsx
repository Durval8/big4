import { formatCurrency } from "../../lib/format";
import { useCanvasChart } from "./useCanvasChart";
import {
  chartFont,
  formatAxisCurrency,
  formatBucketLabel,
  niceAxisTicks,
  pickIndicesAtGranularity,
  roundedRectPath,
  withAlpha,
} from "./canvasUtils";
import type { BucketUnit, TimeBucket } from "../../types/analytics";

interface IncomeExpenseChartProps {
  buckets: TimeBucket[];
  /** Drives the axis labels ("Jul 4" vs. "Jul") and the per-period rate caption. */
  bucketUnit: BucketUnit;
}

const PERIOD_NOUN: Record<BucketUnit, string> = {
  DAY: "day",
  WEEK: "week",
  MONTH: "month",
};

const HEIGHT = 260;
/** Widest a single bucket's slot may get before the run centres rather than spreading further. */
const MAX_GROUP_W = 108;

/**
 * Income and spending side by side per period, with each period's savings rate written above the
 * pair. Grouped bars from a common baseline (rather than the mirrored diverging pair this
 * replaced) because the question people actually ask here is "which bar is taller?" — and that
 * comparison is only reliable when both bars start from the same line.
 */
export function IncomeExpenseChart({ buckets, bucketUnit }: IncomeExpenseChartProps) {
  const maxValue = Math.max(...buckets.flatMap((b) => [b.income, b.expense]), 0);

  const canvasRef = useCanvasChart(
    ({ ctx, width, height, color }) => {
      if (buckets.length === 0) {
        return;
      }
      const padTop = 24;
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
      const baseline = padTop + plotH;
      const heightOf = (v: number) => (v / axisMax) * plotH;

      ctx.strokeStyle = color("--color-border");
      ctx.lineWidth = 1;
      ctx.textAlign = "right";
      ctx.textBaseline = "middle";
      ctx.font = chartFont(600, 11);
      ticks.forEach((t) => {
        const y = Math.round(baseline - heightOf(t)) + 0.5;
        ctx.beginPath();
        ctx.moveTo(padLeft, y);
        ctx.lineTo(padLeft + plotW, y);
        ctx.stroke();
        ctx.fillStyle = color("--color-text-tertiary");
        ctx.fillText(formatAxisCurrency(t), padLeft - 10, y);
      });

      // Two rules keep this compact. First, the pair is the unit: the two bars nearly touch and
      // the whitespace goes *between* groups — reversing that makes 5 buckets read as 10 unrelated
      // bars. Second, a group never gets wider than MAX_GROUP_W: spreading 4 monthly buckets over
      // the full width leaves each pair marooned in ~150px of nothing. Past that cap the groups
      // keep their size and the whole run centres instead, so the chart stays dense at any count.
      const groupW = Math.min(plotW / buckets.length, MAX_GROUP_W);
      const barsW = groupW * buckets.length;
      const barsLeft = padLeft + (plotW - barsW) / 2;
      const barW = Math.max(2, Math.min(38, groupW * 0.36));
      const gap = Math.min(3, groupW * 0.04);
      const positive = color("--color-positive");
      const negative = color("--color-negative");
      const showRates = buckets.length <= 12; // beyond that the labels collide

      // The axis should tick once per bucket — the whole point of choosing "Weekly" is a tick per
      // week — so thinning only kicks in once labels would actually collide, and then at a regular
      // stride (every 2nd, every 3rd) so it stays periodic in the chosen unit rather than
      // collapsing to an unrelated handful. Measured against this unit's own label text, since a
      // month abbreviation ("Jan") is much narrower than a day-and-date one ("Jul 19").
      ctx.font = chartFont(600, 11);
      const widestLabelPx = Math.max(
        ...buckets.map((b) => ctx.measureText(formatBucketLabel(b.start, bucketUnit)).width),
      );
      const labelled = new Set(pickIndicesAtGranularity(buckets.length, groupW, widestLabelPx));

      buckets.forEach((b, i) => {
        const centre = barsLeft + i * groupW + groupW / 2;
        const incH = heightOf(b.income);
        const expH = heightOf(b.expense);
        const radius = Math.min(4, barW / 2);

        const incGrad = ctx.createLinearGradient(0, baseline - incH, 0, baseline);
        incGrad.addColorStop(0, withAlpha(positive, 0.95));
        incGrad.addColorStop(1, withAlpha(positive, 0.55));
        roundedRectPath(ctx, centre - gap / 2 - barW, baseline - incH, barW, incH, radius);
        ctx.fillStyle = incGrad;
        ctx.fill();

        const expGrad = ctx.createLinearGradient(0, baseline - expH, 0, baseline);
        expGrad.addColorStop(0, withAlpha(negative, 0.95));
        expGrad.addColorStop(1, withAlpha(negative, 0.55));
        roundedRectPath(ctx, centre + gap / 2, baseline - expH, barW, expH, radius);
        ctx.fillStyle = expGrad;
        ctx.fill();

        if (showRates && b.income > 0) {
          const rate = Math.round(((b.income - b.expense) / b.income) * 100);
          ctx.textAlign = "center";
          ctx.textBaseline = "alphabetic";
          ctx.fillStyle = rate >= 0 ? positive : negative;
          ctx.font = chartFont(700, 11);
          ctx.fillText(
            `${rate >= 0 ? "+" : ""}${rate}%`,
            centre,
            baseline - Math.max(incH, expH) - 7,
          );
        }

        if (labelled.has(i)) {
          ctx.textAlign = "center";
          ctx.textBaseline = "top";
          ctx.fillStyle = color("--color-text-tertiary");
          ctx.font = chartFont(600, 11);
          ctx.fillText(formatBucketLabel(b.start, bucketUnit), centre, baseline + 10);
        }
      });
    },
    [maxValue, buckets.length, bucketUnit, JSON.stringify(buckets)],
  );

  if (buckets.length === 0) {
    return null;
  }

  return (
    <div className="chart-canvas-wrap" style={{ height: HEIGHT }}>
      <canvas
        ref={canvasRef}
        role="img"
        aria-label={`Income compared with spending, by ${PERIOD_NOUN[bucketUnit]}, across ${buckets.length} periods`}
        className="chart-canvas"
      />
      <table className="visually-hidden">
        <caption>Income and spending per {PERIOD_NOUN[bucketUnit]}</caption>
        <thead>
          <tr>
            <th scope="col">Period</th>
            <th scope="col">Income</th>
            <th scope="col">Spending</th>
          </tr>
        </thead>
        <tbody>
          {buckets.map((b) => (
            <tr key={b.start}>
              <th scope="row">{formatBucketLabel(b.start, bucketUnit)}</th>
              <td>{formatCurrency(b.income)}</td>
              <td>{formatCurrency(b.expense)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
