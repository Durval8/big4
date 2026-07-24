package com.financedash.domain;

public enum TransactionType {
    /** Money enters {@code accountType} from outside the system (e.g. salary). */
    INCOME,
    /** Money leaves {@code accountType} to outside the system (e.g. groceries). */
    EXPENSE,
    /** Money moves from {@code accountType} (source) to {@code linkedAccountType} (destination). */
    TRANSFER,
    /** Direct balance correction / opening balance seed. Counts toward net worth only. */
    ADJUSTMENT
}
