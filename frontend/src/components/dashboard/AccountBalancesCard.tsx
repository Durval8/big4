import type { AccountBalances } from "../../types/transaction";
import { formatCurrency } from "../../lib/format";

interface AccountBalancesCardProps {
  balances: AccountBalances;
}

const ACCOUNTS: Array<{ key: keyof AccountBalances; label: string; icon: string }> = [
  { key: "checking", label: "Checking", icon: "💳" },
  { key: "savings", label: "Savings", icon: "🏦" },
  { key: "investing", label: "Investing", icon: "📈" },
];

export function AccountBalancesCard({ balances }: AccountBalancesCardProps) {
  return (
    <div className="accounts-panel">
      <div className="accounts-panel__title">Balances by Account</div>
      <div className="accounts-row">
        {ACCOUNTS.map(({ key, label, icon }) => (
          <div className={`account-tile account-tile--${key}`} key={key}>
            <span className="account-tile__label">
              <span className="account-tile__icon" aria-hidden="true">
                {icon}
              </span>
              {label}
            </span>
            <span className="account-tile__value">{formatCurrency(balances[key])}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
