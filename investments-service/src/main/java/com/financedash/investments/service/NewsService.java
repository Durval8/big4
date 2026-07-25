package com.financedash.investments.service;

import com.financedash.investments.domain.Holding;
import com.financedash.investments.domain.HoldingStatus;
import com.financedash.investments.domain.NewsFeed;
import com.financedash.investments.domain.NewsItem;
import com.financedash.investments.domain.PriceStatus;
import com.financedash.investments.dto.NewsResponse;
import com.financedash.investments.provider.NewsArticle;
import com.financedash.investments.provider.ProviderException;
import com.financedash.investments.provider.StockNewsProvider;
import com.financedash.investments.repository.HoldingRepository;
import com.financedash.investments.repository.NewsFeedRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds and serves the portfolio news feed. Rebuild is best-effort: a symbol whose fetch fails is
 * skipped; if <b>every</b> fetch fails the previous feed is left intact (never blanked). The feed is
 * a single document, recomputed wholesale on each trigger.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final int SUMMARY_MAX_CHARS = 200;

    private final HoldingRepository holdingRepository;
    private final StockNewsProvider newsProvider;
    private final NewsSelector selector;
    private final NewsFeedRepository feedRepository;
    private final Clock clock;
    private final int lookbackHours;
    private final int perStock;

    public NewsService(HoldingRepository holdingRepository,
                       StockNewsProvider newsProvider,
                       NewsSelector selector,
                       NewsFeedRepository feedRepository,
                       Clock clock,
                       @Value("${news.lookback-hours:48}") int lookbackHours,
                       @Value("${news.per-stock:3}") int perStock) {
        this.holdingRepository = holdingRepository;
        this.newsProvider = newsProvider;
        this.selector = selector;
        this.feedRepository = feedRepository;
        this.clock = clock;
        this.lookbackHours = lookbackHours;
        this.perStock = perStock;
    }

    public NewsResponse getFeed() {
        return NewsResponse.from(feedRepository.findById(NewsFeed.SINGLETON_ID).orElse(null));
    }

    /** Rebuild the feed from current holdings' recent news. Called by the refresh consumer. */
    public void rebuildFeed() {
        List<Holding> holdings = holdingRepository.findByStatusAndPriceStatusNot(
                HoldingStatus.OPEN, PriceStatus.UNRESOLVED);
        if (holdings.isEmpty()) {
            save(List.of());  // no holdings → legitimately empty feed
            return;
        }

        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusDays((long) Math.ceil(lookbackHours / 24.0));
        Instant cutoff = clock.instant().minus(lookbackHours, ChronoUnit.HOURS);

        List<NewsCandidate> candidates = new ArrayList<>();
        boolean anyFetched = false;
        for (Holding h : holdings) {
            try {
                List<NewsArticle> articles = newsProvider.companyNews(h.getStockSymbol(), from, to);
                anyFetched = true; // a returned call (even empty) counts as a successful fetch
                List<NewsArticle> recent = articles.stream()
                        .filter(a -> a.publishedAt().isAfter(cutoff))
                        .limit(perStock)
                        .toList();
                if (!recent.isEmpty()) {
                    candidates.add(new NewsCandidate(h.getStockSymbol(), h.currentValue(), recent));
                }
            } catch (ProviderException e) {
                log.warn("News fetch failed for {} (skipping this round): {}", h.getStockSymbol(), e.getMessage());
            }
        }

        if (!anyFetched) {
            log.warn("All news fetches failed; keeping the previous feed");
            return; // never blank a good feed on a total outage
        }

        List<RankedArticle> picks = selector.select(candidates, new Random());
        List<NewsItem> items = picks.stream().map(this::toItem).toList();
        save(items);
        log.info("Rebuilt news feed with {} item(s) from {} holding(s)", items.size(), holdings.size());
    }

    private void save(List<NewsItem> items) {
        feedRepository.save(new NewsFeed(clock.instant(), items));
    }

    private NewsItem toItem(RankedArticle r) {
        NewsArticle a = r.article();
        return new NewsItem(r.symbol(), a.headline(), trimSummary(a.summary()), a.url(), a.source(), a.publishedAt());
    }

    /** Collapse whitespace and cap to a couple of lines. */
    private static String trimSummary(String summary) {
        if (summary == null) {
            return "";
        }
        String collapsed = summary.strip().replaceAll("\\s+", " ");
        if (collapsed.length() <= SUMMARY_MAX_CHARS) {
            return collapsed;
        }
        return collapsed.substring(0, SUMMARY_MAX_CHARS).trim() + "…";
    }
}
