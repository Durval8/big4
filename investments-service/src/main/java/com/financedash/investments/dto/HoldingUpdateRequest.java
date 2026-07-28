package com.financedash.investments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Correct a holding's symbol and/or amount invested. Data-entry correction only — no cash moves
 * and no event is recorded. The share quantity is derived from amount / avgCost so the per-share
 * cost stays consistent; costBasis is updated to the corrected amount.
 */
public record HoldingUpdateRequest(
        @NotBlank String stockSymbol,
        @NotNull @Positive BigDecimal amount) {
}
