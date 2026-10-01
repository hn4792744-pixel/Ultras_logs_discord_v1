package dev.ultras.discordlogs.listener;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.service.CommandTracker;
import dev.ultras.discordlogs.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.List;
import java.util.Locale;

/**
 * Feeds the CommandTracker / ConfirmationService and writes the command log.
 * The result is only "Failure" when we can prove it (cancelled or no permission); otherwise it is "Unknown".
 */
public final class CommandListener implements Listener {
    private final UltrasDiscordLogs plugin;

    public CommandListener(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String raw = e.getMessage();
        if (raw.length() < 2) return;
        if (!e.isCancelled()) {
            CommandTracker.Rec rec = plugin.commands().record(p, raw);
            plugin.confirmations().onCommand(rec);
        }
        plugin.counters().inc("commands");
        plugin.stats().activity(p, 1);
        if (!plugin.logs().isEnabled("commands")) return;

        String label = CommandTracker.normalize(raw.substring(1).split("\\s+")[0]);
        ConfigurationSection cfg = plugin.config().logSettings("commands").settings();
        List<String> black = cfg.getStringList("blacklist").stream().map(s -> s.toLowerCase(Locale.ROOT).replace("/", "")).toList();
        List<String> white = cfg.getStringList("whitelist").stream().map(s -> s.toLowerCase(Locale.ROOT).replace("/", "")).toList();
        if (black.contains(label)) return;
        if (!white.isEmpty() && !white.contains(label)) return;

        Command cmd = Bukkit.getCommandMap().getCommand(label);
        String result = "Unknown";
        if (e.isCancelled()) result = "Failure (cancelled by a plugin)";
        else if (cmd != null && !cmd.testPermissionSilent(p)) result = "Failure (no permission)";
        String owner = "Unknown";
        if (cmd instanceof PluginIdentifiableCommand pic) owner = pic.getPlugin().getName();
        else if (cmd != null && cmd.getClass().getSimpleName().contains("Vanilla")) owner = "Minecraft";
        String[] parts = raw.split("\\s+");
        String target = "Unknown";
        for (int i = 1; i < parts.length; i++) {
            Player t = Bukkit.getPlayerExact(parts[i]);
            if (t != null) { target = t.getName(); break; }
        }
        PlayerSnapshot snap = PlayerSnapshot.capture(plugin, p);
        plugin.logs().submit(LogContext.of("commands").player(snap).put("command", Text.truncate(Text.sanitizeUserText(raw), 900))
                .put("label", label).put("target", target).put("result", result).put("plugin", owner).put("executor", p.getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent e) {
        CommandTracker.Rec rec = plugin.commands().record(e.getSender(), e.getCommand());
        plugin.confirmations().onCommand(rec);
    }
}
