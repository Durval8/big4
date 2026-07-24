import type { BalanceSummary } from "../../types/transaction";
import { BalanceCard } from "./BalanceCard";

interface BalanceSummaryGridProps {
  summary: BalanceSummary;
}

export function BalanceSummaryGrid({ summary }: BalanceSummaryGridProps) {
  return (
    <div className="balance-grid">
      <BalanceCard label="Net Worth" value={summary.netWorth} tone="signed" />
      <BalanceCard label="Spending" value={summary.spending} />
      <BalanceCard label="Net Spending" value={summary.netSpending} />
      <BalanceCard label="Net Investment" value={summary.netInvestment} tone="signed" />
    </div>
  );
}
