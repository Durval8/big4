import { useCallback, useEffect, useState } from "react";
import { analyticsApi } from "../api/analytics";
import {
  TARGET_PERIODS,
  WINDOW_DAYS,
  isoDate,
  regroupBuckets,
  trimToRecentActivity,
} from "../components/charts/bucketUtils";
import type { BucketUnit, TimeBucket } from "../types/analytics";

export interface IncomeExpenseSeries {
  buckets: TimeBucket[];
  unit: BucketUnit;
}

/**
 * The income-vs-expenses series, on its own clock.
 *
 * This chart deliberately ignores the page's time-range selector. Comparing money in against
 * money out is a question about *rhythm* — does a normal month cover itself? — and the useful
 * answer is the last N periods at a granularity the reader chooses, not whatever window the rest
 * of the page happens to be showing. Tying it to the shared range also made the bar count lurch
 * between 7 and 31 for reasons that had nothing to do with the comparison.
 *
 * So the granularity picks the window (see `WINDOW_DAYS`), the response is regrouped if the
 * backend's bucketing disagrees, and the series is trimmed to the most recent stretch of real
 * activity.
 */
export function useIncomeExpenseSeries(granularity: BucketUnit) {
  const [series, setSeries] = useState<IncomeExpenseSeries | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const to = new Date();
      const from = new Date();
      from.setDate(from.getDate() - (WINDOW_DAYS[granularity] - 1));

      const analytics = await analyticsApi.getWindow(isoDate(from), isoDate(to));
      const regrouped = regroupBuckets(analytics.buckets, analytics.bucketUnit, granularity);
      setSeries({
        buckets: trimToRecentActivity(regrouped, TARGET_PERIODS[granularity]),
        unit: granularity,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load income and expenses");
    } finally {
      setLoading(false);
    }
  }, [granularity]);

  useEffect(() => {
    reload();
  }, [reload]);

  return { series, loading, error, reload };
}
