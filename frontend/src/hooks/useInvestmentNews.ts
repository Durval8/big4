import { useCallback, useEffect, useRef, useState } from "react";
import { newsApi } from "../api/news";
import type { NewsFeed } from "../types/news";

const POLL_INTERVAL_MS = 4000;
const POLL_MAX_TRIES = 8; // ~32s — the async rebuild makes rate-limited fetches, so give it a window

/**
 * Loads the news feed and, after a held-set change, polls until the feed's {@code updatedAt}
 * advances (the rebuild is asynchronous, so an immediate refetch would return the stale feed).
 */
export function useInvestmentNews() {
  const [feed, setFeed] = useState<NewsFeed | null>(null);
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);
  const feedRef = useRef<NewsFeed | null>(null);

  const apply = useCallback((f: NewsFeed) => {
    feedRef.current = f;
    setFeed(f);
  }, []);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      apply(await newsApi.list());
    } catch {
      // leave the last feed in place; the card handles a null/empty feed gracefully
    } finally {
      setLoading(false);
    }
  }, [apply]);

  useEffect(() => {
    reload();
  }, [reload]);

  /** Poll until updatedAt advances past what we currently show (or the window elapses). */
  const pollForUpdate = useCallback(async () => {
    const previous = feedRef.current?.updatedAt ?? null;
    setUpdating(true);
    try {
      for (let i = 0; i < POLL_MAX_TRIES; i++) {
        await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS));
        try {
          const next = await newsApi.list();
          if (next.updatedAt && next.updatedAt !== previous) {
            apply(next);
            return;
          }
        } catch {
          // transient; keep polling
        }
      }
    } finally {
      setUpdating(false);
    }
  }, [apply]);

  return { feed, loading, updating, reload, pollForUpdate };
}
