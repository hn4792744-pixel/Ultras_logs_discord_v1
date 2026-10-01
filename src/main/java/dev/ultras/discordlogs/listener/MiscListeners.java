package dev.ultras.discordlogs.listener;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.service.CommandTracker;
import dev.ultras.discordlogs.util.Reflect;
import dev.ultras.discordlogs.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.List;
import java.util.Set;

/** Advancements, kicks and server/world events. */
public final class MiscListeners implements Listener {
    private static final Set<String> KICK_LABELS = Set.of("kick", "ekick", "kickall");
    private final UltrasDiscordLogs plugin;

    public MiscListeners(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    private static String plain(Component c) { return c == null ? "Unknown" : PlainTextComponentSerializer.plainText().serialize(c); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        var adv = e.getAdvancement();
        var display = adv.getDisplay();
        if (display == null) return; // recipes and hidden root advancements
        Player p = e.getPlayer();
        String key = adv.getKey().getKey();
        PlayerSnapshot snap = PlayerSnapshot.capture(plugin, p);
        plugin.logs().submit(LogContext.of("advancement").player(snap).put("advancement", plain(display.title()))
                .put("advancement_description", plain(display.description()))
                .put("advancement_category", Text.pretty(key.contains("/") ? key.substring(0, key.indexOf('/')) : key))
                .put("advancement_key", adv.getKey().toString()).dedupe(snap.uuid() + "|" + key));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent e) {
        String cause = e.getCause().name();
        List<String> ignored = plugin.config().logSettings("punishments").settings().getStringList("kick-ignored-causes");
        if (ignored.contains(cause)) return;
        Player p = e.getPlayer();
        CommandTracker.Rec rec = plugin.commands().find(KICK_LABELS, p.getName(), null, 2500);
        plugin.punishments().report(dev.ultras.discordlogs.service.PunishmentService.Type.KICK, p.getName(), p.getUniqueId(),
                rec == null ? "Unknown" : rec.executor(), plain(e.reason()), null,
                rec == null ? Text.pretty(cause) : rec.source(), true);
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent e) {
        boolean startup = e.getType() == ServerLoadEvent.LoadType.STARTUP;
        plugin.logs().submit(LogContext.of(startup ? "server-start" : "server-reload")
                .put("action", startup ? "Server started" : "Server reloaded").put("player", "Server").put("plugins_count", plugin.getServer().getPluginManager().getPlugins().length));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent e) {
        plugin.logs().submit(LogContext.of("world-load").put("world_name", e.getWorld().getName()).put("environment", Text.pretty(e.getWorld().getEnvironment().name())).put("player", "Server").dedupe(e.getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent e) {
        plugin.logs().submit(LogContext.of("world-unload").put("world_name", e.getWorld().getName()).put("environment", Text.pretty(e.getWorld().getEnvironment().name())).put("player", "Server").dedupe(e.getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        plugin.logs().submit(LogContext.of("weather").put("world", e.getWorld().getName()).put("weather", e.toWeatherState() ? "Rain started" : "Rain stopped").put("player", "Server"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent e) {
        plugin.logs().submit(LogContext.of("weather").put("world", e.getWorld().getName()).put("weather", e.toThunderState() ? "Thunder started" : "Thunder stopped").put("player", "Server"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTime(TimeSkipEvent e) {
        plugin.logs().submit(LogContext.of("time-change").put("world", e.getWorld().getName()).put("skip_reason", Text.pretty(e.getSkipReason().name()))
                .put("skip_amount", e.getSkipAmount()).put("player", "Server"));
    }

    /** Registered separately: the event class is resolved at runtime so a missing class can never break startup. */
    public static final class GameRuleListener implements Listener {
        private final UltrasDiscordLogs plugin;

        public GameRuleListener(UltrasDiscordLogs plugin) { this.plugin = plugin; }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onRule(io.papermc.paper.event.world.WorldGameRuleChangeEvent e) {
            Object sender = e.getCommandSender();
            Object name = sender == null ? null : Reflect.call(sender, "getName");
            plugin.logs().submit(LogContext.of("gamerule").put("world", e.getWorld().getName())
                    .put("gamerule", Reflect.nameOf(e.getGameRule())).put("value", e.getValue())
                    .put("executor", name == null ? "Unknown" : name).put("player", "Server"));
        }
    }
}
