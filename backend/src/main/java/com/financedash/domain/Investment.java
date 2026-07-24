package com.financedash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A stock holding. Value is a single {@code currentValue} figure (marked to market
 * by editing). Cash movements in/out are recorded as {@link InvestmentEvent}s, which
 * the balance logic folds into the cash accounts — investments are their own ledger,
 * separate from {@code transactions}. Position-change % and net-cash-invested are
 * derived on read (see InvestmentService), not stored.
 */
@Entity
@Table(name = "investments")
@EntityListeners(AuditingEntityListener.class)
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Free-text ticker/name, stored normalized (trimmed, upper-cased). */
    @Column(nullable = false)
    private String stockSymbol;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal currentValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvestmentStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected Investment() {
        // JPA
    }

    public Investment(String stockSymbol, BigDecimal currentValue) {
        this.stockSymbol = stockSymbol;
        this.currentValue = currentValue;
        this.status = InvestmentStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public String getStockSymbol() {
        return stockSymbol;
    }

    public void setStockSymbol(String stockSymbol) {
        this.stockSymbol = stockSymbol;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public InvestmentStatus getStatus() {
        return status;
    }

    public void setStatus(InvestmentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
