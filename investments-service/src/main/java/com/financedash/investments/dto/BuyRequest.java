package com.financedash.investments.dto;

import com.financedash.investments.domain.CashAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Buy a stock by money amount from a cash account. Shares are derived from the buy price.
 * <p>
 * When {@code manualPrice} is provided for a recognized symbol it is treated as the actual
 * entry price: shares = amount / manualPrice, avgCost = manualPrice, latestPrice = API quote.
 * This makes the difference between what you paid and the current market price immediately
 * visible as unrealized P&amp;L. For unrecognized symbols manualPrice is the only pricing source.
 */
public record BuyRequest(
        @NotBlank String stockSymbol,
        @NotNull @Positive BigDecimal amount,
        @NotNull CashAccount sourceAccount,
        @Positive BigDecimal manualPrice) {
}
