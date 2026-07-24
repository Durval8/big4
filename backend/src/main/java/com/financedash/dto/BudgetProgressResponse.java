package com.financedash.dto;

import com.financedash.domain.Category;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * A budget plus its computed spend for a period. {@code spent} is the sum of EXPENSE
 * transactions in the budget's categories over [{@code from}, {@code to}];
 * {@code remaining} is {@code value - spent} (may be negative when over budget).
 */
public record BudgetProgressResponse(
        Long id,
        String name,
        BigDecimal value,
        Set<Category> categories,
        BigDecimal spent,
        BigDecimal remaining,
        LocalDate from,
        LocalDate to
) {
}
