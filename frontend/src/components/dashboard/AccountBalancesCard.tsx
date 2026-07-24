import type { AccountBalances } from "../../types/transaction";
import { formatCurrency } from "../../lib/format";

interface AccountBalancesCardProps {
  balances: AccountBalances;
}

export function AccountBalancesCard({ balances }: AccountBalancesCardProps) {
  const rows: Array<[string, number]> = [
    ["Checking", balances.checking],
    ["Savings", balances.savings],
    ["Investing", balances.investing],
  ];

  return (
    <div className="card">
      <div className="section-header">
        <h2>Balances by Account</h2>
      </div>
      {rows.map(([label, value]) => (
        <div
          key={label}
          style={{ display: "flex", justifyContent: "space-between", padding: "10px 0" }}
        >
          <span>{label}</span>
          <strong>{formatCurrency(value)}</strong>
        </div>
      ))}
    </div>
  );
}
