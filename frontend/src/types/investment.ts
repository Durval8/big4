import type { AccountType } from "./transaction";

export const INVESTMENT_STATUSES = ["OPEN", "CASHED_OUT"] as const;
export type InvestmentStatus = (typeof INVESTMENT_STATUSES)[number];

export const PRICE_STATUSES = ["OK", "STALE", "UNRESOLVED"] as const;
export type PriceStatus = (typeof PRICE_STATUSES)[number];

export interface Investment {
  id: string;
  stockSymbol: string;
  quantity: number;
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

/** Buy by money amount from a cash account. manualPrice is only used for unlisted symbols. */
export interface InvestmentInput {
  stockSymbol: string;
  amount: number;
  sourceAccount: AccountType; // CHECKING or SAVINGS
  manualPrice?: number;
}

/** A data-entry correction — prices are the source of truth now, so you edit shares, not value. */
export interface InvestmentCorrectionInput {
  stockSymbol: string;
  quantity: number;
}

export interface ManualPriceInput {
  price: number;
}

export interface InvestmentSummary {
  totalNetInvested: number;
  totalCurrentValue: number;
  totalRealizedGain: number;
  positionChangePct: number | null;
}
