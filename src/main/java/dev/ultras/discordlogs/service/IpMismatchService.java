package dev.ultras.discordlogs.service;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.storage.IpEntry;
import dev.ultras.discordlogs.storage.PlayerData;
import dev.ultras.discordlogs.storage.PlayerDataStore;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** "Potential Account/IP Mismatch": a neutral signal, never proof of anything. */
public final class IpMismatchService {
    private final UltrasDiscordLogs plugin;

    public IpMismatchService(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    private static String names(List<String> l) {
        if (l.isEmpty()) return "None";
        String s = String.join(", ", l.stream().limit(15).toList());
        return l.size() > 15 ? s + " … (+" + (l.size() - 15) + ")" : s;
    }

    /** Main thread. Records the IP and emits a security log when the account logs in from a never-seen address. */
    public void onJoin(PlayerSnapshot snap, PlayerData d, String ip, long now) {
        if (!plugin.settings().logIps || ip == null || ip.equals("Unknown") || ip.equals("Hidden")) return;
        PlayerDataStore store = plugin.players();
        String prev = d.lastIp;
        boolean mismatch = false;
        if (prev != null && !prev.equals(ip)) {
            ConfigurationSection cfg = plugin.config().logSettings("ip-mismatch").settings();
            boolean onlyNew = cfg.getBoolean("only-new-ips", true);
            mismatch = !onlyNew || !store.hasSeenIp(d, ip);
        }
        if (mismatch && plugin.logs().isEnabled("ip-mismatch")) {
            List<String> cur = store.otherAccounts(ip, d.uuid);
            List<String> old = store.otherAccounts(prev, d.uuid);
            Set<String> linked = new LinkedHashSet<>(cur);
            linked.addAll(old);
            long firstSeen = d.firstJoin;
            synchronized (d.ips) {
                if (d.firstIp != null) { IpEntry e = d.ips.get(d.firstIp); if (e != null) firstSeen = e.firstSeen(); }
            }
            LogContext c = LogContext.of("ip-mismatch").player(snap)
                    .put("account_name", snap.name()).put("current_ip", ip).put("previous_ip", prev)
                    .put("first_seen_ip", d.firstIp).put("first_seen_date", firstSeen > 0 ? plugin.clock().date(firstSeen) + " " + plugin.clock().time(firstSeen) : null)
                    .put("other_accounts_current", names(cur)).put("other_accounts_previous", names(old))
                    .put("linked_accounts", String.valueOf(linked.size()))
                    .put("last_login", d.lastJoin > 0 ? plugin.clock().date(d.lastJoin) + " " + plugin.clock().time(d.lastJoin) : null)
                    .dedupe(snap.uuid() + "|" + ip);
            plugin.logs().submit(c);
        }
        store.recordIp(d, ip, now);
        d.lastIp = ip;
    }
}
