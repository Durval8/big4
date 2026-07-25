package com.financedash.investments.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Token-bucket behavior driven by a fake nanosecond clock (no sleeping). */
class RateLimiterTest {

    @Test
    void startsFullThenRefillsOverTime() {
        long[] now = {0L};
        RateLimiter limiter = new RateLimiter(60, () -> now[0]); // 60/min = 1 token/second

        // Bucket starts at capacity: 60 immediate permits, then empty.
        for (int i = 0; i < 60; i++) {
            assertThat(limiter.tryAcquire()).as("permit %d", i).isTrue();
        }
        assertThat(limiter.tryAcquire()).isFalse();

        // One second later exactly one permit has refilled.
        now[0] += 1_000_000_000L;
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void refillIsCappedAtCapacity() {
        long[] now = {0L};
        RateLimiter limiter = new RateLimiter(10, () -> now[0]);
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire();
        }
        // Idle for an hour — refill must not exceed capacity.
        now[0] += 3_600_000_000_000L;
        int granted = 0;
        while (limiter.tryAcquire()) {
            granted++;
        }
        assertThat(granted).isEqualTo(10);
    }
}
