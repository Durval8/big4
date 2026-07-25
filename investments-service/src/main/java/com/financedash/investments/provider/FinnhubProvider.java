package com.financedash.investments.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import com.financedash.investments.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Finnhub adapter. Uses the {@code /quote} endpoint (free tier: 60 calls/min). An unrecognized
 * symbol comes back as HTTP 200 with {@code c == 0}, which we map to {@link SymbolNotFoundException};
 * 429/5xx/timeouts map to {@link TransientProviderException}. Every call first takes a token from the
 * shared {@link RateLimiter} — the buy-time quote and the periodic job draw from the same quota.
 */
public class FinnhubProvider implements StockPriceProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubProvider.class);
    private static final Duration DEFAULT_RATE_WAIT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final String apiKey;
    private final RateLimiter rateLimiter;
    private final Duration rateWait;

    public FinnhubProvider(RestClient restClient, String apiKey, RateLimiter rateLimiter) {
        this(restClient, apiKey, rateLimiter, DEFAULT_RATE_WAIT);
    }

    FinnhubProvider(RestClient restClient, String apiKey, RateLimiter rateLimiter, Duration rateWait) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.rateLimiter = rateLimiter;
        this.rateWait = rateWait;
    }

    @Override
    public Quote quote(String symbol) {
        acquirePermit(symbol);
        FinnhubQuote body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/quote")
                            .queryParam("symbol", symbol)
                            .queryParam("token", apiKey)
                            .build())
                    .retrieve()
                    .body(FinnhubQuote.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429 || status >= 500) {
                throw new TransientProviderException(
                        "Finnhub returned " + status + " for " + symbol, e);
            }
            // 401/403/400 etc. — misconfiguration or bad request; not retryable, blocks a buy.
            throw new ProviderException("Finnhub error " + status + " for " + symbol, e);
        } catch (ResourceAccessException e) {
            throw new TransientProviderException("Finnhub unreachable for " + symbol, e);
        }

        if (body == null || body.c() <= 0.0) {
            throw new SymbolNotFoundException(symbol);
        }
        Instant asOf = body.t() > 0 ? Instant.ofEpochSecond(body.t()) : Instant.now();
        return new Quote(BigDecimal.valueOf(body.c()), asOf);
    }

    private void acquirePermit(String symbol) {
        try {
            if (!rateLimiter.acquire(rateWait)) {
                throw new TransientProviderException("Rate limit exhausted before quoting " + symbol);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientProviderException("Interrupted while rate-limiting " + symbol, e);
        }
    }

    /** Subset of Finnhub's /quote payload: current price {@code c} and unix timestamp {@code t}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FinnhubQuote(double c, long t) {}
}
