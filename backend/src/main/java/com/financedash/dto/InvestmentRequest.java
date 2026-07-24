package com.financedash.dto;

import com.financedash.domain.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Add a new investment (a buy). {@code amount} debits {@code sourceAccount} and becomes
 * the holding's current value. Adding an already-held symbol merges into that holding.
 * {@code sourceAccount} must be CHECKING or SAVINGS (validated in the service).
 */
public record InvestmentRequest(
        @NotBlank String stockSymbol,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull AccountType sourceAccount
) {
}
