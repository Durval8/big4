import { apiClient } from "./client";
import type { Analytics } from "../types/analytics";
import type { TimeRange } from "../types/transaction";

export const analyticsApi = {
  get: (range: TimeRange) => apiClient.get<Analytics>(`/api/analytics?range=${range}`),

  /**
   * Explicit-window variant. Passing `from` also opts out of the endpoint's
   * earliest-transaction floor, so the window comes back exactly as asked (still subject to the
   * one-year cap) — which is what the income-vs-expenses chart needs to pick its own bucket size.
   */
  getWindow: (from: string, to: string) =>
    apiClient.get<Analytics>(`/api/analytics?from=${from}&to=${to}`),
};
