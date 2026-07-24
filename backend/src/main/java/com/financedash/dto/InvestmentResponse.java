package com.financedash.dto;

import com.financedash.domain.InvestmentStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code netCashInvested} = Σ FUND − Σ CASH_OUT for the holding.
 * {@code positionChangePct} = (currentValue − netCashInvested) / netCashInvested × 100,
 * or {@code null} when netCashInvested ≤ 0 (both derived, not stored).
 */
public record InvestmentResponse(
        Long id,
        String stockSymbol,
        BigDecimal currentValue,
        BigDecimal netCashInvested,
        BigDecimal positionChangePct,
        InvestmentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
