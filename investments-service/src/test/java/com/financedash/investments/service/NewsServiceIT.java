package com.financedash.investments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.financedash.investments.domain.Holding;
import com.financedash.investments.domain.HoldingStatus;
import com.financedash.investments.domain.NewsFeed;
import com.financedash.investments.domain.PriceStatus;
import com.financedash.investments.dto.NewsResponse;
import com.financedash.investments.provider.NewsArticle;
import com.financedash.investments.provider.StockNewsProvider;
import com.financedash.investments.provider.TransientProviderException;
import com.financedash.investments.repository.HoldingRepository;
import com.financedash.investments.repository.NewsFeedRepository;
import com.financedash.investments.support.AbstractContainersTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/** Feed rebuild against real Mongo; news provider mocked, clock fixed. */
@SpringBootTest
@TestPropertySource(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class NewsServiceIT extends AbstractContainersTest {

    private static final Instant T = Instant.parse("2026-07-24T12:00:00Z");
    private static final Instant RECENT = Instant.parse("2026-07-24T09:00:00Z"); // within 48h of T

    @TestConfiguration
    static class FixedClockConfig {
        @Bean @Primary
        Clock fixedClock() {
            return Clock.fixed(T, ZoneOffset.UTC);
        }
    }

    @Autowired private NewsService newsService;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private NewsFeedRepository feedRepository;
    @MockBean private StockNewsProvider newsProvider;

    @BeforeEach
    void clean() {
        holdingRepository.deleteAll();
        feedRepository.deleteAll();
    }

    private void seed(String symbol, String price) {
        Holding h = new Holding(symbol);
        h.setQuantity(new BigDecimal("1.000000"));
        h.setLatestPrice(new BigDecimal(price));
        h.setPriceStatus(PriceStatus.OK);
        h.setStatus(HoldingStatus.OPEN);
        holdingRepository.save(h);
    }

    private static NewsArticle art(String url, String summary) {
        return new NewsArticle("Headline " + url, summary, url, "Reuters", RECENT);
    }

    @Test
    void buildsFeedFromHoldings() {
        seed("AAPL", "600.00");
        seed("MSFT", "100.00");
        when(newsProvider.companyNews(eq("AAPL"), any(), any())).thenReturn(List.of(art("a1", "s"), art("a2", "s")));
        when(newsProvider.companyNews(eq("MSFT"), any(), any())).thenReturn(List.of(art("m1", "s")));

        newsService.rebuildFeed();

        NewsFeed feed = feedRepository.findById(NewsFeed.SINGLETON_ID).orElseThrow();
        assertThat(feed.getItems()).hasSize(3);
        assertThat(feed.getItems()).extracting("symbol").containsOnly("AAPL", "MSFT");
        assertThat(feed.getItems()).extracting("url").doesNotHaveDuplicates();
    }

    @Test
    void trimsLongSummaries() {
        seed("AAPL", "600.00");
        String longSummary = "x".repeat(500);
        when(newsProvider.companyNews(any(), any(), any())).thenReturn(List.of(art("a1", longSummary)));

        newsService.rebuildFeed();

        NewsResponse feed = newsService.getFeed();
        assertThat(feed.items()).hasSize(1);
        assertThat(feed.items().get(0).summary()).endsWith("…").hasSizeLessThanOrEqualTo(201);
    }

    @Test
    void emptyHoldingsYieldEmptyFeed() {
        newsService.rebuildFeed();
        NewsFeed feed = feedRepository.findById(NewsFeed.SINGLETON_ID).orElseThrow();
        assertThat(feed.getItems()).isEmpty();
    }

    @Test
    void staleArticlesOutsideWindowAreDropped() {
        seed("AAPL", "600.00");
        NewsArticle old = new NewsArticle("Old", "s", "old", "src", Instant.parse("2026-07-01T00:00:00Z"));
        when(newsProvider.companyNews(any(), any(), any())).thenReturn(List.of(old));

        newsService.rebuildFeed();

        assertThat(feedRepository.findById(NewsFeed.SINGLETON_ID).orElseThrow().getItems()).isEmpty();
    }

    @Test
    void allFetchesFailingKeepsThePreviousFeed() {
        seed("AAPL", "600.00");
        when(newsProvider.companyNews(any(), any(), any())).thenReturn(List.of(art("a1", "s")));
        newsService.rebuildFeed(); // good feed with one item
        assertThat(feedRepository.findById(NewsFeed.SINGLETON_ID).orElseThrow().getItems()).hasSize(1);

        when(newsProvider.companyNews(any(), any(), any())).thenThrow(new TransientProviderException("429"));
        newsService.rebuildFeed(); // total outage

        // Previous feed retained, not blanked.
        assertThat(feedRepository.findById(NewsFeed.SINGLETON_ID).orElseThrow().getItems()).hasSize(1);
    }

    @Test
    void getFeedIsEmptyWhenNeverBuilt() {
        NewsResponse feed = newsService.getFeed();
        assertThat(feed.updatedAt()).isNull();
        assertThat(feed.items()).isEmpty();
    }
}
