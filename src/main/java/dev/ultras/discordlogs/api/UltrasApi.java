package dev.ultras.discordlogs.api;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.discord.connection.DiscordConnectionManager;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.LogDefinition;
import dev.ultras.discordlogs.logs.LogRegistry;
import dev.ultras.discordlogs.logs.LogService;
import dev.ultras.discordlogs.logs.SubmitResult;
import dev.ultras.discordlogs.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Developer API. Obtain it with {@code UltrasApi.get()} (or Bukkit's ServicesManager).
 * Custom logs: register a {@link CustomLogDefinition}, then call {@link #log(String, Map)} or fire
 * {@link dev.ultras.discordlogs.api.event.UltrasCustomLogEvent}. Everything flows through the same queue/webhook engine.
 */
public final class UltrasApi {
    private final UltrasDiscordLogs plugin;
    private final Map<String, CustomLogDefinition> customs = new ConcurrentHashMap<>();

    public UltrasApi(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    /** @return the API, or null when ULTRAS is not running. */
    public static UltrasApi get() {
        var reg = Bukkit.getServicesManager().getRegistration(UltrasApi.class);
        return reg == null ? null : reg.getProvider();
    }

    public LogRegistry registry() { return plugin.registry(); }
    public LogService logService() { return plugin.logs(); }
    public DiscordConnectionManager connections() { return plugin.connections(); }
    public StorageManager storage() { return plugin.storage(); }

    public boolean registerCustomLog(Plugin owner, CustomLogDefinition d) {
        LogDefinition def = LogDefinition.builder(d.id(), d.category()).file("custom").connection(d.connection())
                .color(d.color()).permission(d.permission()).heavy(!d.enabled()).owner(owner.getName())
                .description("Custom log by " + owner.getName()).build();
        if (!plugin.registry().register(def)) return false;
        customs.put(d.id(), d);
        return true;
    }

    public boolean unregisterCustomLog(String id) {
        customs.remove(id);
        return plugin.registry().unregister(id);
    }

    public CustomLogDefinition customTemplate(String id) { return customs.get(id); }

    /** Thread-safe. Data values become %placeholders%. */
    public SubmitResult log(String logId, Map<String, String> data) {
        return plugin.logs().submit(LogContext.of(logId).putAll(data));
    }

    public SubmitResult log(LogContext context) { return plugin.logs().submit(context); }
}
