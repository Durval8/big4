import { apiClient } from "./client";
import type { BalanceSummary, TimeRange } from "../types/transaction";

export const balancesApi = {
  summary: (range: TimeRange) =>
    apiClient.get<BalanceSummary>(`/api/balances?range=${range}`),
};
