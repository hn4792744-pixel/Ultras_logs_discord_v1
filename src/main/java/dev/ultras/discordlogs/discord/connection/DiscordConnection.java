package dev.ultras.discordlogs.discord.connection;

import dev.ultras.discordlogs.discord.queue.OutboundMessage;
import dev.ultras.discordlogs.discord.rate.RateLimiter;

public interface DiscordConnection {
    String id();
    ConnectionType type();
    ConnectionSettings settings();
    ConnectionStatus status();
    String statusDetail();
    long sent();
    long failed();

    /** Blocking send; only ever called from queue worker threads. */
    SendResult send(OutboundMessage message);

    /** Blocking read-only check (GET) that the destination exists; never sends a message. */
    SendResult validateRemote();

    void attachLimiter(RateLimiter limiter);
    void recordSuccess();
    void recordFailure(SendResult r);
}
