package com.financedash.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransactionSortByTest {

    @Test
    void dateResolvesToTransactionDateField() {
        assertThat(TransactionSortBy.DATE.field()).isEqualTo("transactionDate");
    }

    @Test
    void amountResolvesToAmountField() {
        assertThat(TransactionSortBy.AMOUNT.field()).isEqualTo("amount");
    }
}
