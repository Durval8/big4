package com.financedash.dto;

import com.financedash.domain.Category;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * A budget plus its computed spend for a period. {@code value} is always the budget's raw
 * monthly target (safe to reuse as an edit-form's initial value); {@code periodValue} is
 * {@code value} scaled to the length of [{@code from}, {@code to}] — this is the figure to
 * display against {@code spent}. {@code spent} is the sum of EXPENSE transactions in the
 * budget's categories over [{@code from}, {@code to}]; {@code remaining} is
 * {@code periodValue - spent} (may be negative when over budget).
 */
public record BudgetProgressResponse(
        Long id,
        String name,
        BigDecimal value,
        BigDecimal periodValue,
        Set<Category> categories,
        BigDecimal spent,
        BigDecimal remaining,
        LocalDate from,
        LocalDate to
) {
}
