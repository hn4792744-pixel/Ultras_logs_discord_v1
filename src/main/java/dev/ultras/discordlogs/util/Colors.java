package dev.ultras.discordlogs.util;

import java.util.Locale;
import java.util.Map;

public final class Colors {
    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("RED", 0xFF5555), Map.entry("DARK_RED", 0xAA0000), Map.entry("GREEN", 0x57F287),
            Map.entry("DARK_GREEN", 0x00AA00), Map.entry("BLUE", 0x5865F2), Map.entry("LIGHT_BLUE", 0x55AAFF),
            Map.entry("DARK_BLUE", 0x0000AA), Map.entry("GRAY", 0x99AAB5), Map.entry("DARK_GRAY", 0x555555),
            Map.entry("PURPLE", 0xAA55FF), Map.entry("ORANGE", 0xFF9900), Map.entry("YELLOW", 0xFEE75C),
            Map.entry("GOLD", 0xFFAA00), Map.entry("CYAN", 0x00E5FF), Map.entry("AQUA", 0x55FFFF),
            Map.entry("WHITE", 0xFFFFFF), Map.entry("BLACK", 0x000000), Map.entry("PINK", 0xFF69B4));

    private Colors() {}

    /** @return RGB integer, or -1 when invalid. */
    public static int parse(String in) {
        if (in == null) return -1;
        String s = in.trim();
        if (s.isEmpty()) return -1;
        Integer named = NAMED.get(s.toUpperCase(Locale.ROOT).replace(' ', '_'));
        if (named != null) return named;
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 6) return Integer.parseInt(s, 16);
            int v = Integer.parseInt(s);
            return v >= 0 && v <= 0xFFFFFF ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static int parseOr(String in, int def) {
        int v = parse(in);
        return v < 0 ? def : v;
    }
}
