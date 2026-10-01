package dev.ultras.discordlogs.service;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * The only door for punishment logs. "Command attempted" is never a punishment: callers must pass a confirmation
 * state, and unverified reports are dropped unless logs.punishments.settings.require-confirmation is false.
 */
public final class PunishmentService {
    public enum Type {
        BAN("🔨"), UNBAN("✅"), TEMPBAN("⏳"), IP_BAN("🌐"), IP_UNBAN("🌐"), KICK("👢"), MUTE("🔇"), UNMUTE("🔊"),
        WARN("⚠️"), TEMPMUTE("⏳"), JAIL("⛓️"), UNJAIL("🔓"), OTHER("🚫");
        final String emoji;
        Type(String e) { emoji = e; }
    }

    private final UltrasDiscordLogs plugin;

    public PunishmentService(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    /** Thread-safe: hops to the main thread for player data. @param confirmed TRUE verified, FALSE failed, null unknown */
    public void report(Type type, String target, UUID targetUuid, String executor, String reason, String duration,
                       String source, Boolean confirmed) {
        if (Boolean.FALSE.equals(confirmed)) return;
        boolean require = plugin.config().logSettings("punishments").settings().getBoolean("require-confirmation", true);
        if (confirmed == null && require) {
            plugin.log().debug("Punishment " + type + " for " + target + " dropped (no reliable confirmation).");
            return;
        }
        plugin.runMain(() -> {
            Player online = target == null ? null : Bukkit.getPlayerExact(target);
            PlayerSnapshot snap = online != null ? PlayerSnapshot.capture(plugin, online) : PlayerSnapshot.offline(plugin, target, targetUuid);
            String d = duration;
            if (d == null || d.isBlank())
                d = (type == Type.BAN || type == Type.MUTE || type == Type.IP_BAN) ? "Permanent" : "Unknown";
            LogContext c = LogContext.of("punishments").player(snap)
                    .put("target", target == null ? "Unknown" : target)
                    .put("executor", executor == null || executor.isBlank() ? "Unknown" : executor)
                    .put("reason", reason == null || reason.isBlank() ? "Unknown" : Text.sanitizeUserText(reason))
                    .put("duration", d)
                    .put("punishment_type", Text.pretty(type.name()))
                    .put("punishment_emoji", type.emoji)
                    .put("source", source == null ? "Unknown" : source)
                    .put("confirmation", confirmed == null ? "Unverified" : "Confirmed")
                    .dedupe(type + "|" + target + "|" + reason);
            plugin.logs().submit(c);
            plugin.counters().inc("punish_total");
            plugin.counters().inc("punish_" + type.name().toLowerCase(Locale.ROOT));
        });
    }
}
