package dev.ultras.discordlogs.storage;

import java.util.Map;

public record LogRecord(String logId, String type, String fileKey, String player, String uuid, String ip, String world,
                        String coordinates, String executor, String target, long epochMillis, String date, String time,
                        Map<String, String> data) {}
