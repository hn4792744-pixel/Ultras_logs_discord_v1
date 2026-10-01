package dev.ultras.discordlogs.placeholder;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Single-pass %placeholder% replacement. Values are never re-scanned, so user text cannot inject placeholders. */
public final class PlaceholderEngine {
    private static final Pattern P = Pattern.compile("%([a-zA-Z0-9_]+)%");

    private PlaceholderEngine() {}

    public record Result(String text, int resolved, int missing) {}

    /** Strict mode: unresolved placeholders become "Unknown". */
    public static Result apply(String template, Map<String, String> vars) {
        return run(template, vars, true);
    }

    /** Loose mode: unresolved placeholders are left untouched. */
    public static String applyLoose(String template, Map<String, String> vars) {
        return run(template, vars, false).text();
    }

    private static Result run(String template, Map<String, String> vars, boolean strict) {
        if (template == null || template.isEmpty()) return new Result("", 0, 0);
        Matcher m = P.matcher(template);
        StringBuilder sb = new StringBuilder();
        int resolved = 0, missing = 0, last = 0;
        while (m.find()) {
            sb.append(template, last, m.start());
            String v = vars.get(m.group(1));
            if (v != null) {
                sb.append(v);
                resolved++;
            } else if (strict) {
                sb.append("Unknown");
                missing++;
            } else {
                sb.append(m.group());
            }
            last = m.end();
        }
        sb.append(template, last, template.length());
        return new Result(sb.toString(), resolved, missing);
    }
}
