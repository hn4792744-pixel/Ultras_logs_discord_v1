package dev.ultras.discordlogs.listener;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.service.ItemDescriber;
import dev.ultras.discordlogs.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;

import java.util.stream.Collectors;

/** Optional gameplay logs. Heavy ones (drop/pickup/container/block) are disabled by default in logs.yml. */
public final class GameplayListener implements Listener {
    private final UltrasDiscordLogs plugin;

    public GameplayListener(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    private static String at(Location l) {
        return l == null || l.getWorld() == null ? "Unknown" : l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    private void send(String id, Player p, java.util.function.Consumer<LogContext> extra) {
        PlayerSnapshot snap = PlayerSnapshot.capture(plugin, p);
        LogContext c = LogContext.of(id).player(snap);
        extra.accept(c);
        plugin.logs().submit(c);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (!plugin.logs().isEnabled("item-drop")) return;
        var i = ItemDescriber.describe(e.getItemDrop().getItemStack());
        send("item-drop", e.getPlayer(), c -> c.put("item", i.name()).put("material", i.material()).put("amount", i.amount()).put("enchantments", i.enchants()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !plugin.logs().isEnabled("item-pickup")) return;
        var i = ItemDescriber.describe(e.getItem().getItemStack());
        send("item-pickup", p, c -> c.put("item", i.name()).put("material", i.material()).put("amount", i.amount()).put("enchantments", i.enchants()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !plugin.logs().isEnabled("container-open")) return;
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.PLAYER || t == InventoryType.CREATIVE || t == InventoryType.CRAFTING) return;
        Location l = e.getInventory().getLocation();
        send("container-open", p, c -> c.put("container", Text.pretty(t.name())).put("container_location", at(l)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.logs().isEnabled("block-break")) return;
        Block b = e.getBlock();
        send("block-break", e.getPlayer(), c -> c.put("block", Text.pretty(b.getType().name())).put("block_location", at(b.getLocation())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.logs().isEnabled("block-place")) return;
        Block b = e.getBlockPlaced();
        send("block-place", e.getPlayer(), c -> c.put("block", Text.pretty(b.getType().name())).put("block_location", at(b.getLocation())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        if (!plugin.logs().isEnabled("sign-change")) return;
        String text = e.lines().stream().map(l -> l == null ? "" : PlainTextComponentSerializer.plainText().serialize(l)).collect(Collectors.joining(" | "));
        Block b = e.getBlock();
        send("sign-change", e.getPlayer(), c -> c.put("sign_text", Text.truncate(Text.sanitizeUserText(text), 900)).put("side", e.getSide().name()).put("block_location", at(b.getLocation())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent e) {
        if (!plugin.logs().isEnabled("book-edit")) return;
        var meta = e.getNewBookMeta();
        Component title = meta.title();
        send("book-edit", e.getPlayer(), c -> c.put("book_title", title == null ? "None" : Text.sanitizeUserText(PlainTextComponentSerializer.plainText().serialize(title)))
                .put("pages", meta.getPageCount()).put("signed", e.isSigning() ? "Yes" : "No"));
    }
}
