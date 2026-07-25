package com.financedash.investments.provider;

import java.time.LocalDate;
import java.util.List;

/**
 * Port over the provider's company-news API. Implementations route through the shared rate limiter
 * (news and price quotes draw from the same quota) and return articles newest-first. An unknown /
 * uncovered symbol yields an empty list, not an error.
 */
public interface StockNewsProvider {

    /**
     * @return recent articles for {@code symbol} in [{@code from}, {@code to}], newest first (possibly empty)
     * @throws TransientProviderException a retryable failure (429/5xx/timeout/rate-limited)
     */
    List<NewsArticle> companyNews(String symbol, LocalDate from, LocalDate to);
}
