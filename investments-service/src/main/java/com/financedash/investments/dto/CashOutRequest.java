package com.financedash.investments.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Cash out a percentage of the current position (proceeds go to savings). Percentage-based rather
 * than a money amount so "cash out everything" needs no comparison against a live-priced value:
 * quantity only changes via buy/cash-out, never via the price-refresh job, so 100 always means
 * every remaining share regardless of any price movement since the request was composed.
 */
public record CashOutRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal percentage) {
}
