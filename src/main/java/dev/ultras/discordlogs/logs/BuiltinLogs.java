package dev.ultras.discordlogs.logs;

import org.bukkit.Material;

import static dev.ultras.discordlogs.logs.LogCategory.*;

/** Registers every built-in log. Third-party plugins use the same LogRegistry through the API. */
public final class BuiltinLogs {
    private BuiltinLogs() {}

    private static LogDefinition.Builder b(String id, LogCategory c, String file, Material icon, String conn, String color) {
        return LogDefinition.builder(id, c).file(file).icon(icon).connection(conn).color(color);
    }

    public static void registerAll(LogRegistry r) {
        // players
        r.register(b("join", PLAYERS, "players", Material.LIME_DYE, "join", "#57F287").sample("session_type", "Returning").build());
        r.register(b("quit", PLAYERS, "players", Material.GRAY_DYE, "quit", "#99AAB5").sample("session_duration", "1h 23m").sample("reason", "DISCONNECTED").build());
        r.register(b("first-join", PLAYERS, "players", Material.EMERALD, "players", "#57F287").sample("first_join_date", "2026-09-30").sample("first_join_time", "12:00:00").build());
        r.register(b("player-stats", PLAYERS, "players", Material.PAPER, "players", "#5865F2").sample("stats_play_time", "12h 4m").sample("stats_blocks_broken", "12.4K").sample("stats_deaths", "7").build());
        // combat
        r.register(b("player-kill", COMBAT, "deaths", Material.IRON_SWORD, "deaths", "#FF5555").sample("victim_player", "Victim").sample("killer_player", "Killer").sample("weapon", "Diamond Sword").sample("damage_cause", "ENTITY_ATTACK").build());
        r.register(b("death", COMBAT, "deaths", Material.SKELETON_SKULL, "deaths", "#AA0000").sample("death_cause", "FALL").sample("killer_entity", "Unknown").sample("weapon", "Unknown").sample("death_message", "Player fell from a high place").build());
        // gameplay
        r.register(b("creative-item", GAMEPLAY, "gamemode", Material.DIAMOND, "gamemode", "#FF5555").sample("item", "Diamond Sword").sample("material", "DIAMOND_SWORD").sample("amount", "1").sample("enchantments", "sharpness V").sample("custom_name", "None").sample("item_meta", "None").build());
        r.register(b("gamemode", GAMEPLAY, "gamemode", Material.GOLDEN_APPLE, "gamemode", "#FF5555").sample("old_gamemode", "Survival").sample("new_gamemode", "Creative").sample("source", "Command").sample("command", "/gamemode creative").build());
        r.register(b("teleport", GAMEPLAY, "teleport", Material.ENDER_PEARL, "teleport", "#00E5FF").sample("teleport_type", "COMMAND").sample("from", "world 0, 64, 0").sample("to", "world 100, 70, 100").sample("command", "/tp 100 70 100").build());
        r.register(b("advancement", GAMEPLAY, "gameplay", Material.KNOWLEDGE_BOOK, "gameplay", "#FEE75C").sample("advancement", "Stone Age").sample("advancement_description", "Mine stone with your new pickaxe").sample("advancement_category", "story").build());
        r.register(b("vanish", GAMEPLAY, "gameplay", Material.ENDER_EYE, "staff", "#AA55FF").sample("action", "Enter Vanish").sample("previous_state", "Visible").sample("new_state", "Vanished").build());
        r.register(b("sign-change", GAMEPLAY, "gameplay", Material.OAK_SIGN, "gameplay", "#99AAB5").sample("sign_text", "Line 1 | Line 2").sample("side", "FRONT").build());
        r.register(b("book-edit", GAMEPLAY, "gameplay", Material.WRITABLE_BOOK, "gameplay", "#99AAB5").sample("book_title", "My Book").sample("pages", "3").sample("signed", "No").build());
        r.register(b("economy-transaction", GAMEPLAY, "gameplay", Material.GOLD_INGOT, "gameplay", "#FEE75C").sample("old_balance", "100.0").sample("new_balance", "250.0").sample("cause", "PAY").build());
        r.register(b("item-drop", GAMEPLAY, "gameplay", Material.DROPPER, "gameplay", "#99AAB5").heavy(true).sample("item", "Cobblestone").sample("amount", "64").build());
        r.register(b("item-pickup", GAMEPLAY, "gameplay", Material.HOPPER, "gameplay", "#99AAB5").heavy(true).sample("item", "Cobblestone").sample("amount", "64").build());
        r.register(b("container-open", GAMEPLAY, "gameplay", Material.CHEST, "gameplay", "#99AAB5").heavy(true).sample("container", "CHEST").sample("container_location", "world 10, 64, 10").build());
        r.register(b("block-break", GAMEPLAY, "gameplay", Material.IRON_PICKAXE, "gameplay", "#99AAB5").heavy(true).sample("block", "Stone").sample("block_location", "world 10, 64, 10").build());
        r.register(b("block-place", GAMEPLAY, "gameplay", Material.BRICKS, "gameplay", "#99AAB5").heavy(true).sample("block", "Bricks").sample("block_location", "world 10, 64, 10").build());
        // punishments
        r.register(b("punishments", PUNISHMENTS, "punishments", Material.BARRIER, "punishments", "#FF5555").sample("punishment_type", "BAN").sample("punishment_emoji", "🔨").sample("reason", "Hacking").sample("duration", "Permanent").sample("confirmation", "Confirmed").build());
        r.register(b("freeze", PUNISHMENTS, "punishments", Material.PACKED_ICE, "staff", "#55AAFF").sample("action", "Freeze").sample("reason", "Screenshare").sample("command", "/freeze Target").build());
        // chat
        r.register(b("chat", CHAT, "chat", Material.PAPER, "chat", "#99AAB5").sample("message", "Hello everyone!").build());
        r.register(b("commands", CHAT, "commands", Material.COMMAND_BLOCK, "commands", "#FEE75C").sample("command", "/home").sample("result", "Unknown").sample("plugin", "Essentials").build());
        // security
        r.register(b("ip-mismatch", SECURITY, "security", Material.TRIPWIRE_HOOK, "security", "#FF9900").sample("current_ip", "203.0.113.5").sample("previous_ip", "198.51.100.7").sample("first_seen_ip", "198.51.100.7").sample("linked_accounts", "0").sample("last_login", "2026-09-29 20:00:00").build());
        r.register(b("op-change", SECURITY, "security", Material.BEACON, "security", "#FF9900").sample("action", "OP").sample("confirmation", "Confirmed").build());
        r.register(b("whitelist-change", SECURITY, "security", Material.NAME_TAG, "security", "#FF9900").sample("action", "Whitelist Add").sample("confirmation", "Confirmed").build());
        r.register(b("permission-change", SECURITY, "security", Material.BOOKSHELF, "security", "#FF9900").sample("target_type", "User").sample("added", "+essentials.fly").sample("removed", "-essentials.god").sample("source", "LuckPerms").build());
        // statistics + server
        r.register(b("server-top", STATISTICS, "statistics", Material.DIAMOND_BLOCK, "statistics", "#5865F2").sample("top_kills", "1. Alice — 120\n2. Bob — 88\n3. Carol — 41").sample("top_playtime", "1. Alice — 120h\n2. Bob — 88h\n3. Carol — 41h").build());
        r.register(b("server-stats", STATISTICS, "statistics", Material.CLOCK, "statistics", "#5865F2").sample("online", "12").sample("tps", "20.0").sample("uptime", "3d 4h").build());
        r.register(b("server-start", STATISTICS, "server", Material.LIME_CONCRETE, "server", "#57F287").sample("action", "Server started").build());
        r.register(b("server-stop", STATISTICS, "server", Material.RED_CONCRETE, "server", "#AA0000").sample("action", "Server stopping").build());
        r.register(b("server-reload", STATISTICS, "server", Material.REDSTONE_TORCH, "server", "#FEE75C").sample("action", "Server reloaded").build());
        r.register(b("world-load", STATISTICS, "server", Material.MAP, "server", "#99AAB5").sample("world_name", "world_nether").sample("environment", "NETHER").build());
        r.register(b("world-unload", STATISTICS, "server", Material.FILLED_MAP, "server", "#99AAB5").sample("world_name", "world_nether").sample("environment", "NETHER").build());
        r.register(b("gamerule", STATISTICS, "server", Material.COMPARATOR, "server", "#FEE75C").sample("gamerule", "keepInventory").sample("value", "true").build());
        r.register(b("weather", STATISTICS, "server", Material.WATER_BUCKET, "server", "#55AAFF").heavy(true).sample("weather", "Rain started").build());
        r.register(b("time-change", STATISTICS, "server", Material.CLOCK, "server", "#55AAFF").heavy(true).sample("skip_reason", "COMMAND").sample("skip_amount", "1000").build());
    }
}
