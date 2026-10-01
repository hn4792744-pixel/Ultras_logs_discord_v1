package dev.ultras.discordlogs.service;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.config.LogSettings;
import dev.ultras.discordlogs.discord.connection.DiscordConnection;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.LogDefinition;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.logs.SubmitResult;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test flow: log exists -> permission -> enabled -> connection exists -> valid -> cooldown -> build data -> queue -> result. */
public final class TestService {
    public enum Outcome { SENT, UNKNOWN_LOG, NO_PERMISSION, DISABLED, NO_CONNECTION, INVALID_CONNECTION, COOLDOWN, FAILED }

    private final UltrasDiscordLogs plugin;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public TestService(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    /** Runs the flow and informs the sender. Must be called on the main thread. */
    public Outcome run(CommandSender sender, String logId) {
        Outcome o = execute(sender, logId);
        String key = switch (o) {
            case SENT -> "test.sent";
            case UNKNOWN_LOG -> "test.unknown-log";
            case NO_PERMISSION -> "errors.no-permission";
            case DISABLED -> "test.disabled";
            case NO_CONNECTION -> "test.no-connection";
            case INVALID_CONNECTION -> "test.invalid-connection";
            case COOLDOWN -> "test.cooldown";
            default -> "test.failed";
        };
        long left = 0;
        if (o == Outcome.COOLDOWN) left = remaining(sender);
        LogSettings ls = plugin.registry().get(logId) == null ? null : plugin.config().logSettings(logId);
        plugin.messages().send(sender, key, "log", logId, "seconds", String.valueOf(left),
                "connection", ls == null ? "-" : ls.connection());
        if (sender instanceof Player p) plugin.sounds().play(p, o == Outcome.SENT ? "success" : "error");
        return o;
    }

    private String who(CommandSender s) { return s instanceof Player p ? p.getUniqueId().toString() : "console"; }

    private long remaining(CommandSender s) {
        Long last = cooldowns.get(who(s));
        if (last == null) return 0;
        return Math.max(1, (long) Math.ceil((plugin.settings().testCooldownSeconds * 1000L - (System.currentTimeMillis() - last)) / 1000.0));
    }

    private Outcome execute(CommandSender sender, String logId) {
        LogDefinition def = plugin.registry().get(logId);
        if (def == null) return Outcome.UNKNOWN_LOG;
        LogSettings ls = plugin.config().logSettings(logId);
        if (!sender.hasPermission("ultras.discordlogs.test") || !sender.hasPermission(ls.permission())) return Outcome.NO_PERMISSION;
        if (!ls.enabled()) return Outcome.DISABLED;
        DiscordConnection c = plugin.logs().connectionFor(logId);
        if (c == null) return Outcome.NO_CONNECTION;
        if (!c.settings().enabled() || !c.settings().formatValid()) return Outcome.INVALID_CONNECTION;
        long now = System.currentTimeMillis();
        long cd = plugin.settings().testCooldownSeconds * 1000L;
        Long last = cooldowns.get(who(sender));
        if (last != null && now - last < cd) return Outcome.COOLDOWN;

        LogContext ctx = LogContext.of(logId).test(true);
        if (sender instanceof Player p) ctx.player(PlayerSnapshot.capture(plugin, p));
        else ctx.put("player", "Console").put("uuid", "Console").put("is_op", "Yes").put("is_vanish", "Unknown").put("world", "Unknown");
        ctx.putIfAbsent("executor", sender.getName()).putIfAbsent("target", sender.getName())
                .putIfAbsent("reason", "Test log").putIfAbsent("command", "/uc_discord_logs " + logId);
        def.sample().forEach(ctx::putIfAbsent);
        SubmitResult r = plugin.logs().submit(ctx);
        if (r != SubmitResult.QUEUED) return r == SubmitResult.DISABLED ? Outcome.DISABLED : Outcome.FAILED;
        cooldowns.put(who(sender), now);
        if (cooldowns.size() > 500) cooldowns.values().removeIf(v -> now - v > cd);
        return Outcome.SENT;
    }
}
