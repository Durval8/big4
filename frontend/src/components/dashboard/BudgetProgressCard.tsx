import type { BudgetProgress } from "../../types/budget";
import { formatCurrency, formatEnumLabel } from "../../lib/format";

interface BudgetProgressCardProps {
  budget: BudgetProgress;
  onEdit: (budget: BudgetProgress) => void;
  onDelete: (budget: BudgetProgress) => void;
}

export function BudgetProgressCard({ budget, onEdit, onDelete }: BudgetProgressCardProps) {
  const over = budget.spent > budget.periodValue;
  const pct = budget.periodValue > 0 ? Math.min(100, (budget.spent / budget.periodValue) * 100) : 0;

  return (
    <div className="card">
      <div className="budget-card__header">
        <span className="budget-card__name">{budget.name}</span>
        <div className="row-actions">
          <button onClick={() => onEdit(budget)}>Edit</button>
          <button className="danger" onClick={() => onDelete(budget)}>
            Delete
          </button>
        </div>
      </div>

      <div className="budget-card__amounts">
        <strong>{formatCurrency(budget.spent)}</strong> of {formatCurrency(budget.periodValue)}
      </div>

      <div className="progress-track">
        <div
          className={`progress-fill${over ? " progress-fill--over" : ""}`}
          style={{ width: `${pct}%` }}
        />
      </div>

      <div className="budget-card__footer">
        <span className={`budget-card__remaining${over ? " budget-card__remaining--over" : ""}`}>
          {over
            ? `${formatCurrency(Math.abs(budget.remaining))} over`
            : `${formatCurrency(budget.remaining)} left`}
        </span>
      </div>

      <div className="budget-card__categories">
        {budget.categories.map((c) => (
          <span key={c} className="pill">
            {formatEnumLabel(c)}
          </span>
        ))}
      </div>
    </div>
  );
}
