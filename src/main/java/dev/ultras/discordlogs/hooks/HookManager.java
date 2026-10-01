package dev.ultras.discordlogs.hooks;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.util.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Optional integrations. Nothing here is required; every lookup degrades to "Unknown". */
public final class HookManager {
    public record RankInfo(String group, String prefix) {}

    private static final String[] VANISH_PLUGINS = {"SuperVanish", "PremiumVanish", "Essentials", "EssentialsX", "VanishNoPacket", "CMI"};
    private final UltrasDiscordLogs plugin;
    private boolean luckPerms, vault, vanishPlugin, floodgate, geyser;
    private Object vaultPerm, vaultChat;
    private LuckPermsHook luckPermsHook;

    public HookManager(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    public void init() {
        var pm = Bukkit.getPluginManager();
        luckPerms = plugin.settings().hookLuckPerms && pm.isPluginEnabled("LuckPerms");
        if (luckPerms) {
            try { luckPermsHook = new LuckPermsHook(plugin); luckPermsHook.registerEvents(); }
            catch (Throwable t) { luckPerms = false; plugin.log().warn("LuckPerms hook could not start", t); }
        }
        vault = plugin.settings().hookVault && pm.isPluginEnabled("Vault");
        if (vault) resolveVault();
        vanishPlugin = false;
        for (String n : VANISH_PLUGINS) if (pm.isPluginEnabled(n)) vanishPlugin = true;
        floodgate = pm.isPluginEnabled("floodgate");
        geyser = pm.isPluginEnabled("Geyser-Spigot");
        IntegrationEvents.registerAll(plugin);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void resolveVault() {
        try {
            var sm = Bukkit.getServicesManager();
            var p = sm.getRegistration((Class) Class.forName("net.milkbowl.vault.permission.Permission"));
            if (p != null) vaultPerm = p.getProvider();
            var c = sm.getRegistration((Class) Class.forName("net.milkbowl.vault.chat.Chat"));
            if (c != null) vaultChat = c.getProvider();
        } catch (Throwable t) {
            vault = false;
        }
    }

    public void shutdown() {
        if (luckPermsHook != null) luckPermsHook.unregister();
    }

    public RankInfo rank(Player p) {
        try {
            if (luckPerms && luckPermsHook != null) {
                RankInfo r = luckPermsHook.rank(p);
                if (r != null) return r;
            }
            if (vault) {
                Object g = vaultPerm == null ? null : Reflect.call(vaultPerm, "getPrimaryGroup", p);
                Object pre = vaultChat == null ? null : Reflect.call(vaultChat, "getPlayerPrefix", p);
                if (g != null) return new RankInfo(String.valueOf(g), pre == null ? "" : String.valueOf(pre));
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return new RankInfo("Unknown", "");
    }

    /** @return TRUE/FALSE when a vanish plugin is active, otherwise null (Unknown) unless configured otherwise. */
    public Boolean isVanished(Player p) {
        if (!vanishPlugin) return plugin.settings().vanishAssumeNo ? Boolean.FALSE : null;
        for (MetadataValue v : p.getMetadata("vanished")) if (v.asBoolean()) return true;
        return false;
    }

    public String platform(Player p) {
        UUID id = p.getUniqueId();
        try {
            if (floodgate) {
                Object api = Reflect.callStatic("org.geysermc.floodgate.api.FloodgateApi", "getInstance");
                Object r = Reflect.call(api, "isFloodgatePlayer", id);
                if (r instanceof Boolean b) return b ? "Bedrock" : "Java";
            }
            if (geyser) {
                Object api = Reflect.callStatic("org.geysermc.geyser.api.GeyserApi", "api");
                Object r = Reflect.call(api, "isBedrockPlayer", id);
                if (r instanceof Boolean b) return b ? "Bedrock" : "Java";
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return "Java";
    }

    public List<String> active() {
        List<String> l = new ArrayList<>();
        if (luckPerms) l.add("LuckPerms");
        if (vault) l.add("Vault");
        if (vanishPlugin) l.add("Vanish plugin");
        if (floodgate) l.add("Floodgate");
        if (geyser) l.add("Geyser");
        return l;
    }
}
