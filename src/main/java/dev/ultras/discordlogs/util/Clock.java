package dev.ultras.discordlogs.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class Clock {
    private final ZoneId zone;
    private final DateTimeFormatter date;
    private final DateTimeFormatter time;

    public Clock(ZoneId zone, String datePattern, String timePattern) {
        this.zone = zone;
        DateTimeFormatter d;
        DateTimeFormatter t;
        try { d = DateTimeFormatter.ofPattern(datePattern); } catch (Exception e) { d = DateTimeFormatter.ofPattern("yyyy-MM-dd"); }
        try { t = DateTimeFormatter.ofPattern(timePattern); } catch (Exception e) { t = DateTimeFormatter.ofPattern("HH:mm:ss"); }
        this.date = d;
        this.time = t;
    }

    public ZoneId zone() { return zone; }
    public ZonedDateTime at(long ms) { return Instant.ofEpochMilli(ms).atZone(zone); }
    public String date(long ms) { return date.format(at(ms)); }
    public String time(long ms) { return time.format(at(ms)); }
    public String compactDate(long ms) { return DateTimeFormatter.ofPattern("yyyyMMdd").format(at(ms)); }
    public String isoTimestamp(long ms) { return Instant.ofEpochMilli(ms).toString(); }
}
