package dev.ultras.discordlogs.listener;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.storage.PlayerData;
import dev.ultras.discordlogs.util.DurationParser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Join, first join, quit, returning-player statistics and IP mismatch detection. */
public final class PlayerSessionListener implements Listener {
    private final UltrasDiscordLogs plugin;

    public PlayerSessionListener(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        boolean first = !p.hasPlayedBefore();
        PlayerData d = plugin.players().getOrCreate(p.getUniqueId(), p.getName());
        boolean known = d.firstJoin > 0;
        d.name = p.getName();
        if (!known) d.firstJoin = first || p.getFirstPlayed() <= 0 ? now : p.getFirstPlayed();
        PlayerSnapshot snap = PlayerSnapshot.capture(plugin, p);

        plugin.counters().inc("joins");
        if (first) plugin.counters().inc("new_players");
        plugin.stats().activity(p, 1);

        plugin.ipMismatch().onJoin(snap, d, snap.ip(), now);
        String firstDate = plugin.clock().date(d.firstJoin), firstTime = plugin.clock().time(d.firstJoin);
        d.lastJoin = now;
        d.sessionStart = now;
        plugin.players().markDirty();

        plugin.logs().submit(LogContext.of("join").player(snap)
                .put("session_type", first ? "First Join" : "Returning").put("is_first_join", first ? "Yes" : "No").dedupe(snap.uuid()));
        if (first) {
            plugin.logs().submit(LogContext.of("first-join").player(snap)
                    .put("first_join_date", firstDate).put("first_join_time", firstTime).dedupe(snap.uuid()));
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) plugin.stats().logPlayerStats(p, snap); }, 40L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        PlayerData d = plugin.players().getOrCreate(p.getUniqueId(), p.getName());
        long session = d.sessionStart > 0 ? now - d.sessionStart : -1;
        d.lastQuit = now;
        d.sessionStart = 0;
        PlayerSnapshot snap = PlayerSnapshot.capture(plugin, p);
        try { plugin.stats().store(p, plugin.stats().capture(p)); } catch (Throwable t) { plugin.log().debug("quit stats failed"); }
        plugin.counters().inc("quits");
        plugin.logs().submit(LogContext.of("quit").player(snap)
                .put("session_duration", session < 0 ? "Unknown" : DurationParser.format(session))
                .put("reason", e.getReason().name()).dedupe(snap.uuid()));
    }
}
