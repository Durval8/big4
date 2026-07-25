import { apiClient } from "./client";
import type { NewsFeed } from "../types/news";

// Served by the investments service (routed there by the gateway / dev proxy).
export const newsApi = {
  list: () => apiClient.get<NewsFeed>("/api/investments/news"),
};
