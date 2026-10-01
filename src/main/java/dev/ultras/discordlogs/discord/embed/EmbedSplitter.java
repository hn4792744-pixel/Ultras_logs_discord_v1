package dev.ultras.discordlogs.discord.embed;

import dev.ultras.discordlogs.util.Text;

import java.util.ArrayList;
import java.util.List;

/** Enforces Discord embed limits: truncates oversized text, splits into several embeds and groups them into messages. */
public final class EmbedSplitter {
    public static final int TITLE = 256, DESC = 4096, FIELD_NAME = 256, FIELD_VALUE = 1024, FOOTER = 2048, AUTHOR = 256;
    public static final int MAX_FIELDS = 25, MAX_TOTAL = 6000, MAX_PER_MESSAGE = 10;
    private static final int BUDGET = 5500;

    private EmbedSplitter() {}

    public static EmbedData sanitize(EmbedData e) {
        e.title = Text.truncate(e.title, TITLE);
        e.description = Text.truncate(e.description, DESC);
        e.footerText = Text.truncate(e.footerText, FOOTER);
        e.authorName = Text.truncate(e.authorName, AUTHOR);
        List<EmbedField> clean = new ArrayList<>();
        for (EmbedField f : e.fields) {
            String n = Text.truncate(f.name().isBlank() ? "\u200b" : f.name(), FIELD_NAME);
            String v = Text.truncate(f.value().isBlank() ? "\u200b" : f.value(), FIELD_VALUE);
            clean.add(new EmbedField(n, v, f.inline()));
        }
        e.fields.clear();
        e.fields.addAll(clean);
        if (e.title.isBlank() && e.description.isBlank() && e.fields.isEmpty()) e.description = "\u200b";
        return e;
    }

    public static List<EmbedData> split(EmbedData source) {
        EmbedData e = sanitize(source);
        if (e.fields.size() <= MAX_FIELDS && e.size() <= BUDGET) return List.of(e);

        List<EmbedData> parts = new ArrayList<>();
        EmbedData cur = e.copyShell();
        cur.description = e.description;
        cur.thumbnail = e.thumbnail;
        cur.image = e.image;
        int size = cur.size();
        for (EmbedField f : e.fields) {
            int fs = f.name().length() + f.value().length();
            if (cur.fields.size() >= MAX_FIELDS || size + fs > BUDGET) {
                parts.add(cur);
                cur = e.copyShell();
                size = cur.size();
            }
            cur.fields.add(f);
            size += fs;
        }
        parts.add(cur);
        if (parts.size() > 1) {
            for (int i = 0; i < parts.size(); i++) {
                EmbedData p = parts.get(i);
                String base = e.title.isBlank() ? "" : e.title + " ";
                p.title = Text.truncate(base + "(" + (i + 1) + "/" + parts.size() + ")", TITLE);
            }
        }
        return parts;
    }

    /** Groups embeds into Discord messages respecting 10 embeds and 6000 characters per message. */
    public static List<List<EmbedData>> group(List<EmbedData> embeds) {
        List<List<EmbedData>> out = new ArrayList<>();
        List<EmbedData> cur = new ArrayList<>();
        int size = 0;
        for (EmbedData e : embeds) {
            int s = e.size();
            if (!cur.isEmpty() && (cur.size() >= MAX_PER_MESSAGE || size + s > MAX_TOTAL)) {
                out.add(cur);
                cur = new ArrayList<>();
                size = 0;
            }
            cur.add(e);
            size += s;
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }
}
