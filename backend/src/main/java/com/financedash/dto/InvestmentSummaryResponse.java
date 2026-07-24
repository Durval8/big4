package com.financedash.dto;

import java.math.BigDecimal;

/**
 * Totals across OPEN holdings. {@code positionChangePct} is the aggregate change of
 * current value vs net cash invested, or {@code null} when totalNetInvested ≤ 0.
 */
public record InvestmentSummaryResponse(
        BigDecimal totalNetInvested,
        BigDecimal totalCurrentValue,
        BigDecimal positionChangePct
) {
}
