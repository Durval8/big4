package com.financedash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response for {@code GET /api/analytics} — see
 * docs/superpowers/specs/2026-08-02-transaction-analytics-design.md#api-changes.
 *
 * <p>{@code from}/{@code to} always echo the FINAL window, after the one-year cap and the
 * earliest-transaction floor have been applied — never the raw {@code range} param's naive
 * resolution (e.g. never {@code 1970-01-01} for {@code ALL}). {@code previousFrom}/
 * {@code previousTo} are null when nothing precedes the window.
 */
public record AnalyticsResponse(
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo,
        BucketUnit bucketUnit,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        List<CategoryTotal> categories,
        List<TimeBucket> buckets) {}
