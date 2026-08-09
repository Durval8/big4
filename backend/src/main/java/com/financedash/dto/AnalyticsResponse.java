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
 *
 * <p>{@code categories} and {@code incomeCategories} are deliberately two separate lists rather
 * than one list with a type discriminator: every existing consumer of {@code categories} (the
 * movers chart, the spending breakdown) means EXPENSE by it, and silently widening that list
 * would make an income row show up as "spending" in all of them. The cash-flow Sankey is the only
 * view that wants both, and it wants them on opposite sides of the diagram anyway.
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
        List<CategoryTotal> incomeCategories,
        List<TimeBucket> buckets) {}
