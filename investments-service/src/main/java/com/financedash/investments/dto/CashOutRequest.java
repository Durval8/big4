package com.financedash.investments.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Cash out a money amount of a holding (symmetric with buy); proceeds go to savings. */
public record CashOutRequest(
        @NotNull @Positive BigDecimal amount) {
}
