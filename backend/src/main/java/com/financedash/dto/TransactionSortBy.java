package com.financedash.dto;

/** Shorthand for the `sortBy` query param on GET /api/transactions, resolved to an entity field. */
public enum TransactionSortBy {
    DATE("transactionDate"),
    AMOUNT("amount");

    private final String field;

    TransactionSortBy(String field) {
        this.field = field;
    }

    public String field() {
        return field;
    }
}
