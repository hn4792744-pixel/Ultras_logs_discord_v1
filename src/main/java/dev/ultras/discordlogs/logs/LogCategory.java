package dev.ultras.discordlogs.logs;

import org.bukkit.Material;

public enum LogCategory {
    PUNISHMENTS(Material.BARRIER),
    GAMEPLAY(Material.GRASS_BLOCK),
    PLAYERS(Material.PLAYER_HEAD),
    COMBAT(Material.IRON_SWORD),
    CHAT(Material.OAK_SIGN),
    SECURITY(Material.SHIELD),
    STATISTICS(Material.BOOK),
    CUSTOM(Material.NETHER_STAR);

    private final Material icon;

    LogCategory(Material icon) { this.icon = icon; }

    public Material icon() { return icon; }

    public String key() { return name().toLowerCase(); }
}
