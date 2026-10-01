package dev.ultras.discordlogs.discord.queue;

import dev.ultras.discordlogs.discord.embed.EmbedData;

import java.util.List;

/** One Discord message: optional content plus up to 10 embeds. Contains no secrets. */
public record OutboundMessage(String content, List<EmbedData> embeds) {}
