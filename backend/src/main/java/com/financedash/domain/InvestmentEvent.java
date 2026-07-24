package com.financedash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A cash movement between a cash account and a holding. {@code FUND} debits
 * {@code accountType} (CHECKING/SAVINGS); {@code CASH_OUT} credits SAVINGS. The
 * balance logic reads these to fold investment activity into cash-account balances,
 * so no rows are written to the {@code transactions} table for investing.
 */
@Entity
@Table(name = "investment_events")
@EntityListeners(AuditingEntityListener.class)
public class InvestmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvestmentEventType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** For FUND: the cash account debited. For CASH_OUT: SAVINGS. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType accountType;

    @Column(nullable = false)
    private LocalDate eventDate;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected InvestmentEvent() {
        // JPA
    }

    public InvestmentEvent(Investment investment, InvestmentEventType type,
                           BigDecimal amount, AccountType accountType, LocalDate eventDate) {
        this.investment = investment;
        this.type = type;
        this.amount = amount;
        this.accountType = accountType;
        this.eventDate = eventDate;
    }

    public Long getId() {
        return id;
    }

    public Investment getInvestment() {
        return investment;
    }

    public InvestmentEventType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
