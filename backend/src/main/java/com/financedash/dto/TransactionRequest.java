package com.financedash.dto;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create/update payload. Cross-field rules (category required for INCOME/EXPENSE only,
 * linkedAccountType required for TRANSFER only, linkedAccountType != accountType) are
 * enforced in TransactionService rather than here, since they depend on transactionType.
 */
public record TransactionRequest(
        @NotBlank String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull LocalDate transactionDate,
        @NotNull AccountType accountType,
        AccountType linkedAccountType,
        Category category,
        @NotNull TransactionType transactionType
) {
}
