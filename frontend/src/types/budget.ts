import type { Category } from "./transaction";

export interface Budget {
  id: number;
  name: string;
  value: number;
  categories: Category[];
  createdAt: string;
  updatedAt: string;
}

export interface BudgetInput {
  name: string;
  value: number;
  categories: Category[];
}

export interface BudgetProgress {
  id: number;
  name: string;
  value: number;
  /** `value` prorated to the length of [from, to] — the figure to display against `spent`. */
  periodValue: number;
  categories: Category[];
  spent: number;
  remaining: number;
  from: string;
  to: string;
}
