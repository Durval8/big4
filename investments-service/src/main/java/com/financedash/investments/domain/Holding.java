package com.financedash.investments.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A stock holding — the aggregate the Investments page renders and the price job updates.
 *
 * <p>Value is share-based: {@code currentValue = quantity × latestPrice}. Two accumulators are
 * kept deliberately separate (see INVESTMENT_PRICING.md): {@code netCashInvested} (cash flow,
 * Σ FUND − Σ CASH_OUT) drives the "money invested" tile and the backend fold-in, while
 * {@code costBasis} (accounting cost of shares still held) drives {@code avgCost} and therefore
 * the position-change %. Conflating them reintroduces the partial-cash-out %-skew.
 */
@Document(collection = "holdings")
public class Holding {

    @Id
    private String id;

    /** Uppercased ticker. At most one OPEN holding per symbol (buys of the same symbol merge). */
    @Indexed
    private String stockSymbol;

    private BigDecimal quantity;        // shares held (quantity scale)
    private BigDecimal costBasis;       // accounting cost of shares still held (money scale)
    private BigDecimal avgCost;         // costBasis / quantity (price scale); null when quantity = 0
    private BigDecimal netCashInvested; // Σ FUND.amount − Σ CASH_OUT.amount (money scale)
    private BigDecimal realizedGain;    // Σ (proceeds − shares_sold × avgCost) (money scale)

    private BigDecimal latestPrice;     // last known price per share (price scale); null before first price
    private Instant priceAsOf;
    private PriceStatus priceStatus;

    private HoldingStatus status;
    private List<HoldingEvent> events = new ArrayList<>();

    @Version
    private Long version;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    protected Holding() {}

    public Holding(String stockSymbol) {
        this.stockSymbol = stockSymbol;
        this.quantity = BigDecimal.ZERO;
        this.costBasis = BigDecimal.ZERO;
        this.netCashInvested = BigDecimal.ZERO;
        this.realizedGain = BigDecimal.ZERO;
        this.status = HoldingStatus.OPEN;
        this.priceStatus = PriceStatus.OK;
    }

    /** quantity × latestPrice, money-scaled; ZERO when there is no price or no shares. */
    public BigDecimal currentValue() {
        if (latestPrice == null || quantity == null || quantity.signum() == 0) {
            return BigDecimal.ZERO.setScale(Precision.MONEY);
        }
        return Precision.money(quantity.multiply(latestPrice));
    }

    public String getId() { return id; }
    public String getStockSymbol() { return stockSymbol; }
    public void setStockSymbol(String stockSymbol) { this.stockSymbol = stockSymbol; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getCostBasis() { return costBasis; }
    public void setCostBasis(BigDecimal costBasis) { this.costBasis = costBasis; }
    public BigDecimal getAvgCost() { return avgCost; }
    public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
    public BigDecimal getNetCashInvested() { return netCashInvested; }
    public void setNetCashInvested(BigDecimal netCashInvested) { this.netCashInvested = netCashInvested; }
    public BigDecimal getRealizedGain() { return realizedGain; }
    public void setRealizedGain(BigDecimal realizedGain) { this.realizedGain = realizedGain; }
    public BigDecimal getLatestPrice() { return latestPrice; }
    public void setLatestPrice(BigDecimal latestPrice) { this.latestPrice = latestPrice; }
    public Instant getPriceAsOf() { return priceAsOf; }
    public void setPriceAsOf(Instant priceAsOf) { this.priceAsOf = priceAsOf; }
    public PriceStatus getPriceStatus() { return priceStatus; }
    public void setPriceStatus(PriceStatus priceStatus) { this.priceStatus = priceStatus; }
    public HoldingStatus getStatus() { return status; }
    public void setStatus(HoldingStatus status) { this.status = status; }
    public List<HoldingEvent> getEvents() { return events; }
    public void addEvent(HoldingEvent event) { this.events.add(event); }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
