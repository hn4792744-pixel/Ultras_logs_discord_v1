package dev.ultras.discordlogs.listener;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.service.CommandTracker;
import dev.ultras.discordlogs.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Set;

public final class TeleportListener implements Listener {
    private static final Set<String> TP_LABELS = Set.of("tp", "teleport", "tphere", "tpo", "tppos", "tpa", "tpaccept", "tpahere", "etp", "etphere", "tpall", "back", "spawn", "home", "warp");
    private final UltrasDiscordLogs plugin;

    public TeleportListener(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    private static String loc(Location l) {
        return l == null || l.getWorld() == null ? "Unknown" : l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!plugin.logs().isEnabled("teleport")) return;
        String cause = e.getCause().name();
        List<String> ignored = plugin.config().logSettings("teleport").settings().getStringList("ignored-causes");
        if (ignored.contains(cause)) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to != null && from.getWorld() == to.getWorld() && from.distanceSquared(to) < 1.0) return; // micro moves (e.g. dismounts)
        Player p = e.getPlayer();
        CommandTracker.Rec rec = "COMMAND".equals(cause) ? plugin.commands().find(TP_LABELS, p.getName(), p.getUniqueId(), 2000) : null;
        PlayerSnapshot snap = PlayerSnapshot.capture(plugin, p);
        LogContext c = LogContext.of("teleport").player(snap).put("teleport_type", Text.pretty(cause)).put("from", loc(from)).put("to", loc(to))
                .put("source", rec != null ? rec.source() : switch (cause) { case "PLUGIN" -> "Plugin (not identifiable)"; case "COMMAND" -> "Unknown"; default -> Text.pretty(cause); })
                .put("sender", rec == null ? "Unknown" : rec.executor()).put("command", rec == null ? "Unknown" : rec.full())
                .put("executor", rec == null ? "Unknown" : rec.executor()).put("target", p.getName());
        plugin.logs().submit(c);
    }
}
