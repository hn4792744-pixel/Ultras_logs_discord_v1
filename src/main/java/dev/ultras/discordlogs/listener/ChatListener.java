package dev.ultras.discordlogs.listener;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Chat log with ignore patterns, masked words and blacklisted words (messages containing them are not logged). */
public final class ChatListener implements Listener {
    private final UltrasDiscordLogs plugin;
    private volatile List<Pattern> ignored = List.of();
    private volatile Pattern masked;
    private volatile List<String> blacklisted = List.of();
    private volatile int maxLength = 1000;

    public ChatListener(UltrasDiscordLogs plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        ConfigurationSection s = plugin.config().logSettings("chat").settings();
        List<Pattern> p = new ArrayList<>();
        for (String r : s.getStringList("ignored-patterns")) {
            try { p.add(Pattern.compile(r, Pattern.CASE_INSENSITIVE)); }
            catch (PatternSyntaxException e) { plugin.log().warn("Invalid chat ignored-pattern skipped."); }
        }
        ignored = p;
        List<String> words = s.getStringList("masked-words").stream().filter(w -> !w.isBlank()).map(Pattern::quote).toList();
        masked = words.isEmpty() ? null : Pattern.compile(String.join("|", words), Pattern.CASE_INSENSITIVE);
        blacklisted = s.getStringList("blacklisted-words").stream().filter(w -> !w.isBlank()).map(w -> w.toLowerCase(Locale.ROOT)).toList();
        maxLength = Math.max(20, Math.min(1800, s.getInt("max-length", 1000)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        plugin.counters().inc("chat_messages");
        if (!plugin.logs().isEnabled("chat")) return;
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
        for (Pattern ig : ignored) if (ig.matcher(msg).find()) return;
        String lower = msg.toLowerCase(Locale.ROOT);
        for (String b : blacklisted) if (lower.contains(b)) return;
        if (masked != null) msg = masked.matcher(msg).replaceAll("***");
        final String text = Text.truncate(Text.sanitizeUserText(msg), maxLength);
        plugin.runMain(() -> {
            plugin.stats().activity(p, 1);
            PlayerSnapshot snap = PlayerSnapshot.capture(plugin, p);
            plugin.logs().submit(LogContext.of("chat").player(snap).put("message", text));
        });
    }
}
