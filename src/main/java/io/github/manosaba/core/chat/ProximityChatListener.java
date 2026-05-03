package io.github.manosaba.core.chat;

import io.github.manosaba.core.ManosabaCore;
import io.github.manosaba.core.config.ProximityChatConfig;
import io.github.manosaba.core.game.DeathStatus;
import io.github.manosaba.core.talkbubbles.TalkBubblesBridge;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Objective;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Filters {@link AsyncChatEvent#viewers()} so that a chat message is only
 * delivered to players within configured range of the sender, optionally
 * gated by line-of-sight, the datapack-reported "game running" flag, and
 * the per-player "dead" scoreboard. Also installs a {@link ChatRenderer}
 * that fully owns the chat-line render, building it from a configurable
 * MiniMessage template with three placeholders ({@code <prefix>},
 * {@code <sender>}, {@code <message>}) — the vanilla
 * {@code chat.type.text} translation is not used.
 */
public final class ProximityChatListener implements Listener {

    public static final String PERMISSION_BYPASS_SEND = "manosaba.chat.bypass.send";
    public static final String PERMISSION_BYPASS_RECEIVE = "manosaba.chat.bypass.receive";

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** Which prefix variant to use for a given chat event. */
    private enum Channel {
        /** Sender is alive; viewers are filtered by world / range / line-of-sight / dead rules. */
        ALIVE,
        /** Sender is dead; subject to dead-state rules (silenced to living, etc.). */
        DEAD,
        /** Sender holds {@link #PERMISSION_BYPASS_SEND}; everyone hears, no viewer filtering. */
        GLOBAL,
        /** Game-state gate is on but the game is not running; everyone hears, no viewer filtering. */
        LOBBY
    }

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

        // Game-state gate: lobby mode (game not running) → install lobby
        // prefix but skip viewer filtering so chat stays global.
        ProximityChatConfig.GameStateConfig gsc = cfg.gameState();
        if (gsc.onlyDuringGame() && !DeathStatus.isGameRunning(gsc)) {
            installRenderer(event, cfg.chatFormat(), Channel.LOBBY);
            emitBubbles(event, cfg, sender);
            return;
        }

        boolean bypassSend = sender.hasPermission(PERMISSION_BYPASS_SEND);

        ProximityChatConfig.DeadStateConfig dsc = cfg.deadState();
        Objective deadObj = dsc.mode() == ProximityChatConfig.DeadStateConfig.Mode.SCOREBOARD
                ? DeathStatus.lookupObjective(dsc.objective())
                : null;
        boolean senderDead = !bypassSend && DeathStatus.isDead(sender, dsc, deadObj);

        Channel channel = bypassSend ? Channel.GLOBAL : (senderDead ? Channel.DEAD : Channel.ALIVE);
        installRenderer(event, cfg.chatFormat(), channel);

        if (bypassSend) {
            emitBubbles(event, cfg, sender);
            return; // global broadcast — keep all viewers
        }

        Location senderLoc = sender.getLocation();
        World senderWorld = senderLoc.getWorld();

        Set<Audience> viewers = event.viewers();
        viewers.removeIf(audience ->
                !shouldDeliver(audience, sender, senderLoc, senderWorld, cfg, deadObj, senderDead));

        emitBubbles(event, cfg, sender);
    }

    /**
     * Emits a TalkBubbles plugin message for every player within proximity
     * of {@code sender}, carrying the sender's RAW typed message text (not
     * the prefixed render). Vanilla clients and clients without the mod
     * are silently skipped — they remain able to join the server normally.
     *
     * <p>Bubble proximity is intentionally re-derived here rather than
     * piggybacking on the chat viewer set so that the lobby/global chat
     * paths still produce position-bound bubbles instead of plastering one
     * over every player in the world.</p>
     */
    private void emitBubbles(@NotNull AsyncChatEvent event,
                             @NotNull ProximityChatConfig cfg,
                             @NotNull Player sender) {
        if (!cfg.talkBubbles().enabled()) {
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        UUID senderUuid = sender.getUniqueId();
        Location senderLoc = sender.getLocation();
        World senderWorld = senderLoc.getWorld();
        double rangeSq = cfg.rangeSquared();
        boolean checkLos = cfg.requireLineOfSight();

        // Sender always receives their own bubble (no proximity check on self).
        TalkBubblesBridge.send(plugin, senderUuid, text, sender);

        for (Player viewer : senderWorld.getPlayers()) {
            if (viewer.getUniqueId().equals(senderUuid)) {
                continue;
            }
            double dx = senderLoc.getX() - viewer.getLocation().getX();
            double dy = senderLoc.getY() - viewer.getLocation().getY();
            double dz = senderLoc.getZ() - viewer.getLocation().getZ();
            if (dx * dx + dy * dy + dz * dz > rangeSq) {
                continue;
            }
            if (checkLos && !sender.hasLineOfSight(viewer)) {
                continue;
            }
            TalkBubblesBridge.send(plugin, senderUuid, text, viewer);
        }
    }

    private static void installRenderer(@NotNull AsyncChatEvent event,
                                        @NotNull ProximityChatConfig.ChatFormatConfig fmt,
                                        @NotNull Channel channel) {
        if (!fmt.enabled()) {
            return;
        }
        Component prefix = pickPrefix(fmt, channel);
        String template = fmt.template();
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) -> {
            try {
                return MINI.deserialize(
                        template,
                        Placeholder.component("prefix", prefix),
                        Placeholder.component("sender", sourceDisplayName),
                        Placeholder.component("message", message)
                );
            } catch (RuntimeException ex) {
                // Bad template — degrade gracefully to prefix + name + ": " + message.
                return prefix
                        .append(sourceDisplayName)
                        .append(Component.text(": "))
                        .append(message);
            }
        }));
    }

    private static @NotNull Component pickPrefix(@NotNull ProximityChatConfig.ChatFormatConfig fmt,
                                                 @NotNull Channel channel) {
        return switch (channel) {
            case ALIVE  -> fmt.alive();
            case DEAD   -> fmt.dead();
            case GLOBAL -> fmt.global();
            case LOBBY  -> fmt.lobby();
        };
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
        boolean viewerDead = DeathStatus.isDead(viewer, dsc, deadObj);

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
}
