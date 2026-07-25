package com.financedash.investments.service;

import com.financedash.investments.domain.CashAccount;
import com.financedash.investments.domain.Holding;
import com.financedash.investments.domain.HoldingEvent;
import com.financedash.investments.domain.HoldingStatus;
import com.financedash.investments.domain.InvestmentEventType;
import com.financedash.investments.domain.PriceStatus;
import com.financedash.investments.domain.Precision;
import com.financedash.investments.dto.BuyRequest;
import com.financedash.investments.dto.CashOutRequest;
import com.financedash.investments.dto.HoldingResponse;
import com.financedash.investments.dto.HoldingUpdateRequest;
import com.financedash.investments.dto.ManualPriceRequest;
import com.financedash.investments.dto.SummaryResponse;
import com.financedash.investments.exception.InvalidInvestmentException;
import com.financedash.investments.exception.ProviderUnavailableException;
import com.financedash.investments.exception.ResourceNotFoundException;
import com.financedash.investments.messaging.contract.CashLegCommand;
import com.financedash.investments.messaging.contract.ValueSnapshot;
import com.financedash.investments.provider.ProviderException;
import com.financedash.investments.provider.Quote;
import com.financedash.investments.provider.StockPriceProvider;
import com.financedash.investments.provider.SymbolNotFoundException;
import com.financedash.investments.provider.TransientProviderException;
import com.financedash.investments.repository.HoldingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Investment domain logic: buy/cash-out, corrections, manual pricing, and the totals the page
 * shows. Every state change writes its outgoing cash-leg and/or value-snapshot messages to the
 * outbox in the same flow. Value is share-based; position change is average-cost vs latest price.
 */
@Service
public class HoldingService {

    private final HoldingRepository holdingRepository;
    private final StockPriceProvider priceProvider;
    private final OutboxWriter outbox;
    private final Clock clock;

    public HoldingService(HoldingRepository holdingRepository,
                          StockPriceProvider priceProvider,
                          OutboxWriter outbox,
                          Clock clock) {
        this.holdingRepository = holdingRepository;
        this.priceProvider = priceProvider;
        this.outbox = outbox;
        this.clock = clock;
    }

    // --- reads ---

    public List<HoldingResponse> list() {
        return holdingRepository.findAllByOrderByStockSymbolAsc().stream()
                .map(HoldingResponse::from)
                .toList();
    }

    public HoldingResponse get(String id) {
        return HoldingResponse.from(require(id));
    }

    public SummaryResponse summary() {
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalCurrent = BigDecimal.ZERO;
        BigDecimal totalRealized = BigDecimal.ZERO;
        BigDecimal pricedCostBasis = BigDecimal.ZERO;
        BigDecimal pricedCurrent = BigDecimal.ZERO;
        for (Holding h : holdingRepository.findByStatus(HoldingStatus.OPEN)) {
            totalNet = totalNet.add(h.getNetCashInvested());
            totalCurrent = totalCurrent.add(h.currentValue());
            totalRealized = totalRealized.add(h.getRealizedGain());
            if (h.getLatestPrice() != null && h.getQuantity().signum() > 0) {
                pricedCostBasis = pricedCostBasis.add(h.getCostBasis());
                pricedCurrent = pricedCurrent.add(h.currentValue());
            }
        }
        BigDecimal pct = percentChange(pricedCurrent, pricedCostBasis);
        return new SummaryResponse(
                Precision.money(totalNet), Precision.money(totalCurrent),
                Precision.money(totalRealized), pct);
    }

    // --- writes ---

    public HoldingResponse buy(BuyRequest request) {
        String symbol = normalize(request.stockSymbol());
        PricedSymbol priced = priceForBuy(symbol, request.manualPrice());

        BigDecimal shares = request.amount().divide(priced.price(), Precision.QUANTITY, Precision.ROUNDING);
        if (shares.signum() <= 0) {
            throw new InvalidInvestmentException("Amount is too small to buy a share at the current price");
        }

        Holding h = holdingRepository.findFirstByStockSymbolAndStatus(symbol, HoldingStatus.OPEN)
                .orElseGet(() -> new Holding(symbol));

        h.setQuantity(Precision.quantity(nz(h.getQuantity()).add(shares)));
        h.setCostBasis(Precision.money(nz(h.getCostBasis()).add(request.amount())));
        h.setNetCashInvested(Precision.money(nz(h.getNetCashInvested()).add(request.amount())));
        h.setAvgCost(avgCost(h));
        h.setLatestPrice(priced.price());
        h.setPriceAsOf(priced.asOf());
        h.setPriceStatus(priced.status());
        h.setStatus(HoldingStatus.OPEN);

        String eventId = newEventId();
        LocalDate date = today();
        h.addEvent(new HoldingEvent(eventId, InvestmentEventType.FUND,
                Precision.money(request.amount()), shares, priced.price(), request.sourceAccount(), date));
        holdingRepository.save(h);

        outbox.enqueueCashLeg(CashLegCommand.of(
                eventId, InvestmentEventType.FUND.name(), request.sourceAccount().name(),
                Precision.money(request.amount()), date));
        emitSnapshot();
        return HoldingResponse.from(h);
    }

    public HoldingResponse cashOut(String id, CashOutRequest request) {
        Holding h = require(id);
        if (h.getStatus() != HoldingStatus.OPEN) {
            throw new InvalidInvestmentException("Holding is already cashed out");
        }
        BigDecimal price = h.getLatestPrice();
        if (price == null || price.signum() <= 0) {
            throw new InvalidInvestmentException("No price available; set a manual price before cashing out");
        }
        BigDecimal amount = Precision.money(request.amount());
        BigDecimal currentValue = h.currentValue();
        if (amount.compareTo(currentValue) > 0) {
            throw new InvalidInvestmentException("Cash-out amount exceeds the current position");
        }

        boolean full = amount.compareTo(currentValue) == 0;
        BigDecimal sharesSold = full
                ? h.getQuantity()
                : amount.divide(price, Precision.QUANTITY, Precision.ROUNDING);
        BigDecimal costRemoved = full
                ? h.getCostBasis()
                : Precision.money(sharesSold.multiply(nz(h.getAvgCost())));

        h.setRealizedGain(Precision.money(nz(h.getRealizedGain()).add(amount.subtract(costRemoved))));
        h.setNetCashInvested(Precision.money(nz(h.getNetCashInvested()).subtract(amount)));

        if (full) {
            h.setQuantity(BigDecimal.ZERO.setScale(Precision.QUANTITY));
            h.setCostBasis(BigDecimal.ZERO.setScale(Precision.MONEY));
            h.setAvgCost(null);
            h.setStatus(HoldingStatus.CASHED_OUT);
        } else {
            h.setQuantity(Precision.quantity(h.getQuantity().subtract(sharesSold)));
            h.setCostBasis(Precision.money(h.getCostBasis().subtract(costRemoved)));
            h.setAvgCost(avgCost(h));
        }

        String eventId = newEventId();
        LocalDate date = today();
        h.addEvent(new HoldingEvent(eventId, InvestmentEventType.CASH_OUT,
                amount, sharesSold, price, CashAccount.SAVINGS, date));
        holdingRepository.save(h);

        outbox.enqueueCashLeg(CashLegCommand.of(
                eventId, InvestmentEventType.CASH_OUT.name(), CashAccount.SAVINGS.name(), amount, date));
        emitSnapshot();
        return HoldingResponse.from(h);
    }

    /** Data-entry correction (symbol/quantity). No cash movement; value may change, so snapshot. */
    public HoldingResponse update(String id, HoldingUpdateRequest request) {
        Holding h = require(id);
        if (h.getStatus() != HoldingStatus.OPEN) {
            throw new InvalidInvestmentException("Cannot edit a cashed-out holding");
        }
        h.setStockSymbol(normalize(request.stockSymbol()));
        h.setQuantity(Precision.quantity(request.quantity()));
        h.setAvgCost(avgCost(h));
        holdingRepository.save(h);
        emitSnapshot();
        return HoldingResponse.from(h);
    }

    /** Set a price by hand for an UNRESOLVED holding (the only way it gets valued). */
    public HoldingResponse setManualPrice(String id, ManualPriceRequest request) {
        Holding h = require(id);
        if (h.getStatus() != HoldingStatus.OPEN) {
            throw new InvalidInvestmentException("Cannot price a cashed-out holding");
        }
        if (h.getPriceStatus() != PriceStatus.UNRESOLVED) {
            throw new InvalidInvestmentException("Manual pricing only applies to UNRESOLVED holdings");
        }
        h.setLatestPrice(Precision.price(request.price()));
        h.setPriceAsOf(clock.instant());
        holdingRepository.save(h);
        emitSnapshot();
        return HoldingResponse.from(h);
    }

    /**
     * Admin-only: remove a holding and re-broadcast the investing value. Does NOT refund cash to the
     * backend (use cash-out for that) — deleting a holding with prior FUND legs leaves that cash
     * spent. Not exposed in the UI.
     */
    public void delete(String id) {
        Holding h = require(id);
        holdingRepository.delete(h);
        emitSnapshot();
    }

    // --- price-job callbacks (driven by the refresh consumer) ---

    /** Apply a fresh quote to every OPEN holding of a symbol and re-broadcast the value. */
    public void applyPrice(String symbol, BigDecimal price, Instant asOf) {
        List<Holding> holdings = holdingRepository.findByStockSymbolAndStatus(
                normalize(symbol), HoldingStatus.OPEN);
        if (holdings.isEmpty()) {
            return;
        }
        for (Holding h : holdings) {
            h.setLatestPrice(Precision.price(price));
            h.setPriceAsOf(asOf);
            h.setPriceStatus(PriceStatus.OK);
            holdingRepository.save(h);
        }
        emitSnapshot();
    }

    /** Symbol the provider doesn't recognize: stop fetching it, price it by hand instead. */
    public void markSymbolUnresolved(String symbol) {
        setPriceStatus(symbol, PriceStatus.UNRESOLVED);
    }

    /** Provider persistently failing for a symbol: keep last-known price, flag it stale. */
    public void markSymbolStale(String symbol) {
        setPriceStatus(symbol, PriceStatus.STALE);
    }

    private void setPriceStatus(String symbol, PriceStatus status) {
        for (Holding h : holdingRepository.findByStockSymbolAndStatus(normalize(symbol), HoldingStatus.OPEN)) {
            h.setPriceStatus(status);
            holdingRepository.save(h);
        }
    }

    // --- helpers ---

    private record PricedSymbol(BigDecimal price, Instant asOf, PriceStatus status) {}

    private PricedSymbol priceForBuy(String symbol, BigDecimal manualPrice) {
        try {
            Quote quote = priceProvider.quote(symbol);
            return new PricedSymbol(Precision.price(quote.price()), quote.asOf(), PriceStatus.OK);
        } catch (SymbolNotFoundException notFound) {
            if (manualPrice == null) {
                throw new InvalidInvestmentException(
                        "Symbol '" + symbol + "' is not recognized; provide a manualPrice to add it "
                                + "as an unresolved holding");
            }
            return new PricedSymbol(Precision.price(manualPrice), clock.instant(), PriceStatus.UNRESOLVED);
        } catch (TransientProviderException ex) {
            throw new ProviderUnavailableException(
                    "Price provider is temporarily unavailable; the buy was not recorded. Please retry.");
        } catch (ProviderException ex) {
            // Non-transient provider failure (e.g. a rejected/missing API key → 401). Not the user's
            // fault and not retryable by them, but still block the buy cleanly rather than 500.
            throw new ProviderUnavailableException(
                    "Price provider rejected the request (check the API key); the buy was not recorded.");
        }
    }

    private void emitSnapshot() {
        BigDecimal netValue = holdingRepository.findByStatus(HoldingStatus.OPEN).stream()
                .map(Holding::currentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        outbox.enqueueValueSnapshot(ValueSnapshot.of(Precision.money(netValue), clock.instant()));
    }

    private Holding require(String id) {
        return holdingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holding " + id + " not found"));
    }

    private static BigDecimal avgCost(Holding h) {
        if (h.getQuantity() == null || h.getQuantity().signum() == 0) {
            return null;
        }
        return h.getCostBasis().divide(h.getQuantity(), Precision.PRICE, Precision.ROUNDING);
    }

    /** (current − cost) / cost × 100 at 2dp; null when cost ≤ 0. */
    private static BigDecimal percentChange(BigDecimal current, BigDecimal cost) {
        if (cost.signum() <= 0) {
            return null;
        }
        return current.subtract(cost)
                .divide(cost, 6, Precision.ROUNDING)
                .multiply(BigDecimal.valueOf(100))
                .setScale(Precision.PCT, Precision.ROUNDING);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
