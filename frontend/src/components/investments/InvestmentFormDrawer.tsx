import { useState, type FormEvent } from "react";
import { TRANSACTION_ACCOUNT_TYPES, type AccountType } from "../../types/transaction";
import type { Investment, InvestmentCorrectionInput, InvestmentInput } from "../../types/investment";
import { Button } from "../common/Button";
import { TextField } from "../common/TextField";
import { CurrencyInput } from "../common/CurrencyInput";
import { Select } from "../common/Select";
import { formatEnumLabel } from "../../lib/format";

interface InvestmentFormDrawerProps {
  investment: Investment | null; // null = buy, else edit (correction)
  onClose: () => void;
  onCreate: (input: InvestmentInput) => Promise<void>;
  onUpdate: (id: string, input: InvestmentCorrectionInput) => Promise<void>;
}

export function InvestmentFormDrawer({ investment, onClose, onCreate, onUpdate }: InvestmentFormDrawerProps) {
  const editing = investment !== null;
  const [stockSymbol, setStockSymbol] = useState(investment?.stockSymbol ?? "");
  const [amount, setAmount] = useState<number | "">("");
  const [sourceAccount, setSourceAccount] = useState<AccountType>("CHECKING");
  const [manualPrice, setManualPrice] = useState<number | "">("");
  const [quantity, setQuantity] = useState<number | "">(editing ? investment.quantity : "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      if (editing) {
        if (quantity === "") return;
        await onUpdate(investment.id, { stockSymbol, quantity });
      } else {
        if (amount === "") return;
        await onCreate({
          stockSymbol,
          amount,
          sourceAccount,
          manualPrice: manualPrice === "" ? undefined : manualPrice,
        });
      }
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save investment");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="drawer-overlay" onClick={onClose}>
      <div className="drawer" onClick={(e) => e.stopPropagation()}>
        <h2>{editing ? "Edit Investment" : "Buy Investment"}</h2>
        {error && <div className="error-banner">{error}</div>}
        <form onSubmit={handleSubmit}>
          <TextField
            label="Stock"
            value={stockSymbol}
            onChange={(e) => setStockSymbol(e.target.value)}
            placeholder="e.g. AAPL"
            required
          />

          {editing ? (
            <>
              <TextField
                label="Shares"
                type="number"
                step="0.000001"
                value={quantity === "" ? "" : String(quantity)}
                onChange={(e) => setQuantity(e.target.value === "" ? "" : Number(e.target.value))}
                required
              />
              <p className="field-hint">
                A correction only — prices are fetched automatically, so this adjusts your share count
                without moving cash.
              </p>
            </>
          ) : (
            <>
              <CurrencyInput label="Amount to invest" value={amount} onChange={setAmount} required />
              <Select
                label="Fund from"
                value={sourceAccount}
                onChange={(v) => setSourceAccount(v as AccountType)}
                options={TRANSACTION_ACCOUNT_TYPES.map((a) => ({ value: a, label: formatEnumLabel(a) }))}
              />
              <CurrencyInput
                label="Manual price (optional)"
                value={manualPrice}
                onChange={setManualPrice}
              />
              <p className="field-hint">
                Shares are computed from the live price. Set a manual price only for a symbol the price
                provider doesn't list.
              </p>
            </>
          )}

          <div className="drawer-actions">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? "Saving…" : editing ? "Save" : "Buy"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
