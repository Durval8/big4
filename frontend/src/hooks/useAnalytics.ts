import { useCallback, useEffect, useState } from "react";
import { analyticsApi } from "../api/analytics";
import type { Analytics } from "../types/analytics";
import type { TimeRange } from "../types/transaction";

/** Loads the Dashboard's spending-visualization data for the given (shared) time range. */
export function useAnalytics(range: TimeRange) {
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setAnalytics(await analyticsApi.get(range));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load analytics");
    } finally {
      setLoading(false);
    }
  }, [range]);

  useEffect(() => {
    reload();
  }, [reload]);

  return { analytics, loading, error, reload };
}
