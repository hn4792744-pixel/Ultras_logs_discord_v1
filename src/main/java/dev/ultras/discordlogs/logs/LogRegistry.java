package dev.ultras.discordlogs.logs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Single source of truth for log types. GUI, tab completion, test system and permissions all read from here. */
public final class LogRegistry {
    private final Map<String, LogDefinition> map = new ConcurrentHashMap<>();
    private final List<String> order = new CopyOnWriteArrayList<>();

    public synchronized boolean register(LogDefinition def) {
        if (map.putIfAbsent(def.id(), def) != null) return false;
        order.add(def.id());
        return true;
    }

    public synchronized boolean unregister(String id) {
        order.remove(id);
        return map.remove(id) != null;
    }

    public LogDefinition get(String id) { return id == null ? null : map.get(id); }

    public List<LogDefinition> all() {
        List<LogDefinition> out = new ArrayList<>();
        for (String id : order) { LogDefinition d = map.get(id); if (d != null) out.add(d); }
        return out;
    }

    public List<LogDefinition> byCategory(LogCategory c) {
        return all().stream().filter(d -> d.category() == c).toList();
    }

    public List<String> ids() { return new ArrayList<>(order); }

    public Set<String> fileKeys(LogCategory c) {
        Set<String> keys = new LinkedHashSet<>();
        for (LogDefinition d : byCategory(c)) keys.add(d.fileKey());
        return keys;
    }

    public Set<String> allFileKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (LogDefinition d : all()) keys.add(d.fileKey());
        return keys;
    }
}
