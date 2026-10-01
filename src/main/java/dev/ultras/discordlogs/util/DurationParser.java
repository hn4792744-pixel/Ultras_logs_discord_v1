package dev.ultras.discordlogs.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern P = Pattern.compile("^(\\d+)\\s*(ms|s|m|h|d)?$");

    private DurationParser() {}

    /** @return milliseconds, or -1 if invalid. Plain numbers are seconds. */
    public static long parseMillis(String in) {
        if (in == null) return -1;
        Matcher m = P.matcher(in.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return -1;
        long n = Long.parseLong(m.group(1));
        String u = m.group(2) == null ? "s" : m.group(2);
        return switch (u) {
            case "ms" -> n;
            case "s" -> n * 1000L;
            case "m" -> n * 60_000L;
            case "h" -> n * 3_600_000L;
            default -> n * 86_400_000L;
        };
    }

    public static String format(long ms) {
        if (ms < 0) return "Unknown";
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
