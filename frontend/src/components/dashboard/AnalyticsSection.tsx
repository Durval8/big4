import { Link } from "react-router-dom";
import { useAnalytics } from "../../hooks/useAnalytics";
import { formatCurrency, formatDate } from "../../lib/format";
import { EmptyState } from "../common/EmptyState";
import { SpendingDonut } from "../charts/SpendingDonut";
import { SpendingTrendChart } from "../charts/SpendingTrendChart";
import { CategoryMoversChart } from "../charts/CategoryMoversChart";
import { canShowCategoryFlow, canShowMovers, canShowTrend } from "../charts/thresholds";
import type { TimeRange } from "../../types/transaction";

interface AnalyticsSectionProps {
  range: TimeRange;
}

/**
 * The dashboard's condensed read on spending. The full cash-flow diagram and the income
 * comparison live on Reports — this section deliberately carries only the two questions worth
 * answering without leaving the dashboard ("what did I spend it on" and "what changed"), and
 * links out for the rest rather than duplicating it.
 *
 * Each chart independently decides whether it clears its render threshold; if none do, the
 * section shows a single empty state rather than three empty boxes. `range=WEEK` frequently
 * showing few or no charts is intended — the section earns its density as the window widens.
 */
export function AnalyticsSection({ range }: AnalyticsSectionProps) {
  const { analytics, loading, error } = useAnalytics(range);

  if (loading && !analytics) {
    return (
      <div className="dashboard-section">
        <div className="section-header">
          <h2>Insights</h2>
        </div>
        <p>Loading…</p>
      </div>
    );
  }

  if (error || !analytics) {
    return (
      <div className="dashboard-section">
        <div className="section-header">
          <h2>Insights</h2>
        </div>
        {error && <div className="error-banner">{error}</div>}
      </div>
    );
  }

  const showSpending = canShowCategoryFlow(analytics);
  const showTrend = canShowTrend(analytics);
  const showMovers = canShowMovers(analytics);
  const showAny = showSpending || showTrend || showMovers;

  return (
    <div className="dashboard-section">
      <div className="section-header">
        <h2>Insights</h2>
        <Link className="section-header__link" to="/reports">
          Full reports →
        </Link>
      </div>

      {!showAny ? (
        <div className="card">
          <EmptyState message="Not enough activity in this period to show trends yet." />
        </div>
      ) : (
        <div className="report-stack">
          {showSpending && (
            <section className="card">
              <header className="card-head">
                <h3 className="card-head__title">Spending by category</h3>
                <span className="card-head__meta">{formatCurrency(analytics.totalExpense)} total</span>
              </header>
              <SpendingDonut categories={analytics.categories} />
            </section>
          )}

          <div className="report-split">
            {showTrend && (
              <section className="card">
                <header className="card-head">
                  <h3 className="card-head__title">Spending over time</h3>
                </header>
                <SpendingTrendChart buckets={analytics.buckets} />
              </section>
            )}
            {showMovers && (
              <section className="card">
                <header className="card-head">
                  <h3 className="card-head__title">Biggest movers</h3>
                  {analytics.previousFrom && analytics.previousTo && (
                    <span className="card-head__meta">
                      vs. {formatDate(analytics.previousFrom)} – {formatDate(analytics.previousTo)}
                    </span>
                  )}
                </header>
                <CategoryMoversChart categories={analytics.categories} />
              </section>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
