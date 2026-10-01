package dev.ultras.discordlogs.service;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Configurable UI sounds. Accepts enum-style names (UI_BUTTON_CLICK) and namespaced keys (ui.button.click). */
public final class SoundService {
    private final UltrasDiscordLogs plugin;
    private volatile Map<String, Sound> index;

    public SoundService(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    private Map<String, Sound> index() {
        Map<String, Sound> i = index;
        if (i != null) return i;
        Map<String, Sound> m = new HashMap<>();
        for (Sound s : Registry.SOUNDS) {
            NamespacedKey k = Registry.SOUNDS.getKey(s);
            if (k == null) continue;
            m.put(k.getKey().toUpperCase(Locale.ROOT).replace('.', '_'), s);
            m.put(k.getKey().toUpperCase(Locale.ROOT), s);
        }
        index = m;
        return m;
    }

    private Sound resolve(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        if (n.startsWith("minecraft:")) n = n.substring("minecraft:".length());
        return index().get(n.toUpperCase(Locale.ROOT).replace('.', '_'));
    }

    public boolean isValid(String name) { return resolve(name) != null; }

    public void play(Player p, String key) {
        if (!plugin.settings().soundsEnabled) return;
        var c = plugin.config().config();
        String path = "sounds." + key;
        Sound s = resolve(c.getString(path + ".sound", ""));
        if (s == null) return;
        p.playSound(p.getLocation(), s, (float) c.getDouble(path + ".volume", 0.5), (float) c.getDouble(path + ".pitch", 1.0));
    }
}
