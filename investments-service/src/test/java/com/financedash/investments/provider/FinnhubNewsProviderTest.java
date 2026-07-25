package com.financedash.investments.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.financedash.investments.ratelimit.RateLimiter;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Company-news adapter against a stubbed HTTP layer (no sockets — MockRestServiceServer). */
class FinnhubNewsProviderTest {

    private static final LocalDate FROM = LocalDate.of(2026, 7, 22);
    private static final LocalDate TO = LocalDate.of(2026, 7, 24);

    private FinnhubNewsProvider provider(MockRestServiceServer[] out) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://finnhub.test/api/v1");
        out[0] = MockRestServiceServer.bindTo(builder).build();
        return new FinnhubNewsProvider(builder.build(), "k", new RateLimiter(100_000, System::nanoTime),
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void parsesAndSortsNewestFirst() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        FinnhubNewsProvider provider = provider(server);
        server[0].expect(requestTo(containsString("/company-news")))
                .andRespond(withSuccess("""
                        [ {"headline":"Older","summary":"s1","url":"u1","source":"Reuters","datetime":1700000000},
                          {"headline":"Newer","summary":"s2","url":"u2","source":"Bloomberg","datetime":1700009999} ]
                        """, MediaType.APPLICATION_JSON));

        List<NewsArticle> news = provider.companyNews("AAPL", FROM, TO);

        assertThat(news).extracting(NewsArticle::headline).containsExactly("Newer", "Older");
        assertThat(news.get(0).source()).isEqualTo("Bloomberg");
    }

    @Test
    void preservesUtf8Characters() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        FinnhubNewsProvider provider = provider(server);
        server[0].expect(requestTo(containsString("/company-news")))
                .andRespond(withSuccess("""
                        [ {"headline":"Klarman’s picks","summary":"café report…","url":"u1",
                           "source":"Yahoo","datetime":1700000000} ]
                        """, MediaType.APPLICATION_JSON));

        List<NewsArticle> news = provider.companyNews("NVDA", FROM, TO);

        assertThat(news).hasSize(1);
        assertThat(news.get(0).headline()).isEqualTo("Klarman’s picks"); // curly apostrophe, not mojibake
        assertThat(news.get(0).summary()).isEqualTo("café report…");
    }

    @Test
    void unknownSymbolReturnsEmptyList() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        FinnhubNewsProvider provider = provider(server);
        server[0].expect(requestTo(containsString("/company-news")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(provider.companyNews("BOGUS", FROM, TO)).isEmpty();
    }

    @Test
    void rateLimitedIsTransient() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        FinnhubNewsProvider provider = provider(server);
        server[0].expect(requestTo(containsString("/company-news")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.companyNews("AAPL", FROM, TO))
                .isInstanceOf(TransientProviderException.class);
    }

    @Test
    void serverErrorIsTransient() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        FinnhubNewsProvider provider = provider(server);
        server[0].expect(requestTo(containsString("/company-news")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> provider.companyNews("AAPL", FROM, TO))
                .isInstanceOf(TransientProviderException.class);
    }
}
