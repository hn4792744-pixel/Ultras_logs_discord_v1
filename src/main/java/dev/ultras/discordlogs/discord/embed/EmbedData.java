package dev.ultras.discordlogs.discord.embed;

import java.util.ArrayList;
import java.util.List;

public final class EmbedData {
    public String title = "";
    public String description = "";
    public String url = "";
    public int color = 0x5865F2;
    public String footerText = "";
    public String footerIcon = "";
    public String authorName = "";
    public String authorIcon = "";
    public String authorUrl = "";
    public String thumbnail = "";
    public String image = "";
    public String timestamp = "";
    public final List<EmbedField> fields = new ArrayList<>();

    public int size() {
        int n = title.length() + description.length() + footerText.length() + authorName.length();
        for (EmbedField f : fields) n += f.name().length() + f.value().length();
        return n;
    }

    public EmbedData copyShell() {
        EmbedData c = new EmbedData();
        c.title = title; c.url = url; c.color = color; c.footerText = footerText; c.footerIcon = footerIcon;
        c.authorName = authorName; c.authorIcon = authorIcon; c.authorUrl = authorUrl; c.timestamp = timestamp;
        return c;
    }
}
