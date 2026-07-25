package com.financedash.investments.dto;

import java.math.BigDecimal;

/** Totals across OPEN holdings for the Investments page header. */
public record SummaryResponse(
        BigDecimal totalNetInvested,
        BigDecimal totalCurrentValue,
        BigDecimal totalRealizedGain,
        BigDecimal positionChangePct) {
}
