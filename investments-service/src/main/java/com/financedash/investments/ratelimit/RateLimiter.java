package com.financedash.investments.ratelimit;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Token-bucket rate limiter — the real governor of provider throughput (the broker gives
 * durability/retry, not rate relief). Refills continuously to {@code permitsPerMinute}. The clock
 * is injectable ({@code nanoClock}) so tests can drive it deterministically without sleeping.
 * Thread-safe.
 */
public class RateLimiter {

    private final double capacity;
    private final double refillPerNano;
    private final LongSupplier nanoClock;

    private double available;
    private long lastRefillNanos;

    public RateLimiter(int permitsPerMinute, LongSupplier nanoClock) {
        if (permitsPerMinute <= 0) {
            throw new IllegalArgumentException("permitsPerMinute must be positive");
        }
        this.capacity = permitsPerMinute;
        this.refillPerNano = permitsPerMinute / 60_000_000_000.0; // per minute → per nanosecond
        this.nanoClock = nanoClock;
        this.available = permitsPerMinute;
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    /** Non-blocking: consume one permit if available. */
    public synchronized boolean tryAcquire() {
        refill();
        if (available >= 1.0) {
            available -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * Block until a permit is available or {@code timeout} elapses. Returns false on timeout.
     * Uses short sleeps; the wait is against wall-clock, independent of the injected nano clock.
     */
    public boolean acquire(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            if (tryAcquire()) {
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(20);
        }
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsed = now - lastRefillNanos;
        if (elapsed > 0) {
            available = Math.min(capacity, available + elapsed * refillPerNano);
            lastRefillNanos = now;
        }
    }
}
