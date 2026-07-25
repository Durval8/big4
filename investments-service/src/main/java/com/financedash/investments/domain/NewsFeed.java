package com.financedash.investments.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The rendered news feed — a single precomputed document (the page shows one ranked ≤7-item list).
 * Rebuilt on the 4h tick and whenever the held-symbol set changes.
 */
@Document(collection = "news_feed")
public class NewsFeed {

    /** There is only ever one document. */
    public static final String SINGLETON_ID = "SINGLETON";

    @Id
    private String id;
    private Instant updatedAt;
    private List<NewsItem> items = new ArrayList<>();

    protected NewsFeed() {}

    public NewsFeed(Instant updatedAt, List<NewsItem> items) {
        this.id = SINGLETON_ID;
        this.updatedAt = updatedAt;
        this.items = items;
    }

    public String getId() { return id; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<NewsItem> getItems() { return items; }
}
