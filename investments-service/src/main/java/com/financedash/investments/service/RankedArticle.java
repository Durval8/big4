package com.financedash.investments.service;

import com.financedash.investments.provider.NewsArticle;

/** One selected feed entry: the article and the holding it represents. */
public record RankedArticle(String symbol, NewsArticle article) {
}
