import { TRANSACTION_ACCOUNT_TYPES, CATEGORIES, type AccountType, type Category } from "../../types/transaction";
import { formatEnumLabel } from "../../lib/format";

export interface TransactionFilterValues {
  accountType?: AccountType;
  category?: Category;
  sortBy?: "DATE" | "AMOUNT";
  sortDir?: "ASC" | "DESC";
}

interface TransactionFiltersProps {
  value: TransactionFilterValues;
  onChange: (value: TransactionFilterValues) => void;
}

export function TransactionFilters({ value, onChange }: TransactionFiltersProps) {
  return (
    <div className="filters-bar">
      <select
        value={value.accountType ?? ""}
        onChange={(e) =>
          onChange({ ...value, accountType: (e.target.value || undefined) as AccountType | undefined })
        }
      >
        <option value="">All accounts</option>
        {TRANSACTION_ACCOUNT_TYPES.map((type) => (
          <option key={type} value={type}>
            {formatEnumLabel(type)}
          </option>
        ))}
      </select>
      <select
        value={value.category ?? ""}
        onChange={(e) => onChange({ ...value, category: (e.target.value || undefined) as Category | undefined })}
      >
        <option value="">All categories</option>
        {CATEGORIES.map((category) => (
          <option key={category} value={category}>
            {formatEnumLabel(category)}
          </option>
        ))}
      </select>
      <select
        value={value.sortBy ?? "DATE"}
        onChange={(e) => onChange({ ...value, sortBy: e.target.value as "DATE" | "AMOUNT" })}
      >
        <option value="DATE">Sort by date</option>
        <option value="AMOUNT">Sort by amount</option>
      </select>
      <select
        value={value.sortDir ?? "DESC"}
        onChange={(e) => onChange({ ...value, sortDir: e.target.value as "ASC" | "DESC" })}
      >
        <option value="DESC">Descending</option>
        <option value="ASC">Ascending</option>
      </select>
    </div>
  );
}
