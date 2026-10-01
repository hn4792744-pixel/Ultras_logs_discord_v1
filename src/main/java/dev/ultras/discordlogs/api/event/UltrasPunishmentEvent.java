package dev.ultras.discordlogs.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fire this AFTER a punishment succeeded (punishment plugins / hooks). ULTRAS never logs attempts. */
public final class UltrasPunishmentEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String type, target, executor, reason, source;
    private final UUID targetUuid;
    private final long durationMillis;

    /** @param type BAN, UNBAN, TEMPBAN, IP_BAN, IP_UNBAN, KICK, MUTE, UNMUTE, WARN, TEMPMUTE, JAIL, UNJAIL
     *  @param durationMillis milliseconds, or -1 for permanent/not applicable */
    public UltrasPunishmentEvent(String type, String target, UUID targetUuid, String executor, String reason,
                                 long durationMillis, String source, boolean async) {
        super(async);
        this.type = type; this.target = target; this.targetUuid = targetUuid; this.executor = executor;
        this.reason = reason; this.durationMillis = durationMillis; this.source = source;
    }

    public String type() { return type; }
    public String target() { return target; }
    public UUID targetUuid() { return targetUuid; }
    public String executor() { return executor; }
    public String reason() { return reason; }
    public long durationMillis() { return durationMillis; }
    public String source() { return source; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
