package dev.ultras.discordlogs.listener;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.PlayerSnapshot;
import dev.ultras.discordlogs.service.CommandTracker;
import dev.ultras.discordlogs.service.ItemDescriber;
import dev.ultras.discordlogs.util.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Set;

public final class CombatListener implements Listener {
    private static final Set<String> KILL_LABELS = Set.of("kill", "suicide", "execute", "ekill", "esuicide");
    private final UltrasDiscordLogs plugin;

    public CombatListener(UltrasDiscordLogs plugin) { this.plugin = plugin; }

    private static String entityName(Entity e) {
        if (e == null) return "Unknown";
        if (e.customName() != null) return PlainTextComponentSerializer.plainText().serialize(e.customName());
        return Text.pretty(e.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        EntityDamageEvent last = victim.getLastDamageCause();
        Player killer = victim.getKiller();
        String cause = last == null ? "UNKNOWN" : last.getCause().name();
        String message = e.deathMessage() == null ? null : PlainTextComponentSerializer.plainText().serialize(e.deathMessage());
        plugin.counters().inc("deaths");
        PlayerSnapshot vs = PlayerSnapshot.capture(plugin, victim);
        CommandTracker.Rec kill = "KILL".equals(cause) ? plugin.commands().find(KILL_LABELS, victim.getName(), victim.getUniqueId(), 2500) : null;

        if (killer != null && !killer.equals(victim)) {
            plugin.counters().inc("player_kills");
            PlayerSnapshot ks = PlayerSnapshot.capture(plugin, killer);
            String weapon = "Unknown";
            String enchants = "Unknown";
            if (last instanceof EntityDamageByEntityEvent ede) {
                if (ede.getDamager() instanceof Player) {
                    weapon = ItemDescriber.brief(killer.getInventory().getItemInMainHand());
                    enchants = ItemDescriber.describe(killer.getInventory().getItemInMainHand()).enchants();
                } else if (ede.getDamager() instanceof Trident t) {
                    weapon = ItemDescriber.brief(t.getItemStack());
                    enchants = ItemDescriber.describe(t.getItemStack()).enchants();
                } else if (ede.getDamager() instanceof Projectile pr) {
                    weapon = "Projectile: " + Text.pretty(pr.getType().name());
                }
            }
            LogContext c = LogContext.of("player-kill").player("victim_", vs).player("killer_", ks).player(vs)
                    .put("executor", ks.name()).put("target", vs.name())
                    .put("weapon", weapon).put("weapon_enchantments", enchants).put("damage_cause", Text.pretty(cause))
                    .put("command", kill == null ? null : kill.full()).put("death_message", message)
                    .dedupe(vs.uuid() + "|" + ks.uuid());
            plugin.logs().submit(c);
            return;
        }

        String killerEntity = "None";
        String weapon = "None";
        if (last instanceof EntityDamageByEntityEvent ede) {
            Entity src = ede.getDamager();
            if (src instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) src = shooter;
            killerEntity = entityName(src);
            if (src instanceof LivingEntity le && le.getEquipment() != null) {
                var hand = le.getEquipment().getItemInMainHand();
                weapon = hand.getType().isAir() ? "None" : ItemDescriber.brief(hand);
            } else {
                weapon = "Unknown";
            }
        }
        LogContext c = LogContext.of("death").player(vs).put("death_cause", Text.pretty(cause)).put("killer_entity", killerEntity)
                .put("weapon", weapon).put("death_message", message).put("executor", kill == null ? null : kill.executor())
                .put("command", kill == null ? null : kill.full()).dedupe(vs.uuid());
        plugin.logs().submit(c);
    }
}
