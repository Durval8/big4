import type { AccountType } from "./transaction";

export const INVESTMENT_STATUSES = ["OPEN", "CASHED_OUT"] as const;
export type InvestmentStatus = (typeof INVESTMENT_STATUSES)[number];

export interface Investment {
  id: number;
  stockSymbol: string;
  currentValue: number;
  netCashInvested: number;
  positionChangePct: number | null;
  status: InvestmentStatus;
  createdAt: string;
  updatedAt: string;
}

export interface InvestmentInput {
  stockSymbol: string;
  amount: number;
  sourceAccount: AccountType; // CHECKING or SAVINGS
}

export interface InvestmentUpdateInput {
  stockSymbol: string;
  currentValue: number;
}

export interface InvestmentSummary {
  totalNetInvested: number;
  totalCurrentValue: number;
  positionChangePct: number | null;
}
