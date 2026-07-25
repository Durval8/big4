package com.financedash.investments.service;

import com.financedash.investments.provider.NewsArticle;
import java.math.BigDecimal;
import java.util.List;

/**
 * A stock's input to news selection: its symbol, current position value (drives the draw odds), and
 * its candidate articles newest-first (already capped to NEWS_PER_STOCK).
 */
public record NewsCandidate(String symbol, BigDecimal positionValue, List<NewsArticle> articles) {
}
