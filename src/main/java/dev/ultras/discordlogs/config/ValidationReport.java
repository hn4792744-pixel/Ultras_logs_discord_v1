package dev.ultras.discordlogs.config;

import dev.ultras.discordlogs.util.SecretRedactor;

import java.util.ArrayList;
import java.util.List;

public final class ValidationReport {
    public enum Level { INFO, WARN, ERROR }

    public record Issue(Level level, String message) {}

    private final List<Issue> issues = new ArrayList<>();

    public void info(String m) { issues.add(new Issue(Level.INFO, SecretRedactor.redact(m))); }
    public void warn(String m) { issues.add(new Issue(Level.WARN, SecretRedactor.redact(m))); }
    public void error(String m) { issues.add(new Issue(Level.ERROR, SecretRedactor.redact(m))); }
    public List<Issue> issues() { return issues; }
    public int count(Level l) { return (int) issues.stream().filter(i -> i.level() == l).count(); }
    public boolean hasErrors() { return count(Level.ERROR) > 0; }
}
