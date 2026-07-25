package com.financedash.investments.service;

import com.financedash.investments.provider.NewsArticle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Value-weighted stock draw (see docs/INVESTMENT_NEWS.md). Stocks are ordered by position value
 * (ties broken randomly) and given geometrically-decreasing odds {@code decay^rank}. To fill the
 * feed we repeatedly draw a stock by its odds and take its newest unused article (deduped by url);
 * when a stock's articles are exhausted it drops out and the remaining stocks' odds renormalize.
 * The RNG is passed in so tests can seed it for deterministic results.
 */
@Component
public class NewsSelector {

    private final double decay;
    private final int maxItems;

    public NewsSelector(@Value("${news.weight-decay:0.625}") double decay,
                        @Value("${news.max-items:7}") int maxItems) {
        this.decay = decay;
        this.maxItems = maxItems;
    }

    private static final class Stock {
        final String symbol;
        double weight;
        final Deque<NewsArticle> remaining;
        Stock(String symbol, List<NewsArticle> articles) {
            this.symbol = symbol;
            this.remaining = new ArrayDeque<>(articles);
        }
    }

    public List<RankedArticle> select(List<NewsCandidate> candidates, RandomGenerator rng) {
        // Order by position value desc; equal values get a random order (stable via a random key).
        List<NewsCandidate> ordered = new ArrayList<>(candidates);
        java.util.Map<NewsCandidate, Double> tie = new java.util.IdentityHashMap<>();
        for (NewsCandidate c : ordered) {
            tie.put(c, rng.nextDouble());
        }
        ordered.sort(Comparator
                .comparing(NewsCandidate::positionValue).reversed()
                .thenComparing(tie::get));

        // Assign decay^rank weights.
        List<Stock> stocks = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Stock s = new Stock(ordered.get(i).symbol(), ordered.get(i).articles());
            s.weight = Math.pow(decay, i);
            stocks.add(s);
        }

        List<RankedArticle> result = new ArrayList<>();
        Set<String> usedUrls = new HashSet<>();

        while (result.size() < maxItems) {
            List<Stock> active = new ArrayList<>();
            for (Stock s : stocks) {
                if (peekUnused(s, usedUrls) != null) {
                    active.add(s);
                }
            }
            if (active.isEmpty()) {
                break;
            }
            Stock chosen = drawByWeight(active, rng);
            NewsArticle article = peekUnused(chosen, usedUrls);
            chosen.remaining.pollFirst();
            usedUrls.add(article.url());
            result.add(new RankedArticle(chosen.symbol, article));
        }
        return result;
    }

    /** Front article whose url isn't already used, discarding used-url duplicates; null if none. */
    private static NewsArticle peekUnused(Stock s, Set<String> usedUrls) {
        while (!s.remaining.isEmpty() && usedUrls.contains(s.remaining.peekFirst().url())) {
            s.remaining.pollFirst();
        }
        return s.remaining.peekFirst();
    }

    /** Weighted random pick over the active stocks (weights renormalized implicitly by the sum). */
    private static Stock drawByWeight(List<Stock> active, RandomGenerator rng) {
        double total = 0;
        for (Stock s : active) {
            total += s.weight;
        }
        double r = rng.nextDouble() * total;
        double acc = 0;
        for (Stock s : active) {
            acc += s.weight;
            if (r < acc) {
                return s;
            }
        }
        return active.get(active.size() - 1); // floating-point safety net
    }
}
