import { useState, type FormEvent } from "react";
import { CATEGORIES, type Category } from "../../types/transaction";
import type { BudgetInput, BudgetProgress } from "../../types/budget";
import { Button } from "../common/Button";
import { TextField } from "../common/TextField";
import { CurrencyInput } from "../common/CurrencyInput";
import { formatEnumLabel } from "../../lib/format";

interface BudgetFormDrawerProps {
  budget: BudgetProgress | null;
  onClose: () => void;
  onSubmit: (input: BudgetInput) => Promise<void>;
}

export function BudgetFormDrawer({ budget, onClose, onSubmit }: BudgetFormDrawerProps) {
  const [name, setName] = useState(budget?.name ?? "");
  const [value, setValue] = useState<number | "">(budget?.value ?? "");
  const [categories, setCategories] = useState<Category[]>(budget?.categories ?? []);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function toggleCategory(category: Category) {
    setCategories((current) =>
      current.includes(category)
        ? current.filter((c) => c !== category)
        : [...current, category],
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (value === "" || categories.length === 0) {
      setError("Enter an amount and select at least one category.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({ name, value, categories });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save budget");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="drawer-overlay" onClick={onClose}>
      <div className="drawer" onClick={(e) => e.stopPropagation()}>
        <h2>{budget ? "Edit Budget" : "New Budget"}</h2>
        {error && <div className="error-banner">{error}</div>}
        <form onSubmit={handleSubmit}>
          <TextField
            label="Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
          <CurrencyInput label="Budget amount" value={value} onChange={setValue} required />

          <div className="field">
            <label>Categories</label>
            <div className="checkbox-grid">
              {CATEGORIES.map((category) => (
                <label key={category}>
                  <input
                    type="checkbox"
                    checked={categories.includes(category)}
                    onChange={() => toggleCategory(category)}
                  />
                  {formatEnumLabel(category)}
                </label>
              ))}
            </div>
          </div>

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
