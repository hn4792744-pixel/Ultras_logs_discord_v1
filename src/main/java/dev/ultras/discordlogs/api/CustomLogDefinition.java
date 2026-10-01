package dev.ultras.discordlogs.api;

import dev.ultras.discordlogs.logs.LogCategory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describes a log owned by another plugin: id, category, permission, connection, template and enabled flag.
 * Server owners can still override connection/enabled/color in logs.yml and the template in messages.yml
 * (discord-messages.&lt;id&gt;).
 */
public final class CustomLogDefinition {
    private final String id;
    private LogCategory category = LogCategory.CUSTOM;
    private String permission = "";
    private String connection = "default";
    private String title = "";
    private String description = "";
    private String footer = "ULTRAS | Minecraft Logs";
    private String color = "#5865F2";
    private boolean enabled = true;
    private final Map<String, String> fields = new LinkedHashMap<>();

    public CustomLogDefinition(String id) { this.id = id; }

    public CustomLogDefinition category(LogCategory c) { category = c; return this; }
    public CustomLogDefinition permission(String p) { permission = p; return this; }
    public CustomLogDefinition connection(String c) { connection = c; return this; }
    public CustomLogDefinition title(String t) { title = t; return this; }
    public CustomLogDefinition description(String d) { description = d; return this; }
    public CustomLogDefinition footer(String f) { footer = f; return this; }
    public CustomLogDefinition color(String c) { color = c; return this; }
    public CustomLogDefinition enabled(boolean e) { enabled = e; return this; }
    /** Adds an embed field; the value may contain %placeholders% supplied in the event data. */
    public CustomLogDefinition field(String name, String value) { fields.put(name, value); return this; }

    public String id() { return id; }
    public LogCategory category() { return category; }
    public String permission() { return permission; }
    public String connection() { return connection; }
    public String title() { return title.isBlank() ? id : title; }
    public String description() { return description; }
    public String footer() { return footer; }
    public String color() { return color; }
    public boolean enabled() { return enabled; }
    public Map<String, String> fields() { return fields; }
}
