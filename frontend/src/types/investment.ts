import type { AccountType } from "./transaction";

export const INVESTMENT_STATUSES = ["OPEN", "CASHED_OUT"] as const;
export type InvestmentStatus = (typeof INVESTMENT_STATUSES)[number];

export const PRICE_STATUSES = ["OK", "STALE", "UNRESOLVED"] as const;
export type PriceStatus = (typeof PRICE_STATUSES)[number];

export interface Investment {
  id: string;
  stockSymbol: string;
  quantity: number;
  costBasis: number;
  avgCost: number | null;
  latestPrice: number | null;
  currentValue: number;
  netCashInvested: number;
  realizedGain: number;
  positionChangePct: number | null;
  priceStatus: PriceStatus;
  priceAsOf: string | null;
  status: InvestmentStatus;
  createdAt: string;
  updatedAt: string;
}

/** Buy by money amount from a cash account. manualPrice is used as entry price for recognized symbols too. */
export interface InvestmentInput {
  stockSymbol: string;
  amount: number;
  sourceAccount: AccountType; // CHECKING or SAVINGS
  manualPrice?: number;
}

/** A data-entry correction — corrects the total amount invested; shares are re-derived from avgCost. */
export interface InvestmentCorrectionInput {
  stockSymbol: string;
  amount: number;
}

export interface ManualPriceInput {
  price: number;
}

/** Percentage (0, 100] of the current position to sell; 100 always closes the holding. */
export interface CashOutInput {
  percentage: number;
}

export interface InvestmentSummary {
  totalNetInvested: number;
  totalCurrentValue: number;
  totalRealizedGain: number;
  positionChangePct: number | null;
}
