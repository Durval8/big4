package com.financedash.investments.config;

import com.financedash.investments.provider.FinnhubProvider;
import com.financedash.investments.provider.StockPriceProvider;
import com.financedash.investments.ratelimit.RateLimiter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires the stock-price provider: a Finnhub adapter behind the shared rate limiter. */
@Configuration
public class PricingConfig {

    /**
     * The single throttle governing all provider calls (periodic job + buy-time quote). Sized to
     * the Finnhub free tier (60/min) with headroom via {@code pricing.rate-limit-per-minute}.
     */
    @Bean
    public RateLimiter providerRateLimiter(
            @Value("${pricing.rate-limit-per-minute:50}") int permitsPerMinute) {
        return new RateLimiter(permitsPerMinute, System::nanoTime);
    }

    @Bean
    public RestClient finnhubRestClient(@Value("${finnhub.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean
    public StockPriceProvider stockPriceProvider(
            RestClient finnhubRestClient,
            @Value("${finnhub.api-key:}") String apiKey,
            RateLimiter providerRateLimiter) {
        return new FinnhubProvider(finnhubRestClient, apiKey, providerRateLimiter);
    }

    /** News shares the same RestClient and rate limiter as quotes (one Finnhub quota). */
    @Bean
    public com.financedash.investments.provider.StockNewsProvider stockNewsProvider(
            RestClient finnhubRestClient,
            @Value("${finnhub.api-key:}") String apiKey,
            RateLimiter providerRateLimiter,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new com.financedash.investments.provider.FinnhubNewsProvider(
                finnhubRestClient, apiKey, providerRateLimiter, objectMapper);
    }
}
