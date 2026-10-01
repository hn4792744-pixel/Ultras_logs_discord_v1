package dev.ultras.discordlogs.service;

import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Remembers who ran which command in the last few seconds. Event listeners (gamemode, teleport, kick...) use it to
 * attribute an effect to a command, and they only do so when the match is unambiguous; otherwise the value stays Unknown.
 */
public final class CommandTracker {
    public enum Kind { PLAYER, CONSOLE, COMMAND_BLOCK, OTHER }

    public record Rec(Kind kind, String sender, UUID senderUuid, boolean op, String full, String label, List<String> args, long at) {
        public String executor() {
            return switch (kind) {
                case PLAYER -> sender;
                case CONSOLE -> "Console";
                case COMMAND_BLOCK -> "Command Block";
                default -> "Unknown";
            };
        }

        public String source() {
            return switch (kind) {
                case PLAYER -> op ? "Admin (OP)" : "Player";
                case CONSOLE -> "Console";
                case COMMAND_BLOCK -> "Command Block";
                default -> "Unknown";
            };
        }
    }

    private static final long KEEP_MS = 4000;
    private final ConcurrentLinkedDeque<Rec> recent = new ConcurrentLinkedDeque<>();

    public static String normalize(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        int i = l.indexOf(':');
        return i >= 0 ? l.substring(i + 1) : l;
    }

    public Rec record(CommandSender s, String rawCommand) {
        String raw = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        String[] parts = raw.trim().split("\\s+");
        Kind kind = s instanceof Player ? Kind.PLAYER : s instanceof ConsoleCommandSender ? Kind.CONSOLE
                : s instanceof BlockCommandSender ? Kind.COMMAND_BLOCK : Kind.OTHER;
        Rec r = new Rec(kind, s.getName(), s instanceof Player p ? p.getUniqueId() : null, s.isOp(), "/" + raw.trim(),
                normalize(parts[0]), new ArrayList<>(Arrays.asList(parts).subList(1, parts.length)), System.currentTimeMillis());
        recent.addFirst(r);
        long cut = r.at() - KEEP_MS;
        for (Iterator<Rec> it = recent.descendingIterator(); it.hasNext(); ) {
            if (it.next().at() < cut) it.remove(); else break;
        }
        return r;
    }

    /**
     * Newest command whose label is in {@code labels} and that concerns the given player (self-targeted or named).
     * @return null when nothing matches within the window (caller must then report Unknown).
     */
    public Rec find(Set<String> labels, String targetName, UUID targetUuid, long windowMs) {
        long now = System.currentTimeMillis();
        for (Rec r : recent) {
            if (now - r.at() > windowMs) break;
            if (!labels.contains(r.label())) continue;
            if (targetUuid != null && targetUuid.equals(r.senderUuid())) return r;
            for (String a : r.args()) if (a.equalsIgnoreCase(targetName) || a.equals("@s")) return r;
        }
        return null;
    }
}
