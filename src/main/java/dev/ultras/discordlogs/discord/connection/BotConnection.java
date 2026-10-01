package dev.ultras.discordlogs.discord.connection;

import dev.ultras.discordlogs.discord.queue.OutboundMessage;

import java.net.URI;
import java.util.Map;

/**
 * Optional bot transport using the Discord REST API (Authorization: Bot token + channel id).
 * Sending messages does not need a gateway session, so nothing can block or crash the server.
 */
public final class BotConnection extends AbstractConnection {
    private static final String API = "https://discord.com/api/v10";

    public BotConnection(ConnectionSettings s, DiscordHttp http) { super(s, http); }

    @Override protected ConnectionStatus good() { return ConnectionStatus.READY; }
    @Override protected ConnectionStatus bad() { return ConnectionStatus.ERROR; }

    private Map<String, String> auth() { return Map.of("Authorization", "Bot " + settings.token().trim()); }

    @Override
    public SendResult send(OutboundMessage message) {
        if (!settings.formatValid()) return SendResult.fail(0, "connection not configured");
        DiscordHttp.Response r = http.request("POST", URI.create(API + "/channels/" + settings.channelId() + "/messages"),
                auth(), body(message, false).toString());
        observe(r);
        return r.result();
    }

    @Override
    public SendResult validateRemote() {
        if (!settings.formatValid()) return SendResult.fail(0, "connection not configured");
        DiscordHttp.Response me = http.request("GET", URI.create(API + "/users/@me"), auth(), null);
        if (!me.result().success()) { applyValidation(me.result()); return me.result(); }
        DiscordHttp.Response ch = http.request("GET", URI.create(API + "/channels/" + settings.channelId()), auth(), null);
        applyValidation(ch.result());
        return ch.result();
    }
}
