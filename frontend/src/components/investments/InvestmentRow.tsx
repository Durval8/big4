import type { Investment } from "../../types/investment";
import { formatCurrency, formatEnumLabel, formatPercent } from "../../lib/format";

interface InvestmentRowProps {
  investment: Investment;
  onEdit: (investment: Investment) => void;
  onCashOut: (investment: Investment) => void;
}

export function InvestmentRow({ investment, onEdit, onCashOut }: InvestmentRowProps) {
  const pct = investment.positionChangePct;
  const pctClass = pct == null ? "" : pct > 0 ? "amount--positive" : pct < 0 ? "amount--negative" : "";
  const open = investment.status === "OPEN";

  return (
    <tr>
      <td>{investment.stockSymbol}</td>
      <td>{formatCurrency(investment.netCashInvested)}</td>
      <td>{formatCurrency(investment.currentValue)}</td>
      <td className={pctClass}>{formatPercent(pct)}</td>
      <td>
        <span className="pill">{formatEnumLabel(investment.status)}</span>
      </td>
      <td>
        {open && (
          <div className="row-actions">
            <button onClick={() => onEdit(investment)}>Edit</button>
            <button onClick={() => onCashOut(investment)}>Cash out</button>
          </div>
        )}
      </td>
    </tr>
  );
}
