package dev.ultras.discordlogs.logs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Data for one log event. Values are plain strings captured on the thread that created the context. */
public final class LogContext {
    private final String logId;
    private final Map<String, String> vars = new LinkedHashMap<>();
    private String dedupeKey;
    private boolean test;

    private LogContext(String logId) { this.logId = logId; }

    public static LogContext of(String logId) { return new LogContext(logId); }

    /** Null and empty values are skipped, so the renderer treats them as "not available". */
    public LogContext put(String key, Object value) {
        if (value != null) {
            String s = String.valueOf(value);
            if (!s.isEmpty()) vars.put(key, s);
        }
        return this;
    }

    public LogContext putIfAbsent(String key, Object value) {
        if (!vars.containsKey(key)) put(key, value);
        return this;
    }

    public LogContext putAll(Map<String, String> m) { m.forEach(this::put); return this; }
    public LogContext player(PlayerSnapshot s) { s.writeTo(this, ""); return this; }
    public LogContext player(String prefix, PlayerSnapshot s) { s.writeTo(this, prefix); return this; }
    public LogContext dedupe(String key) { this.dedupeKey = key; return this; }
    public LogContext test(boolean t) { this.test = t; return this; }

    public String logId() { return logId; }
    public String dedupeKey() { return dedupeKey; }
    public boolean isTest() { return test; }
    public String get(String k) { return vars.get(k); }
    public Map<String, String> vars() { return Collections.unmodifiableMap(vars); }
}
