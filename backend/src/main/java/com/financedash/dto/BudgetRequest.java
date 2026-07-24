package com.financedash.dto;

import com.financedash.domain.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Set;

public record BudgetRequest(
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0.01") BigDecimal value,
        @NotEmpty Set<Category> categories
) {
}
