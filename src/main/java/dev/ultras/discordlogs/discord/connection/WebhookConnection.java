package dev.ultras.discordlogs.discord.connection;

import dev.ultras.discordlogs.discord.queue.OutboundMessage;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Stateless Discord webhook transport. Needs no bot, no login and no token. */
public final class WebhookConnection extends AbstractConnection {
    public WebhookConnection(ConnectionSettings s, DiscordHttp http) { super(s, http); }

    @Override protected ConnectionStatus good() { return ConnectionStatus.VALID; }
    @Override protected ConnectionStatus bad() { return ConnectionStatus.INVALID; }

    private URI uri(boolean withThread) {
        String base = settings.webhookUrl().trim();
        if (withThread && !settings.threadId().isBlank())
            base += (base.contains("?") ? "&" : "?") + "thread_id=" + URLEncoder.encode(settings.threadId(), StandardCharsets.UTF_8);
        return URI.create(base);
    }

    @Override
    public SendResult send(OutboundMessage message) {
        if (!settings.formatValid()) return SendResult.fail(0, "connection not configured");
        DiscordHttp.Response r = http.request("POST", uri(true), Map.of(), body(message, true).toString());
        observe(r);
        return r.result();
    }

    @Override
    public SendResult validateRemote() {
        if (!settings.formatValid()) return SendResult.fail(0, "connection not configured");
        DiscordHttp.Response r = http.request("GET", uri(false), Map.of(), null);
        applyValidation(r.result());
        return r.result();
    }
}
