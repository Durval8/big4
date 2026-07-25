package com.financedash.investments.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One buy or sell against a holding — the investing cash-flow ledger, embedded in the holding
 * document. Records the {@code shares} and {@code price} of the trade so cost basis and realized
 * gain stay reconstructable. {@code eventId} is the stable id carried on the cash-leg message and
 * used by the backend to dedupe (idempotency key) — it must not change across republish.
 */
public class HoldingEvent {

    private String eventId;
    private InvestmentEventType type;
    private BigDecimal amount;   // cash moved (money scale)
    private BigDecimal shares;   // shares bought/sold (quantity scale)
    private BigDecimal price;    // price per share at the trade (price scale)
    private CashAccount account; // FUND: source; CASH_OUT: always SAVINGS
    private LocalDate date;

    protected HoldingEvent() {}

    public HoldingEvent(String eventId, InvestmentEventType type, BigDecimal amount,
                        BigDecimal shares, BigDecimal price, CashAccount account, LocalDate date) {
        this.eventId = eventId;
        this.type = type;
        this.amount = amount;
        this.shares = shares;
        this.price = price;
        this.account = account;
        this.date = date;
    }

    public String getEventId() { return eventId; }
    public InvestmentEventType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getShares() { return shares; }
    public BigDecimal getPrice() { return price; }
    public CashAccount getAccount() { return account; }
    public LocalDate getDate() { return date; }
}
