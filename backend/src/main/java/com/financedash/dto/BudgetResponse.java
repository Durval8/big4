package com.financedash.dto;

import com.financedash.domain.Budget;
import com.financedash.domain.Category;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record BudgetResponse(
        Long id,
        String name,
        BigDecimal value,
        Set<Category> categories,
        Instant createdAt,
        Instant updatedAt
) {
    public static BudgetResponse from(Budget b) {
        return new BudgetResponse(
                b.getId(),
                b.getName(),
                b.getValue(),
                b.getCategories(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}
