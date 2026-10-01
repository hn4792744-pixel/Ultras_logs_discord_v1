package dev.ultras.discordlogs.hooks;

import dev.ultras.discordlogs.UltrasDiscordLogs;
import dev.ultras.discordlogs.logs.LogContext;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Only loaded when LuckPerms is present (kept out of the main code path). */
final class LuckPermsHook {
    private final UltrasDiscordLogs plugin;
    private final LuckPerms api;
    private EventSubscription<NodeMutateEvent> subscription;

    LuckPermsHook(UltrasDiscordLogs plugin) {
        this.plugin = plugin;
        this.api = LuckPermsProvider.get();
    }

    HookManager.RankInfo rank(Player p) {
        CachedMetaData md = api.getPlayerAdapter(Player.class).getMetaData(p);
        String g = md.getPrimaryGroup();
        String pre = md.getPrefix();
        return new HookManager.RankInfo(g == null ? "Unknown" : g, pre == null ? "" : pre);
    }

    void registerEvents() {
        subscription = api.getEventBus().subscribe(plugin, NodeMutateEvent.class, this::onMutate);
    }

    void unregister() {
        if (subscription != null) subscription.close();
    }

    private void onMutate(NodeMutateEvent e) {
        try {
            Set<Node> added = new HashSet<>(e.getDataAfter());
            added.removeAll(e.getDataBefore());
            Set<Node> removed = new HashSet<>(e.getDataBefore());
            removed.removeAll(e.getDataAfter());
            if (added.isEmpty() && removed.isEmpty()) return;
            String holder = e.isUser() ? ((User) e.getTarget()).getUsername() : ((Group) e.getTarget()).getName();
            LogContext c = LogContext.of("permission-change")
                    .put("target", holder == null ? "Unknown" : holder)
                    .put("target_type", e.isUser() ? "User" : "Group")
                    .put("added", summarize(added))
                    .put("removed", summarize(removed))
                    .put("executor", "Unknown")
                    .put("source", "LuckPerms")
                    .put("uuid", e.isUser() ? ((User) e.getTarget()).getUniqueId().toString() : null)
                    .put("player", holder == null ? "Unknown" : holder);
            plugin.logs().submit(c);
        } catch (Throwable t) {
            plugin.log().warn("LuckPerms permission-change log failed", t);
        }
    }

    private static String summarize(Set<Node> nodes) {
        if (nodes.isEmpty()) return null;
        String s = nodes.stream().limit(10).map(n -> (n.getValue() ? "+" : "-") + n.getKey()).collect(Collectors.joining(", "));
        return nodes.size() > 10 ? s + " … (+" + (nodes.size() - 10) + ")" : s;
    }
}
