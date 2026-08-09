import { useState } from "react";
import { useIncomeExpenseSeries } from "../../hooks/useIncomeExpenseSeries";
import { IncomeExpenseChart } from "../charts/IncomeExpenseChart";
import { GRANULARITY_LABEL } from "../charts/bucketUtils";
import { EmptyState } from "../common/EmptyState";
import { formatCurrency, formatPercent } from "../../lib/format";
import { BUCKET_UNITS, type BucketUnit } from "../../types/analytics";

/**
 * Income vs. expenses, self-contained: it owns its granularity, fetches its own window, and is
 * unaffected by the page's time-range selector. See `useIncomeExpenseSeries` for why.
 *
 * The summary line reports totals *over the plotted periods*, not over the page range — otherwise
 * the number above the chart wouldn't be the number the chart is drawing.
 */
export function IncomeExpenseCard() {
  const [granularity, setGranularity] = useState<BucketUnit>("MONTH");
  const { series, loading, error } = useIncomeExpenseSeries(granularity);

  const buckets = series?.buckets ?? [];
  const income = buckets.reduce((sum, b) => sum + b.income, 0);
  const expense = buckets.reduce((sum, b) => sum + b.expense, 0);
  const savingsRate = income > 0 ? ((income - expense) / income) * 100 : null;

  return (
    <section className="card card--chart">
      <header className="card-head">
        <div className="card-head__group">
          <h3 className="card-head__title">Income vs. expenses</h3>
          {buckets.length > 0 && (
            <span className="card-head__meta">
              {formatCurrency(income)} in · {formatCurrency(expense)} out ·{" "}
              {formatPercent(savingsRate)} saved over {buckets.length}{" "}
              {buckets.length === 1 ? "period" : "periods"}
            </span>
          )}
        </div>

        <div className="segmented" role="group" aria-label="Bucket size">
          {BUCKET_UNITS.map((unit) => (
            <button
              key={unit}
              type="button"
              aria-pressed={unit === granularity}
              className={`segmented__option${unit === granularity ? " segmented__option--active" : ""}`}
              onClick={() => setGranularity(unit)}
            >
              {GRANULARITY_LABEL[unit]}
            </button>
          ))}
        </div>
      </header>

      <span className="chart-legend">
        <span className="chart-legend__item">
          <span className="chart-legend__swatch" style={{ background: "var(--color-positive)" }} />
          Income
        </span>
        <span className="chart-legend__item">
          <span className="chart-legend__swatch" style={{ background: "var(--color-negative)" }} />
          Expenses
        </span>
      </span>

      {error && <div className="error-banner">{error}</div>}
      {loading && buckets.length === 0 ? (
        <p>Loading…</p>
      ) : buckets.length > 0 ? (
        <IncomeExpenseChart buckets={buckets} bucketUnit={granularity} />
      ) : (
        !error && (
          <EmptyState
            message={`No ${GRANULARITY_LABEL[granularity].toLowerCase()} activity recorded yet.`}
          />
        )
      )}
    </section>
  );
}
