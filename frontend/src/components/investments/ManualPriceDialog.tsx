import { useState } from "react";
import type { Investment } from "../../types/investment";
import { Button } from "../common/Button";
import { CurrencyInput } from "../common/CurrencyInput";

interface ManualPriceDialogProps {
  investment: Investment;
  onCancel: () => void;
  onConfirm: (price: number) => Promise<void>;
}

/** Sets a price by hand for an UNRESOLVED holding — the only way such a holding gets valued. */
export function ManualPriceDialog({ investment, onCancel, onConfirm }: ManualPriceDialogProps) {
  const [price, setPrice] = useState<number | "">(investment.latestPrice ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConfirm() {
    if (price === "") return;
    setSubmitting(true);
    setError(null);
    try {
      await onConfirm(price);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to set price");
      setSubmitting(false);
    }
  }

  return (
    <div className="dialog-overlay" onClick={onCancel}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h3>Set price for {investment.stockSymbol}</h3>
        <p>
          This symbol isn&apos;t recognized by the price provider, so it&apos;s priced manually.
          Value = {investment.stockSymbol} price × {investment.quantity} shares.
        </p>
        {error && <div className="error-banner">{error}</div>}
        <CurrencyInput label="Price per share" value={price} onChange={setPrice} required />
        <div className="dialog-actions">
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
          <Button onClick={handleConfirm} disabled={submitting}>
            {submitting ? "Saving…" : "Set price"}
          </Button>
        </div>
      </div>
    </div>
  );
}
