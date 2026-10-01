package dev.ultras.discordlogs.discord.connection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Shared HTTP client for all connections. Never includes URLs or tokens in results or exceptions. */
public final class DiscordHttp implements AutoCloseable {
    private static final String UA = "DiscordBot (https://github.com/ultras/ULTRAS_Discord_logs_v1, 1.0.0)";
    private final HttpClient client;
    private final long timeoutMs;

    public DiscordHttp(long timeoutMs) {
        this.timeoutMs = Math.max(2000, timeoutMs);
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Observed bucket state, used by the rate limiter. */
    public record Bucket(int remaining, long resetAfterMs) {}

    public record Response(SendResult result, Bucket bucket) {}

    public Response request(String method, URI uri, Map<String, String> headers, String json) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", UA).header("Accept", "application/json");
            headers.forEach(b::header);
            if (json != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(map(r), bucket(r));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response(SendResult.retry(0, "interrupted"), null);
        } catch (Exception e) {
            return new Response(SendResult.retry(0, "network error (" + e.getClass().getSimpleName() + ")"), null);
        }
    }

    private static Bucket bucket(HttpResponse<String> r) {
        try {
            String rem = r.headers().firstValue("x-ratelimit-remaining").orElse(null);
            String reset = r.headers().firstValue("x-ratelimit-reset-after").orElse(null);
            if (rem == null || reset == null) return null;
            return new Bucket(Integer.parseInt(rem.trim()), (long) (Double.parseDouble(reset.trim()) * 1000));
        } catch (Exception e) {
            return null;
        }
    }

    private static SendResult map(HttpResponse<String> r) {
        int s = r.statusCode();
        if (s >= 200 && s < 300) return SendResult.ok(s);
        if (s == 429) {
            long ms = 1000;
            try {
                JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                if (o.has("retry_after")) ms = (long) Math.ceil(o.get("retry_after").getAsDouble() * 1000);
            } catch (Exception e) {
                ms = r.headers().firstValue("retry-after").map(v -> {
                    try { return (long) Math.ceil(Double.parseDouble(v) * 1000); } catch (Exception ex) { return 1000L; }
                }).orElse(1000L);
            }
            return SendResult.limited(Math.max(250, Math.min(ms, 120_000)));
        }
        if (s == 408 || s >= 500) return SendResult.retry(s, "discord server error (HTTP " + s + ")");
        String hint = switch (s) {
            case 400 -> "bad request (payload rejected)";
            case 401 -> "unauthorized (invalid token/webhook)";
            case 403 -> "forbidden (missing permissions)";
            case 404 -> "not found (webhook/channel deleted?)";
            default -> "rejected";
        };
        return SendResult.fail(s, "HTTP " + s + " " + hint);
    }

    @Override
    public void close() {
        try { client.close(); } catch (Throwable ignored) { /* shutting down */ }
    }
}
