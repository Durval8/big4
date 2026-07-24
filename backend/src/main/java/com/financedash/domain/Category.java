package com.financedash.domain;

/**
 * Applies only to INCOME/EXPENSE transactions. TRANSFER and ADJUSTMENT
 * transactions carry no category — their meaning is already expressed by
 * transactionType + linkedAccountType.
 */
public enum Category {
    GROCERIES,
    TRANSPORTATION,
    DINING_OUT,
    UTILITIES,
    HOUSING,
    HEALTHCARE,
    ENTERTAINMENT,
    SHOPPING,
    TRAVEL,
    SUBSCRIPTIONS,
    INSURANCE,
    SALARY,
    FREELANCE_INCOME,
    INVESTMENT_INCOME,
    GIFTS,
    OTHER_INCOME,
    OTHER_EXPENSE
}
