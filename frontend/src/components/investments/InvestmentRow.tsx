import type { Investment } from "../../types/investment";
import { formatCurrency, formatEnumLabel, formatPercent, formatPrice, formatShares } from "../../lib/format";

interface InvestmentRowProps {
  investment: Investment;
  onEdit: (investment: Investment) => void;
  onCashOut: (investment: Investment) => void;
  onSetPrice: (investment: Investment) => void;
}

export function InvestmentRow({ investment, onEdit, onCashOut, onSetPrice }: InvestmentRowProps) {
  const pct = investment.positionChangePct;
  const pctClass = pct == null ? "" : pct > 0 ? "amount--positive" : pct < 0 ? "amount--negative" : "";
  const open = investment.status === "OPEN";
  const unresolved = investment.priceStatus === "UNRESOLVED";
  const stale = investment.priceStatus === "STALE";

  return (
    <tr>
      <td data-label="Stock">{investment.stockSymbol}</td>
      <td data-label="Shares">{formatShares(investment.quantity)}</td>
      <td data-label="Avg cost">{formatPrice(investment.avgCost)}</td>
      <td data-label="Price">
        {formatPrice(investment.latestPrice)}
        {unresolved && (
          <span className="pill pill--warning" title="Not recognized by the price provider — priced manually">
            {" "}
            Unpriced
          </span>
        )}
        {stale && (
          <span className="pill pill--warning" title="Provider is failing; showing last-known price">
            {" "}
            Stale
          </span>
        )}
      </td>
      <td data-label="Value">{formatCurrency(investment.currentValue)}</td>
      <td data-label="Change" className={pctClass}>{formatPercent(pct)}</td>
      <td data-label="Status">
        <span className="pill">{formatEnumLabel(investment.status)}</span>
      </td>
      <td className="transaction-table__actions-cell">
        {open && (
          <div className="row-actions">
            {unresolved && <button onClick={() => onSetPrice(investment)}>Set price</button>}
            <button onClick={() => onEdit(investment)}>Edit</button>
            <button onClick={() => onCashOut(investment)}>Cash out</button>
          </div>
        )}
      </td>
    </tr>
  );
}
