import { useState, type FormEvent } from "react";
import {
  ACCOUNT_TYPES,
  CATEGORIES,
  TRANSACTION_TYPES,
  categoryApplies,
  transferApplies,
  type AccountType,
  type Category,
  type Transaction,
  type TransactionInput,
  type TransactionType,
} from "../../types/transaction";
import { Button } from "../common/Button";
import { TextField } from "../common/TextField";
import { Select } from "../common/Select";
import { CurrencyInput } from "../common/CurrencyInput";
import { formatEnumLabel } from "../../lib/format";

interface TransactionFormDrawerProps {
  transaction: Transaction | null;
  onClose: () => void;
  onSubmit: (input: TransactionInput) => Promise<void>;
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export function TransactionFormDrawer({ transaction, onClose, onSubmit }: TransactionFormDrawerProps) {
  const [description, setDescription] = useState(transaction?.description ?? "");
  const [amount, setAmount] = useState<number | "">(transaction?.amount ?? "");
  const [transactionDate, setTransactionDate] = useState(transaction?.transactionDate ?? todayIso());
  const [transactionType, setTransactionType] = useState<TransactionType>(transaction?.transactionType ?? "EXPENSE");
  const [accountType, setAccountType] = useState<AccountType>(transaction?.accountType ?? "CHECKING");
  const [linkedAccountType, setLinkedAccountType] = useState<AccountType | "">(
    transaction?.linkedAccountType ?? "",
  );
  const [category, setCategory] = useState<Category | "">(transaction?.category ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const needsCategory = categoryApplies(transactionType);
  const needsLinkedAccount = transferApplies(transactionType);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (amount === "") return;
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        description,
        amount,
        transactionDate,
        accountType,
        transactionType,
        linkedAccountType: needsLinkedAccount && linkedAccountType ? linkedAccountType : null,
        category: needsCategory && category ? category : null,
      });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save transaction");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="drawer-overlay" onClick={onClose}>
      <div className="drawer" onClick={(e) => e.stopPropagation()}>
        <h2>{transaction ? "Edit Transaction" : "New Transaction"}</h2>
        {error && <div className="error-banner">{error}</div>}
        <form onSubmit={handleSubmit}>
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            required
          />
          <CurrencyInput label="Amount" value={amount} onChange={setAmount} required />
          <TextField
            label="Date"
            type="date"
            value={transactionDate}
            onChange={(e) => setTransactionDate(e.target.value)}
            required
          />
          <Select
            label="Type"
            value={transactionType}
            onChange={(v) => setTransactionType(v as TransactionType)}
            options={TRANSACTION_TYPES.map((t) => ({ value: t, label: formatEnumLabel(t) }))}
          />
          <Select
            label={needsLinkedAccount ? "From Account" : "Account"}
            value={accountType}
            onChange={(v) => setAccountType(v as AccountType)}
            options={ACCOUNT_TYPES.map((a) => ({ value: a, label: formatEnumLabel(a) }))}
          />
          {needsLinkedAccount && (
            <Select
              label="To Account"
              value={linkedAccountType}
              onChange={(v) => setLinkedAccountType(v as AccountType)}
              options={ACCOUNT_TYPES.filter((a) => a !== accountType).map((a) => ({
                value: a,
                label: formatEnumLabel(a),
              }))}
              placeholder="Select destination account"
              required
            />
          )}
          {needsCategory && (
            <Select
              label="Category"
              value={category}
              onChange={(v) => setCategory(v as Category)}
              options={CATEGORIES.map((c) => ({ value: c, label: formatEnumLabel(c) }))}
              placeholder="Select category"
              required
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
