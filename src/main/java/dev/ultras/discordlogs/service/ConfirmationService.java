package dev.ultras.discordlogs.service;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.util.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Verifies vanilla commands by comparing server state before and after (ban list, op list, whitelist).
 * A command is only logged as an effect when the state really changed, never because it was typed.
 */
public final class ConfirmationService {
    private static final Pattern IP = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$|^[0-9a-fA-F:]+$");
    private static final Set<String> LABELS = Set.of("ban", "ban-ip", "pardon", "pardon-ip", "op", "deop", "whitelist");
    private static final long VERIFY_DELAY_TICKS = 4L;
    private final UltrasDiscordLogs plugin;

    public ConfirmationService(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    /** Main thread. Called when a command is about to run. */
    public void onCommand(CommandTracker.Rec r) {
        if (!LABELS.contains(r.label()) || r.args().isEmpty()) return;
        String arg0 = r.args().get(0);
        String rest = r.args().size() > 1 ? String.join(" ", r.args().subList(1, r.args().size())) : "";
        switch (r.label()) {
            case "ban" -> verifyBan(r, arg0, rest, true);
            case "pardon" -> verifyBan(r, arg0, rest, false);
            case "ban-ip" -> verifyIp(r, arg0, rest, true);
            case "pardon-ip" -> verifyIp(r, arg0, rest, false);
            case "op" -> verifyOp(r, arg0, true);
            case "deop" -> verifyOp(r, arg0, false);
            case "whitelist" -> { if (r.args().size() >= 2) verifyWhitelist(r, r.args().get(0).toLowerCase(), r.args().get(1)); }
            default -> { }
        }
    }

    private static OfflinePlayer cached(String name) { return Bukkit.getOfflinePlayerIfCached(name); }

    private static Boolean banned(OfflinePlayer p) {
        Object o = p == null ? null : Reflect.call(p, "isBanned");
        return o instanceof Boolean b ? b : null;
    }

    private void verifyBan(CommandTracker.Rec r, String name, String reason, boolean ban) {
        Boolean before = banned(cached(name));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            OfflinePlayer after = cached(name);
            Boolean now = banned(after);
            if (now == null) return;
            boolean confirmed;
            if (ban) confirmed = now && (Boolean.FALSE.equals(before) || (before == null && freshBan(after)));
            else confirmed = !now && Boolean.TRUE.equals(before);
            if (!confirmed) return;
            plugin.punishments().report(ban ? PunishmentService.Type.BAN : PunishmentService.Type.UNBAN, after.getName() == null ? name : after.getName(),
                    after.getUniqueId(), r.executor(), reason, null, r.source(), true);
        }, VERIFY_DELAY_TICKS);
    }

    private static boolean freshBan(OfflinePlayer p) {
        Object entry = Reflect.call(p, "getBanEntry");
        Object created = Reflect.call(entry, "getCreated");
        return created instanceof Date d && System.currentTimeMillis() - d.getTime() < 15_000;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> ipBans() {
        Object o = Reflect.call(Bukkit.getServer(), "getIPBans");
        return o instanceof Set<?> s ? (Set<String>) s : null;
    }

    private void verifyIp(CommandTracker.Rec r, String arg, String reason, boolean ban) {
        String ip = arg;
        String shown = "Hidden";
        if (!IP.matcher(arg).matches()) {
            Player p = Bukkit.getPlayerExact(arg);
            InetSocketAddress a = p == null ? null : p.getAddress();
            if (a == null || a.getAddress() == null) return; // cannot resolve: stay silent instead of guessing
            ip = a.getAddress().getHostAddress();
        }
        Set<String> beforeSet = ipBans();
        Boolean before = beforeSet == null ? null : beforeSet.contains(ip);
        final String fip = ip;
        final String display = plugin.settings().logIps ? ip : shown;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Set<String> s = ipBans();
            if (s == null || before == null) return;
            boolean now = s.contains(fip);
            if (ban ? (now && !before) : (!now && before))
                plugin.punishments().report(ban ? PunishmentService.Type.IP_BAN : PunishmentService.Type.IP_UNBAN,
                        display, null, r.executor(), reason, null, r.source(), true);
        }, VERIFY_DELAY_TICKS);
    }

    private void verifyOp(CommandTracker.Rec r, String name, boolean op) {
        OfflinePlayer before = cached(name);
        if (before == null) return;
        boolean was = before.isOp();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            OfflinePlayer after = cached(name);
            if (after == null || after.isOp() == was || after.isOp() != op) return;
            submitState("op-change", after, r, op ? "OP" : "DeOP");
        }, VERIFY_DELAY_TICKS);
    }

    private void verifyWhitelist(CommandTracker.Rec r, String action, String name) {
        if (!action.equals("add") && !action.equals("remove")) return;
        OfflinePlayer before = cached(name);
        Boolean was = before == null ? null : before.isWhitelisted();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            OfflinePlayer after = cached(name);
            if (after == null) return;
            boolean now = after.isWhitelisted();
            boolean add = action.equals("add");
            boolean changed = add ? (now && !Boolean.TRUE.equals(was) && was != null) : (!now && Boolean.TRUE.equals(was));
            if (changed) submitState("whitelist-change", after, r, add ? "Whitelist Add" : "Whitelist Remove");
        }, VERIFY_DELAY_TICKS);
    }

    private void submitState(String logId, OfflinePlayer target, CommandTracker.Rec r, String action) {
        LogContext c = LogContext.of(logId).player(PlayerSnapshot.offline(plugin, target.getName(), target.getUniqueId()))
                .put("target", target.getName()).put("executor", r.executor()).put("source", r.source())
                .put("command", r.full()).put("action", action).put("confirmation", "Confirmed")
                .dedupe(action + "|" + target.getUniqueId());
        plugin.logs().submit(c);
    }

    public List<String> labels() { return List.copyOf(LABELS); }
}
