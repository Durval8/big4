import { useState } from "react";
import { useAnalytics } from "../hooks/useAnalytics";
import { TimeRangeSelector } from "../components/layout/TimeRangeSelector";
import { KpiCard } from "../components/reports/KpiCard";
import { CashFlowSankey } from "../components/charts/CashFlowSankey";
import { SpendingDonut } from "../components/charts/SpendingDonut";
import { SpendingTrendChart } from "../components/charts/SpendingTrendChart";
import { CategoryMoversChart } from "../components/charts/CategoryMoversChart";
import { IncomeExpenseCard } from "../components/reports/IncomeExpenseCard";
import { EmptyState } from "../components/common/EmptyState";
import { CurrencyValue } from "../components/common/CurrencyValue";
import { formatCurrency, formatDate, formatPercent } from "../lib/format";
import type { TimeRange } from "../types/transaction";

type Tab = "cashflow" | "spending";

const TABS: { id: Tab; label: string }[] = [
  { id: "cashflow", label: "Cash flow" },
  { id: "spending", label: "Spending" },
];

/**
 * The reporting surface: a KPI row that stays put, and tabs that swap the analysis beneath it.
 * Both tabs read one `/api/analytics` response, so switching between them costs no request and
 * the numbers can never disagree across tabs.
 */
export function ReportsPage() {
  const [range, setRange] = useState<TimeRange>("MONTH");
  const [tab, setTab] = useState<Tab>("cashflow");
  const { analytics, loading, error } = useAnalytics(range);

  const netIncome = analytics ? analytics.totalIncome - analytics.totalExpense : 0;
  // Undefined rather than 0 when nothing came in: "0% saved" and "no income to save from" are
  // different facts, and formatPercent already renders null as an em dash.
  const savingsRate =
    analytics && analytics.totalIncome > 0 ? (netIncome / analytics.totalIncome) * 100 : null;

  return (
    <div className="page">
      <div className="section-header">
        <h2>Reports</h2>
        <TimeRangeSelector value={range} onChange={setRange} />
      </div>

      {error && <div className="error-banner">{error}</div>}
      {loading && !analytics && <p>Loading…</p>}

      {analytics && (
        <>
          <div className="kpi-row">
            <KpiCard
              label="Total income"
              value={<CurrencyValue amount={analytics.totalIncome} />}
              tone="positive"
            />
            <KpiCard
              label="Total expenses"
              value={<CurrencyValue amount={analytics.totalExpense} />}
              tone="negative"
            />
            <KpiCard
              label="Net income"
              value={<CurrencyValue amount={netIncome} />}
              tone={netIncome >= 0 ? "positive" : "negative"}
            />
            <KpiCard
              label="Savings rate"
              value={formatPercent(savingsRate)}
              hint={savingsRate === null ? "no income this period" : undefined}
            />
          </div>

          <div className="report-tabs" role="tablist" aria-label="Report type">
            {TABS.map((t) => (
              <button
                key={t.id}
                type="button"
                role="tab"
                id={`tab-${t.id}`}
                aria-selected={tab === t.id}
                aria-controls={`panel-${t.id}`}
                className={`report-tabs__tab${tab === t.id ? " report-tabs__tab--active" : ""}`}
                onClick={() => setTab(t.id)}
              >
                {t.label}
              </button>
            ))}
            <span className="report-tabs__window">
              {formatDate(analytics.from)} – {formatDate(analytics.to)}
            </span>
          </div>

          {tab === "cashflow" ? (
            <div id="panel-cashflow" role="tabpanel" aria-labelledby="tab-cashflow" className="report-stack">
              <section className="card card--chart">
                <header className="card-head">
                  <h3 className="card-head__title">Where the money went</h3>
                  <span className="card-head__meta">
                    {formatCurrency(analytics.totalIncome)} in ·{" "}
                    {formatCurrency(analytics.totalExpense)} out
                  </span>
                </header>
                {analytics.totalIncome > 0 || analytics.totalExpense > 0 ? (
                  <CashFlowSankey
                    incomeCategories={analytics.incomeCategories}
                    categories={analytics.categories}
                    totalIncome={analytics.totalIncome}
                    totalExpense={analytics.totalExpense}
                  />
                ) : (
                  <EmptyState message="No income or spending in this period yet." />
                )}
              </section>

              {/* Owns its own granularity and window — see IncomeExpenseCard. */}
              <IncomeExpenseCard />
            </div>
          ) : (
            <div id="panel-spending" role="tabpanel" aria-labelledby="tab-spending" className="report-stack">
              <section className="card card--chart">
                <header className="card-head">
                  <h3 className="card-head__title">Spending by category</h3>
                  <span className="card-head__meta">{formatCurrency(analytics.totalExpense)} total</span>
                </header>
                {analytics.categories.length > 0 ? (
                  <SpendingDonut categories={analytics.categories} />
                ) : (
                  <EmptyState message="No spending in this period yet." />
                )}
              </section>

              <div className="report-split">
                <section className="card card--chart">
                  <header className="card-head">
                    <h3 className="card-head__title">Spending over time</h3>
                  </header>
                  {analytics.buckets.length >= 2 ? (
                    <SpendingTrendChart buckets={analytics.buckets} />
                  ) : (
                    <EmptyState message="Needs at least two periods to plot a trend." />
                  )}
                </section>

                <section className="card card--chart">
                  <header className="card-head">
                    <h3 className="card-head__title">Biggest movers</h3>
                    {analytics.previousFrom && analytics.previousTo && (
                      <span className="card-head__meta">
                        vs. {formatDate(analytics.previousFrom)} – {formatDate(analytics.previousTo)}
                      </span>
                    )}
                  </header>
                  {analytics.previousFrom ? (
                    <CategoryMoversChart categories={analytics.categories} />
                  ) : (
                    <EmptyState message="No earlier period to compare against." />
                  )}
                </section>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
