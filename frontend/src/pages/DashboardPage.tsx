import { useState } from "react";
import { useBalances } from "../hooks/useBalances";
import { TimeRangeSelector } from "../components/layout/TimeRangeSelector";
import { BalanceSummaryGrid } from "../components/dashboard/BalanceSummaryGrid";
import { AccountBalancesCard } from "../components/dashboard/AccountBalancesCard";
import { BudgetSection } from "../components/dashboard/BudgetSection";
import type { TimeRange } from "../types/transaction";

export function DashboardPage() {
  const [range, setRange] = useState<TimeRange>("MONTH");
  const { summary, loading, error } = useBalances(range);

  return (
    <div>
      <div className="section-header">
        <h2>Overview</h2>
        <TimeRangeSelector value={range} onChange={setRange} />
      </div>
      {error && <div className="error-banner">{error}</div>}
      {loading && !summary ? (
        <p>Loading…</p>
      ) : summary ? (
        <>
          <BalanceSummaryGrid summary={summary} />
          <AccountBalancesCard balances={summary.accountBalances} />
        </>
      ) : null}

      <BudgetSection range={range} />
    </div>
  );
}
