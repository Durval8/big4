package com.financedash.investments.dto;

import com.financedash.investments.domain.Holding;
import com.financedash.investments.domain.HoldingStatus;
import com.financedash.investments.domain.PriceStatus;
import com.financedash.investments.domain.Precision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * View of a holding for the Investments page. {@code positionChangePct} is unrealized return
 * against average cost — {@code (latestPrice − avgCost) / avgCost × 100} — and is {@code null}
 * when there are no shares or no price (never divides by zero).
 */
public record HoldingResponse(
        String id,
        String stockSymbol,
        BigDecimal quantity,
        BigDecimal costBasis,
        BigDecimal avgCost,
        BigDecimal latestPrice,
        BigDecimal currentValue,
        BigDecimal netCashInvested,
        BigDecimal realizedGain,
        BigDecimal positionChangePct,
        PriceStatus priceStatus,
        Instant priceAsOf,
        HoldingStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static HoldingResponse from(Holding h) {
        return new HoldingResponse(
                h.getId(),
                h.getStockSymbol(),
                h.getQuantity(),
                h.getCostBasis(),
                h.getAvgCost(),
                h.getLatestPrice(),
                h.currentValue(),
                h.getNetCashInvested(),
                h.getRealizedGain(),
                positionChangePct(h),
                h.getPriceStatus(),
                h.getPriceAsOf(),
                h.getStatus(),
                h.getCreatedAt(),
                h.getUpdatedAt());
    }

    /** (latestPrice − avgCost) / avgCost × 100, or null when unmeasurable. */
    static BigDecimal positionChangePct(Holding h) {
        BigDecimal avgCost = h.getAvgCost();
        BigDecimal price = h.getLatestPrice();
        if (avgCost == null || price == null
                || h.getQuantity() == null || h.getQuantity().signum() == 0
                || avgCost.signum() <= 0) {
            return null;
        }
        return price.subtract(avgCost)
                .divide(avgCost, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(Precision.PCT, RoundingMode.HALF_UP);
    }
}
