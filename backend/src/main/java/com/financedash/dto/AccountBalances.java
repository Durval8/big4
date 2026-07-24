package com.financedash.dto;

import java.math.BigDecimal;

public record AccountBalances(
        BigDecimal checking,
        BigDecimal savings,
        BigDecimal investing
) {
}
