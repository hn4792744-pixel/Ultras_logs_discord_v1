package dev.ultras.discordlogs.storage;

import dev.ultras.discordlogs.config.Settings;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Storage abstraction. The log engine only talks to this interface, so SQLite / MySQL / PostgreSQL backends can be
 * added later without touching the engine. All methods are called from the single storage thread.
 */
public interface StorageBackend {
    void init() throws IOException;

    void appendRecord(LogRecord record, Settings settings) throws IOException;

    /** Removes local log files for the given file keys (backing them up first when configured). @return files handled. */
    int deleteRecords(Collection<String> fileKeys, Settings settings) throws IOException;

    Map<UUID, PlayerData> loadPlayers() throws IOException;

    void savePlayers(Collection<PlayerData> players) throws IOException;

    Map<String, Long> loadCounters() throws IOException;

    void saveCounters(Map<String, Long> counters) throws IOException;
}
