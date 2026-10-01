package dev.ultras.discordlogs.storage;

import dev.ultras.discordlogs.config.Settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/** Daily / monthly / size based rotation with optional gzip, archive folder and retention. */
final class LogRotator {
    private LogRotator() {}

    static void rotateIfNeeded(Path active, Settings s, long nowMs) throws IOException {
        if (!s.rotationEnabled || !Files.exists(active)) return;
        ZonedDateTime modified = java.time.Instant.ofEpochMilli(Files.getLastModifiedTime(active).toMillis()).atZone(s.zone);
        ZonedDateTime now = java.time.Instant.ofEpochMilli(nowMs).atZone(s.zone);
        String stamp = null;
        switch (s.rotationMode) {
            case "DAILY" -> {
                if (modified.toLocalDate().isBefore(LocalDate.from(now)))
                    stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(modified);
            }
            case "MONTHLY" -> {
                if (YearMonth.from(modified).isBefore(YearMonth.from(now)))
                    stamp = DateTimeFormatter.ofPattern("yyyy-MM").format(modified);
            }
            default -> { }
        }
        if (stamp == null && s.rotationMaxSizeMb > 0 && Files.size(active) >= s.rotationMaxSizeMb * 1024L * 1024L)
            stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").format(now);
        if (stamp == null) return;

        String fileName = active.getFileName().toString();
        String base = fileName.substring(0, fileName.length() - ".yml".length());
        Path dir = s.rotationArchive ? active.getParent().resolve("archive") : active.getParent();
        Files.createDirectories(dir);
        Path target = dir.resolve(base + "-" + stamp + ".yml");
        int n = 1;
        while (Files.exists(target) || Files.exists(Path.of(target + ".gz")))
            target = dir.resolve(base + "-" + stamp + "-" + (n++) + ".yml");
        Files.move(active, target, StandardCopyOption.REPLACE_EXISTING);
        if (s.rotationCompress) gzip(target);
        prune(dir, base, s.rotationMaxFiles);
    }

    private static void gzip(Path file) throws IOException {
        Path gz = Path.of(file + ".gz");
        try (InputStream in = Files.newInputStream(file);
             OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            in.transferTo(out);
        }
        Files.delete(file);
    }

    private static void prune(Path dir, String base, int keep) throws IOException {
        try (Stream<Path> st = Files.list(dir)) {
            List<Path> old = st.filter(p -> p.getFileName().toString().startsWith(base + "-"))
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed()).toList();
            for (int i = keep; i < old.size(); i++) Files.deleteIfExists(old.get(i));
        }
    }
}
