import { useState } from "react";
import { useBudgets } from "../../hooks/useBudgets";
import { BudgetProgressCard } from "./BudgetProgressCard";
import { BudgetFormDrawer } from "./BudgetFormDrawer";
import { Button } from "../common/Button";
import { EmptyState } from "../common/EmptyState";
import type { BudgetProgress } from "../../types/budget";
import type { TimeRange } from "../../types/transaction";

interface BudgetSectionProps {
  range: TimeRange;
}

export function BudgetSection({ range }: BudgetSectionProps) {
  const { budgets, loading, error, create, update, remove } = useBudgets(range);

  const [isCreating, setIsCreating] = useState(false);
  const [editing, setEditing] = useState<BudgetProgress | null>(null);
  const [deleting, setDeleting] = useState<BudgetProgress | null>(null);

  const drawerOpen = isCreating || editing !== null;

  function closeDrawer() {
    setIsCreating(false);
    setEditing(null);
  }

  return (
    <div style={{ marginTop: 40 }}>
      <div className="section-header">
        <h2>Budgets</h2>
        <Button onClick={() => setIsCreating(true)}>Add Budget</Button>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {loading && budgets.length === 0 ? (
        <p>Loading…</p>
      ) : budgets.length === 0 ? (
        <div className="card">
          <EmptyState message="No budgets yet. Create one to track spending against a target." />
        </div>
      ) : (
        <div className="budget-grid">
          {budgets.map((budget) => (
            <BudgetProgressCard
              key={budget.id}
              budget={budget}
              onEdit={setEditing}
              onDelete={setDeleting}
            />
          ))}
        </div>
      )}

      {drawerOpen && (
        <BudgetFormDrawer
          budget={editing}
          onClose={closeDrawer}
          onSubmit={async (input) => {
            if (editing) {
              await update(editing.id, input);
            } else {
              await create(input);
            }
          }}
        />
      )}

      {deleting && (
        <div className="dialog-overlay" onClick={() => setDeleting(null)}>
          <div className="dialog" onClick={(e) => e.stopPropagation()}>
            <h3>Delete budget?</h3>
            <p>“{deleting.name}” will be permanently removed. This can't be undone.</p>
            <div className="dialog-actions">
              <Button variant="secondary" onClick={() => setDeleting(null)}>
                Cancel
              </Button>
              <Button
                variant="danger"
                onClick={async () => {
                  await remove(deleting.id);
                  setDeleting(null);
                }}
              >
                Delete
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
