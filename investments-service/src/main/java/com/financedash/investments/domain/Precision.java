package com.financedash.investments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single source of truth for numeric precision across the holding model. Kept deliberately
 * modest for a single-currency, single-user app (open question #10 — "reasonable precision"):
 * money to cents, share quantities to 6 dp (fractional shares arise from buy-by-amount),
 * prices/avg-cost to 4 dp, percentages to 2 dp.
 */
public final class Precision {

    public static final int MONEY = 2;
    public static final int QUANTITY = 6;
    public static final int PRICE = 4;
    public static final int PCT = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Precision() {}

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(MONEY, ROUNDING);
    }

    public static BigDecimal quantity(BigDecimal v) {
        return v.setScale(QUANTITY, ROUNDING);
    }

    public static BigDecimal price(BigDecimal v) {
        return v.setScale(PRICE, ROUNDING);
    }
}
