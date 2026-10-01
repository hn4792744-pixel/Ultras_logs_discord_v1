package dev.ultras.discordlogs.discord.connection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ultras.discordlogs.discord.embed.EmbedData;
import dev.ultras.discordlogs.discord.embed.EmbedJson;
import dev.ultras.discordlogs.discord.queue.OutboundMessage;
import dev.ultras.discordlogs.discord.rate.RateLimiter;
import dev.ultras.discordlogs.util.SecretRedactor;
import dev.ultras.discordlogs.util.Text;

import java.util.concurrent.atomic.AtomicLong;

abstract class AbstractConnection implements DiscordConnection {
    protected final ConnectionSettings settings;
    protected final DiscordHttp http;
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private volatile ConnectionStatus status;
    private volatile String detail = "";
    private volatile RateLimiter limiter;

    protected AbstractConnection(ConnectionSettings s, DiscordHttp http) {
        this.settings = s;
        this.http = http;
        SecretRedactor.register(s.secret());
        if (!s.enabled()) { status = ConnectionStatus.DISABLED; detail = "disabled in configuration"; }
        else if (!s.configured()) { status = bad(); detail = "not configured yet"; }
        else if (!s.formatValid()) { status = bad(); detail = "invalid format"; }
        else { status = ConnectionStatus.UNKNOWN; detail = "not validated yet"; }
    }

    protected abstract ConnectionStatus good();
    protected abstract ConnectionStatus bad();

    /** Attached by the queue so response headers feed the lane's limiter. */
    @Override
    public void attachLimiter(RateLimiter l) { this.limiter = l; }

    protected void observe(DiscordHttp.Response r) {
        RateLimiter l = limiter;
        if (l != null && r.bucket() != null) l.observe(r.bucket().remaining(), r.bucket().resetAfterMs());
    }

    @Override public String id() { return settings.id(); }
    @Override public ConnectionType type() { return settings.type(); }
    @Override public ConnectionSettings settings() { return settings; }
    @Override public ConnectionStatus status() { return status; }
    @Override public String statusDetail() { return detail; }
    @Override public long sent() { return sent.get(); }
    @Override public long failed() { return failed.get(); }

    @Override
    public void recordSuccess() {
        sent.incrementAndGet();
        if (status != ConnectionStatus.DISABLED) { status = good(); detail = "ok"; }
    }

    @Override
    public void recordFailure(SendResult r) {
        failed.incrementAndGet();
        detail = SecretRedactor.redact(r.detail());
        if (r.kind() == SendResult.Kind.PERMANENT && status != ConnectionStatus.DISABLED) status = bad();
    }

    protected void applyValidation(SendResult r) {
        if (status == ConnectionStatus.DISABLED || !settings.formatValid()) return;
        if (r.success()) { status = good(); detail = "validated"; }
        else if (r.kind() == SendResult.Kind.PERMANENT) { status = bad(); detail = r.detail(); }
        else detail = r.detail();
    }

    protected JsonObject body(OutboundMessage m, boolean webhook) {
        JsonObject o = new JsonObject();
        if (m.content() != null && !m.content().isBlank()) o.addProperty("content", Text.truncate(m.content(), 2000));
        JsonArray arr = new JsonArray();
        for (EmbedData e : m.embeds()) arr.add(EmbedJson.toJson(e));
        if (arr.size() > 0) o.add("embeds", arr);
        JsonObject am = new JsonObject();
        JsonArray parse = new JsonArray();
        if (settings.allowUsers()) parse.add("users");
        if (settings.allowRoles()) parse.add("roles");
        if (settings.allowEveryone()) parse.add("everyone");
        am.add("parse", parse);
        o.add("allowed_mentions", am);
        if (webhook) {
            String u = settings.username();
            String low = u == null ? "" : u.toLowerCase();
            if (u != null && !u.isBlank() && !low.contains("discord") && !low.contains("clyde"))
                o.addProperty("username", Text.truncate(u, 80));
            if (settings.avatarUrl() != null && settings.avatarUrl().startsWith("http")) o.addProperty("avatar_url", settings.avatarUrl());
            if (settings.threadName() != null && !settings.threadName().isBlank()) o.addProperty("thread_name", Text.truncate(settings.threadName(), 100));
        }
        return o;
    }
}
