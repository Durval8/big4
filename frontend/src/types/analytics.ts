import type { Category } from "./transaction";

export const BUCKET_UNITS = ["DAY", "WEEK", "MONTH"] as const;
export type BucketUnit = (typeof BUCKET_UNITS)[number];

export interface CategoryTotal {
  category: Category;
  amount: number;
  /** Null when no prior period exists at all (not the same as 0 — see thresholds.ts). */
  previousAmount: number | null;
}

export interface TimeBucket {
  start: string; // ISO date
  income: number;
  expense: number;
}

export interface Analytics {
  from: string;
  to: string;
  /** Both null together when nothing precedes the window. */
  previousFrom: string | null;
  previousTo: string | null;
  bucketUnit: BucketUnit;
  totalIncome: number;
  totalExpense: number;
  /** EXPENSE only, desc by amount. A category with no spend in this window but some in the prior
   * one still appears (amount: 0), so the movers chart can show the drop. */
  categories: CategoryTotal[];
  /** INCOME only, desc by amount — the cash-flow diagram's source side. Deliberately a separate
   * list from `categories`: every other consumer reads that one and means "spending" by it. */
  incomeCategories: CategoryTotal[];
  /** Gap-filled — every bucket in the window appears, including zero-activity ones. */
  buckets: TimeBucket[];
}
