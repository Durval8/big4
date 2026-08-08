package com.financedash.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Time-bucket granularity for {@code GET /api/analytics}'s series, derived purely from the final
 * window length (never passed by the client) — see
 * docs/superpowers/specs/2026-08-02-transaction-analytics-design.md#bucket-granularity.
 *
 * <p>Deliberately has no "step down if too few buckets" rule: that was considered and dropped
 * (see the spec) because it's unreachable above 31 days and powerless below it, since {@link #DAY}
 * has nothing to step down to. Short windows are a render-threshold (frontend) concern instead.
 */
public enum BucketUnit {
    DAY,
    WEEK,
    MONTH;

    /**
     * Window length in days (inclusive) → unit: ≤31 days is {@link #DAY}, ≤26 weeks is
     * {@link #WEEK}, otherwise {@link #MONTH}. Closed at both ends because the analytics window is
     * capped at one year — a window can never be long enough to need anything coarser than MONTH.
     */
    public static BucketUnit forWindow(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 31) {
            return DAY;
        }
        long weeks = (days + 6) / 7; // ceil(days / 7)
        return weeks <= 26 ? WEEK : MONTH;
    }

    /**
     * Contiguous {@code [start, end]} boundaries (both inclusive) covering {@code [from, to]} at
     * this granularity. {@code WEEK} buckets are anchored at {@code from}, not calendar weeks;
     * {@code MONTH} buckets align to calendar months, so the first and last bucket may be partial
     * (clipped to {@code from}/{@code to}) while interior buckets are full calendar months.
     */
    public List<LocalDate[]> boundaries(LocalDate from, LocalDate to) {
        List<LocalDate[]> result = new ArrayList<>();
        LocalDate start = from;
        while (!start.isAfter(to)) {
            LocalDate end = switch (this) {
                case DAY -> start;
                case WEEK -> start.plusDays(6);
                case MONTH -> start.withDayOfMonth(start.lengthOfMonth());
            };
            if (end.isAfter(to)) {
                end = to;
            }
            result.add(new LocalDate[] {start, end});
            start = switch (this) {
                case DAY -> start.plusDays(1);
                case WEEK -> start.plusDays(7);
                case MONTH -> start.plusMonths(1).withDayOfMonth(1);
            };
        }
        return result;
    }
}
