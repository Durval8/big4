package com.financedash.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Cash out (partial or full) an amount from a holding into the SAVINGS account. */
public record CashOutRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {
}
