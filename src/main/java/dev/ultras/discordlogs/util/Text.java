package dev.ultras.discordlogs.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ";
    private static final Pattern PROTECTED = Pattern.compile("&#[0-9a-fA-F]{6}|[&§][0-9a-fk-orA-FK-OR]|%[a-zA-Z0-9_]+%|\\{[a-zA-Z0-9_]+}");
    private static final Pattern COLOR_CODE = Pattern.compile("(&#[0-9a-fA-F]{6}|&[0-9a-fA-Fr])");
    private static final Pattern STRIP = Pattern.compile("&#[0-9a-fA-F]{6}|[&§][0-9a-fk-orxA-FK-ORX]");

    private Text() {}

    public static String smallCaps(String in) {
        if (in == null || in.isEmpty()) return "";
        StringBuilder out = new StringBuilder(in.length());
        Matcher m = PROTECTED.matcher(in);
        int last = 0;
        while (m.find()) {
            convert(in, last, m.start(), out);
            out.append(m.group());
            last = m.end();
        }
        convert(in, last, in.length(), out);
        return out.toString();
    }

    private static void convert(String s, int from, int to, StringBuilder out) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') out.append(SMALL.charAt(c - 'a'));
            else if (c >= 'A' && c <= 'Z') out.append(SMALL.charAt(c - 'A'));
            else out.append(c);
        }
    }

    public static String bold(String s) {
        if (s == null || s.isEmpty()) return "";
        String r = COLOR_CODE.matcher(s).replaceAll("$1&l");
        return r.startsWith("&") ? r : "&l" + r;
    }

    public static String stripColors(String s) {
        return s == null ? "" : STRIP.matcher(s).replaceAll("");
    }

    public static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return max <= 1 ? s.substring(0, Math.max(0, max)) : s.substring(0, max - 1) + "…";
    }

    public static String yesNo(Boolean b) {
        return b == null ? "Unknown" : (b ? "Yes" : "No");
    }

    public static String pretty(String enumName) {
        if (enumName == null || enumName.isBlank()) return "Unknown";
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("[_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    /** Prevents accidental mass pings and mention formatting in user-provided text shown on Discord. */
    public static String sanitizeUserText(String s) {
        if (s == null) return "";
        return s.replace("@everyone", "@\u200beveryone").replace("@here", "@\u200bhere")
                .replace("<@", "<\u200b@").trim();
    }

    public static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fB", v / 1_000_000_000);
        if (a >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", v / 1_000_000);
        if (a >= 10_000) return String.format(Locale.ROOT, "%.1fK", v / 1_000);
        return String.format(Locale.ROOT, "%,d", (long) v);
    }
}
