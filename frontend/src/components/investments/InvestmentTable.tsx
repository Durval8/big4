import type { Investment } from "../../types/investment";
import { InvestmentRow } from "./InvestmentRow";
import { EmptyState } from "../common/EmptyState";

interface InvestmentTableProps {
  investments: Investment[];
  onEdit: (investment: Investment) => void;
  onCashOut: (investment: Investment) => void;
  onSetPrice: (investment: Investment) => void;
}

export function InvestmentTable({ investments, onEdit, onCashOut, onSetPrice }: InvestmentTableProps) {
  if (investments.length === 0) {
    return <EmptyState message="No investments yet. Add one to start tracking a position." />;
  }

  return (
    <table className="transaction-table">
      <thead>
        <tr>
          <th scope="col">Stock</th>
          <th scope="col">Shares</th>
          <th scope="col">Avg cost</th>
          <th scope="col">Price</th>
          <th scope="col">Value</th>
          <th scope="col">Change</th>
          <th scope="col">Status</th>
          <th scope="col"></th>
        </tr>
      </thead>
      <tbody>
        {investments.map((inv) => (
          <InvestmentRow
            key={inv.id}
            investment={inv}
            onEdit={onEdit}
            onCashOut={onCashOut}
            onSetPrice={onSetPrice}
          />
        ))}
      </tbody>
    </table>
  );
}
