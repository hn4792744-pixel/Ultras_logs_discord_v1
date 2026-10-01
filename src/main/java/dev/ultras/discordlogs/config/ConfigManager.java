package dev.ultras.discordlogs.config;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogDefinition;
import dev.ultras.discordlogs.util.Colors;
import dev.ultras.discordlogs.util.DurationParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Loads, validates and exposes every YAML file. A broken file never replaces a working configuration. */
public final class ConfigManager {
    public static final String[] FILES = {"config.yml", "discord.yml", "logs.yml", "messages.yml", "messages_en.yml", "messages_ar.yml"};
    private final UltrasDiscordLogs plugin;
    private final Map<String, FileConfiguration> files = new HashMap<>();
    private volatile Settings settings;
    private final RuntimeOverrides overrides;

    public ConfigManager(UltrasDiscordLogs plugin) {
        this.plugin = plugin;
        this.overrides = new RuntimeOverrides(plugin.getDataFolder());
    }

    public RuntimeOverrides overrides() { return overrides; }
    public Settings settings() { return settings; }
    public FileConfiguration config() { return files.get("config.yml"); }
    public FileConfiguration messages() { return files.get("messages.yml"); }

    public FileConfiguration lang(String code) {
        FileConfiguration f = files.get("messages_" + code + ".yml");
        return f != null ? f : files.get("messages_en.yml");
    }

    public ConfigurationSection discord() { return files.get("discord.yml").getConfigurationSection("discord"); }

    public String defaultConnectionId() {
        String d = files.get("discord.yml").getString("discord.default-connection");
        return d != null && !d.isBlank() ? d : settings.defaultConnection;
    }

    public ValidationReport load() {
        ValidationReport report = new ValidationReport();
        plugin.getDataFolder().mkdirs();
        Map<String, FileConfiguration> next = new HashMap<>();
        for (String name : FILES) {
            File f = new File(plugin.getDataFolder(), name);
            if (!f.exists()) plugin.saveResource(name, false);
            YamlConfiguration y = new YamlConfiguration();
            try {
                y.load(f);
            } catch (InvalidConfigurationException | java.io.IOException e) {
                report.error(name + " could not be parsed; keeping the previous version (" + e.getClass().getSimpleName() + ").");
                FileConfiguration prev = files.get(name);
                if (prev != null) { next.put(name, prev); continue; }
                y = new YamlConfiguration();
            }
            try (Reader r = new InputStreamReader(plugin.getResource(name), StandardCharsets.UTF_8)) {
                y.setDefaults(YamlConfiguration.loadConfiguration(r));
            } catch (Exception ignored) {
                // defaults are a convenience only
            }
            next.put(name, y);
        }
        files.clear();
        files.putAll(next);
        settings = new Settings(config(), overrides);
        plugin.log().setDebug(settings.debug);
        validate(report);
        return report;
    }

    public LogSettings logSettings(String id) {
        ConfigurationSection s = files.get("logs.yml").getConfigurationSection("logs." + id);
        LogDefinition def = plugin.registry().get(id);
        boolean defEnabled = def == null || !def.heavy();
        boolean enabled = s == null ? defEnabled : s.getBoolean("enabled", defEnabled);
        enabled = overrides.bool("logs." + id + ".enabled", enabled);
        String conn = s != null ? s.getString("connection", "") : "";
        if (conn == null || conn.isBlank()) conn = def != null ? def.defaultConnection() : settings.defaultConnection;
        String tpl = s != null ? s.getString("template", id) : id;
        String perm = s != null ? s.getString("permission", "") : "";
        if (perm == null || perm.isBlank()) perm = def != null && !def.permission().isBlank() ? def.permission() : "ultras.discordlogs.test";
        String color = s != null ? s.getString("color", "") : "";
        long dedupe = s != null ? s.getLong("dedupe-ms", 0) : 0;
        ConfigurationSection custom = s != null ? s.getConfigurationSection("settings") : null;
        if (custom == null) custom = new YamlConfiguration();
        return new LogSettings(id, enabled, conn, tpl == null || tpl.isBlank() ? id : tpl, perm, color, dedupe, custom);
    }

    private void validate(ValidationReport r) {
        FileConfiguration logs = files.get("logs.yml");
        ConfigurationSection conns = discord() == null ? null : discord().getConfigurationSection("connections");
        ConfigurationSection logsSec = logs.getConfigurationSection("logs");
        if (logsSec != null) {
            for (String id : logsSec.getKeys(false)) {
                ConfigurationSection s = logsSec.getConfigurationSection(id);
                if (s == null) continue;
                if (plugin.registry().get(id) == null) r.info("logs.yml contains '" + id + "' which is not a registered log (yet).");
                String conn = s.getString("connection", "");
                if (!conn.isBlank() && (conns == null || !conns.contains(conn)))
                    r.error("Log '" + id + "' references missing connection '" + conn + "'.");
                String col = s.getString("color", "");
                if (!col.isBlank() && Colors.parse(col) < 0) r.error("Log '" + id + "' has an invalid color.");
            }
        }
        ConfigurationSection tpl = messages().getConfigurationSection("discord-messages");
        if (tpl != null) {
            for (String id : tpl.getKeys(false)) {
                ConfigurationSection t = tpl.getConfigurationSection(id);
                if (t == null) continue;
                String col = t.getString("color", "");
                if (!col.isBlank() && Colors.parse(col) < 0) r.error("Template '" + id + "' has an invalid color.");
            }
        }
        for (String k : new String[]{"click", "success", "error"}) {
            String snd = config().getString("sounds." + k + ".sound", "");
            if (!snd.isBlank() && !plugin.sounds().isValid(snd)) r.warn("Invalid sound '" + snd + "' for sounds." + k + ".");
        }
        checkInterval(r, "logs.server-top.settings.interval", "1m");
        checkInterval(r, "logs.server-stats.settings.interval", "30m");
        String mode = settings.rotationMode;
        if (!mode.equals("DAILY") && !mode.equals("MONTHLY") && !mode.equals("NONE")) r.warn("storage.rotation.mode must be DAILY, MONTHLY or NONE.");
    }

    private void checkInterval(ValidationReport r, String path, String def) {
        String v = files.get("logs.yml").getString(path, def);
        long ms = DurationParser.parseMillis(v);
        if (ms < 10_000) r.error("Invalid or too short interval at " + path + " (minimum 10s, e.g. 1m).");
    }
}
