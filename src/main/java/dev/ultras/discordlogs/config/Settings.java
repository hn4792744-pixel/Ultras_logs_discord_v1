package dev.ultras.discordlogs.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.util.Locale;

/** Immutable snapshot of config.yml (+ GUI overrides). A new one is built on every reload. */
public final class Settings {
    public final String prefix, language, defaultConnection, dateFormat, timeFormat, avatarUrl;
    public final boolean bold, smallCaps, debug, asyncLogging, saveLocalLogs, guiEnabled, soundsEnabled, logIps,
            fallbackToDefault, startupValidation, avatarEnabled, vanishAssumeNo, hookVault, hookLuckPerms;
    public final int testCooldownSeconds, queueCapacity, maxRetries, shutdownFlushSeconds;
    public final long baseBackoffMs, maxBackoffMs, requestTimeoutMs, minIntervalMs;
    public final ZoneId zone;
    public final boolean rotationEnabled, rotationCompress, rotationArchive, backupBeforeDelete;
    public final String rotationMode;
    public final int rotationMaxFiles, rotationMaxSizeMb;

    public Settings(FileConfiguration c, RuntimeOverrides o) {
        prefix = c.getString("plugin.prefix", "&c&lULTRAS &8|");
        String lang = c.getString("plugin.language", "en").toLowerCase(Locale.ROOT);
        language = lang.equals("ar") ? "ar" : "en";
        bold = c.getBoolean("plugin.bold", true);
        smallCaps = c.getBoolean("plugin.small-caps", true);
        debug = c.getBoolean("settings.debug", false);
        asyncLogging = c.getBoolean("settings.async-logging", true);
        saveLocalLogs = o.bool("settings.save-local-logs", c.getBoolean("settings.save-local-logs", true));
        ZoneId z;
        String tz = c.getString("settings.timezone", "SYSTEM");
        try { z = "SYSTEM".equalsIgnoreCase(tz) ? ZoneId.systemDefault() : ZoneId.of(tz); } catch (Exception e) { z = ZoneId.systemDefault(); }
        zone = z;
        dateFormat = c.getString("settings.date-format", "yyyy-MM-dd");
        timeFormat = c.getString("settings.time-format", "HH:mm:ss");
        testCooldownSeconds = Math.max(0, c.getInt("test.cooldown-seconds", 10));
        guiEnabled = c.getBoolean("gui.enabled", true);
        soundsEnabled = o.bool("sounds.enabled", c.getBoolean("sounds.enabled", true));
        logIps = o.bool("security.log-ip-addresses", c.getBoolean("security.log-ip-addresses", true));
        defaultConnection = c.getString("discord.default-connection", "default");
        fallbackToDefault = c.getBoolean("discord.fallback-to-default-when-unconfigured", true);
        startupValidation = c.getBoolean("discord.startup-validation", true);
        avatarEnabled = c.getBoolean("discord.avatars.enabled", true);
        avatarUrl = c.getString("discord.avatars.url", "https://mc-heads.net/avatar/%uuid%/128");
        queueCapacity = Math.max(50, c.getInt("discord.queue.capacity", 2000));
        maxRetries = Math.max(0, c.getInt("discord.queue.max-retries", 5));
        baseBackoffMs = Math.max(100, c.getLong("discord.queue.base-backoff-ms", 1000));
        maxBackoffMs = Math.max(baseBackoffMs, c.getLong("discord.queue.max-backoff-ms", 30000));
        requestTimeoutMs = Math.max(2000, c.getLong("discord.queue.request-timeout-ms", 10000));
        minIntervalMs = Math.max(0, c.getLong("discord.queue.min-interval-ms", 500));
        shutdownFlushSeconds = Math.max(0, c.getInt("discord.queue.shutdown-flush-seconds", 5));
        rotationEnabled = c.getBoolean("storage.rotation.enabled", true);
        rotationMode = c.getString("storage.rotation.mode", "DAILY").toUpperCase(Locale.ROOT);
        rotationCompress = c.getBoolean("storage.rotation.compress-old", true);
        rotationArchive = c.getBoolean("storage.rotation.archive", true);
        rotationMaxFiles = Math.max(1, c.getInt("storage.rotation.max-files", 30));
        rotationMaxSizeMb = Math.max(0, c.getInt("storage.rotation.max-size-mb", 10));
        backupBeforeDelete = c.getBoolean("storage.backup-before-delete", true);
        vanishAssumeNo = c.getBoolean("hooks.vanish.assume-no-when-absent", false);
        hookVault = c.getBoolean("hooks.vault", true);
        hookLuckPerms = c.getBoolean("hooks.luckperms", true);
    }
}
