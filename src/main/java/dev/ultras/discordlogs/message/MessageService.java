package dev.ultras.discordlogs.message;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.config.Settings;
import dev.ultras.discordlogs.placeholder.PlaceholderEngine;
import dev.ultras.discordlogs.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/** Every user-visible string comes from YAML; nothing is hardcoded in Java. */
public final class MessageService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private final UltrasDiscordLogs plugin;

    public MessageService(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    public static Map<String, String> vars(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) if (kv[i + 1] != null) m.put(kv[i], kv[i + 1]);
        return m;
    }

    public static Component legacy(String s) { return LEGACY.deserialize(s == null ? "" : s.replace('§', '&')); }

    /** Language of a sender: player preference, otherwise the server default. */
    public String lang(CommandSender s) {
        if (s instanceof Player p) {
            String l = plugin.players().language(p.getUniqueId());
            if (l != null) return l;
        }
        return plugin.settings().language;
    }

    public String raw(String lang, String key) {
        FileConfiguration f = plugin.config().lang(lang);
        String v = f.getString(key);
        if (v == null) v = plugin.config().lang("en").getString(key);
        return v;
    }

    public String text(String lang, String key, Map<String, String> vars) {
        String v = raw(lang, key);
        if (v == null) return key;
        if ("en".equals(lang) && plugin.settings().smallCaps) v = Text.smallCaps(v);
        return PlaceholderEngine.applyLoose(v, vars);
    }

    public String plain(String lang, String key, Map<String, String> vars) {
        String v = raw(lang, key);
        return v == null ? key : PlaceholderEngine.applyLoose(v, vars);
    }

    public Component gui(String lang, String key, Map<String, String> vars) {
        return legacy(text(lang, key, vars)).decoration(TextDecoration.ITALIC, false);
    }

    public Component guiPlain(String lang, String key, Map<String, String> vars) {
        return legacy(plain(lang, key, vars)).decoration(TextDecoration.ITALIC, false);
    }

    /** Prefixed, bold (configurable) chat message. */
    public Component message(String lang, String key, Map<String, String> vars) {
        Settings s = plugin.settings();
        String body = text(lang, key, vars);
        String full = s.prefix + " " + body;
        if (s.bold) full = Text.bold(full);
        return legacy(full);
    }

    public void send(CommandSender to, String key, String... kv) {
        to.sendMessage(message(lang(to), key, vars(kv)));
    }

    // ---- Discord templates: language overrides -> messages.yml -> _defaults --------------------------------------

    public ConfigurationSection template(String key) {
        return plugin.config().messages().getConfigurationSection("discord-messages." + key);
    }

    public ConfigurationSection bundle(String name) {
        return plugin.config().messages().getConfigurationSection("discord-messages._bundles." + name);
    }

    public String discordString(String templateKey, String path, String def) {
        String lang = plugin.settings().language;
        String v = plugin.config().lang(lang).getString("discord." + templateKey + "." + path);
        if (v != null && !v.isEmpty()) return v;
        FileConfiguration m = plugin.config().messages();
        v = m.getString("discord-messages." + templateKey + "." + path);
        if (v != null) return v;
        v = m.getString("discord-messages._defaults." + path);
        return v != null ? v : def;
    }

    public String fieldName(String fieldKey, String def) {
        String v = plugin.config().lang(plugin.settings().language).getString("discord-field-names." + fieldKey);
        return v != null && !v.isEmpty() ? v : def;
    }
}
