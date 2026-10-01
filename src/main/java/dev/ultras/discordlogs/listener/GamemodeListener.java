package dev.ultras.discordlogs.listener;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.service.CommandTracker;
import dev.ultras.discordlogs.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

import java.util.List;
import java.util.Set;

public final class GamemodeListener implements Listener {
    private static final Set<String> LABELS = Set.of("gamemode", "gm", "gmc", "gms", "gma", "gmsp", "egamemode", "egm", "egmc", "egms", "egma", "egmsp");
    private final UltrasDiscordLogs plugin;

    public GamemodeListener(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChange(PlayerGameModeChangeEvent e) {
        if (!plugin.logs().isEnabled("gamemode")) return;
        String cause = e.getCause().name();
        List<String> ignored = plugin.config().logSettings("gamemode").settings().getStringList("ignored-causes");
        if (ignored.contains(cause)) return;
        Player p = e.getPlayer();
        CommandTracker.Rec rec = "COMMAND".equals(cause) ? plugin.commands().find(LABELS, p.getName(), p.getUniqueId(), 2000) : null;
        String source = rec != null ? rec.source() : switch (cause) {
            case "PLUGIN" -> "Plugin (not identifiable)";
            case "DEFAULT_GAMEMODE" -> "Default gamemode (server setting)";
            case "COMMAND", "UNKNOWN" -> "Unknown";
            default -> Text.pretty(cause);
        };
        PlayerSnapshot snap = PlayerSnapshot.capture(plugin, p);
        String newGm = Text.pretty(e.getNewGameMode().name());
        plugin.logs().submit(LogContext.of("gamemode").player(snap).put("gamemode", newGm)
                .put("old_gamemode", snap.gamemode()).put("new_gamemode", newGm)
                .put("executor", rec == null ? "Unknown" : rec.executor()).put("target", p.getName())
                .put("command", rec == null ? "Unknown" : rec.full()).put("source", source)
                .dedupe(snap.uuid() + "|" + newGm));
    }
}
