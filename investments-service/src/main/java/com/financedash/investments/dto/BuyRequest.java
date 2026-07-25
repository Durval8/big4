package com.financedash.investments.dto;

import com.financedash.investments.domain.CashAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Buy a stock by money amount from a cash account. Shares are derived from the fetched price.
 * {@code manualPrice} is used only when the symbol is UNRESOLVED (provider doesn't recognize it);
 * for recognized symbols the live price wins and any manualPrice is ignored.
 */
public record BuyRequest(
        @NotBlank String stockSymbol,
        @NotNull @Positive BigDecimal amount,
        @NotNull CashAccount sourceAccount,
        @Positive BigDecimal manualPrice) {
}
