package dev.ultras.discordlogs.logs;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable description of one log type. Everything configurable lives in YAML; this is only the registry entry. */
public final class LogDefinition {
    private final String id, fileKey, defaultConnection, defaultColor, description, owner, permission;
    private final LogCategory category;
    private final Material icon;
    private final boolean heavy;
    private final Map<String, String> sample;

    private LogDefinition(Builder b) {
        id = b.id; category = b.category; fileKey = b.fileKey; icon = b.icon; defaultConnection = b.connection;
        defaultColor = b.color; description = b.description; heavy = b.heavy; owner = b.owner; permission = b.permission;
        sample = Map.copyOf(b.sample);
    }

    public String id() { return id; }
    public LogCategory category() { return category; }
    public String fileKey() { return fileKey; }
    public Material icon() { return icon; }
    public String defaultConnection() { return defaultConnection; }
    public String defaultColor() { return defaultColor; }
    public String description() { return description; }
    public boolean heavy() { return heavy; }
    public String owner() { return owner; }
    public String permission() { return permission; }
    public Map<String, String> sample() { return sample; }

    public static Builder builder(String id, LogCategory category) { return new Builder(id, category); }

    public static final class Builder {
        private final String id;
        private final LogCategory category;
        private String fileKey = "custom", connection = "default", color = "#5865F2", description = "", owner = "ULTRAS", permission = "";
        private Material icon = Material.PAPER;
        private boolean heavy;
        private final Map<String, String> sample = new LinkedHashMap<>();

        private Builder(String id, LogCategory category) { this.id = id; this.category = category; }

        public Builder file(String v) { fileKey = v; return this; }
        public Builder icon(Material m) { icon = m; return this; }
        public Builder connection(String c) { connection = c; return this; }
        public Builder color(String c) { color = c; return this; }
        public Builder description(String d) { description = d; return this; }
        public Builder heavy(boolean h) { heavy = h; return this; }
        public Builder owner(String o) { owner = o; return this; }
        public Builder permission(String p) { permission = p == null ? "" : p; return this; }
        public Builder sample(String k, String v) { sample.put(k, v); return this; }
        public LogDefinition build() { return new LogDefinition(this); }
    }
}
