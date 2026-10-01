package dev.ultras.discordlogs.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerData {
    public final UUID uuid;
    public volatile String name;
    public volatile long firstJoin;
    public volatile long lastJoin;
    public volatile long lastQuit;
    public volatile String language;
    public volatile String firstIp;
    public volatile String lastIp;
    public volatile long sessionStart;
    public volatile Map<String, Long> stats = Map.of();
    public volatile long statsUpdated;
    /** ip -> entry, insertion ordered (oldest first). Guard with synchronized(ips). */
    public final Map<String, IpEntry> ips = new LinkedHashMap<>();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}
