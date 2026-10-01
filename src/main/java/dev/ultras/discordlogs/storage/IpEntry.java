package dev.ultras.discordlogs.storage;

public record IpEntry(long firstSeen, long lastSeen, int count) {}
