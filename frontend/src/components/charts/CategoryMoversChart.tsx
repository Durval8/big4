import { formatCurrency, formatEnumLabel } from "../../lib/format";
import { CHART_COLOR_VARS } from "./canvasUtils";
import type { CategoryTotal } from "../../types/analytics";

interface CategoryMoversChartProps {
  categories: CategoryTotal[];
}

const MAX_ROWS = 6;

interface Mover {
  label: string;
  amount: number;
  delta: number;
  /** Null when the category had no spend at all last period — a share of ∞, not of 0. */
  pct: number | null;
  colorVar: string;
}

/**
 * The categories that moved most against the previous period.
 *
 * Rendered as DOM rows rather than canvas — unlike the other charts here, the interesting content
 * is six labelled numbers, which the browser already lays out better than a canvas can, and gets
 * text selection and screen-reader access for free.
 *
 * Colour follows the *reading*, not the direction: spending more is `--color-negative` and
 * spending less is `--color-positive`, so a wall of red means overspend at a glance.
 */
export function CategoryMoversChart({ categories }: CategoryMoversChartProps) {
  const movers: Mover[] = categories
    .filter((c) => c.previousAmount !== null)
    .map((c, i) => {
      const previous = c.previousAmount as number;
      return {
        label: formatEnumLabel(c.category),
        amount: c.amount,
        delta: c.amount - previous,
        pct: previous > 0 ? ((c.amount - previous) / previous) * 100 : null,
        colorVar: CHART_COLOR_VARS[i % CHART_COLOR_VARS.length],
      };
    })
    .filter((m) => m.delta !== 0)
    .sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta))
    .slice(0, MAX_ROWS);

  if (movers.length === 0) {
    return null;
  }

  const maxAbsDelta = Math.max(...movers.map((m) => Math.abs(m.delta)));

  return (
    <ul className="movers">
      {movers.map((m) => {
        const up = m.delta > 0;
        const semantic = up ? "var(--color-negative)" : "var(--color-positive)";
        return (
          <li key={m.label} className="movers__row">
            <span className="movers__badge" style={{ background: `var(${m.colorVar})` }}>
              {m.label.charAt(0)}
            </span>
            <span className="movers__main">
              <span className="movers__name">{m.label}</span>
              <span className="movers__track">
                <span
                  className="movers__fill"
                  style={{
                    width: `${(Math.abs(m.delta) / maxAbsDelta) * 100}%`,
                    background: semantic,
                  }}
                />
              </span>
            </span>
            {/* The change is the headline, not the balance: this list is sorted by movement, and
                leading with the amount makes a category that dropped to nothing read as "$0.00"
                — which looks like an empty category rather than the biggest drop on the page. */}
            <span className="movers__figures">
              <span className="movers__amount" style={{ color: semantic }}>
                {up ? "▲" : "▼"} {formatCurrency(Math.abs(m.delta))}
                {m.pct !== null && ` (${Math.abs(Math.round(m.pct))}%)`}
              </span>
              <span className="movers__delta">
                {m.amount > 0 ? `now ${formatCurrency(m.amount)}` : "nothing this period"}
              </span>
            </span>
          </li>
        );
      })}
    </ul>
  );
}
