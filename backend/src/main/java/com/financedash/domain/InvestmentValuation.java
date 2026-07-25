package com.financedash.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Backend's shallow copy of the investing net value for the dashboard — a single row overwritten by
 * {@code ValueSnapshot} messages (last-write-wins by {@code asOf}). Stale-but-coherent by design:
 * the dashboard reads this rather than calling the service, so net worth stays a single, consistent
 * number even when the service is down.
 */
@Entity
@Table(name = "investment_valuation")
public class InvestmentValuation {

    /** There is only ever one row. */
    public static final String SINGLETON_ID = "SINGLETON";

    @Id
    private String id;
    private BigDecimal netValue;
    private Instant asOf;

    protected InvestmentValuation() {}

    public InvestmentValuation(BigDecimal netValue, Instant asOf) {
        this.id = SINGLETON_ID;
        this.netValue = netValue;
        this.asOf = asOf;
    }

    public String getId() { return id; }
    public BigDecimal getNetValue() { return netValue; }
    public Instant getAsOf() { return asOf; }

    public void update(BigDecimal netValue, Instant asOf) {
        this.netValue = netValue;
        this.asOf = asOf;
    }
}
