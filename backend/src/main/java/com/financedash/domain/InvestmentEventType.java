package com.financedash.domain;

public enum InvestmentEventType {
    /** Cash moved from a cash account into the holding (a buy). */
    FUND,
    /** Cash moved from the holding to the SAVINGS account (a cash-out). */
    CASH_OUT
}
