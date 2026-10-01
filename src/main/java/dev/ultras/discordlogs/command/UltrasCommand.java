package dev.ultras.discordlogs.command;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /uc_discord_logs [reload | &lt;log-name&gt;] - the only command of the plugin. */
public final class UltrasCommand implements CommandExecutor, TabCompleter {
    private final UltrasDiscordLogs plugin;

    public UltrasCommand(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    private static boolean has(CommandSender s, String perm) {
        return s.hasPermission(perm) || s.hasPermission("ultras.discordlogs.admin");
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(s instanceof Player p)) { plugin.messages().send(s, "system.players-only"); return true; }
            if (!has(s, "ultras.discordlogs.use")) { plugin.messages().send(s, "errors.no-permission"); return true; }
            plugin.gui().openMain(p);
            return true;
        }
        String a = args[0].toLowerCase(Locale.ROOT);
        if (a.equals("reload")) {
            if (!has(s, "ultras.discordlogs.reload")) { plugin.messages().send(s, "errors.no-permission"); return true; }
            plugin.reloadAndReport(s);
            return true;
        }
        if (!has(s, "ultras.discordlogs.test")) { plugin.messages().send(s, "errors.no-permission"); return true; }
        if (plugin.registry().get(a) == null) { plugin.messages().send(s, "test.unknown-log", "log", a); return true; }
        plugin.tests().run(s, a);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) return out;
        String pre = args[0].toLowerCase(Locale.ROOT);
        if (has(s, "ultras.discordlogs.reload") && "reload".startsWith(pre)) out.add("reload");
        if (has(s, "ultras.discordlogs.test"))
            for (String id : plugin.registry().ids()) if (id.startsWith(pre)) out.add(id);
        return out;
    }
}
