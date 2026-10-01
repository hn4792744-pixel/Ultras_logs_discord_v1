package dev.ultras.discordlogs.listener;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.api.event.UltrasCustomLogEvent;
import dev.ultras.discordlogs.api.event.UltrasFreezeEvent;
import dev.ultras.discordlogs.api.event.UltrasPunishmentEvent;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.service.PunishmentService;
import dev.ultras.discordlogs.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Locale;

/** Receives events fired by other plugins and turns them into logs. */
public final class ApiListener implements Listener {
    private final UltrasDiscordLogs plugin;

    public ApiListener(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCustom(UltrasCustomLogEvent e) {
        Runnable r = () -> {
            LogContext c = LogContext.of(e.logId()).putAll(e.data());
            if (e.player() != null) c.player(PlayerSnapshot.capture(plugin, e.player()));
            plugin.logs().submit(c);
        };
        if (e.player() != null && !Bukkit.isPrimaryThread()) plugin.runMain(r); else r.run();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPunishment(UltrasPunishmentEvent e) {
        PunishmentService.Type t;
        try { t = PunishmentService.Type.valueOf(e.type().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { t = PunishmentService.Type.OTHER; }
        plugin.punishments().report(t, e.target(), e.targetUuid(), e.executor(), e.reason(),
                e.durationMillis() > 0 ? DurationParser.format(e.durationMillis()) : null, e.source(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFreeze(UltrasFreezeEvent e) {
        PlayerSnapshot snap = PlayerSnapshot.capture(plugin, e.player());
        plugin.logs().submit(LogContext.of("freeze").player(snap).put("action", e.frozen() ? "Freeze" : "Unfreeze")
                .put("executor", e.executor() == null ? "Unknown" : e.executor()).put("target", snap.name())
                .put("reason", e.reason() == null ? "Unknown" : e.reason()).put("command", e.command() == null ? "Unknown" : e.command())
                .dedupe(snap.uuid() + "|" + e.frozen()));
    }
}
