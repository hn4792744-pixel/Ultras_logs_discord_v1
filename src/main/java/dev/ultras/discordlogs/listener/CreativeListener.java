package dev.ultras.discordlogs.listener;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.service.ItemDescriber;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Minecraft does not tell the server whether a creative click came from the creative menu or from moving an item
 * already owned. We therefore only log when the player's inventory gains items of that kind ("gain check").
 */
public final class CreativeListener implements Listener {
    private final UltrasDiscordLogs plugin;

    public CreativeListener(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    private static int count(Player p, ItemStack ref) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (it != null && it.isSimilar(ref)) n += it.getAmount();
        return n;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || p.getGameMode() != GameMode.CREATIVE) return;
        ItemStack cursor = e.getCursor();
        if (cursor.getType().isAir() || !plugin.logs().isEnabled("creative-item")) return;
        boolean gainCheck = plugin.config().logSettings("creative-item").settings().getBoolean("gain-check", true);
        if (gainCheck) {
            ItemStack current = e.getCurrentItem();
            int before = count(p, cursor);
            int after = before - (current != null && current.isSimilar(cursor) ? current.getAmount() : 0) + cursor.getAmount();
            if (e.getSlot() >= 0 && after <= before) return; // moved, not created
            if (e.getSlot() < 0 && before >= cursor.getAmount()) return; // dropped something already owned
        }
        ItemDescriber.Info i = ItemDescriber.describe(cursor);
        PlayerSnapshot snap = PlayerSnapshot.capture(plugin, p);
        plugin.logs().submit(LogContext.of("creative-item").player(snap).put("item", i.name()).put("material", i.material())
                .put("amount", i.amount()).put("enchantments", i.enchants()).put("custom_name", i.customName())
                .put("item_meta", i.meta()).dedupe(snap.uuid() + "|" + i.material() + "|" + i.amount()));
    }
}
