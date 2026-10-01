package dev.ultras.discordlogs.discord.connection;

import org.bukkit.configuration.ConfigurationSection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Immutable connection definition. toString never prints secrets. */
public record ConnectionSettings(String id, ConnectionType type, boolean enabled, String webhookUrl, String token,
                                 String channelId, String username, String avatarUrl, String threadId,
                                 String threadName, boolean allowUsers, boolean allowRoles, boolean allowEveryone) {

    public static final Pattern WEBHOOK = Pattern.compile(
            "^https://(?:(?:canary|ptb)\\.)?discord(?:app)?\\.com/api(?:/v\\d+)?/webhooks/\\d{15,25}/[A-Za-z0-9._-]{20,}$");
    private static final Pattern CHANNEL = Pattern.compile("^\\d{15,25}$");

    public static ConnectionSettings parse(String id, ConfigurationSection s, ConnectionType type) {
        ConfigurationSection am = s.getConfigurationSection("allowed-mentions");
        return new ConnectionSettings(id, type, s.getBoolean("enabled", true),
                s.getString("webhook-url", ""), s.getString("token", ""), String.valueOf(s.get("channel-id", "")).trim(),
                s.getString("username", ""), s.getString("avatar-url", ""),
                String.valueOf(s.get("thread-id", "")).trim(), s.getString("thread-name", ""),
                am != null && am.getBoolean("users", false), am != null && am.getBoolean("roles", false),
                am != null && am.getBoolean("everyone", false));
    }

    private static boolean placeholder(String v) {
        if (v == null || v.isBlank()) return true;
        String u = v.toUpperCase(Locale.ROOT);
        return u.contains("_HERE") || u.contains("WEBHOOK_URL") || u.contains("BOT_TOKEN") || u.contains("CHANNEL_ID")
                || u.contains("PASTE_");
    }

    /** true when the admin actually filled in the secret (not empty / not a template placeholder). */
    public boolean configured() {
        return type == ConnectionType.WEBHOOK ? !placeholder(webhookUrl) : (!placeholder(token) && !placeholder(channelId));
    }

    public boolean formatValid() {
        if (!configured()) return false;
        if (type == ConnectionType.WEBHOOK) return WEBHOOK.matcher(webhookUrl.trim()).matches();
        return token.trim().split("\\.").length == 3 && token.trim().length() >= 50 && CHANNEL.matcher(channelId).matches();
    }

    public String secret() {
        return type == ConnectionType.WEBHOOK ? webhookUrl.trim() : token.trim();
    }

    /** Connections sharing the same destination share one queue (and therefore one rate limiter). */
    public String laneKey() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String src = type + "|" + secret() + "|" + (type == ConnectionType.BOT ? channelId : threadId);
            return HexFormat.of().formatHex(md.digest(src.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            return id;
        }
    }

    @Override
    public String toString() {
        return "ConnectionSettings[id=" + id + ", type=" + type + ", enabled=" + enabled + "]";
    }
}
