package dev.ultras.discordlogs.storage;

import dev.ultras.discordlogs.UltrasDiscordLogs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** All file/database I/O happens on one dedicated background thread; the main thread never touches disk. */
public final class StorageManager {
    private final UltrasDiscordLogs plugin;
    private final StorageBackend backend;
    private final PlayerDataStore players = new PlayerDataStore();
    private final CounterStore counters = new CounterStore();
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(20_000), r -> {
        Thread t = new Thread(r, "ULTRAS-Storage");
        t.setDaemon(true);
        return t;
    });

    public StorageManager(UltrasDiscordLogs plugin, StorageBackend backend) {
        this.plugin = plugin;
        this.backend = backend;
    }

    public PlayerDataStore players() { return players; }
    public CounterStore counters() { return counters; }

    public void init() throws IOException {
        backend.init();
        players.load(backend.loadPlayers());
        counters.load(backend.loadCounters());
    }

    public void appendAsync(LogRecord r) {
        if (!plugin.settings().saveLocalLogs) return;
        try {
            io.execute(() -> {
                try { backend.appendRecord(r, plugin.settings()); }
                catch (Throwable t) { plugin.log().warnLimited("storage-append", 30_000, "Could not write a local log record (" + t.getClass().getSimpleName() + ")."); }
            });
        } catch (RejectedExecutionException e) {
            plugin.log().warnLimited("storage-full", 30_000, "Local log write queue is full; dropping records.");
        }
    }

    public CompletableFuture<Integer> deleteAsync(Collection<String> fileKeys) {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        List<String> keys = new ArrayList<>(fileKeys);
        try {
            io.execute(() -> {
                try { f.complete(backend.deleteRecords(keys, plugin.settings())); }
                catch (Throwable t) { f.completeExceptionally(t); }
            });
        } catch (RejectedExecutionException e) {
            f.completeExceptionally(e);
        }
        return f;
    }

    /** Persists player data and counters (only when something changed). */
    public void saveDataAsync(boolean force) {
        try {
            io.execute(() -> saveNow(force));
        } catch (RejectedExecutionException ignored) {
            // shutting down; shutdown() saves synchronously
        }
    }

    private void saveNow(boolean force) {
        try {
            if (force || players.consumeDirty()) backend.savePlayers(new ArrayList<>(players.all()));
            backend.saveCounters(counters.snapshot());
        } catch (Throwable t) {
            plugin.log().warnLimited("storage-save", 30_000, "Could not save plugin data (" + t.getClass().getSimpleName() + ").");
        }
    }

    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(8, TimeUnit.SECONDS)) io.shutdownNow();
        } catch (InterruptedException e) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
        saveNow(true);
    }
}
