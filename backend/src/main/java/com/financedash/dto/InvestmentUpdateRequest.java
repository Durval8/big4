package com.financedash.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Edit a holding: rename and/or mark-to-market its current value. No cash moves —
 * changing {@code currentValue} here is a revaluation, not a buy.
 */
public record InvestmentUpdateRequest(
        @NotBlank String stockSymbol,
        @NotNull @DecimalMin(value = "0.00") BigDecimal currentValue
) {
}
