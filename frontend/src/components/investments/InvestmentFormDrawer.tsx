import { useState, type FormEvent } from "react";
import { TRANSACTION_ACCOUNT_TYPES, type AccountType } from "../../types/transaction";
import type { Investment, InvestmentInput, InvestmentUpdateInput } from "../../types/investment";
import { Button } from "../common/Button";
import { TextField } from "../common/TextField";
import { CurrencyInput } from "../common/CurrencyInput";
import { Select } from "../common/Select";
import { formatEnumLabel } from "../../lib/format";

interface InvestmentFormDrawerProps {
  investment: Investment | null; // null = add, else edit
  onClose: () => void;
  onCreate: (input: InvestmentInput) => Promise<void>;
  onUpdate: (id: number, input: InvestmentUpdateInput) => Promise<void>;
}

export function InvestmentFormDrawer({ investment, onClose, onCreate, onUpdate }: InvestmentFormDrawerProps) {
  const editing = investment !== null;
  const [stockSymbol, setStockSymbol] = useState(investment?.stockSymbol ?? "");
  const [amount, setAmount] = useState<number | "">(editing ? investment.currentValue : "");
  const [sourceAccount, setSourceAccount] = useState<AccountType>("CHECKING");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (amount === "") return;
    setSubmitting(true);
    setError(null);
    try {
      if (editing) {
        await onUpdate(investment.id, { stockSymbol, currentValue: amount });
      } else {
        await onCreate({ stockSymbol, amount, sourceAccount });
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
        <h2>{editing ? "Edit Investment" : "New Investment"}</h2>
        {error && <div className="error-banner">{error}</div>}
        <form onSubmit={handleSubmit}>
          <TextField
            label="Stock"
            value={stockSymbol}
            onChange={(e) => setStockSymbol(e.target.value)}
            placeholder="e.g. AAPL"
            required
          />
          <CurrencyInput
            label={editing ? "Current position (mark-to-market)" : "Amount"}
            value={amount}
            onChange={setAmount}
            required
          />
          {!editing && (
            <Select
              label="Fund from"
              value={sourceAccount}
              onChange={(v) => setSourceAccount(v as AccountType)}
              options={TRANSACTION_ACCOUNT_TYPES.map((a) => ({ value: a, label: formatEnumLabel(a) }))}
            />
          )}
          <div className="drawer-actions">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? "Saving…" : "Save"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
