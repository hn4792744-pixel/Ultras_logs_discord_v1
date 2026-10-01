package dev.ultras.discordlogs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fire this after a player was frozen/unfrozen by a freeze plugin. */
public final class UltrasFreezeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String executor, reason, command;
    private final boolean frozen;

    public UltrasFreezeEvent(Player player, String executor, String reason, String command, boolean frozen) {
        this.player = player; this.executor = executor; this.reason = reason; this.command = command; this.frozen = frozen;
    }

    public Player player() { return player; }
    public String executor() { return executor; }
    public String reason() { return reason; }
    public String command() { return command; }
    public boolean frozen() { return frozen; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
