package io.github.manosaba.core.chat;

import io.github.manosaba.core.ManosabaCore;
import io.github.manosaba.core.config.ProximityChatConfig;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Filters {@link AsyncChatEvent#viewers()} so that a chat message is only
 * delivered to players within configured range of the sender, optionally
 * gated by line-of-sight, the datapack-reported "game running" flag, and
 * the per-player "dead" scoreboard.
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

        // Game-state gate: when the datapack reports the game is not running,
        // fall through to vanilla global delivery.
        ProximityChatConfig.GameStateConfig gsc = cfg.gameState();
        if (gsc.onlyDuringGame() && !isGameRunning(gsc)) {
            return;
        }

        Player sender = event.getPlayer();
        if (sender.hasPermission(PERMISSION_BYPASS_SEND)) {
            return;
        }

        Location senderLoc = sender.getLocation();
        World senderWorld = senderLoc.getWorld();

        ProximityChatConfig.DeadStateConfig dsc = cfg.deadState();
        Objective deadObj = dsc.mode() == ProximityChatConfig.DeadStateConfig.Mode.SCOREBOARD
                ? lookupObjective(dsc.objective())
                : null;
        boolean senderDead = isDead(dsc, deadObj, sender);

        Set<Audience> viewers = event.viewers();
        viewers.removeIf(audience ->
                !shouldDeliver(audience, sender, senderLoc, senderWorld, cfg, deadObj, senderDead));
    }

    private boolean shouldDeliver(@NotNull Audience audience,
                                  @NotNull Player sender,
                                  @NotNull Location senderLoc,
                                  @NotNull World senderWorld,
                                  @NotNull ProximityChatConfig cfg,
                                  @Nullable Objective deadObj,
                                  boolean senderDead) {
        if (audience instanceof Player viewer) {
            return shouldDeliverToPlayer(viewer, sender, senderLoc, senderWorld, cfg, deadObj, senderDead);
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
                                          @NotNull ProximityChatConfig cfg,
                                          @Nullable Objective deadObj,
                                          boolean senderDead) {
        if (viewer.getUniqueId().equals(sender.getUniqueId())) {
            return true;
        }
        if (viewer.hasPermission(PERMISSION_BYPASS_RECEIVE)) {
            return true;
        }

        ProximityChatConfig.DeadStateConfig dsc = cfg.deadState();
        boolean viewerDead = isDead(dsc, deadObj, viewer);

        // Dead sender → block delivery to alive players.
        if (dsc.deadSilencedToLiving() && senderDead && !viewerDead) {
            return false;
        }
        // Dead viewer → always hear (bypass world / distance / LoS).
        if (dsc.deadHearAll() && viewerDead) {
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

    /**
     * Reads the datapack's {@code <holder> <objective>} score from the main
     * scoreboard. Bukkit's scoreboard is technically not thread-safe for
     * async access, but this is a read-only path (single map lookup + int
     * read) which is safe enough in practice.
     */
    private static boolean isGameRunning(@NotNull ProximityChatConfig.GameStateConfig gsc) {
        Objective objective = lookupObjective(gsc.objective());
        if (objective == null) {
            return false;
        }
        @SuppressWarnings("deprecation")
        Score score = objective.getScore(gsc.holder());
        return score.isScoreSet() && score.getScore() == gsc.inGameValue();
    }

    private static boolean isDead(@NotNull ProximityChatConfig.DeadStateConfig dsc,
                                  @Nullable Objective deadObj,
                                  @NotNull Player player) {
        return switch (dsc.mode()) {
            case SCOREBOARD -> {
                if (deadObj == null) {
                    yield false;
                }
                @SuppressWarnings("deprecation")
                Score score = deadObj.getScore(player.getName());
                yield score.isScoreSet() && score.getScore() == dsc.deadValue();
            }
            case SPECTATOR -> player.getGameMode() == GameMode.SPECTATOR;
        };
    }

    private static @Nullable Objective lookupObjective(@NotNull String name) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return null;
        }
        Scoreboard board = manager.getMainScoreboard();
        return board.getObjective(name);
    }
}
