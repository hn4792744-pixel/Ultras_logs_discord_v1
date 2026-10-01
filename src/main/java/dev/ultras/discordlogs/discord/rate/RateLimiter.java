package dev.ultras.discordlogs.discord.rate;

import java.util.concurrent.TimeUnit;

/** Per-lane limiter: minimum spacing between requests plus server-imposed pauses (Retry-After, X-RateLimit-*). */
public final class RateLimiter {
    private final long minIntervalNanos;
    private long notBefore = System.nanoTime();

    public RateLimiter(long minIntervalMs) {
        this.minIntervalNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, minIntervalMs));
    }

    /** Blocks the calling (worker) thread until the next request is allowed. */
    public void awaitTurn() throws InterruptedException {
        long wait;
        synchronized (this) {
            long now = System.nanoTime();
            wait = notBefore - now;
            notBefore = Math.max(now, notBefore) + minIntervalNanos;
        }
        if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
    }

    public synchronized void penalize(long ms) {
        long target = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, ms));
        if (target > notBefore) notBefore = target;
    }

    /** Applies Discord's bucket headers: when no requests remain, wait for the reset. */
    public void observe(int remaining, long resetAfterMs) {
        if (remaining == 0 && resetAfterMs > 0) penalize(resetAfterMs);
    }
}
