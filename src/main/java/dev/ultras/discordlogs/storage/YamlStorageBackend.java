package dev.ultras.discordlogs.storage;

import dev.ultras.discordlogs.config.Settings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class YamlStorageBackend implements StorageBackend {
    private final Path root;
    private final Path logsDir;
    private final Path dataDir;

    public YamlStorageBackend(Path pluginFolder) {
        this.root = pluginFolder;
        this.logsDir = pluginFolder.resolve("logs");
        this.dataDir = pluginFolder.resolve("data");
    }

    @Override
    public void init() throws IOException {
        Files.createDirectories(logsDir);
        Files.createDirectories(dataDir);
    }

    static String quote(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }

    static String toYaml(LogRecord r) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("- id: ").append(quote(r.logId())).append('\n');
        sb.append("  type: ").append(quote(r.type())).append('\n');
        sb.append("  player: ").append(quote(r.player())).append('\n');
        sb.append("  uuid: ").append(quote(r.uuid())).append('\n');
        sb.append("  ip: ").append(quote(r.ip())).append('\n');
        sb.append("  date: ").append(quote(r.date())).append('\n');
        sb.append("  time: ").append(quote(r.time())).append('\n');
        sb.append("  world: ").append(quote(r.world())).append('\n');
        sb.append("  coordinates: ").append(quote(r.coordinates())).append('\n');
        sb.append("  executor: ").append(quote(r.executor())).append('\n');
        sb.append("  target: ").append(quote(r.target())).append('\n');
        sb.append("  data:\n");
        if (r.data().isEmpty()) sb.setLength(sb.length() - 1);
        if (r.data().isEmpty()) sb.append(" {}\n");
        for (Map.Entry<String, String> e : r.data().entrySet())
            sb.append("    ").append(e.getKey().replaceAll("[^A-Za-z0-9_]", "_")).append(": ").append(quote(e.getValue())).append('\n');
        return sb.toString();
    }

    @Override
    public void appendRecord(LogRecord r, Settings s) throws IOException {
        Path file = logsDir.resolve(r.fileKey().replaceAll("[^A-Za-z0-9_-]", "_") + ".yml");
        LogRotator.rotateIfNeeded(file, s, r.epochMillis());
        Files.writeString(file, toYaml(r), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public int deleteRecords(Collection<String> fileKeys, Settings s) throws IOException {
        int handled = 0;
        Path backup = root.resolve("backups").resolve("deleted-" + System.currentTimeMillis());
        for (String key : fileKeys) {
            String safe = key.replaceAll("[^A-Za-z0-9_-]", "_");
            List<Path> targets = new ArrayList<>();
            Path active = logsDir.resolve(safe + ".yml");
            if (Files.exists(active)) targets.add(active);
            Path archive = logsDir.resolve("archive");
            if (Files.isDirectory(archive)) {
                try (Stream<Path> st = Files.list(archive)) {
                    st.filter(p -> p.getFileName().toString().startsWith(safe + "-")).forEach(targets::add);
                }
            }
            for (Path p : targets) {
                if (s.backupBeforeDelete) {
                    Files.createDirectories(backup);
                    Files.move(p, backup.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.deleteIfExists(p);
                }
                handled++;
            }
        }
        return handled;
    }

    @Override
    public Map<UUID, PlayerData> loadPlayers() {
        Map<UUID, PlayerData> out = new HashMap<>();
        Path f = dataDir.resolve("players.yml");
        if (!Files.exists(f)) return out;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f.toFile());
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return out;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection p = sec.getConfigurationSection(k);
            if (p == null) continue;
            UUID id;
            try { id = UUID.fromString(k); } catch (IllegalArgumentException e) { continue; }
            PlayerData d = new PlayerData(id, p.getString("name", "Unknown"));
            d.firstJoin = p.getLong("first-join");
            d.lastJoin = p.getLong("last-join");
            d.lastQuit = p.getLong("last-quit");
            d.language = p.getString("language");
            d.firstIp = p.getString("first-ip");
            d.lastIp = p.getString("last-ip");
            d.statsUpdated = p.getLong("stats-updated");
            for (String e : p.getStringList("ips")) {
                String[] a = e.split("\\|");
                if (a.length == 4) {
                    try { d.ips.put(a[0], new IpEntry(Long.parseLong(a[1]), Long.parseLong(a[2]), Integer.parseInt(a[3]))); }
                    catch (NumberFormatException ignored) { /* skip corrupt entry */ }
                }
            }
            ConfigurationSection st = p.getConfigurationSection("stats");
            if (st != null) {
                Map<String, Long> m = new HashMap<>();
                for (String sk : st.getKeys(false)) m.put(sk, st.getLong(sk));
                d.stats = Map.copyOf(m);
            }
            out.put(id, d);
        }
        return out;
    }

    @Override
    public void savePlayers(Collection<PlayerData> players) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        for (PlayerData d : players) {
            String b = "players." + d.uuid + ".";
            y.set(b + "name", d.name);
            y.set(b + "first-join", d.firstJoin);
            y.set(b + "last-join", d.lastJoin);
            y.set(b + "last-quit", d.lastQuit);
            if (d.language != null) y.set(b + "language", d.language);
            if (d.firstIp != null) y.set(b + "first-ip", d.firstIp);
            if (d.lastIp != null) y.set(b + "last-ip", d.lastIp);
            List<String> ips = new ArrayList<>();
            synchronized (d.ips) {
                d.ips.forEach((ip, e) -> ips.add(ip + "|" + e.firstSeen() + "|" + e.lastSeen() + "|" + e.count()));
            }
            if (!ips.isEmpty()) y.set(b + "ips", ips);
            y.set(b + "stats-updated", d.statsUpdated);
            for (Map.Entry<String, Long> e : d.stats.entrySet()) y.set(b + "stats." + e.getKey(), e.getValue());
        }
        atomicSave(y, dataDir.resolve("players.yml"));
    }

    @Override
    public Map<String, Long> loadCounters() {
        Map<String, Long> out = new HashMap<>();
        Path f = dataDir.resolve("counters.yml");
        if (!Files.exists(f)) return out;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f.toFile());
        ConfigurationSection s = y.getConfigurationSection("counters");
        if (s != null) for (String k : s.getKeys(false)) out.put(k, s.getLong(k));
        return out;
    }

    @Override
    public void saveCounters(Map<String, Long> counters) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        counters.forEach((k, v) -> y.set("counters." + k.replace('.', '_'), v));
        atomicSave(y, dataDir.resolve("counters.yml"));
    }

    private void atomicSave(YamlConfiguration y, Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, y.saveToString(), StandardCharsets.UTF_8);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
