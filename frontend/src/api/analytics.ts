import { apiClient } from "./client";
import type { Analytics } from "../types/analytics";
import type { TimeRange } from "../types/transaction";

export const analyticsApi = {
  get: (range: TimeRange) => apiClient.get<Analytics>(`/api/analytics?range=${range}`),
};
