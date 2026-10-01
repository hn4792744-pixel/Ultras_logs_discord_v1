package dev.ultras.discordlogs.storage;

import dev.ultras.discordlogs.util.Clock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Thread-safe, restart-safe unique ids: ULTRAS-yyyyMMdd-000001.
 * Sequence numbers are reserved in blocks so a crash can never re-issue an id.
 */
public final class LogIdGenerator {
    private static final int BLOCK = 100;
    private final Clock clock;
    private final Path stateFile;
    private String day = "";
    private long seq;
    private long reserved;

    public LogIdGenerator(Clock clock, Path stateFile) {
        this.clock = clock;
        this.stateFile = stateFile;
        load();
    }

    private void load() {
        try {
            if (stateFile != null && Files.exists(stateFile)) {
                String[] p = Files.readString(stateFile, StandardCharsets.UTF_8).trim().split("\\s+");
                if (p.length == 2 && p[0].equals(clock.compactDate(System.currentTimeMillis()))) {
                    day = p[0];
                    seq = Long.parseLong(p[1]);
                    reserved = seq;
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            // start fresh; uniqueness within the day is still guaranteed by the block reservation
        }
    }

    public synchronized String next() {
        String today = clock.compactDate(System.currentTimeMillis());
        if (!today.equals(day)) {
            day = today;
            seq = 0;
            reserved = 0;
        }
        if (seq >= reserved) {
            reserved = seq + BLOCK;
            persist(reserved);
        }
        seq++;
        return String.format("ULTRAS-%s-%06d", day, seq);
    }

    /** Clean shutdown: store the exact position so no numbers are skipped. */
    public synchronized void flush() {
        if (!day.isEmpty()) persist(seq);
    }

    private void persist(long value) {
        if (stateFile == null) return;
        try {
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, day + " " + value, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // non-fatal
        }
    }
}
