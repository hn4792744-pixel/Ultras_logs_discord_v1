package dev.ultras.discordlogs.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Persistent named counters (keys must not contain dots). */
public final class CounterStore {
    private final ConcurrentHashMap<String, AtomicLong> map = new ConcurrentHashMap<>();

    public void load(Map<String, Long> data) {
        data.forEach((k, v) -> map.put(k, new AtomicLong(v)));
    }

    public long inc(String key) { return map.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet(); }
    public long get(String key) { AtomicLong a = map.get(key); return a == null ? 0 : a.get(); }
    public void reset(String key) { map.remove(key); }

    public Map<String, Long> snapshot() {
        Map<String, Long> m = new HashMap<>();
        map.forEach((k, v) -> m.put(k, v.get()));
        return m;
    }
}
