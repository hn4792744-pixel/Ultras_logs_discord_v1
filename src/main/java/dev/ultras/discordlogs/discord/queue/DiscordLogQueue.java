package dev.ultras.discordlogs.discord.queue;

import dev.ultras.discordlogs.discord.connection.DiscordConnection;
import dev.ultras.discordlogs.discord.connection.SendResult;
import dev.ultras.discordlogs.discord.rate.RateLimiter;
import dev.ultras.discordlogs.util.PluginLog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded asynchronous delivery queue. One lane (queue + worker thread + rate limiter) per Discord destination,
 * so every log that targets the same webhook is serialized and rate limited together.
 */
public final class DiscordLogQueue {
    public record Tuning(int capacity, int maxRetries, long baseBackoffMs, long maxBackoffMs, long minIntervalMs) {}

    private record Item(DiscordConnection connection, OutboundMessage message) {}

    private final PluginLog log;
    private volatile Tuning tuning;
    private final Map<String, Lane> lanes = new ConcurrentHashMap<>();
    private final AtomicInteger threadIds = new AtomicInteger();
    private final AtomicLong totalSent = new AtomicLong();
    private final AtomicLong totalFailed = new AtomicLong();
    private final AtomicLong totalDropped = new AtomicLong();
    private volatile boolean stopped;

    public DiscordLogQueue(PluginLog log, Tuning tuning) {
        this.log = log;
        this.tuning = tuning;
    }

    public void tune(Tuning t) { this.tuning = t; }

    /** @return false if the queue is shut down. Never blocks. */
    public boolean enqueue(DiscordConnection c, List<OutboundMessage> messages) {
        if (stopped) return false;
        Lane lane = lanes.computeIfAbsent(c.settings().laneKey(), k -> new Lane(k));
        for (OutboundMessage m : messages) lane.offer(new Item(c, m));
        return true;
    }

    public int pending() { return lanes.values().stream().mapToInt(l -> l.deque.size()).sum(); }
    public long sent() { return totalSent.get(); }
    public long failed() { return totalFailed.get(); }
    public long dropped() { return totalDropped.get(); }

    public int pending(DiscordConnection c) {
        Lane l = lanes.get(c.settings().laneKey());
        return l == null ? 0 : l.deque.size();
    }

    /** Stops lanes whose destination no longer exists after a reload (pending messages are still flushed). */
    public void retainLanes(Set<String> activeKeys) {
        lanes.entrySet().removeIf(e -> {
            if (activeKeys.contains(e.getKey())) return false;
            e.getValue().stop(System.nanoTime() + TimeUnit.SECONDS.toNanos(10));
            return true;
        });
    }

    /** Flushes what it can within the deadline, then stops every worker. */
    public void shutdown(int flushSeconds) {
        stopped = true;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(0, flushSeconds));
        lanes.values().forEach(l -> l.stop(deadline));
        for (Lane l : lanes.values()) {
            try {
                l.thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()) + 500));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (l.thread.isAlive()) l.thread.interrupt();
        }
        lanes.clear();
    }

    private final class Lane implements Runnable {
        final LinkedBlockingDeque<Item> deque;
        final RateLimiter limiter;
        final Thread thread;
        volatile boolean stopping;
        volatile long deadline = Long.MAX_VALUE;

        Lane(String key) {
            Tuning t = tuning;
            this.deque = new LinkedBlockingDeque<>(Math.max(50, t.capacity()));
            this.limiter = new RateLimiter(t.minIntervalMs());
            this.thread = new Thread(this, "ULTRAS-Discord-" + threadIds.incrementAndGet());
            this.thread.setDaemon(true);
            this.thread.start();
        }

        void offer(Item item) {
            item.connection().attachLimiter(limiter);
            while (!deque.offerLast(item)) {
                if (deque.pollFirst() != null) {
                    totalDropped.incrementAndGet();
                    log.warnLimited("queue-full-" + item.connection().id(), 60_000,
                            "Discord queue for connection '" + item.connection().id() + "' is full; dropping the oldest logs.");
                }
            }
        }

        void stop(long deadlineNanos) {
            stopping = true;
            deadline = deadlineNanos;
        }

        private boolean expired() {
            return stopping && System.nanoTime() - deadline > 0;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    if (stopping && (deque.isEmpty() || expired())) return;
                    Item item = deque.poll(400, TimeUnit.MILLISECONDS);
                    if (item == null) continue;
                    try {
                        deliver(item);
                    } catch (InterruptedException e) {
                        return;
                    } catch (Throwable t) {
                        totalFailed.incrementAndGet();
                        log.warn("Unexpected error while sending Discord log using connection '" + item.connection().id() + "'", t);
                    }
                }
            } catch (InterruptedException ignored) {
                // shutting down
            }
        }

        private void deliver(Item item) throws InterruptedException {
            DiscordConnection c = item.connection();
            Tuning t = tuning;
            int attempt = 0;
            int limited = 0;
            while (true) {
                if (expired()) { totalDropped.incrementAndGet(); return; }
                limiter.awaitTurn();
                SendResult r = c.send(item.message());
                switch (r.kind()) {
                    case SUCCESS -> {
                        c.recordSuccess();
                        totalSent.incrementAndGet();
                        return;
                    }
                    case RATE_LIMITED -> {
                        limiter.penalize(r.retryAfterMs());
                        if (++limited > 15) { fail(c, r); return; }
                        log.debug("Rate limited on connection '" + c.id() + "', waiting " + r.retryAfterMs() + "ms");
                    }
                    case RETRYABLE -> {
                        if (++attempt > t.maxRetries()) { fail(c, r); return; }
                        long backoff = Math.min(t.maxBackoffMs(), t.baseBackoffMs() * (1L << Math.min(attempt - 1, 10)));
                        if (stopping) { fail(c, r); return; }
                        TimeUnit.MILLISECONDS.sleep(backoff);
                    }
                    case PERMANENT -> { fail(c, r); return; }
                }
            }
        }

        private void fail(DiscordConnection c, SendResult r) {
            c.recordFailure(r);
            totalFailed.incrementAndGet();
            log.warnLimited("send-fail-" + c.id(), 30_000,
                    "Failed to send Discord log using connection '" + c.id() + "' (" + r.detail() + ").");
        }
    }
}
