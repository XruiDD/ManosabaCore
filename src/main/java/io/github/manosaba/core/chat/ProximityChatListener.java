package io.github.manosaba.core.chat;

import io.github.manosaba.core.ManosabaCore;
import io.github.manosaba.core.config.ProximityChatConfig;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Filters {@link AsyncChatEvent#viewers()} so that a chat message is only
 * delivered to players within configured range of the sender, optionally
 * gated by line-of-sight.
 *
 * <p>This listener intentionally does not touch
 * {@link AsyncChatEvent#renderer(net.kyori.adventure.chat.ChatType.Bound)} or
 * {@link AsyncChatEvent#message(net.kyori.adventure.text.Component)}. Other
 * plugins remain free to control message format.</p>
 */
public final class ProximityChatListener implements Listener {

    public static final String PERMISSION_BYPASS_SEND = "manosaba.chat.bypass.send";
    public static final String PERMISSION_BYPASS_RECEIVE = "manosaba.chat.bypass.receive";

    private final ManosabaCore plugin;

    public ProximityChatListener(@NotNull ManosabaCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(@NotNull AsyncChatEvent event) {
        ProximityChatConfig cfg = plugin.chatConfig();
        if (cfg == null || !cfg.enabled()) {
            return;
        }

        Player sender = event.getPlayer();
        if (sender.hasPermission(PERMISSION_BYPASS_SEND)) {
            return;
        }

        Location senderLoc = sender.getLocation();
        World senderWorld = senderLoc.getWorld();

        Set<Audience> viewers = event.viewers();
        viewers.removeIf(audience -> !shouldDeliver(audience, sender, senderLoc, senderWorld, cfg));
    }

    private boolean shouldDeliver(@NotNull Audience audience,
                                  @NotNull Player sender,
                                  @NotNull Location senderLoc,
                                  @NotNull World senderWorld,
                                  @NotNull ProximityChatConfig cfg) {
        if (audience instanceof Player viewer) {
            return shouldDeliverToPlayer(viewer, sender, senderLoc, senderWorld, cfg);
        }
        if (audience instanceof ConsoleCommandSender) {
            return true;
        }
        return true;
    }

    private boolean shouldDeliverToPlayer(@NotNull Player viewer,
                                          @NotNull Player sender,
                                          @NotNull Location senderLoc,
                                          @NotNull World senderWorld,
                                          @NotNull ProximityChatConfig cfg) {
        if (viewer.getUniqueId().equals(sender.getUniqueId())) {
            return true;
        }
        if (viewer.hasPermission(PERMISSION_BYPASS_RECEIVE)) {
            return true;
        }

        boolean senderIsSpectator = sender.getGameMode() == GameMode.SPECTATOR;
        boolean viewerIsSpectator = viewer.getGameMode() == GameMode.SPECTATOR;

        // Spectator sender → block delivery to non-spectators.
        if (cfg.spectatorsSilencedToOthers() && senderIsSpectator && !viewerIsSpectator) {
            return false;
        }
        // Spectator viewer → always hear (bypass world / distance / LoS).
        if (cfg.spectatorsHearAll() && viewerIsSpectator) {
            return true;
        }

        Location viewerLoc = viewer.getLocation();
        if (!senderWorld.getUID().equals(viewerLoc.getWorld().getUID())) {
            return false;
        }

        double dx = senderLoc.getX() - viewerLoc.getX();
        double dy = senderLoc.getY() - viewerLoc.getY();
        double dz = senderLoc.getZ() - viewerLoc.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq > cfg.rangeSquared()) {
            return false;
        }

        if (cfg.requireLineOfSight() && !sender.hasLineOfSight(viewer)) {
            return false;
        }

        return true;
    }
}
