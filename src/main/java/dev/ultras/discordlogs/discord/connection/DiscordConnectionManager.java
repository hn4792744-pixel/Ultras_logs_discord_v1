package dev.ultras.discordlogs.discord.connection;

import dev.ultras.discordlogs.config.ValidationReport;
import dev.ultras.discordlogs.util.PluginLog;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Central owner of all connections. Connections are created once and reused by every log that references them. */
public final class DiscordConnectionManager {
    private final PluginLog log;
    private volatile DiscordHttp http;
    private volatile Map<String, DiscordConnection> connections = Map.of();
    private volatile String defaultId = "default";
    private final ExecutorService validator = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ULTRAS-Discord-Validate");
        t.setDaemon(true);
        return t;
    });

    public DiscordConnectionManager(PluginLog log, long timeoutMs) {
        this.log = log;
        this.http = new DiscordHttp(timeoutMs);
    }

    /** Reconciles with discord.yml: unchanged connections are kept, changed/new ones are rebuilt, removed ones dropped. */
    public void load(ConfigurationSection section, String defaultConnection, ValidationReport report) {
        Map<String, DiscordConnection> next = new LinkedHashMap<>();
        Map<String, DiscordConnection> old = connections;
        List<String> unconfigured = new ArrayList<>();
        Set<String> seenLower = new java.util.HashSet<>();
        ConfigurationSection conns = section == null ? null : section.getConfigurationSection("connections");
        if (conns != null) {
            for (String id : conns.getKeys(false)) {
                ConfigurationSection s = conns.getConfigurationSection(id);
                if (s == null) { report.error("Connection '" + id + "' is not a valid section."); continue; }
                if (!seenLower.add(id.toLowerCase(Locale.ROOT))) { report.error("Duplicate connection id '" + id + "' (ids are case-insensitive)."); continue; }
                ConnectionType type;
                try {
                    type = ConnectionType.valueOf(s.getString("type", "WEBHOOK").trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    report.error("Connection '" + id + "' has an invalid type (use WEBHOOK or BOT).");
                    continue;
                }
                ConnectionSettings cs = ConnectionSettings.parse(id, s, type);
                if (cs.enabled() && !cs.configured()) unconfigured.add(id);
                else if (cs.enabled() && !cs.formatValid()) report.error("Connection '" + id + "' is invalid: check "
                        + (type == ConnectionType.WEBHOOK ? "webhook-url" : "token / channel-id") + " format.");
                if (type == ConnectionType.WEBHOOK && s.contains("token")) report.warn("Connection '" + id + "' is a WEBHOOK; its 'token' key is ignored.");
                if (type == ConnectionType.BOT && s.contains("webhook-url")) report.warn("Connection '" + id + "' is a BOT; its 'webhook-url' key is ignored.");
                String u = cs.username().toLowerCase(Locale.ROOT);
                if (type == ConnectionType.WEBHOOK && (u.contains("discord") || u.contains("clyde")))
                    report.warn("Connection '" + id + "': Discord rejects webhook usernames containing 'discord' or 'clyde'; the username will be ignored.");
                DiscordConnection prev = old.get(id);
                if (prev != null && prev.settings().equals(cs)) next.put(id, prev);
                else next.put(id, type == ConnectionType.WEBHOOK ? new WebhookConnection(cs, http) : new BotConnection(cs, http));
            }
        }
        if (!unconfigured.isEmpty())
            report.info(unconfigured.size() + " connection(s) are not configured yet: " + String.join(", ", unconfigured));
        if (!next.containsKey(defaultConnection)) report.error("Default connection '" + defaultConnection + "' does not exist in discord.yml.");
        this.defaultId = defaultConnection;
        this.connections = Map.copyOf(next);
    }

    public DiscordConnection get(String id) { return id == null ? null : connections.get(id); }
    public DiscordConnection defaultConnection() { return connections.get(defaultId); }
    public String defaultId() { return defaultId; }
    public Collection<DiscordConnection> all() { return connections.values(); }
    public boolean hasBotConnections() { return connections.values().stream().anyMatch(c -> c.type() == ConnectionType.BOT && c.settings().enabled()); }

    public Set<String> laneKeys() {
        return connections.values().stream().map(c -> c.settings().laneKey()).collect(Collectors.toSet());
    }

    /** Asynchronous, read-only validation (GET) of every enabled connection. One failing never affects others. */
    public void validateAllAsync() {
        for (DiscordConnection c : connections.values()) {
            if (!c.settings().enabled() || !c.settings().formatValid()) continue;
            validator.execute(() -> {
                try {
                    SendResult r = c.validateRemote();
                    if (!r.success() && r.kind() == SendResult.Kind.PERMANENT)
                        log.warn("Connection '" + c.id() + "' failed validation (" + r.detail() + ").");
                } catch (Throwable t) {
                    log.warn("Validation of connection '" + c.id() + "' failed", t);
                }
            });
        }
    }

    public CompletableFuture<SendResult> validateAsync(DiscordConnection c) {
        return CompletableFuture.supplyAsync(c::validateRemote, validator);
    }

    public void shutdown() {
        validator.shutdownNow();
        http.close();
    }
}
