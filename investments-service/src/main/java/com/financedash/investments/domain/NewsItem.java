package com.financedash.investments.domain;

import java.time.Instant;

/** One rendered news entry, embedded in the feed. {@code url} is also the dedup key. */
public class NewsItem {

    private String symbol;
    private String headline;
    private String summary;
    private String url;
    private String source;
    private Instant publishedAt;

    protected NewsItem() {}

    public NewsItem(String symbol, String headline, String summary, String url, String source, Instant publishedAt) {
        this.symbol = symbol;
        this.headline = headline;
        this.summary = summary;
        this.url = url;
        this.source = source;
        this.publishedAt = publishedAt;
    }

    public String getSymbol() { return symbol; }
    public String getHeadline() { return headline; }
    public String getSummary() { return summary; }
    public String getUrl() { return url; }
    public String getSource() { return source; }
    public Instant getPublishedAt() { return publishedAt; }
}
