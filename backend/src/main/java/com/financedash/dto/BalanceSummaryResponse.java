package com.financedash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BalanceSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal netWorth,
        BigDecimal spending,
        BigDecimal netSpending,
        BigDecimal netInvestment,
        AccountBalances accountBalances
) {
}
