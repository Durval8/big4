export const ACCOUNT_TYPES = ["CHECKING", "SAVINGS", "INVESTING"] as const;
export type AccountType = (typeof ACCOUNT_TYPES)[number];

// Accounts selectable on a transaction. INVESTING is no longer a transaction
// account — it's the Investments entity, and its balance reflects holdings.
export const TRANSACTION_ACCOUNT_TYPES = ["CHECKING", "SAVINGS"] as const;

export const TRANSACTION_TYPES = ["INCOME", "EXPENSE", "TRANSFER", "ADJUSTMENT"] as const;
export type TransactionType = (typeof TRANSACTION_TYPES)[number];

export const CATEGORIES = [
  "GROCERIES",
  "TRANSPORTATION",
  "DINING_OUT",
  "UTILITIES",
  "HOUSING",
  "HEALTHCARE",
  "ENTERTAINMENT",
  "SHOPPING",
  "TRAVEL",
  "SUBSCRIPTIONS",
  "INSURANCE",
  "SALARY",
  "FREELANCE_INCOME",
  "INVESTMENT_INCOME",
  "GIFTS",
  "OTHER_INCOME",
  "OTHER_EXPENSE",
] as const;
export type Category = (typeof CATEGORIES)[number];

export const TIME_RANGES = ["WEEK", "MONTH", "YEAR", "ALL"] as const;
export type TimeRange = (typeof TIME_RANGES)[number];

/** Category only applies to INCOME/EXPENSE; TRANSFER needs a linkedAccountType instead. */
export function categoryApplies(type: TransactionType): boolean {
  return type === "INCOME" || type === "EXPENSE";
}

export function transferApplies(type: TransactionType): boolean {
  return type === "TRANSFER";
}

export interface Transaction {
  id: number;
  description: string;
  amount: number;
  transactionDate: string; // ISO date (yyyy-MM-dd)
  accountType: AccountType;
  linkedAccountType: AccountType | null;
  category: Category | null;
  transactionType: TransactionType;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionInput {
  description: string;
  amount: number;
  transactionDate: string;
  accountType: AccountType;
  linkedAccountType: AccountType | null;
  category: Category | null;
  transactionType: TransactionType;
}

export interface AccountBalances {
  checking: number;
  savings: number;
  investing: number;
}

export interface BalanceSummary {
  from: string;
  to: string;
  netWorth: number;
  spending: number;
  netSpending: number;
  netInvestment: number;
  accountBalances: AccountBalances;
}

export interface TransactionFilters {
  from?: string;
  to?: string;
  accountType?: AccountType;
  category?: Category;
}
