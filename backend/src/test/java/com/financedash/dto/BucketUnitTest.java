package com.financedash.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BucketUnitTest {

    @Test
    void thirtyOneDayWindowIsDay() {
        // 31 days inclusive: 2026-07-01..2026-07-31.
        assertThat(BucketUnit.forWindow(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .isEqualTo(BucketUnit.DAY);
    }

    @Test
    void thirtyTwoDayWindowIsWeek() {
        // 32 days inclusive: one day over the DAY band; ceil(32/7) = 5 weeks, within the WEEK band.
        assertThat(BucketUnit.forWindow(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)))
                .isEqualTo(BucketUnit.WEEK);
    }

    @Test
    void oneHundredEightyTwoDayWindowIsWeek() {
        // 182 days = exactly 26 weeks.
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = from.plusDays(181);
        assertThat(BucketUnit.forWindow(from, to)).isEqualTo(BucketUnit.WEEK);
    }

    @Test
    void oneHundredEightyThreeDayWindowIsMonth() {
        // 183 days = ceil(183/7) = 27 weeks, one over the WEEK band.
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = from.plusDays(182);
        assertThat(BucketUnit.forWindow(from, to)).isEqualTo(BucketUnit.MONTH);
    }

    @Test
    void dayBoundariesAreOneDayEach() {
        List<LocalDate[]> boundaries = BucketUnit.DAY.boundaries(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));
        assertThat(boundaries).hasSize(3);
        assertThat(boundaries.get(0)).containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));
        assertThat(boundaries.get(2)).containsExactly(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3));
    }

    @Test
    void weekBoundariesAreAnchoredAtFromAndClipTheLastOne() {
        // 10-day window: [Jul 1..Jul 7], [Jul 8..Jul 10] (clipped).
        List<LocalDate[]> boundaries = BucketUnit.WEEK.boundaries(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));
        assertThat(boundaries).hasSize(2);
        assertThat(boundaries.get(0)).containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7));
        assertThat(boundaries.get(1)).containsExactly(LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 10));
    }

    @Test
    void monthBoundariesAlignToCalendarMonthsWithPartialEnds() {
        // Jun 15 -> Aug 10: partial June, full July, partial August.
        List<LocalDate[]> boundaries = BucketUnit.MONTH.boundaries(
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 8, 10));
        assertThat(boundaries).hasSize(3);
        assertThat(boundaries.get(0)).containsExactly(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 30));
        assertThat(boundaries.get(1)).containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        assertThat(boundaries.get(2)).containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));
    }
}
