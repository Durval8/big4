package com.financedash.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TimeRangeTest {

    // A fixed reference date so the assertions don't depend on the wall clock.
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 24);

    @Test
    void weekResolvesToSevenDayInclusiveWindow() {
        assertThat(TimeRange.WEEK.resolveFrom(TODAY)).isEqualTo(LocalDate.of(2026, 7, 18));
    }

    @Test
    void monthResolvesToOneMonthInclusiveWindow() {
        assertThat(TimeRange.MONTH.resolveFrom(TODAY)).isEqualTo(LocalDate.of(2026, 6, 25));
    }

    @Test
    void yearResolvesToOneYearInclusiveWindow() {
        assertThat(TimeRange.YEAR.resolveFrom(TODAY)).isEqualTo(LocalDate.of(2025, 7, 25));
    }

    @Test
    void allResolvesToEpoch() {
        assertThat(TimeRange.ALL.resolveFrom(TODAY)).isEqualTo(LocalDate.of(1970, 1, 1));
    }
}
