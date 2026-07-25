package com.financedash.investments.provider;

import java.time.Instant;

/** One news article from the provider, provider-agnostic. */
public record NewsArticle(
        String headline,
        String summary,
        String url,
        String source,
        Instant publishedAt) {
}
