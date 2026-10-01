package dev.ultras.discordlogs.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory cache of player data with an IP -> accounts index. Persistence goes through the StorageBackend. */
public final class PlayerDataStore {
    public static final int MAX_IPS = 20;
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> ipIndex = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public void load(Map<UUID, PlayerData> loaded) {
        players.putAll(loaded);
        for (PlayerData d : loaded.values()) synchronized (d.ips) { d.ips.keySet().forEach(ip -> index(ip, d.uuid)); }
    }

    public PlayerData get(UUID id) { return players.get(id); }

    public PlayerData getOrCreate(UUID id, String name) {
        return players.computeIfAbsent(id, k -> { dirty = true; return new PlayerData(k, name); });
    }

    public PlayerData byName(String name) {
        if (name == null) return null;
        for (PlayerData d : players.values()) if (d.name != null && d.name.equalsIgnoreCase(name)) return d;
        return null;
    }

    public int size() { return players.size(); }
    public Collection<PlayerData> all() { return players.values(); }
    public void markDirty() { dirty = true; }
    public boolean consumeDirty() { boolean d = dirty; dirty = false; return d; }

    private void index(String ip, UUID id) {
        ipIndex.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(id);
    }

    /** Records an IP for the account (history is capped; oldest entries are dropped). */
    public void recordIp(PlayerData d, String ip, long now) {
        synchronized (d.ips) {
            IpEntry e = d.ips.remove(ip);
            d.ips.put(ip, e == null ? new IpEntry(now, now, 1) : new IpEntry(e.firstSeen(), now, e.count() + 1));
            while (d.ips.size() > MAX_IPS) {
                String oldest = d.ips.keySet().iterator().next();
                d.ips.remove(oldest);
                Set<UUID> s = ipIndex.get(oldest);
                if (s != null) s.remove(d.uuid);
            }
        }
        if (d.firstIp == null) d.firstIp = ip;
        index(ip, d.uuid);
        dirty = true;
    }

    public boolean hasSeenIp(PlayerData d, String ip) {
        synchronized (d.ips) { return d.ips.containsKey(ip); }
    }

    /** Other accounts (by name) that used this IP, excluding the given account. */
    public List<String> otherAccounts(String ip, UUID exclude) {
        List<String> out = new ArrayList<>();
        Set<UUID> ids = ipIndex.get(ip);
        if (ids == null) return out;
        for (UUID id : ids) {
            if (id.equals(exclude)) continue;
            PlayerData d = players.get(id);
            out.add(d != null && d.name != null ? d.name : id.toString());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public String language(UUID id) {
        PlayerData d = players.get(id);
        return d == null ? null : d.language;
    }

    public void setLanguage(UUID id, String name, String lang) {
        getOrCreate(id, name).language = lang.toLowerCase(Locale.ROOT);
        dirty = true;
    }
}
