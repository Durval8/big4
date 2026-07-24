package com.financedash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

@Entity
@Table(name = "transactions")
@EntityListeners(AuditingEntityListener.class)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType accountType;

    /** Destination account for a TRANSFER. Null for every other transaction type. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AccountType linkedAccountType;

    /** Only set for INCOME/EXPENSE. Null for TRANSFER/ADJUSTMENT. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType transactionType;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected Transaction() {
        // JPA
    }

    public Transaction(String description, BigDecimal amount, LocalDate transactionDate,
                        AccountType accountType, AccountType linkedAccountType,
                        Category category, TransactionType transactionType) {
        this.description = description;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.accountType = accountType;
        this.linkedAccountType = linkedAccountType;
        this.category = category;
        this.transactionType = transactionType;
    }

    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public AccountType getLinkedAccountType() {
        return linkedAccountType;
    }

    public void setLinkedAccountType(AccountType linkedAccountType) {
        this.linkedAccountType = linkedAccountType;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
