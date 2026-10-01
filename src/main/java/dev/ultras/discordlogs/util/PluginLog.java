package dev.ultras.discordlogs.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PluginLog {
    private final Logger logger;
    private volatile boolean debug;
    private final ConcurrentHashMap<String, Long> once = new ConcurrentHashMap<>();

    public PluginLog(Logger logger) {
        this.logger = logger;
    }

    public void setDebug(boolean debug) { this.debug = debug; }

    public boolean debugEnabled() { return debug; }

    public void info(String m) { logger.info(SecretRedactor.redact(m)); }

    public void warn(String m) { logger.warning(SecretRedactor.redact(m)); }

    public void warn(String m, Throwable t) {
        logger.warning(SecretRedactor.redact(m) + " (" + SecretRedactor.describe(t) + ")");
        if (debug) stack(t);
    }

    public void error(String m, Throwable t) {
        logger.severe(SecretRedactor.redact(m) + " (" + SecretRedactor.describe(t) + ")");
        if (debug) stack(t);
    }

    public void debug(String m) {
        if (debug) logger.info("[debug] " + SecretRedactor.redact(m));
    }

    /** Logs at most once per interval for the given key (spam protection for repeated failures). */
    public void warnLimited(String key, long intervalMs, String m) {
        long now = System.currentTimeMillis();
        Long prev = once.get(key);
        if (prev != null && now - prev < intervalMs) return;
        once.put(key, now);
        warn(m);
    }

    private void stack(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getName());
        for (StackTraceElement e : t.getStackTrace()) sb.append("\n\tat ").append(e);
        logger.log(Level.WARNING, "[debug] " + sb);
    }
}
