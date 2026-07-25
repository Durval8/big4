import type { NewsFeed } from "../../types/news";
import { formatRelativeTime } from "../../lib/format";
import { EmptyState } from "../common/EmptyState";

interface NewsCardProps {
  feed: NewsFeed | null;
  loading: boolean;
  updating: boolean;
}

export function NewsCard({ feed, loading, updating }: NewsCardProps) {
  const items = feed?.items ?? [];

  return (
    <div className="card news-card">
      <div className="news-card__header">
        <h3>News</h3>
        <span className="news-card__meta">
          {updating
            ? "updating… new holdings' news appears within about a minute"
            : feed?.updatedAt
              ? `Updated ${formatRelativeTime(feed.updatedAt)}`
              : ""}
        </span>
      </div>

      {loading && !feed ? (
        <p>Loading…</p>
      ) : items.length === 0 ? (
        <EmptyState message="No recent news for your holdings." />
      ) : (
        <ul className="news-list">
          {items.map((item) => (
            <li key={item.url} className="news-item">
              <a className="news-item__headline" href={item.url} target="_blank" rel="noreferrer">
                {item.headline}
              </a>
              {item.summary && <p className="news-item__summary">{item.summary}</p>}
              <div className="news-item__meta">
                <span className="pill">{item.symbol}</span>
                {item.source && <span>{item.source}</span>}
                <span>{formatRelativeTime(item.publishedAt)}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
