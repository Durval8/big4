import { apiClient } from "./client";
import type {
  Investment,
  InvestmentInput,
  InvestmentSummary,
  InvestmentUpdateInput,
} from "../types/investment";

export const investmentsApi = {
  list: () => apiClient.get<Investment[]>("/api/investments"),
  summary: () => apiClient.get<InvestmentSummary>("/api/investments/summary"),
  create: (input: InvestmentInput) => apiClient.post<Investment>("/api/investments", input),
  update: (id: number, input: InvestmentUpdateInput) =>
    apiClient.put<Investment>(`/api/investments/${id}`, input),
  cashOut: (id: number, amount: number) =>
    apiClient.post<Investment>(`/api/investments/${id}/cash-out`, { amount }),
  remove: (id: number) => apiClient.del(`/api/investments/${id}`),
};
