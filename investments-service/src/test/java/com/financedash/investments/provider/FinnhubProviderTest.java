package com.financedash.investments.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.financedash.investments.ratelimit.RateLimiter;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Adapter contract against a stubbed HTTP layer via {@link MockRestServiceServer} — no embedded
 * server, no sockets (this sandbox can't open loopback selector pipes), no real network.
 */
class FinnhubProviderTest {

    private FinnhubProvider provider(MockRestServiceServer[] out) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://finnhub.test/api/v1");
        out[0] = MockRestServiceServer.bindTo(builder).build();
        return new FinnhubProvider(builder.build(), "test-key", new RateLimiter(100_000, System::nanoTime));
    }

    @Test
    void returnsQuoteForKnownSymbol() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        FinnhubProvider provider = provider(server);
        server[0].expect(requestTo(containsString("/quote")))
                .andRespond(withSuccess("{\"c\":261.74,\"t\":1699900000}", MediaType.APPLICATION_JSON));

        Quote quote = provider.quote("AAPL");

        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("261.74"));
        assertThat(quote.asOf().getEpochSecond()).isEqualTo(1699900000L);
    }

    @Test
    void zeroPriceMeansUnknownSymbol() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        FinnhubProvider provider = provider(server);
        server[0].expect(requestTo(containsString("/quote")))
                .andRespond(withSuccess("{\"c\":0,\"t\":0}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.quote("BOGUS")).isInstanceOf(SymbolNotFoundException.class);
    }

    @Test
    void rateLimitedResponseIsTransient() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        FinnhubProvider provider = provider(server);
        server[0].expect(requestTo(containsString("/quote")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.quote("AAPL")).isInstanceOf(TransientProviderException.class);
    }

    @Test
    void serverErrorIsTransient() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        FinnhubProvider provider = provider(server);
        server[0].expect(requestTo(containsString("/quote")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> provider.quote("AAPL")).isInstanceOf(TransientProviderException.class);
    }

    @Test
    void exhaustedRateLimiterIsTransientWithoutCallingProvider() {
        // A single permit and a frozen clock: after draining, acquire never succeeds and no HTTP
        // call is made. The client is mock-bound (no expectations) so no real socket is opened.
        RestClient.Builder builder = RestClient.builder().baseUrl("http://finnhub.test/api/v1");
        MockRestServiceServer.bindTo(builder).build();
        RateLimiter empty = new RateLimiter(1, () -> 0L);
        empty.tryAcquire();
        FinnhubProvider limited =
                new FinnhubProvider(builder.build(), "k", empty, java.time.Duration.ofMillis(100));

        assertThatThrownBy(() -> limited.quote("AAPL")).isInstanceOf(TransientProviderException.class);
    }
}
