package com.financedash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Backend projection of an investing cash movement, fed by {@code CashLegCommand} messages from the
 * investments service. This is what {@code BalanceService} folds into the CHECKING/SAVINGS balances
 * and sums for {@code netInvestment} — the same math the old local {@code InvestmentEvent} drove,
 * now message-sourced. The primary key is the message's {@code eventId}, making inserts idempotent.
 */
@Entity
@Table(name = "investment_cash_flow")
public class InvestmentCashFlow {

    @Id
    private String eventId;

    @Enumerated(EnumType.STRING)
    private CashLegType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private AccountType accountType; // FUND: source account; CASH_OUT: SAVINGS

    private LocalDate flowDate;

    protected InvestmentCashFlow() {}

    public InvestmentCashFlow(String eventId, CashLegType type, BigDecimal amount,
                              AccountType accountType, LocalDate flowDate) {
        this.eventId = eventId;
        this.type = type;
        this.amount = amount;
        this.accountType = accountType;
        this.flowDate = flowDate;
    }

    public String getEventId() { return eventId; }
    public CashLegType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public AccountType getAccountType() { return accountType; }
    public LocalDate getFlowDate() { return flowDate; }
}
