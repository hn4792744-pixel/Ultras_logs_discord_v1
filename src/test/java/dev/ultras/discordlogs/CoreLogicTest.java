package dev.ultras.discordlogs;

import dev.ultras.discordlogs.discord.embed.EmbedData;
import dev.ultras.discordlogs.discord.embed.EmbedField;
import dev.ultras.discordlogs.discord.embed.EmbedJson;
import dev.ultras.discordlogs.discord.embed.EmbedSplitter;
import dev.ultras.discordlogs.placeholder.PlaceholderEngine;
import dev.ultras.discordlogs.storage.LogIdGenerator;
import dev.ultras.discordlogs.util.Clock;
import dev.ultras.discordlogs.util.Colors;
import dev.ultras.discordlogs.util.DurationParser;
import dev.ultras.discordlogs.util.SecretRedactor;
import dev.ultras.discordlogs.util.Text;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class CoreLogicTest {
    @Test
    void placeholdersResolveAndReportMissing() {
        var r = PlaceholderEngine.apply("%player% in %world% %nothing%", Map.of("player", "Steve", "world", "nether"));
        assertEquals("Steve in nether Unknown", r.text());
        assertEquals(2, r.resolved());
        assertEquals(1, r.missing());
    }

    @Test
    void placeholderValuesAreNeverRescanned() {
        var r = PlaceholderEngine.apply("%message%", Map.of("message", "%player%", "player", "X"));
        assertEquals("%player%", r.text());
    }

    @Test
    void logIdsAreUniqueAcrossThreads() throws Exception {
        LogIdGenerator g = new LogIdGenerator(new Clock(ZoneId.of("UTC"), "yyyy-MM-dd", "HH:mm:ss"), null);
        Set<String> ids = ConcurrentHashMap.newKeySet();
        var pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(8);
        for (int t = 0; t < 8; t++) pool.execute(() -> { for (int i = 0; i < 2000; i++) ids.add(g.next()); done.countDown(); });
        done.await();
        pool.shutdown();
        assertEquals(16000, ids.size());
        assertTrue(ids.iterator().next().matches("ULTRAS-\\d{8}-\\d{6}"));
    }

    @Test
    void secretsAreRedacted() {
        String url = "https://discord.com/api/webhooks/123456789012345678/abcDEF_ghiJKL-mnoPQR1234567890";
        assertFalse(SecretRedactor.redact("failed " + url).contains("abcDEF"));
        SecretRedactor.register("my-super-secret-value");
        assertEquals("x [REDACTED] y", SecretRedactor.redact("x my-super-secret-value y"));
    }

    @Test
    void embedSplitterRespectsDiscordLimits() {
        EmbedData e = new EmbedData();
        e.title = "T".repeat(500);
        e.description = "D".repeat(5000);
        for (int i = 0; i < 60; i++) e.fields.add(new EmbedField("n" + i, "v".repeat(900), true));
        List<EmbedData> parts = EmbedSplitter.split(e);
        assertTrue(parts.size() > 1);
        for (EmbedData p : parts) {
            assertTrue(p.fields.size() <= 25);
            assertTrue(p.size() <= 6000);
            assertTrue(p.title.length() <= 256);
        }
        for (List<EmbedData> g : EmbedSplitter.group(parts)) {
            assertTrue(g.size() <= 10);
            assertTrue(g.stream().mapToInt(EmbedData::size).sum() <= 6000);
        }
    }

    @Test
    void emptyEmbedGetsPlaceholderText() {
        assertFalse(EmbedSplitter.split(new EmbedData()).get(0).description.isEmpty());
    }

    @Test
    void embedJsonOnlyAcceptsHttpUrls() {
        EmbedData e = new EmbedData();
        e.title = "x";
        e.thumbnail = "javascript:alert(1)";
        assertFalse(EmbedJson.toJson(e).has("thumbnail"));
        e.thumbnail = "https://example.com/a.png";
        assertTrue(EmbedJson.toJson(e).has("thumbnail"));
    }

    @Test
    void durationsAndColors() {
        assertEquals(60_000, DurationParser.parseMillis("1m"));
        assertEquals(30_000, DurationParser.parseMillis("30"));
        assertEquals(-1, DurationParser.parseMillis("abc"));
        assertEquals("1h 1m 1s", DurationParser.format(3_661_000));
        assertEquals(0xFF5555, Colors.parse("#FF5555"));
        assertEquals(-1, Colors.parse("#GG0000"));
        assertTrue(Colors.parse("dark_red") > 0);
    }

    @Test
    void textHelpers() {
        assertEquals("&c&l&lA &8&l|", Text.bold("&c&lA &8|").replace("&l&l&l", "&l&l"));
        assertEquals("ʜᴇʟʟᴏ %player%", Text.smallCaps("Hello %player%"));
        assertEquals("@\u200beveryone", Text.sanitizeUserText("@everyone"));
        Set<String> s = new HashSet<>(List.of(Text.pretty("ENDER_PEARL")));
        assertTrue(s.contains("Ender Pearl"));
    }
}
