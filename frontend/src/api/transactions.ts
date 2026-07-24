import { apiClient } from "./client";
import type { Transaction, TransactionFilters, TransactionInput } from "../types/transaction";

function buildQuery(filters: TransactionFilters): string {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.accountType) params.set("accountType", filters.accountType);
  if (filters.category) params.set("category", filters.category);
  const query = params.toString();
  return query ? `?${query}` : "";
}

export const transactionsApi = {
  list: (filters: TransactionFilters = {}) =>
    apiClient.get<Transaction[]>(`/api/transactions${buildQuery(filters)}`),
  create: (input: TransactionInput) => apiClient.post<Transaction>("/api/transactions", input),
  update: (id: number, input: TransactionInput) =>
    apiClient.put<Transaction>(`/api/transactions/${id}`, input),
  remove: (id: number) => apiClient.del(`/api/transactions/${id}`),
};
