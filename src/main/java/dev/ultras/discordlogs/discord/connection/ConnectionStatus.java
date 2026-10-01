package dev.ultras.discordlogs.discord.connection;

/** WEBHOOK uses VALID/INVALID, BOT uses READY/ERROR. ONLINE is reserved for gateway-based bots. */
public enum ConnectionStatus { VALID, INVALID, READY, ONLINE, ERROR, DISABLED, UNKNOWN }
