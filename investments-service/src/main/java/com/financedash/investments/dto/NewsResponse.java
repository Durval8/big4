package com.financedash.investments.dto;

import com.financedash.investments.domain.NewsFeed;
import com.financedash.investments.domain.NewsItem;
import java.time.Instant;
import java.util.List;

/** The Investments page's news feed: when it was built and up to 7 ranked items. */
public record NewsResponse(Instant updatedAt, List<Item> items) {

    public record Item(String symbol, String headline, String summary, String url,
                       String source, Instant publishedAt) {
    }

    public static NewsResponse from(NewsFeed feed) {
        if (feed == null) {
            return new NewsResponse(null, List.of());
        }
        List<Item> items = feed.getItems().stream()
                .map(NewsResponse::toItem)
                .toList();
        return new NewsResponse(feed.getUpdatedAt(), items);
    }

    private static Item toItem(NewsItem i) {
        return new Item(i.getSymbol(), i.getHeadline(), i.getSummary(), i.getUrl(),
                i.getSource(), i.getPublishedAt());
    }
}
