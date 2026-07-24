package com.financedash.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 24);

    @Test
    void rangeWinsAndResolvesAgainstToday() {
        Period p = Period.resolve(TimeRange.MONTH, null, null, TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 6, 25));
        assertThat(p.to()).isEqualTo(TODAY);
    }

    @Test
    void rangeTakesPrecedenceOverExplicitFrom() {
        Period p = Period.resolve(TimeRange.ALL, LocalDate.of(2026, 1, 1), null, TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(1970, 1, 1));
    }

    @Test
    void explicitFromToUsedWhenNoRange() {
        Period p = Period.resolve(null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(p.to()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void defaultsToLastMonthWhenNothingProvided() {
        Period p = Period.resolve(null, null, null, TODAY);
        assertThat(p.from()).isEqualTo(TODAY.minusMonths(1).plusDays(1));
        assertThat(p.to()).isEqualTo(TODAY);
    }
}
