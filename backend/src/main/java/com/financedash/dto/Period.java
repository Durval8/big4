package com.financedash.dto;

import java.time.LocalDate;

/**
 * A resolved [from, to] date window. Built from the {@code range} / {@code from} /
 * {@code to} query params shared by the balances and budgets endpoints.
 */
public record Period(LocalDate from, LocalDate to) {

    /**
     * Resolves the window the same way for every endpoint: {@code range} wins if present;
     * otherwise an explicit {@code from} is used; otherwise it defaults to the last month.
     * {@code to} defaults to today.
     */
    public static Period resolve(TimeRange range, LocalDate from, LocalDate to, LocalDate today) {
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom;
        if (range != null) {
            effectiveFrom = range.resolveFrom(effectiveTo);
        } else if (from != null) {
            effectiveFrom = from;
        } else {
            effectiveFrom = TimeRange.MONTH.resolveFrom(effectiveTo);
        }
        return new Period(effectiveFrom, effectiveTo);
    }
}
