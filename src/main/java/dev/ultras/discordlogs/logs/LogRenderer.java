package dev.ultras.discordlogs.logs;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.config.LogSettings;
import dev.ultras.discordlogs.discord.embed.EmbedData;
import dev.ultras.discordlogs.discord.embed.EmbedField;
import dev.ultras.discordlogs.message.MessageService;
import dev.ultras.discordlogs.placeholder.PlaceholderEngine;
import dev.ultras.discordlogs.util.Colors;
import dev.ultras.discordlogs.util.Text;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;

/** Turns a template (messages.yml) plus placeholder values into an embed. Runs off the main thread. */
public final class LogRenderer {
    public record Rendered(String content, EmbedData embed) {}

    private final UltrasDiscordLogs plugin;

    public LogRenderer(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    public Rendered render(LogDefinition def, LogSettings ls, Map<String, String> vars, boolean test, long now) {
        MessageService m = plugin.messages();
        String key = ls.template();
        ConfigurationSection tpl = m.template(key);
        if (tpl == null) { key = def.id(); tpl = m.template(key); }
        EmbedData e = new EmbedData();
        String content = "";
        var custom = tpl == null ? plugin.api().customTemplate(def.id()) : null;
        if (custom != null) {
            e.title = apply(custom.title(), vars);
            e.description = apply(custom.description(), vars);
            e.footerText = apply(custom.footer(), vars);
            e.thumbnail = applyUrl("%avatar%", vars);
            e.timestamp = plugin.clock().isoTimestamp(now);
            custom.fields().forEach((n, val) -> {
                PlaceholderEngine.Result v = PlaceholderEngine.apply(val, vars);
                if (!(v.resolved() == 0 && v.missing() > 0) && !v.text().isBlank()) e.fields.add(new EmbedField(n, v.text(), true));
            });
        } else if (tpl == null) {
            e.title = def.id();
            vars.forEach((k, v) -> e.fields.add(new EmbedField(k, v, true)));
        } else {
            e.title = apply(m.discordString(key, "title", def.id()), vars);
            e.description = apply(m.discordString(key, "description", ""), vars);
            content = apply(m.discordString(key, "content", ""), vars);
            e.footerText = apply(m.discordString(key, "footer", "ULTRAS | Minecraft Logs"), vars);
            e.footerIcon = apply(m.discordString(key, "footer-icon", ""), vars);
            e.authorName = apply(m.discordString(key, "author-name", ""), vars);
            e.authorIcon = apply(m.discordString(key, "author-icon", ""), vars);
            e.thumbnail = applyUrl(m.discordString(key, "thumbnail", ""), vars);
            e.image = applyUrl(m.discordString(key, "image", ""), vars);
            e.url = apply(m.discordString(key, "url", ""), vars);
            ConfigurationSection fields = tpl.getConfigurationSection("fields");
            if (fields != null) addFields(fields, e, vars, 0);
            if (!tpl.contains("timestamp") ? plugin.config().messages().getBoolean("discord-messages._defaults.timestamp", true) : tpl.getBoolean("timestamp", true))
                e.timestamp = plugin.clock().isoTimestamp(now);
        }
        String color = !ls.color().isBlank() ? ls.color() : m.discordString(key, "color", def.defaultColor());
        e.color = Colors.parseOr(color, Colors.parseOr(def.defaultColor(), 0x5865F2));
        if (test) {
            String mark = plugin.messages().plain(plugin.settings().language, "discord.test-footer", Map.of());
            e.footerText = e.footerText.isBlank() ? mark : e.footerText + " | " + mark;
        }
        return new Rendered(content, e);
    }

    private void addFields(ConfigurationSection fields, EmbedData e, Map<String, String> vars, int depth) {
        MessageService m = plugin.messages();
        for (String fk : fields.getKeys(false)) {
            ConfigurationSection f = fields.getConfigurationSection(fk);
            if (f == null || !f.getBoolean("enabled", true)) continue;
            String bundle = f.getString("bundle", "");
            if (!bundle.isBlank()) {
                ConfigurationSection b = m.bundle(bundle);
                if (b != null && depth < 2) addFields(b, e, vars, depth + 1);
                continue;
            }
            PlaceholderEngine.Result v = PlaceholderEngine.apply(f.getString("value", ""), vars);
            // skip fields whose data is entirely unavailable, so embeds only show what is known
            if (v.resolved() == 0 && v.missing() > 0) continue;
            if (v.text().isBlank()) continue;
            String name = PlaceholderEngine.apply(m.fieldName(fk, f.getString("name", fk)), vars).text();
            e.fields.add(new EmbedField(name, v.text(), f.getBoolean("inline", true)));
        }
    }

    private static String apply(String t, Map<String, String> vars) {
        return PlaceholderEngine.apply(t, vars).text();
    }

    private static String applyUrl(String t, Map<String, String> vars) {
        PlaceholderEngine.Result r = PlaceholderEngine.apply(t, vars);
        return r.missing() > 0 ? "" : r.text();
    }

    public static String sanitizeValue(String s) { return Text.sanitizeUserText(s); }
}
