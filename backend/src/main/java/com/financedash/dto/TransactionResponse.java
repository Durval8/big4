package com.financedash.dto;

import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        String description,
        BigDecimal amount,
        LocalDate transactionDate,
        AccountType accountType,
        AccountType linkedAccountType,
        Category category,
        TransactionType transactionType,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getDescription(),
                t.getAmount(),
                t.getTransactionDate(),
                t.getAccountType(),
                t.getLinkedAccountType(),
                t.getCategory(),
                t.getTransactionType(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
