package dev.ultras.discordlogs.hooks;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.service.CommandTracker;
import dev.ultras.discordlogs.service.PunishmentService;
import dev.ultras.discordlogs.util.DurationParser;
import dev.ultras.discordlogs.util.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bridges third-party plugins through reflection (no compile-time dependency, nothing required).
 * Each bridge only fires on events the other plugin itself reports, so results are confirmed by that plugin.
 */
final class IntegrationEvents {
    private static final Set<String> VANISH_LABELS = Set.of("vanish", "v", "sv", "svanish", "pv", "premiumvanish", "supervanish", "nv");

    private IntegrationEvents() {}

    static void registerAll(UltrasDiscordLogs plugin) {
        register(plugin, "AdvancedBan", "me.leoko.advancedban.bukkit.event.PunishmentEvent", e -> advancedBan(plugin, e, true));
        register(plugin, "AdvancedBan", "me.leoko.advancedban.bukkit.event.RevokePunishmentEvent", e -> advancedBan(plugin, e, false));
        register(plugin, "Essentials", "net.ess3.api.events.MuteStatusChangeEvent", e -> essMute(plugin, e));
        register(plugin, "Essentials", "net.ess3.api.events.JailStatusChangeEvent", e -> essJail(plugin, e));
        register(plugin, "Essentials", "net.ess3.api.events.UserBalanceUpdateEvent", e -> essBalance(plugin, e));
        register(plugin, "Essentials", "net.ess3.api.events.VanishStatusChangeEvent", e -> {
            Player p = player(Reflect.call(e, "getAffected"));
            if (p != null && Reflect.call(e, "getValue") instanceof Boolean b) vanish(plugin, p, b);
        });
        for (String pl : new String[]{"SuperVanish", "PremiumVanish"}) {
            register(plugin, pl, "de.myzelyam.api.vanish.PlayerHideEvent", e -> { if (Reflect.call(e, "getPlayer") instanceof Player p) vanish(plugin, p, true); });
            register(plugin, pl, "de.myzelyam.api.vanish.PlayerShowEvent", e -> { if (Reflect.call(e, "getPlayer") instanceof Player p) vanish(plugin, p, false); });
        }
    }

    @SuppressWarnings("unchecked")
    private static void register(UltrasDiscordLogs plugin, String pluginName, String className, Consumer<Object> handler) {
        Plugin other = Bukkit.getPluginManager().getPlugin(pluginName);
        if (other == null || !other.isEnabled()) return;
        try {
            Class<? extends Event> c = (Class<? extends Event>) Class.forName(className, true, other.getClass().getClassLoader());
            Bukkit.getPluginManager().registerEvent(c, new Listener() { }, EventPriority.MONITOR, (l, e) -> {
                try { handler.accept(e); }
                catch (Throwable t) { plugin.log().warnLimited("hook-" + className, 60_000, "Hook event " + c.getSimpleName() + " failed (" + t.getClass().getSimpleName() + ")."); }
            }, plugin, true);
            plugin.log().info("Hooked " + pluginName + " event " + c.getSimpleName());
        } catch (Throwable ignored) {
            // the other plugin version does not have this event: skip silently
        }
    }

    private static Player player(Object iuser) {
        if (iuser instanceof Player p) return p;
        return Reflect.call(iuser, "getBase") instanceof Player p ? p : null;
    }

    private static String nameOf(Object iuser) {
        Object n = Reflect.call(iuser, "getName");
        if (n == null) n = Reflect.call(Reflect.call(iuser, "getBase"), "getName");
        return n == null ? "Unknown" : String.valueOf(n);
    }

    private static String controller(Object e) {
        Object ctrl = Reflect.call(e, "getController");
        Object sender = Reflect.call(ctrl, "getSender");
        Object n = Reflect.call(sender, "getName");
        return n == null ? "Unknown" : String.valueOf(n);
    }

    private static void advancedBan(UltrasDiscordLogs plugin, Object event, boolean apply) {
        Object pun = Reflect.call(event, "getPunishment");
        if (pun == null) return;
        String type = String.valueOf(Reflect.call(pun, "getType")).toUpperCase(Locale.ROOT);
        PunishmentService.Type t;
        switch (type) {
            case "BAN" -> t = apply ? PunishmentService.Type.BAN : PunishmentService.Type.UNBAN;
            case "TEMP_BAN" -> t = apply ? PunishmentService.Type.TEMPBAN : PunishmentService.Type.UNBAN;
            case "IP_BAN", "TEMP_IP_BAN" -> t = apply ? PunishmentService.Type.IP_BAN : PunishmentService.Type.IP_UNBAN;
            case "MUTE" -> t = apply ? PunishmentService.Type.MUTE : PunishmentService.Type.UNMUTE;
            case "TEMP_MUTE" -> t = apply ? PunishmentService.Type.TEMPMUTE : PunishmentService.Type.UNMUTE;
            case "WARNING", "TEMP_WARNING" -> { if (!apply) return; t = PunishmentService.Type.WARN; }
            case "KICK" -> { if (!apply) return; t = PunishmentService.Type.KICK; }
            default -> { return; }
        }
        String name = String.valueOf(Reflect.call(pun, "getName"));
        Object uuid = Reflect.call(pun, "getUuid");
        UUID id = null;
        try { if (uuid != null) id = UUID.fromString(String.valueOf(uuid)); } catch (IllegalArgumentException ignored) { /* AdvancedBan uses dashless ids sometimes */ }
        String duration = null;
        if (apply && Reflect.call(pun, "getEnd") instanceof Long end && Reflect.call(pun, "getStart") instanceof Long start && end > 0 && end > start)
            duration = DurationParser.format(end - start);
        plugin.punishments().report(t, name, id, String.valueOf(Reflect.call(pun, "getOperator")),
                String.valueOf(Reflect.call(pun, "getReason")), duration, "AdvancedBan", true);
    }

    private static void essMute(UltrasDiscordLogs plugin, Object e) {
        if (!(Reflect.call(e, "getValue") instanceof Boolean on)) return;
        Object ts = Reflect.call(e, "getTimestamp");
        Long end = ts instanceof Long l ? l : (ts instanceof java.util.Optional<?> o && o.orElse(null) instanceof Long l2 ? l2 : null);
        boolean temp = on && end != null && end > System.currentTimeMillis();
        Object reason = Reflect.call(e, "getReason");
        plugin.punishments().report(on ? (temp ? PunishmentService.Type.TEMPMUTE : PunishmentService.Type.MUTE) : PunishmentService.Type.UNMUTE,
                nameOf(Reflect.call(e, "getAffected")), null, controller(e), reason == null ? null : String.valueOf(reason),
                temp ? DurationParser.format(end - System.currentTimeMillis()) : null, "EssentialsX", true);
    }

    private static void essJail(UltrasDiscordLogs plugin, Object e) {
        if (!(Reflect.call(e, "getValue") instanceof Boolean on)) return;
        plugin.punishments().report(on ? PunishmentService.Type.JAIL : PunishmentService.Type.UNJAIL,
                nameOf(Reflect.call(e, "getAffected")), null, controller(e), null, null, "EssentialsX", true);
    }

    private static void essBalance(UltrasDiscordLogs plugin, Object e) {
        if (!(Reflect.call(e, "getPlayer") instanceof Player p)) return;
        Object oldB = Reflect.call(e, "getOldBalance"), newB = Reflect.call(e, "getNewBalance"), cause = Reflect.call(e, "getCause");
        plugin.runMain(() -> plugin.logs().submit(LogContext.of("economy-transaction").player(PlayerSnapshot.capture(plugin, p))
                .put("old_balance", oldB).put("new_balance", newB).put("cause", cause).put("source", "EssentialsX")));
    }

    static void vanish(UltrasDiscordLogs plugin, Player p, boolean vanished) {
        plugin.runMain(() -> {
            var rec = plugin.commands().find(VANISH_LABELS, p.getName(), p.getUniqueId(), 2500);
            PlayerSnapshot s = PlayerSnapshot.capture(plugin, p);
            LogContext c = LogContext.of("vanish").player(s)
                    .put("is_vanish", vanished ? "Yes" : "No")
                    .put("action", vanished ? "Enter Vanish" : "Exit Vanish")
                    .put("previous_state", vanished ? "Visible" : "Vanished")
                    .put("new_state", vanished ? "Vanished" : "Visible")
                    .put("executor", rec == null ? "Unknown" : rec.executor())
                    .put("command", rec == null ? "Unknown" : rec.full())
                    .dedupe(s.uuid() + "|" + vanished);
            plugin.logs().submit(c);
        });
    }
}
