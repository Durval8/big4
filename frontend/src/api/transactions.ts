import { apiClient } from "./client";
import type { PageResponse, Transaction, TransactionFilters, TransactionInput } from "../types/transaction";

function buildQuery(filters: TransactionFilters, page: number): string {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.accountType) params.set("accountType", filters.accountType);
  if (filters.category) params.set("category", filters.category);
  if (filters.sortBy) params.set("sortBy", filters.sortBy);
  if (filters.sortDir) params.set("sortDir", filters.sortDir);
  params.set("page", String(page));
  return `?${params.toString()}`;
}

export const transactionsApi = {
  list: (filters: TransactionFilters = {}, page = 0) =>
    apiClient.get<PageResponse<Transaction>>(`/api/transactions${buildQuery(filters, page)}`),
  create: (input: TransactionInput) => apiClient.post<Transaction>("/api/transactions", input),
  update: (id: number, input: TransactionInput) =>
    apiClient.put<Transaction>(`/api/transactions/${id}`, input),
  remove: (id: number) => apiClient.del(`/api/transactions/${id}`),
};
