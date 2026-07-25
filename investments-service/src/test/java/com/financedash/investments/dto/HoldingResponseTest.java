package com.financedash.investments.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.financedash.investments.domain.Holding;
import com.financedash.investments.domain.HoldingStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Position-change % is unrealized return vs average cost, with divide-by-zero guards. */
class HoldingResponseTest {

    private Holding holding(String qty, String avgCost, String price) {
        Holding h = new Holding("AAPL");
        h.setQuantity(qty == null ? null : new BigDecimal(qty));
        h.setAvgCost(avgCost == null ? null : new BigDecimal(avgCost));
        h.setLatestPrice(price == null ? null : new BigDecimal(price));
        h.setStatus(HoldingStatus.OPEN);
        return h;
    }

    @Test
    void gainIsPositivePercent() {
        assertThat(HoldingResponse.from(holding("5", "10", "15")).positionChangePct())
                .isEqualByComparingTo("50.00");
    }

    @Test
    void lossIsNegativePercent() {
        assertThat(HoldingResponse.from(holding("5", "10", "8")).positionChangePct())
                .isEqualByComparingTo("-20.00");
    }

    @Test
    void nullWhenNoShares() {
        assertThat(HoldingResponse.from(holding("0", null, "15")).positionChangePct()).isNull();
    }

    @Test
    void nullWhenNoPrice() {
        assertThat(HoldingResponse.from(holding("5", "10", null)).positionChangePct()).isNull();
    }

    @Test
    void currentValueIsQuantityTimesPrice() {
        assertThat(HoldingResponse.from(holding("5", "10", "15")).currentValue())
                .isEqualByComparingTo("75.00");
    }
}
