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
      <td>{investment.stockSymbol}</td>
      <td>{formatShares(investment.quantity)}</td>
      <td>{formatPrice(investment.avgCost)}</td>
      <td>
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
      <td>{formatCurrency(investment.currentValue)}</td>
      <td className={pctClass}>{formatPercent(pct)}</td>
      <td>
        <span className="pill">{formatEnumLabel(investment.status)}</span>
      </td>
      <td>
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
