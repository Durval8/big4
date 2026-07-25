package com.financedash.investments.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Set a price by hand for an UNRESOLVED holding (the only way such a holding gets valued). */
public record ManualPriceRequest(
        @NotNull @Positive BigDecimal price) {
}
