package dev.ultras.discordlogs.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Changes made from the GUI are stored here so the admin's commented YAML files are never rewritten. */
public final class RuntimeOverrides {
    private final File file;
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    public RuntimeOverrides(File dataFolder) {
        this.file = new File(new File(dataFolder, "data"), "gui-overrides.yml");
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            for (String k : y.getKeys(true)) if (!y.isConfigurationSection(k)) values.put(k, y.get(k));
        }
    }

    public Boolean bool(String key) {
        Object o = values.get(key);
        return o instanceof Boolean b ? b : null;
    }

    public boolean bool(String key, boolean def) {
        Boolean b = bool(key);
        return b == null ? def : b;
    }

    public synchronized void set(String key, Object v) {
        values.put(key, v);
        save();
    }

    public synchronized void clear() {
        values.clear();
        save();
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        values.forEach(y::set);
        try {
            file.getParentFile().mkdirs();
            y.save(file);
        } catch (IOException ignored) {
            // non-critical
        }
    }
}
