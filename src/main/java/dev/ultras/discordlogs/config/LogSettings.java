package dev.ultras.discordlogs.config;

import org.bukkit.configuration.ConfigurationSection;

/** Effective per-log settings (logs.yml + GUI overrides). */
public record LogSettings(String id, boolean enabled, String connection, String template, String permission,
                          String color, long dedupeMs, ConfigurationSection settings) {}
