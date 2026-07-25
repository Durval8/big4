export interface NewsItem {
  symbol: string;
  headline: string;
  summary: string;
  url: string;
  source: string;
  publishedAt: string;
}

export interface NewsFeed {
  updatedAt: string | null;
  items: NewsItem[];
}
