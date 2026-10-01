package dev.ultras.discordlogs.logs;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.config.LogSettings;
import dev.ultras.discordlogs.config.Settings;
import dev.ultras.discordlogs.discord.connection.ConnectionStatus;
import dev.ultras.discordlogs.discord.connection.DiscordConnection;
import dev.ultras.discordlogs.discord.embed.EmbedData;
import dev.ultras.discordlogs.discord.embed.EmbedSplitter;
import dev.ultras.discordlogs.discord.queue.OutboundMessage;
import dev.ultras.discordlogs.storage.LogIdGenerator;
import dev.ultras.discordlogs.storage.LogRecord;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The log engine: Log -> (validate, dedupe, id) -> pipeline thread -> render -> Discord queue + local storage.
 * It never knows whether the destination is a webhook or a bot.
 */
public final class LogService {
    private final UltrasDiscordLogs plugin;
    private final LogRenderer renderer;
    private final LogIdGenerator ids;
    private final Map<String, Long> recent = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor pipeline = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10_000), r -> {
        Thread t = new Thread(r, "ULTRAS-Pipeline");
        t.setDaemon(true);
        return t;
    });

    public LogService(UltrasDiscordLogs plugin) {
        this.plugin = plugin;
        this.renderer = new LogRenderer(plugin);
        this.ids = new LogIdGenerator(plugin.clock(), Path.of(plugin.getDataFolder().getPath(), "data", "log-id.state"));
    }

    public LogIdGenerator ids() { return ids; }

    /** Safe to call from any thread. Returns immediately; heavy work happens on the pipeline thread. */
    public SubmitResult submit(LogContext ctx) {
        try {
            return doSubmit(ctx);
        } catch (Throwable t) {
            plugin.log().warn("Log '" + ctx.logId() + "' could not be processed", t);
            return SubmitResult.OVERLOADED;
        }
    }

    private final Map<String, Boolean> enabledCache = new ConcurrentHashMap<>();

    /** Cheap check for hot listeners; the cache is cleared on reload and when the GUI toggles a log. */
    public boolean isEnabled(String id) {
        return enabledCache.computeIfAbsent(id, k -> plugin.registry().get(k) != null && plugin.config().logSettings(k).enabled());
    }

    public void invalidateCache() { enabledCache.clear(); }

    private SubmitResult doSubmit(LogContext ctx) {
        LogDefinition def = plugin.registry().get(ctx.logId());
        if (def == null) return SubmitResult.UNKNOWN_LOG;
        LogSettings ls = plugin.config().logSettings(def.id());
        if (!ls.enabled()) return SubmitResult.DISABLED;

        DiscordConnection conn = resolveConnection(ls);
        SubmitResult connState = SubmitResult.QUEUED;
        if (conn == null) connState = SubmitResult.NO_CONNECTION;
        else if (!conn.settings().enabled() || !conn.settings().formatValid()) connState = SubmitResult.INVALID_CONNECTION;
        if (ctx.isTest() && connState != SubmitResult.QUEUED) return connState;

        if (!ctx.isTest() && ctx.dedupeKey() != null && ls.dedupeMs() > 0) {
            long now = System.currentTimeMillis();
            String k = def.id() + "|" + ctx.dedupeKey();
            Long prev = recent.put(k, now);
            if (prev != null && now - prev < ls.dedupeMs()) return SubmitResult.DUPLICATE;
            if (recent.size() > 5000) recent.values().removeIf(v -> now - v > 60_000);
        }

        String logId = ctx.isTest() ? "ULTRAS-TEST" : ids.next();
        long now = System.currentTimeMillis();
        Map<String, String> vars = new LinkedHashMap<>(ctx.vars());
        Settings s = plugin.settings();
        vars.put("log_id", logId);
        vars.putIfAbsent("server", plugin.getServer().getName());
        vars.putIfAbsent("version", plugin.getServer().getMinecraftVersion());
        vars.put("date", plugin.clock().date(now));
        vars.put("time", plugin.clock().time(now));
        vars.put("log_type", def.id());
        if (!vars.containsKey("location") && vars.containsKey("x")) vars.put("location", vars.get("x") + ", " + vars.get("y") + ", " + vars.get("z"));

        final DiscordConnection target = connState == SubmitResult.QUEUED ? conn : null;
        Runnable job = () -> process(def, ls, target, ctx.isTest(), vars, logId, now);
        if (!s.asyncLogging) { job.run(); return connState; }
        try {
            pipeline.execute(job);
        } catch (RejectedExecutionException e) {
            plugin.log().warnLimited("pipeline-full", 30_000, "Log pipeline is overloaded; dropping logs.");
            return SubmitResult.OVERLOADED;
        }
        return connState;
    }

    private DiscordConnection resolveConnection(LogSettings ls) {
        DiscordConnection c = plugin.connections().get(ls.connection());
        Settings s = plugin.settings();
        if (s.fallbackToDefault && (c == null || (c.settings().enabled() && !c.settings().configured()))) {
            DiscordConnection d = plugin.connections().defaultConnection();
            if (d != null) return d;
        }
        return c;
    }

    /** Resolves the connection a log would use (used by GUI and test validation). */
    public DiscordConnection connectionFor(String logId) {
        return resolveConnection(plugin.config().logSettings(logId));
    }

    private void process(LogDefinition def, LogSettings ls, DiscordConnection conn, boolean test,
                         Map<String, String> vars, String logId, long now) {
        try {
            if (!test) {
                plugin.counters().inc("log_" + def.id());
                plugin.storage().appendAsync(toRecord(def, vars, logId, now));
            }
            if (conn == null) return;
            LogRenderer.Rendered r = renderer.render(def, ls, vars, test, now);
            List<EmbedData> parts = EmbedSplitter.split(r.embed());
            List<OutboundMessage> out = new ArrayList<>();
            List<List<EmbedData>> groups = EmbedSplitter.group(parts);
            for (int i = 0; i < groups.size(); i++) out.add(new OutboundMessage(i == 0 ? r.content() : "", groups.get(i)));
            if (!plugin.queue().enqueue(conn, out))
                plugin.log().debug("Queue closed; log " + logId + " not delivered to Discord.");
        } catch (Throwable t) {
            plugin.log().warn("Failed to process log '" + def.id() + "'", t);
        }
    }

    private LogRecord toRecord(LogDefinition def, Map<String, String> v, String logId, long now) {
        Map<String, String> data = new LinkedHashMap<>(v);
        for (String k : new String[]{"player", "uuid", "ip", "world", "x", "y", "z", "location", "executor", "target", "log_id", "date", "time", "avatar", "prefix"})
            data.remove(k);
        String coords = v.containsKey("x") ? v.get("x") + ", " + v.get("y") + ", " + v.get("z") : "Unknown";
        return new LogRecord(logId, def.id(), def.fileKey(), v.getOrDefault("player", "Unknown"), v.getOrDefault("uuid", "Unknown"),
                v.getOrDefault("ip", "Unknown"), v.getOrDefault("world", "Unknown"), coords, v.getOrDefault("executor", "Unknown"),
                v.getOrDefault("target", "Unknown"), now, v.get("date"), v.get("time"), data);
    }

    public void shutdown() {
        pipeline.shutdown();
        try {
            if (!pipeline.awaitTermination(5, TimeUnit.SECONDS)) pipeline.shutdownNow();
        } catch (InterruptedException e) {
            pipeline.shutdownNow();
            Thread.currentThread().interrupt();
        }
        ids.flush();
    }
}
