package dev.ultras.discordlogs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Map;

/** Fire this from any plugin (sync or async) to send a registered custom log through ULTRAS. */
public final class UltrasCustomLogEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String logId;
    private final Map<String, String> data;
    private final Player player;

    public UltrasCustomLogEvent(String logId, Map<String, String> data, Player player, boolean async) {
        super(async);
        this.logId = logId;
        this.data = data;
        this.player = player;
    }

    public UltrasCustomLogEvent(String logId, Map<String, String> data) { this(logId, data, null, false); }

    public String logId() { return logId; }
    public Map<String, String> data() { return data; }
    public Player player() { return player; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
