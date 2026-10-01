package dev.ultras.discordlogs.gui;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.discord.connection.DiscordConnection;
import dev.ultras.discordlogs.logs.LogCategory;
import dev.ultras.discordlogs.logs.LogDefinition;
import dev.ultras.discordlogs.config.LogSettings;
import dev.ultras.discordlogs.message.MessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** All menus. Every click is re-checked for permission server-side; menus are plain inventories with a holder. */
public final class GuiManager implements Listener {
    private static final int[] LOG_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int[] MAIN_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    private enum Type { MAIN, LOGS, SETTINGS, CONNECTIONS, LANGUAGE, CONFIRM }

    private static final class Holder implements InventoryHolder {
        final Type type;
        final LogCategory category;
        final int page;
        Inventory inv;
        final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();

        Holder(Type t, LogCategory c, int page) { type = t; category = c; this.page = page; }

        @Override public Inventory getInventory() { return inv; }
    }

    private final UltrasDiscordLogs plugin;

    public GuiManager(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------------------------------ helpers

    private String lang(Player p) { return plugin.messages().lang(p); }

    private Component t(Player p, String key, Map<String, String> v) { return plugin.messages().gui(lang(p), key, v); }

    private Component t(Player p, String key) { return t(p, key, Map.of()); }

    private ItemStack item(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        if (lore != null && !lore.isEmpty()) meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private List<Component> lines(Player p, String key, Map<String, String> v) {
        String raw = plugin.messages().raw(lang(p), key);
        List<Component> out = new ArrayList<>();
        if (raw == null) return out;
        for (String line : raw.split("\n")) out.add(MessageService.legacy(dev.ultras.discordlogs.placeholder.PlaceholderEngine.applyLoose(line, v))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        return out;
    }

    private Holder create(Player p, Type type, LogCategory cat, int page, int rows, String titleKey, Map<String, String> v) {
        Holder h = new Holder(type, cat, page);
        h.inv = Bukkit.createInventory(h, rows * 9, t(p, titleKey, v));
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), null);
        for (int i = 0; i < rows * 9; i++) h.inv.setItem(i, pane);
        return h;
    }

    private void set(Holder h, int slot, ItemStack it, BiConsumer<Player, ClickType> action) {
        h.inv.setItem(slot, it);
        if (action != null) h.actions.put(slot, action);
    }

    private void show(Player p, Holder h) {
        p.openInventory(h.inv);
    }

    private void closeButton(Player p, Holder h, int slot) {
        set(h, slot, item(Material.BARRIER, t(p, "gui.close"), null), (pl, c) -> { plugin.sounds().play(pl, "click"); pl.closeInventory(); });
    }

    private void backButton(Player p, Holder h, int slot, Runnable r) {
        set(h, slot, item(Material.ARROW, t(p, "gui.back"), null), (pl, c) -> { plugin.sounds().play(pl, "click"); r.run(); });
    }

    private boolean can(Player p, String perm) {
        if (p.hasPermission(perm) || p.hasPermission("ultras.discordlogs.admin")) return true;
        plugin.messages().send(p, "errors.no-permission");
        plugin.sounds().play(p, "error");
        return false;
    }

    // ------------------------------------------------------------------------------------------ main

    public void openMain(Player p) {
        if (!plugin.settings().guiEnabled) { plugin.messages().send(p, "system.gui-disabled"); return; }
        if (!can(p, "ultras.discordlogs.use")) return;
        Holder h = create(p, Type.MAIN, null, 0, 6, "gui.main.title", Map.of());
        LogCategory[] cats = {null, LogCategory.PUNISHMENTS, LogCategory.GAMEPLAY, LogCategory.PLAYERS, LogCategory.COMBAT, LogCategory.CHAT, LogCategory.SECURITY, LogCategory.STATISTICS};
        Material[] icons = {Material.WRITABLE_BOOK, Material.BARRIER, Material.GRASS_BLOCK, Material.PLAYER_HEAD, Material.IRON_SWORD, Material.OAK_SIGN, Material.SHIELD, Material.BOOK};
        for (int i = 0; i < cats.length; i++) {
            LogCategory c = cats[i];
            String key = c == null ? "all" : c.key();
            List<LogDefinition> defs = c == null ? plugin.registry().all() : plugin.registry().byCategory(c);
            long enabled = defs.stream().filter(d -> plugin.logs().isEnabled(d.id())).count();
            Map<String, String> v = MessageService.vars("enabled", String.valueOf(enabled), "total", String.valueOf(defs.size()));
            set(h, MAIN_SLOTS[i], item(icons[i], t(p, "gui.category." + key + ".name"), lines(p, "gui.category." + key + ".lore", v)),
                    (pl, ck) -> { plugin.sounds().play(pl, "click"); openLogs(pl, c, 0); });
        }
        set(h, MAIN_SLOTS[8], item(Material.COMPARATOR, t(p, "gui.category.settings.name"), lines(p, "gui.category.settings.lore", Map.of())),
                (pl, ck) -> { plugin.sounds().play(pl, "click"); openSettings(pl); });
        set(h, MAIN_SLOTS[9], item(Material.COMPASS, t(p, "gui.category.language.name"), lines(p, "gui.category.language.lore", Map.of())),
                (pl, ck) -> { plugin.sounds().play(pl, "click"); openLanguage(pl); });
        Map<String, String> info = MessageService.vars("version", plugin.getPluginMeta().getVersion(), "queue", String.valueOf(plugin.queue().pending()),
                "sent", String.valueOf(plugin.queue().sent()), "failed", String.valueOf(plugin.queue().failed()), "hooks", String.join(", ", plugin.hooks().active()));
        set(h, 40, item(Material.NETHER_STAR, t(p, "gui.main.info.name"), lines(p, "gui.main.info.lore", info)), null);
        closeButton(p, h, 49);
        show(p, h);
    }

    // ------------------------------------------------------------------------------------------ logs

    public void openLogs(Player p, LogCategory cat, int page) {
        if (!can(p, "ultras.discordlogs.use")) return;
        List<LogDefinition> defs = cat == null ? plugin.registry().all() : plugin.registry().byCategory(cat);
        int pages = Math.max(1, (int) Math.ceil(defs.size() / (double) LOG_SLOTS.length));
        int pg = Math.max(0, Math.min(page, pages - 1));
        Holder h = create(p, Type.LOGS, cat, pg, 6, "gui.logs.title", MessageService.vars("page", String.valueOf(pg + 1), "pages", String.valueOf(pages),
                "category", plugin.messages().plain(lang(p), "gui.category." + (cat == null ? "all" : cat.key()) + ".plain", Map.of())));
        int from = pg * LOG_SLOTS.length;
        for (int i = 0; i < LOG_SLOTS.length && from + i < defs.size(); i++) {
            LogDefinition d = defs.get(from + i);
            set(h, LOG_SLOTS[i], logItem(p, d), (pl, click) -> onLogClick(pl, d, click, cat, pg));
        }
        if (pg > 0) set(h, 45, item(Material.ARROW, t(p, "gui.prev"), null), (pl, c) -> { plugin.sounds().play(pl, "click"); openLogs(pl, cat, pg - 1); });
        if (pg < pages - 1) set(h, 53, item(Material.ARROW, t(p, "gui.next"), null), (pl, c) -> { plugin.sounds().play(pl, "click"); openLogs(pl, cat, pg + 1); });
        backButton(p, h, 48, () -> openMain(p));
        closeButton(p, h, 49);
        set(h, 50, item(Material.TNT, t(p, "gui.delete.button"), lines(p, "gui.delete.button-lore", Map.of())),
                (pl, c) -> { if (can(pl, "ultras.discordlogs.delete")) { plugin.sounds().play(pl, "click"); openConfirm(pl, cat); } });
        show(p, h);
    }

    private ItemStack logItem(Player p, LogDefinition d) {
        LogSettings ls = plugin.config().logSettings(d.id());
        DiscordConnection c = plugin.logs().connectionFor(d.id());
        String desc = plugin.messages().raw(lang(p), "log-descriptions." + d.id());
        Map<String, String> v = MessageService.vars("id", d.id(), "status", plugin.messages().plain(lang(p), ls.enabled() ? "gui.status.enabled" : "gui.status.disabled", Map.of()),
                "connection", c == null ? "-" : c.id(), "type", c == null ? "-" : c.type().name(),
                "state", c == null ? "-" : c.status().name(), "count", String.valueOf(plugin.counters().get("log_" + d.id())),
                "description", desc == null ? d.description() : desc, "owner", d.owner());
        List<Component> lore = lines(p, "gui.log.lore", v);
        return item(ls.enabled() ? d.icon() : Material.GRAY_DYE, t(p, ls.enabled() ? "gui.log.name-enabled" : "gui.log.name-disabled", v), lore);
    }

    private void onLogClick(Player p, LogDefinition d, ClickType click, LogCategory cat, int page) {
        if (click.isRightClick()) {
            if (!can(p, "ultras.discordlogs.admin")) return;
            boolean now = !plugin.config().logSettings(d.id()).enabled();
            plugin.config().overrides().set("logs." + d.id() + ".enabled", now);
            plugin.logs().invalidateCache();
            plugin.stats().start();
            plugin.sounds().play(p, "success");
            openLogs(p, cat, page);
        } else {
            plugin.sounds().play(p, "click");
            plugin.tests().run(p, d.id());
        }
    }

    // ------------------------------------------------------------------------------------------ delete

    private void openConfirm(Player p, LogCategory cat) {
        Holder h = create(p, Type.CONFIRM, cat, 0, 3, "gui.delete.title", Map.of());
        String scope = plugin.messages().plain(lang(p), "gui.category." + (cat == null ? "all" : cat.key()) + ".plain", Map.of());
        set(h, 11, item(Material.RED_CONCRETE, t(p, "gui.delete.confirm"), lines(p, "gui.delete.confirm-lore", MessageService.vars("scope", scope))),
                (pl, c) -> {
                    if (!can(pl, "ultras.discordlogs.delete")) return;
                    var keys = cat == null ? plugin.registry().allFileKeys() : plugin.registry().fileKeys(cat);
                    var defs = cat == null ? plugin.registry().all() : plugin.registry().byCategory(cat);
                    pl.closeInventory();
                    plugin.storage().deleteAsync(keys).whenComplete((n, err) -> plugin.runMain(() -> {
                        if (err != null) { plugin.messages().send(pl, "gui.delete.failed"); plugin.sounds().play(pl, "error"); return; }
                        defs.forEach(d -> plugin.counters().reset("log_" + d.id()));
                        plugin.messages().send(pl, "gui.delete.done", "count", String.valueOf(n), "scope", scope);
                        plugin.sounds().play(pl, "success");
                    }));
                });
        set(h, 13, item(Material.PAPER, t(p, "gui.delete.info"), lines(p, "gui.delete.info-lore", MessageService.vars("scope", scope))), null);
        set(h, 15, item(Material.LIME_CONCRETE, t(p, "gui.delete.cancel"), null), (pl, c) -> { plugin.sounds().play(pl, "click"); openLogs(pl, cat, 0); });
        show(p, h);
    }

    // ------------------------------------------------------------------------------------------ settings / connections / language

    public void openSettings(Player p) {
        if (!can(p, "ultras.discordlogs.use")) return;
        Holder h = create(p, Type.SETTINGS, null, 0, 4, "gui.settings.title", Map.of());
        toggle(p, h, 10, Material.NOTE_BLOCK, "gui.settings.sounds", "sounds.enabled", plugin.settings().soundsEnabled);
        toggle(p, h, 11, Material.COMPASS, "gui.settings.log-ips", "security.log-ip-addresses", plugin.settings().logIps);
        toggle(p, h, 12, Material.CHEST, "gui.settings.local-logs", "settings.save-local-logs", plugin.settings().saveLocalLogs);
        set(h, 14, item(Material.TRIPWIRE_HOOK, t(p, "gui.settings.connections"), lines(p, "gui.settings.connections-lore", Map.of())),
                (pl, c) -> { plugin.sounds().play(pl, "click"); openConnections(pl); });
        set(h, 15, item(Material.REDSTONE, t(p, "gui.settings.reload"), lines(p, "gui.settings.reload-lore", Map.of())), (pl, c) -> {
            if (!can(pl, "ultras.discordlogs.reload")) return;
            pl.closeInventory();
            plugin.reloadAndReport(pl);
        });
        backButton(p, h, 31, () -> openMain(p));
        show(p, h);
    }

    private void toggle(Player p, Holder h, int slot, Material icon, String key, String path, boolean value) {
        Map<String, String> v = MessageService.vars("state", plugin.messages().plain(lang(p), value ? "gui.status.enabled" : "gui.status.disabled", Map.of()));
        set(h, slot, item(value ? icon : Material.GRAY_DYE, t(p, key + ".name", v), lines(p, key + ".lore", v)), (pl, c) -> {
            if (!can(pl, "ultras.discordlogs.admin")) return;
            plugin.config().overrides().set(path, !value);
            plugin.reloadQuiet();
            plugin.sounds().play(pl, "success");
            openSettings(pl);
        });
    }

    public void openConnections(Player p) {
        if (!can(p, "ultras.discordlogs.use")) return;
        Holder h = create(p, Type.CONNECTIONS, null, 0, 6, "gui.connections.title", Map.of());
        int i = 0;
        for (DiscordConnection c : plugin.connections().all()) {
            if (i >= LOG_SLOTS.length) break;
            Map<String, String> v = MessageService.vars("id", c.id(), "type", c.type().name(), "state", c.status().name(), "detail", c.statusDetail(),
                    "sent", String.valueOf(c.sent()), "failed", String.valueOf(c.failed()), "pending", String.valueOf(plugin.queue().pending(c)));
            Material m = switch (c.status()) { case VALID, READY, ONLINE -> Material.LIME_DYE; case INVALID, ERROR -> Material.RED_DYE; case DISABLED -> Material.GRAY_DYE; default -> Material.YELLOW_DYE; };
            set(h, LOG_SLOTS[i++], item(m, t(p, "gui.connections.name", v), lines(p, "gui.connections.lore", v)), (pl, click) -> {
                if (!can(pl, "ultras.discordlogs.test")) return;
                plugin.sounds().play(pl, "click");
                plugin.connections().validateAsync(c).whenComplete((r, err) -> plugin.runMain(() -> {
                    plugin.messages().send(pl, r != null && r.success() ? "gui.connections.valid" : "gui.connections.invalid", "id", c.id());
                    if (pl.isOnline()) openConnections(pl);
                }));
            });
        }
        backButton(p, h, 48, () -> openSettings(p));
        closeButton(p, h, 49);
        show(p, h);
    }

    public void openLanguage(Player p) {
        if (!can(p, "ultras.discordlogs.use")) return;
        Holder h = create(p, Type.LANGUAGE, null, 0, 3, "gui.language.title", Map.of());
        String cur = lang(p);
        String[][] langs = {{"en", "English"}, {"ar", "العربية"}};
        int[] slots = {11, 15};
        for (int i = 0; i < langs.length; i++) {
            String code = langs[i][0], name = langs[i][1];
            boolean sel = code.equals(cur);
            ItemStack it = item(sel ? Material.LIME_BANNER : Material.WHITE_BANNER,
                    MessageService.legacy((sel ? "&a&l" : "&f&l") + name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    lines(p, sel ? "gui.language.selected" : "gui.language.select", Map.of()));
            set(h, slots[i], it, (pl, c) -> {
                plugin.players().setLanguage(pl.getUniqueId(), pl.getName(), code);
                plugin.sounds().play(pl, "success");
                plugin.messages().send(pl, "system.language-updated", "language", name);
                openLanguage(pl);
            });
        }
        backButton(p, h, 22, () -> openMain(p));
        show(p, h);
    }

    // ------------------------------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory().getHolder() != h) return;
        BiConsumer<Player, ClickType> a = h.actions.get(e.getSlot());
        if (a == null) return;
        try {
            a.accept(p, e.getClick());
        } catch (Throwable t) {
            plugin.log().warn("GUI action failed", t);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
