import { useState } from "react";
import type { Investment } from "../../types/investment";
import { Button } from "../common/Button";
import { CurrencyInput } from "../common/CurrencyInput";
import { formatCurrency } from "../../lib/format";

interface CashOutDialogProps {
  investment: Investment;
  onCancel: () => void;
  onConfirm: (amount: number) => Promise<void>;
}

export function CashOutDialog({ investment, onCancel, onConfirm }: CashOutDialogProps) {
  const [amount, setAmount] = useState<number | "">(investment.currentValue);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConfirm() {
    if (amount === "") return;
    if (amount > investment.currentValue) {
      setError("Amount exceeds the current position.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onConfirm(amount);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cash out");
      setSubmitting(false);
    }
  }

  return (
    <div className="dialog-overlay" onClick={onCancel}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h3>Cash out {investment.stockSymbol}</h3>
        <p>
          Moves the amount to your savings account. Current position:{" "}
          {formatCurrency(investment.currentValue)}.
        </p>
        {error && <div className="error-banner">{error}</div>}
        <CurrencyInput label="Amount to cash out" value={amount} onChange={setAmount} required />
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
