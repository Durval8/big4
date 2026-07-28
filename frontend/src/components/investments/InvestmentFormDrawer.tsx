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
  const [amount, setAmount] = useState<number | "">(editing ? investment.costBasis : "");
  const [sourceAccount, setSourceAccount] = useState<AccountType>("CHECKING");
  const [manualPrice, setManualPrice] = useState<number | "">("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      if (amount === "") return;
      if (editing) {
        await onUpdate(investment.id, { stockSymbol, amount });
      } else {
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
              <CurrencyInput label="Amount invested" value={amount} onChange={setAmount} required />
              <p className="field-hint">
                Correction only — share count is re-derived from your entry price. No cash movement
                is recorded.
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
                Optional. If set, shares are derived from this price and the difference vs. the live
                price shows immediately as P&amp;L. Required for symbols the provider doesn't list.
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
