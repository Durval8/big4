package com.financedash.investments.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financedash.investments.ratelimit.RateLimiter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Finnhub adapter for {@code /company-news} (free tier, North-American companies). Shares the same
 * {@link RateLimiter} as {@link FinnhubProvider} so news fetches and price quotes can't jointly
 * exceed the quota. An unknown/uncovered symbol returns HTTP 200 with an empty array → empty list;
 * 429/5xx/timeouts map to {@link TransientProviderException}.
 */
public class FinnhubNewsProvider implements StockNewsProvider {

    private static final Duration DEFAULT_RATE_WAIT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final String apiKey;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final Duration rateWait;

    public FinnhubNewsProvider(RestClient restClient, String apiKey, RateLimiter rateLimiter,
                               ObjectMapper objectMapper) {
        this(restClient, apiKey, rateLimiter, objectMapper, DEFAULT_RATE_WAIT);
    }

    FinnhubNewsProvider(RestClient restClient, String apiKey, RateLimiter rateLimiter,
                        ObjectMapper objectMapper, Duration rateWait) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.rateWait = rateWait;
    }

    @Override
    public List<NewsArticle> companyNews(String symbol, LocalDate from, LocalDate to) {
        acquirePermit(symbol);
        byte[] raw;
        try {
            raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/company-news")
                            .queryParam("symbol", symbol)
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .queryParam("token", apiKey)
                            .build())
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429 || status >= 500) {
                throw new TransientProviderException("Finnhub news " + status + " for " + symbol, e);
            }
            throw new ProviderException("Finnhub news error " + status + " for " + symbol, e);
        } catch (ResourceAccessException e) {
            throw new TransientProviderException("Finnhub unreachable for news " + symbol, e);
        }

        if (raw == null || raw.length == 0) {
            return List.of();
        }
        // Parse from bytes so Jackson auto-detects the JSON encoding (UTF-8), regardless of any
        // charset the HTTP Content-Type header claims — otherwise curly quotes/ellipses mojibake.
        FinnhubNewsItem[] body;
        try {
            body = objectMapper.readValue(raw, FinnhubNewsItem[].class);
        } catch (IOException e) {
            throw new ProviderException("Malformed Finnhub news payload for " + symbol, e);
        }
        return java.util.Arrays.stream(body)
                .filter(i -> i.headline() != null && i.url() != null)
                .map(i -> new NewsArticle(
                        i.headline(),
                        i.summary() == null ? "" : i.summary(),
                        i.url(),
                        i.source() == null ? "" : i.source(),
                        i.datetime() > 0 ? Instant.ofEpochSecond(i.datetime()) : Instant.EPOCH))
                .sorted(Comparator.comparing(NewsArticle::publishedAt).reversed())
                .toList();
    }

    private void acquirePermit(String symbol) {
        try {
            if (!rateLimiter.acquire(rateWait)) {
                throw new TransientProviderException("Rate limit exhausted before news " + symbol);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientProviderException("Interrupted while rate-limiting news " + symbol, e);
        }
    }

    /** Subset of a Finnhub company-news item. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FinnhubNewsItem(String headline, String summary, String url, String source, long datetime) {}
}
