package dev.ultras.discordlogs.service;

import dev.ultras.discordlogs.util.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemDescriber {
    public record Info(String name, String material, String amount, String enchants, String customName, String meta) {}

    private ItemDescriber() {}

    public static Info describe(ItemStack it) {
        if (it == null) return new Info("Unknown", "Unknown", "0", "None", "None", "None");
        ItemMeta m = it.getItemMeta();
        String custom = "None";
        if (m != null && m.hasDisplayName() && m.displayName() != null)
            custom = PlainTextComponentSerializer.plainText().serialize(m.displayName());
        List<String> ench = new ArrayList<>();
        addEnchants(ench, it.getEnchantments());
        if (m instanceof EnchantmentStorageMeta esm) addEnchants(ench, esm.getStoredEnchants());
        List<String> meta = new ArrayList<>();
        if (m != null) {
            if (m.hasLore() && m.lore() != null) meta.add("Lore: " + m.lore().size() + " line(s)");
            if (m.isUnbreakable()) meta.add("Unbreakable");
            if (m instanceof Damageable d && d.hasDamage()) meta.add("Damage: " + d.getDamage());
            if (!m.getItemFlags().isEmpty()) meta.add("Flags: " + m.getItemFlags().size());
        }
        String name = "None".equals(custom) ? Text.pretty(it.getType().name()) : custom;
        return new Info(name, it.getType().name(), String.valueOf(it.getAmount()),
                ench.isEmpty() ? "None" : String.join(", ", ench), custom, meta.isEmpty() ? "None" : String.join("; ", meta));
    }

    private static void addEnchants(List<String> out, Map<Enchantment, Integer> map) {
        for (Map.Entry<Enchantment, Integer> e : map.entrySet()) {
            NamespacedKey k = Registry.ENCHANTMENT.getKey(e.getKey());
            out.add((k == null ? "unknown" : k.getKey()) + " " + e.getValue());
        }
    }

    public static String brief(ItemStack it) {
        if (it == null || it.getType().isAir()) return "None";
        Info i = describe(it);
        return i.name() + (i.enchants().equals("None") ? "" : " [" + i.enchants() + "]");
    }
}
