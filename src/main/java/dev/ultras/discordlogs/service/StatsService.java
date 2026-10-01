package dev.ultras.discordlogs.service;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.storage.PlayerData;
import dev.ultras.discordlogs.util.DurationParser;
import dev.ultras.discordlogs.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Player statistics, server-top and periodic server statistics.
 * Lag protection: vanilla stats are captured for at most a few online players per second (round robin) and every
 * ranking is computed asynchronously from that cache, so nothing ever iterates all offline player files.
 */
public final class StatsService {
    private static final String[] TOP_KEYS = {"kills", "deaths", "blocks_broken", "blocks_placed", "playtime", "distance", "damage_dealt", "mob_kills"};
    private final UltrasDiscordLogs plugin;
    private final Map<UUID, AtomicInteger> activity = new ConcurrentHashMap<>();
    private final Map<UUID, String> activityNames = new ConcurrentHashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private volatile List<Material> blocks, items;
    private int rotation;

    public StatsService(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    // ---------------------------------------------------------------- activity / counters

    public void activity(Player p, int points) {
        activity.computeIfAbsent(p.getUniqueId(), k -> new AtomicInteger()).addAndGet(points);
        activityNames.put(p.getUniqueId(), p.getName());
    }

    // ---------------------------------------------------------------- vanilla statistics

    private static Statistic stat(String... names) {
        for (String n : names) {
            try { return Statistic.valueOf(n); } catch (IllegalArgumentException ignored) { /* try next */ }
        }
        return null;
    }

    private static long get(Player p, String... names) {
        Statistic s = stat(names);
        if (s == null) return 0;
        try { return p.getStatistic(s); } catch (Throwable t) { return 0; }
    }

    private void materials() {
        if (blocks != null) return;
        List<Material> b = new ArrayList<>(), i = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isLegacy()) continue;
            if (m.isBlock()) b.add(m);
            if (m.isItem()) i.add(m);
        }
        blocks = b;
        items = i;
    }

    private static long sum(Player p, String statName, List<Material> mats) {
        Statistic s = stat(statName);
        if (s == null) return 0;
        long total = 0;
        for (Material m : mats) {
            try { total += p.getStatistic(s, m); } catch (Throwable ignored) { /* invalid pair */ }
        }
        return total;
    }

    /** Main thread only. */
    public Map<String, Long> capture(Player p) {
        materials();
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("playtime_ticks", get(p, "PLAY_ONE_MINUTE", "PLAY_TIME"));
        m.put("blocks_broken", sum(p, "MINE_BLOCK", blocks));
        m.put("blocks_placed", sum(p, "USE_ITEM", blocks));
        m.put("walk_cm", get(p, "WALK_ONE_CM"));
        m.put("sprint_cm", get(p, "SPRINT_ONE_CM"));
        m.put("fly_cm", get(p, "FLY_ONE_CM"));
        m.put("swim_cm", get(p, "SWIM_ONE_CM"));
        m.put("jumps", get(p, "JUMP"));
        m.put("damage_taken", get(p, "DAMAGE_TAKEN"));
        m.put("damage_dealt", get(p, "DAMAGE_DEALT"));
        m.put("deaths", get(p, "DEATHS"));
        m.put("player_kills", get(p, "PLAYER_KILLS"));
        m.put("mob_kills", get(p, "MOB_KILLS"));
        m.put("items_picked_up", sum(p, "PICKUP", items));
        m.put("items_dropped", sum(p, "DROP", items));
        m.put("items_crafted", sum(p, "CRAFT_ITEM", items));
        m.put("items_used", sum(p, "USE_ITEM", items));
        m.put("chests_opened", get(p, "CHEST_OPENED"));
        m.put("villager_trades", get(p, "TRADED_WITH_VILLAGER"));
        m.put("fish_caught", get(p, "FISH_CAUGHT"));
        return m;
    }

    public void store(Player p, Map<String, Long> stats) {
        PlayerData d = plugin.players().getOrCreate(p.getUniqueId(), p.getName());
        d.stats = Map.copyOf(stats);
        d.statsUpdated = System.currentTimeMillis();
        plugin.players().markDirty();
    }

    private static String blocks(long cm) { return Text.compact(cm / 100.0) + " blocks"; }

    /** Sends the returning-player statistics log (main thread). */
    public void logPlayerStats(Player p, PlayerSnapshot snap) {
        if (!plugin.logs().isEnabled("player-stats")) return;
        Map<String, Long> s = capture(p);
        store(p, s);
        LogContext c = LogContext.of("player-stats").player(snap)
                .put("stats_play_time", DurationParser.format(s.get("playtime_ticks") * 50L))
                .put("stats_blocks_broken", Text.compact(s.get("blocks_broken")))
                .put("stats_blocks_placed", Text.compact(s.get("blocks_placed")))
                .put("stats_walked", blocks(s.get("walk_cm"))).put("stats_sprinted", blocks(s.get("sprint_cm")))
                .put("stats_flown", blocks(s.get("fly_cm"))).put("stats_swum", blocks(s.get("swim_cm")))
                .put("stats_jumps", Text.compact(s.get("jumps")))
                .put("stats_damage_taken", hearts(s.get("damage_taken"))).put("stats_damage_dealt", hearts(s.get("damage_dealt")))
                .put("stats_deaths", Text.compact(s.get("deaths"))).put("stats_player_kills", Text.compact(s.get("player_kills")))
                .put("stats_mob_kills", Text.compact(s.get("mob_kills")))
                .put("stats_items_picked_up", Text.compact(s.get("items_picked_up")))
                .put("stats_items_dropped", Text.compact(s.get("items_dropped")))
                .put("stats_items_crafted", Text.compact(s.get("items_crafted")))
                .put("stats_items_used", Text.compact(s.get("items_used")))
                .put("stats_chests_opened", Text.compact(s.get("chests_opened")))
                .put("stats_villager_trades", Text.compact(s.get("villager_trades")))
                .put("stats_fish_caught", Text.compact(s.get("fish_caught")))
                .dedupe(snap.uuid());
        plugin.logs().submit(c);
    }

    private static String hearts(long raw) { return String.format(Locale.ROOT, "%s ❤", Text.compact(raw / 20.0)); }

    // ---------------------------------------------------------------- scheduling

    public void start() {
        stop();
        if (plugin.logs().isEnabled("server-top")) {
            long every = ticks(plugin.config().logSettings("server-top").settings().getString("interval", "1m"));
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::rotateCapture, 40L, 20L));
            tasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::publishTop, every, every));
        }
        if (plugin.logs().isEnabled("server-stats")) {
            long every = ticks(plugin.config().logSettings("server-stats").settings().getString("interval", "30m"));
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::publishServerStats, every, every));
        }
    }

    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    private static long ticks(String interval) {
        long ms = DurationParser.parseMillis(interval);
        return Math.max(200L, ms / 50L);
    }

    private void rotateCapture() {
        List<? extends Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) return;
        int batch = Math.min(3, online.size());
        for (int i = 0; i < batch; i++) {
            Player p = online.get(Math.floorMod(rotation++, online.size()));
            try { store(p, capture(p)); } catch (Throwable t) { plugin.log().debug("stats capture failed: " + t.getClass().getSimpleName()); }
        }
    }

    // ---------------------------------------------------------------- server top (async)

    private void publishTop() {
        try {
            ConfigurationSection cfg = plugin.config().logSettings("server-top").settings();
            int size = Math.max(1, Math.min(10, cfg.getInt("top-size", 3)));
            ConfigurationSection cats = cfg.getConfigurationSection("categories");
            LogContext c = LogContext.of("server-top");
            for (String key : TOP_KEYS) {
                if (cats != null && !cats.getBoolean(key, true)) continue;
                String text = ranking(key, size);
                if (text != null) c.put("top_" + key, text);
            }
            plugin.logs().submit(c);
        } catch (Throwable t) {
            plugin.log().warn("Server top failed", t);
        }
    }

    private static long value(Map<String, Long> s, String key) {
        return switch (key) {
            case "kills" -> s.getOrDefault("player_kills", 0L);
            case "deaths" -> s.getOrDefault("deaths", 0L);
            case "blocks_broken" -> s.getOrDefault("blocks_broken", 0L);
            case "blocks_placed" -> s.getOrDefault("blocks_placed", 0L);
            case "playtime" -> s.getOrDefault("playtime_ticks", 0L);
            case "distance" -> s.getOrDefault("walk_cm", 0L) + s.getOrDefault("sprint_cm", 0L) + s.getOrDefault("fly_cm", 0L) + s.getOrDefault("swim_cm", 0L);
            case "damage_dealt" -> s.getOrDefault("damage_dealt", 0L);
            case "mob_kills" -> s.getOrDefault("mob_kills", 0L);
            default -> 0L;
        };
    }

    private static String format(String key, long v) {
        return switch (key) {
            case "playtime" -> DurationParser.format(v * 50L);
            case "distance" -> blocks(v);
            case "damage_dealt" -> hearts(v);
            default -> Text.compact(v);
        };
    }

    String ranking(String key, int size) {
        record Row(String name, long value) {}
        List<Row> rows = new ArrayList<>();
        for (PlayerData d : plugin.players().all()) {
            if (d.stats.isEmpty() || d.name == null) continue;
            long v = value(d.stats, key);
            if (v > 0) rows.add(new Row(d.name, v));
        }
        if (rows.isEmpty()) return null;
        rows.sort(Comparator.comparingLong(Row::value).reversed());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(size, rows.size()); i++) {
            sb.append(i + 1).append(". ").append(Text.sanitizeUserText(rows.get(i).name())).append(" — ")
                    .append(format(key, rows.get(i).value())).append('\n');
        }
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------- server statistics (main thread snapshot)

    private boolean on(ConfigurationSection stats, String key) { return stats == null || stats.getBoolean(key, true); }

    private void publishServerStats() {
        try {
            ConfigurationSection cfg = plugin.config().logSettings("server-stats").settings();
            ConfigurationSection st = cfg.getConfigurationSection("stats");
            LogContext c = LogContext.of("server-stats");
            var counters = plugin.counters();
            if (on(st, "online-players")) c.put("online", Bukkit.getOnlinePlayers().size()).put("max_players", Bukkit.getMaxPlayers());
            if (on(st, "total-joins")) c.put("total_joins", counters.get("joins"));
            if (on(st, "total-quits")) c.put("total_quits", counters.get("quits"));
            if (on(st, "active-players")) c.put("active_players", activity.size());
            if (on(st, "unique-players")) c.put("unique_players", plugin.players().size());
            if (on(st, "new-players")) c.put("new_players", counters.get("new_players"));
            if (on(st, "punishments")) {
                c.put("punish_total", counters.get("punish_total"));
                c.put("punish_bans", counters.get("punish_ban") + counters.get("punish_tempban") + counters.get("punish_ip_ban"));
                c.put("punish_kicks", counters.get("punish_kick"));
                c.put("punish_mutes", counters.get("punish_mute") + counters.get("punish_tempmute"));
                c.put("punish_warns", counters.get("punish_warn"));
            }
            if (on(st, "deaths")) c.put("deaths", counters.get("deaths"));
            if (on(st, "player-kills")) c.put("player_kills", counters.get("player_kills"));
            if (on(st, "commands")) c.put("commands", counters.get("commands"));
            if (on(st, "chat-messages")) c.put("chat_messages", counters.get("chat_messages"));
            if (on(st, "most-active")) c.put("most_active", mostActive());
            if (on(st, "uptime")) c.put("uptime", DurationParser.format(ManagementFactory.getRuntimeMXBean().getUptime()));
            if (on(st, "tps")) { double[] t = Bukkit.getTPS(); if (t.length > 0) c.put("tps", String.format(Locale.ROOT, "%.2f", Math.min(20.0, t[0]))); }
            if (on(st, "mspt")) c.put("mspt", String.format(Locale.ROOT, "%.2f ms", Bukkit.getAverageTickTime()));
            if (on(st, "memory")) {
                Runtime r = Runtime.getRuntime();
                long used = (r.totalMemory() - r.freeMemory()) >> 20, max = r.maxMemory() >> 20;
                c.put("memory", used + " / " + max + " MB");
            }
            if (on(st, "cpu")) {
                var os = ManagementFactory.getOperatingSystemMXBean();
                if (os instanceof com.sun.management.OperatingSystemMXBean x && x.getProcessCpuLoad() >= 0)
                    c.put("cpu", String.format(Locale.ROOT, "%.1f%%", x.getProcessCpuLoad() * 100));
            }
            if (on(st, "java-version")) c.put("java_version", System.getProperty("java.version"));
            if (on(st, "plugins")) c.put("plugins_count", Bukkit.getPluginManager().getPlugins().length);
            if (on(st, "worlds")) c.put("worlds_count", Bukkit.getWorlds().size());
            plugin.logs().submit(c);
            if (cfg.getBoolean("reset-activity-after-report", true)) { activity.clear(); activityNames.clear(); }
        } catch (Throwable t) {
            plugin.log().warn("Server statistics failed", t);
        }
    }

    private String mostActive() {
        List<Map.Entry<UUID, AtomicInteger>> e = new ArrayList<>(activity.entrySet());
        e.sort((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, e.size()); i++)
            sb.append(i + 1).append(". ").append(Text.sanitizeUserText(activityNames.getOrDefault(e.get(i).getKey(), "Unknown")))
                    .append(" — ").append(e.get(i).getValue().get()).append(" actions\n");
        return sb.length() == 0 ? null : sb.toString().trim();
    }
}
