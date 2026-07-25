package com.financedash.investments.provider;

/**
 * Port over the stock-price API — the only thing in the app that talks to the provider, so it stays
 * swappable and mockable (WireMock in tests). Implementations must route calls through the shared
 * rate limiter.
 */
public interface StockPriceProvider {

    /**
     * @return the latest quote for {@code symbol}
     * @throws SymbolNotFoundException  the provider does not recognize the symbol
     * @throws TransientProviderException a retryable failure (429/5xx/timeout/rate-limited)
     */
    Quote quote(String symbol);
}
