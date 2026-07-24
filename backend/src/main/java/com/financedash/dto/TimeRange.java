package com.financedash.dto;

import java.time.LocalDate;

/** Shorthand for the `range` query param on GET /api/balances, resolved to a [from, to] window. */
public enum TimeRange {
    WEEK,
    MONTH,
    YEAR,
    ALL;

    public LocalDate resolveFrom(LocalDate today) {
        return switch (this) {
            case WEEK -> today.minusWeeks(1).plusDays(1);
            case MONTH -> today.minusMonths(1).plusDays(1);
            case YEAR -> today.minusYears(1).plusDays(1);
            case ALL -> LocalDate.of(1970, 1, 1);
        };
    }
}
