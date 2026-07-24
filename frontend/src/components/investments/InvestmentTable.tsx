import type { Investment } from "../../types/investment";
import { InvestmentRow } from "./InvestmentRow";
import { EmptyState } from "../common/EmptyState";

interface InvestmentTableProps {
  investments: Investment[];
  onEdit: (investment: Investment) => void;
  onCashOut: (investment: Investment) => void;
}

export function InvestmentTable({ investments, onEdit, onCashOut }: InvestmentTableProps) {
  if (investments.length === 0) {
    return <EmptyState message="No investments yet. Add one to start tracking a position." />;
  }

  return (
    <table className="transaction-table">
      <thead>
        <tr>
          <th>Stock</th>
          <th>Invested</th>
          <th>Current position</th>
          <th>Change</th>
          <th>Status</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {investments.map((inv) => (
          <InvestmentRow key={inv.id} investment={inv} onEdit={onEdit} onCashOut={onCashOut} />
        ))}
      </tbody>
    </table>
  );
}
