package com.financedash.domain;

public enum InvestmentStatus {
    /** Still held (currentValue may be any non-negative amount). */
    OPEN,
    /** Fully cashed out (currentValue == 0); kept for history. */
    CASHED_OUT
}
