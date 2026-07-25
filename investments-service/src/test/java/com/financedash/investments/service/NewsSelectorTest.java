package com.financedash.investments.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.financedash.investments.provider.NewsArticle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Value-weighted stock draw — deterministic under a seeded RNG; structural + statistical checks. */
class NewsSelectorTest {

    private final NewsSelector selector = new NewsSelector(0.625, 7);

    private static NewsArticle art(String url) {
        return new NewsArticle("Headline " + url, "Summary", url, "src", Instant.parse("2026-07-24T10:00:00Z"));
    }

    private static NewsCandidate stock(String symbol, long value, String... urls) {
        return new NewsCandidate(symbol, BigDecimal.valueOf(value), java.util.Arrays.stream(urls).map(NewsSelectorTest::art).toList());
    }

    @Test
    void capsAtMaxItems() {
        List<NewsCandidate> candidates = List.of(
                stock("A", 500, "a1", "a2", "a3"),
                stock("B", 400, "b1", "b2", "b3"),
                stock("C", 300, "c1", "c2", "c3"));
        List<RankedArticle> out = selector.select(candidates, new Random(1));
        assertThat(out).hasSize(7); // 9 candidates, capped at 7
    }

    @Test
    void exhaustsBothStocksWhenUnderCap() {
        List<NewsCandidate> candidates = List.of(
                stock("A", 500, "a1", "a2", "a3"),
                stock("B", 400, "b1", "b2", "b3"));
        List<RankedArticle> out = selector.select(candidates, new Random(1));
        assertThat(out).hasSize(6);
        assertThat(out).extracting(r -> r.article().url()).doesNotHaveDuplicates();
        assertThat(out).extracting(RankedArticle::symbol).contains("A", "B");
    }

    @Test
    void dedupesSharedUrlAcrossStocks() {
        List<NewsCandidate> candidates = List.of(
                stock("A", 500, "shared", "a2"),
                stock("B", 400, "shared", "b2"));
        List<RankedArticle> out = selector.select(candidates, new Random(3));
        long shared = out.stream().filter(r -> r.article().url().equals("shared")).count();
        assertThat(shared).isEqualTo(1);
        assertThat(out).extracting(r -> r.article().url()).doesNotHaveDuplicates();
    }

    @Test
    void singleHoldingReturnsItsArticles() {
        List<RankedArticle> out = selector.select(List.of(stock("A", 500, "a1", "a2", "a3")), new Random(7));
        assertThat(out).hasSize(3);
        assertThat(out).allMatch(r -> r.symbol().equals("A"));
    }

    @Test
    void emptyInputYieldsEmpty() {
        assertThat(selector.select(List.of(), new Random(1))).isEmpty();
    }

    @Test
    void deterministicForAGivenSeed() {
        List<NewsCandidate> candidates = List.of(
                stock("A", 500, "a1", "a2", "a3"),
                stock("B", 400, "b1", "b2", "b3"),
                stock("C", 300, "c1", "c2", "c3"));
        List<RankedArticle> first = selector.select(candidates, new Random(42));
        List<RankedArticle> second = selector.select(candidates, new Random(42));
        assertThat(first).usingRecursiveComparison().isEqualTo(second);
    }

    @Test
    void biggerHoldingIsDrawnFirstMoreOften() {
        // One article each; fill one slot; count who lands it across many seeds.
        NewsCandidate big = stock("BIG", 1000, "big");
        NewsCandidate small = stock("SMALL", 1, "small");
        NewsSelector oneSlot = new NewsSelector(0.625, 1);
        int bigWins = 0;
        for (int seed = 0; seed < 500; seed++) {
            RankedArticle first = oneSlot.select(List.of(big, small), new Random(seed)).get(0);
            if (first.symbol().equals("BIG")) {
                bigWins++;
            }
        }
        assertThat(bigWins).isGreaterThan(250);            // BIG has the higher odds…
        assertThat(bigWins).isLessThan(500);               // …but SMALL still wins sometimes
    }
}
