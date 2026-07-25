package com.financedash.investments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Correct a holding's symbol and/or share quantity. Prices are the source of truth now, so this is
 * a data-entry correction only — no cash moves and no event is recorded. avgCost is recomputed from
 * the (unchanged) cost basis over the corrected quantity.
 */
public record HoldingUpdateRequest(
        @NotBlank String stockSymbol,
        @NotNull @Positive BigDecimal quantity) {
}
