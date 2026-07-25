import { apiClient } from "./client";
import type {
  Investment,
  InvestmentCorrectionInput,
  InvestmentInput,
  InvestmentSummary,
} from "../types/investment";

// Served by the investments service (routed there by the gateway / dev proxy).
export const investmentsApi = {
  list: () => apiClient.get<Investment[]>("/api/investments"),
  summary: () => apiClient.get<InvestmentSummary>("/api/investments/summary"),
  create: (input: InvestmentInput) => apiClient.post<Investment>("/api/investments", input),
  update: (id: string, input: InvestmentCorrectionInput) =>
    apiClient.put<Investment>(`/api/investments/${id}`, input),
  cashOut: (id: string, amount: number) =>
    apiClient.post<Investment>(`/api/investments/${id}/cash-out`, { amount }),
  setPrice: (id: string, price: number) =>
    apiClient.post<Investment>(`/api/investments/${id}/price`, { price }),
  remove: (id: string) => apiClient.del(`/api/investments/${id}`),
};
