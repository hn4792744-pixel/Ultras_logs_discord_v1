package dev.ultras.discordlogs.logs;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.hooks.HookManager;
import dev.ultras.discordlogs.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.UUID;

/** Immutable, thread-safe copy of player state, captured on the main thread. */
public record PlayerSnapshot(String name, String uuid, String ip, String rank, String prefix, String world, String x,
                             String y, String z, String gamemode, String op, String vanish, String platform,
                             String ping, String avatar) {

    public static PlayerSnapshot capture(UltrasDiscordLogs plugin, Player p) {
        HookManager hooks = plugin.hooks();
        Location l = p.getLocation();
        InetSocketAddress addr = p.getAddress();
        String ip = !plugin.settings().logIps ? "Hidden"
                : (addr == null || addr.getAddress() == null ? "Unknown" : addr.getAddress().getHostAddress());
        HookManager.RankInfo rank = hooks.rank(p);
        return new PlayerSnapshot(p.getName(), p.getUniqueId().toString(), ip, rank.group(), rank.prefix(),
                l.getWorld() == null ? "Unknown" : l.getWorld().getName(),
                String.valueOf(l.getBlockX()), String.valueOf(l.getBlockY()), String.valueOf(l.getBlockZ()),
                Text.pretty(p.getGameMode().name()), Text.yesNo(p.isOp()), Text.yesNo(hooks.isVanished(p)),
                hooks.platform(p), String.valueOf(p.getPing()), avatar(plugin, p.getUniqueId().toString(), p.getName()));
    }

    /** Minimal snapshot for offline players or names only (every unknown value is "Unknown"). */
    public static PlayerSnapshot offline(UltrasDiscordLogs plugin, String name, UUID id) {
        String u = id == null ? "Unknown" : id.toString();
        return new PlayerSnapshot(name == null ? "Unknown" : name, u, "Unknown", "Unknown", "", "Unknown", "Unknown",
                "Unknown", "Unknown", "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
                id == null ? null : avatar(plugin, u, name));
    }

    private static String avatar(UltrasDiscordLogs plugin, String uuid, String name) {
        if (!plugin.settings().avatarEnabled) return null;
        return plugin.settings().avatarUrl.replace("%uuid%", uuid).replace("%name%", name == null ? "" : name);
    }

    public String location() { return "Unknown".equals(x) ? "Unknown" : x + ", " + y + ", " + z; }

    public void writeTo(LogContext c, String pre) {
        c.put(pre + "player", name).put(pre + "uuid", uuid).put(pre + "ip", ip).put(pre + "rank", rank)
                .put(pre + "prefix", prefix).put(pre + "world", world).put(pre + "x", x).put(pre + "y", y)
                .put(pre + "z", z).put(pre + "location", location()).put(pre + "gamemode", gamemode)
                .put(pre + "is_op", op).put(pre + "is_vanish", vanish).put(pre + "platform", platform)
                .put(pre + "ping", ping).put(pre + "avatar", avatar);
    }
}
