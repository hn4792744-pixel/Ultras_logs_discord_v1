package dev.ultras.discordlogs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public record BotConfig(boolean enabled, String token, Map<String, String> channels) {

    private static final Pattern CHANNEL_ID = Pattern.compile("^\\d{15,25}$");

    public static BotConfig load(FileConfiguration config) {
        if (config == null) {
            return new BotConfig(false, "", Map.of());
        }

        ConfigurationSection bot = config.getConfigurationSection("bot");
        if (bot == null) {
            return new BotConfig(false, "", Map.of());
        }

        boolean enabled = bot.getBoolean("enabled", false);
        String token = bot.getString("token", "").trim();

        Map<String, String> channels = new LinkedHashMap<>();
        ConfigurationSection section = bot.getConfigurationSection("channels");

        if (section != null) {
            for (String logId : section.getKeys(false)) {
                String channelId = section.getString(logId, "").trim();
                if (!channelId.isBlank()) {
                    channels.put(logId, channelId);
                }
            }
        }

        return new BotConfig(enabled, token, Map.copyOf(channels));
    }

    public boolean configured() {
        return !token.isBlank()
                && !token.equalsIgnoreCase("BOT_TOKEN_HERE")
                && token.length() >= 50
                && token.split("\\.").length == 3;
    }

    public boolean channelValid(String logId) {
        String channelId = channels.get(logId);
        return channelId != null && CHANNEL_ID.matcher(channelId).matches();
    }

    public String channelId(String logId) {
        return channels.getOrDefault(logId, "");
    }

    public boolean hasChannel(String logId) {
        return channelValid(logId);
    }
}
