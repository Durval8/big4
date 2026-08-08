import { useAnalytics } from "../../hooks/useAnalytics";
import { formatDate } from "../../lib/format";
import { EmptyState } from "../common/EmptyState";
import { CategoryFlowChart } from "./charts/CategoryFlowChart";
import { SpendingTrendChart } from "./charts/SpendingTrendChart";
import { IncomeExpenseChart } from "./charts/IncomeExpenseChart";
import { CategoryMoversChart } from "./charts/CategoryMoversChart";
import { canShowCategoryFlow, canShowIncomeExpense, canShowMovers, canShowTrend } from "./charts/thresholds";
import type { TimeRange } from "../../types/transaction";

interface AnalyticsSectionProps {
  range: TimeRange;
}

/**
 * Dashboard spending visualizations — see
 * docs/superpowers/specs/2026-08-02-transaction-analytics-design.md. Each chart independently
 * decides whether it clears its render threshold; if none do, the section shows a single empty
 * state rather than four separate empty boxes. `range=WEEK` frequently showing few or no charts
 * is intended, not a bug — the section is progressive, earning its density as the window widens.
 */
export function AnalyticsSection({ range }: AnalyticsSectionProps) {
  const { analytics, loading, error } = useAnalytics(range);

  if (loading && !analytics) {
    return (
      <div style={{ marginTop: 40 }}>
        <div className="section-header">
          <h2>Insights</h2>
        </div>
        <p>Loading…</p>
      </div>
    );
  }

  if (error || !analytics) {
    return (
      <div style={{ marginTop: 40 }}>
        <div className="section-header">
          <h2>Insights</h2>
        </div>
        {error && <div className="error-banner">{error}</div>}
      </div>
    );
  }

  const showFlow = canShowCategoryFlow(analytics);
  const showTrend = canShowTrend(analytics);
  const showIncomeExpense = canShowIncomeExpense(analytics);
  const showMovers = canShowMovers(analytics);
  const showAny = showFlow || showTrend || showIncomeExpense || showMovers;

  return (
    <div style={{ marginTop: 40 }}>
      <div className="section-header">
        <h2>Insights</h2>
      </div>

      {!showAny ? (
        <div className="card">
          <EmptyState message="Not enough activity in this period to show trends yet." />
        </div>
      ) : (
        <div className="analytics-grid">
          {showFlow && (
            <div className="card">
              <h3 className="chart-card__title">Spending by category</h3>
              <CategoryFlowChart categories={analytics.categories} />
            </div>
          )}
          {showTrend && (
            <div className="card">
              <h3 className="chart-card__title">Spending over time</h3>
              <SpendingTrendChart buckets={analytics.buckets} />
            </div>
          )}
          {showIncomeExpense && (
            <div className="card">
              <h3 className="chart-card__title">Income vs. expense</h3>
              <IncomeExpenseChart buckets={analytics.buckets} />
            </div>
          )}
          {showMovers && (
            <div className="card">
              <h3 className="chart-card__title">
                Biggest movers
                {analytics.previousFrom && analytics.previousTo && (
                  <span className="chart-card__subtitle">
                    {" "}
                    vs. {formatDate(analytics.previousFrom)} – {formatDate(analytics.previousTo)}
                  </span>
                )}
              </h3>
              <CategoryMoversChart categories={analytics.categories} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
