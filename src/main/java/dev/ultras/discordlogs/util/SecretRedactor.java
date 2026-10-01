package dev.ultras.discordlogs.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Removes webhook URLs, bot tokens and registered secrets from any text before it is logged. */
public final class SecretRedactor {
    private static final Pattern WEBHOOK = Pattern.compile("https?://[\\w.-]*discord(?:app)?\\.com/api(?:/v\\d+)?/webhooks/\\d+/[\\w.-]+(?:\\?\\S*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN = Pattern.compile("[\\w-]{23,30}\\.[\\w-]{6,8}\\.[\\w-]{27,}");
    private static final Set<String> SECRETS = ConcurrentHashMap.newKeySet();

    private SecretRedactor() {}

    public static void register(String secret) {
        if (secret != null && secret.length() >= 8) SECRETS.add(secret);
    }

    public static void clear() {
        SECRETS.clear();
    }

    public static String redact(String in) {
        if (in == null) return "";
        String out = in;
        for (String s : SECRETS) out = out.replace(s, "[REDACTED]");
        out = WEBHOOK.matcher(out).replaceAll("[REDACTED_WEBHOOK]");
        out = TOKEN.matcher(out).replaceAll("[REDACTED_TOKEN]");
        return out;
    }

    public static String describe(Throwable t) {
        if (t == null) return "";
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ": " + redact(m));
    }
}
