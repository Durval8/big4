import { useState } from "react";
import type { Investment } from "../../types/investment";
import { Button } from "../common/Button";
import { formatCurrency } from "../../lib/format";

interface CashOutDialogProps {
  investment: Investment;
  onCancel: () => void;
  onConfirm: (percentage: number) => Promise<void>;
}

const QUICK_PERCENTAGES = [25, 50, 75, 100];

export function CashOutDialog({ investment, onCancel, onConfirm }: CashOutDialogProps) {
  const [percentage, setPercentage] = useState<number | "">(100);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConfirm() {
    if (percentage === "" || percentage <= 0 || percentage > 100) {
      setError("Enter a percentage greater than 0 and at most 100.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onConfirm(percentage);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cash out");
      setSubmitting(false);
    }
  }

  // Proceeds are computed fresh server-side from the live price at execution time, so this is an
  // estimate only — it can differ slightly if the price moves between now and confirming.
  const estimatedProceeds =
    percentage === "" ? null : (investment.currentValue * percentage) / 100;

  return (
    <div className="dialog-overlay" onClick={onCancel}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h3>Cash out {investment.stockSymbol}</h3>
        <p>
          Moves the proceeds to your savings account. Current position:{" "}
          {formatCurrency(investment.currentValue)}.
        </p>
        {error && <div className="error-banner">{error}</div>}
        <div className="quick-percentages">
          {QUICK_PERCENTAGES.map((pct) => (
            <button
              key={pct}
              type="button"
              className={`quick-percentages__option${percentage === pct ? " quick-percentages__option--active" : ""}`}
              onClick={() => setPercentage(pct)}
            >
              {pct === 100 ? "All" : `${pct}%`}
            </button>
          ))}
        </div>
        <div className="field">
          <label htmlFor="cash-out-percentage">Percentage to cash out</label>
          <input
            id="cash-out-percentage"
            type="number"
            step="1"
            min="0.01"
            max="100"
            inputMode="decimal"
            required
            value={percentage}
            onChange={(e) => setPercentage(e.target.value === "" ? "" : Number(e.target.value))}
          />
        </div>
        {estimatedProceeds !== null && (
          <p className="field-hint">Estimated proceeds: {formatCurrency(estimatedProceeds)}</p>
        )}
        <div className="dialog-actions">
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
          <Button onClick={handleConfirm} disabled={submitting}>
            {submitting ? "Cashing out…" : "Cash out to savings"}
          </Button>
        </div>
      </div>
    </div>
  );
}
