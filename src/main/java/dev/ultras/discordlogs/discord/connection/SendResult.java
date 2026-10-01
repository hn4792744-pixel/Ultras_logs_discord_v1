package dev.ultras.discordlogs.discord.connection;

public record SendResult(Kind kind, long retryAfterMs, int http, String detail) {
    public enum Kind { SUCCESS, RATE_LIMITED, RETRYABLE, PERMANENT }

    public static SendResult ok(int http) { return new SendResult(Kind.SUCCESS, 0, http, "ok"); }
    public static SendResult limited(long ms) { return new SendResult(Kind.RATE_LIMITED, ms, 429, "rate limited"); }
    public static SendResult retry(int http, String d) { return new SendResult(Kind.RETRYABLE, 0, http, d); }
    public static SendResult fail(int http, String d) { return new SendResult(Kind.PERMANENT, 0, http, d); }
    public boolean success() { return kind == Kind.SUCCESS; }
}
