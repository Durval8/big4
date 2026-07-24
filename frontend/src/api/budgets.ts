import { apiClient } from "./client";
import type { Budget, BudgetInput, BudgetProgress } from "../types/budget";
import type { TimeRange } from "../types/transaction";

export const budgetsApi = {
  list: () => apiClient.get<Budget[]>("/api/budgets"),
  progress: (range: TimeRange) =>
    apiClient.get<BudgetProgress[]>(`/api/budgets/progress?range=${range}`),
  create: (input: BudgetInput) => apiClient.post<Budget>("/api/budgets", input),
  update: (id: number, input: BudgetInput) => apiClient.put<Budget>(`/api/budgets/${id}`, input),
  remove: (id: number) => apiClient.del(`/api/budgets/${id}`),
};
