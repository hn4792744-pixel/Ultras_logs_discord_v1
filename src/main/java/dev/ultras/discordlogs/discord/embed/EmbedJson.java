package dev.ultras.discordlogs.discord.embed;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class EmbedJson {
    private EmbedJson() {}

    private static boolean url(String s) {
        return s != null && (s.startsWith("https://") || s.startsWith("http://"));
    }

    public static JsonObject toJson(EmbedData e) {
        JsonObject o = new JsonObject();
        if (!e.title.isBlank()) o.addProperty("title", e.title);
        if (!e.description.isBlank()) o.addProperty("description", e.description);
        if (url(e.url)) o.addProperty("url", e.url);
        o.addProperty("color", e.color);
        if (!e.timestamp.isBlank()) o.addProperty("timestamp", e.timestamp);
        if (!e.footerText.isBlank()) {
            JsonObject f = new JsonObject();
            f.addProperty("text", e.footerText);
            if (url(e.footerIcon)) f.addProperty("icon_url", e.footerIcon);
            o.add("footer", f);
        }
        if (!e.authorName.isBlank()) {
            JsonObject a = new JsonObject();
            a.addProperty("name", e.authorName);
            if (url(e.authorIcon)) a.addProperty("icon_url", e.authorIcon);
            if (url(e.authorUrl)) a.addProperty("url", e.authorUrl);
            o.add("author", a);
        }
        if (url(e.thumbnail)) {
            JsonObject t = new JsonObject();
            t.addProperty("url", e.thumbnail);
            o.add("thumbnail", t);
        }
        if (url(e.image)) {
            JsonObject t = new JsonObject();
            t.addProperty("url", e.image);
            o.add("image", t);
        }
        if (!e.fields.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (EmbedField f : e.fields) {
                JsonObject fo = new JsonObject();
                fo.addProperty("name", f.name());
                fo.addProperty("value", f.value());
                fo.addProperty("inline", f.inline());
                arr.add(fo);
            }
            o.add("fields", arr);
        }
        return o;
    }
}
