package io.github.manosaba.core.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import io.github.manosaba.core.ManosabaCore;
import io.github.manosaba.core.config.ProximityChatConfig;
import io.github.manosaba.core.game.DeathStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simple Voice Chat plugin implementation. Maintains a persistent ISOLATED
 * voice group that dead players are moved into and out of every
 * {@code voicechat.sync-period-ticks} ticks.
 */
public final class ManosabaVoicechatPlugin implements VoicechatPlugin {

    public static final String PLUGIN_ID = "manosaba_core";

    private final ManosabaCore plugin;

    private @Nullable VoicechatServerApi serverApi;
    private @Nullable Group deadGroup;
    private @Nullable BukkitTask syncTask;

    private ManosabaVoicechatPlugin(@NotNull ManosabaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a {@link ManosabaVoicechatPlugin} instance with the SVC
     * service. Returns a {@link Runnable} that performs full cleanup
     * (cancel task, remove group, unregister service) when invoked.
     */
    public static @NotNull Runnable register(@NotNull ManosabaCore plugin) {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            throw new IllegalStateException("BukkitVoicechatService is not available");
        }
        ManosabaVoicechatPlugin vc = new ManosabaVoicechatPlugin(plugin);
        service.registerPlugin(vc);
        return () -> {
            vc.shutdown();
            Bukkit.getServicesManager().unregister(vc);
        };
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        // Server-side API is delivered later via VoicechatServerStartedEvent.
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onServerStopped);
    }

    private void onServerStarted(@NotNull VoicechatServerStartedEvent event) {
        this.serverApi = event.getVoicechat();
        rebuildGroup();
        startSync();
    }

    private void onServerStopped(@NotNull VoicechatServerStoppedEvent event) {
        stopSync();
        this.deadGroup = null;
        this.serverApi = null;
    }

    private void rebuildGroup() {
        VoicechatServerApi api = this.serverApi;
        if (api == null) {
            return;
        }
        ProximityChatConfig cfg = plugin.chatConfig();
        String name = cfg != null ? cfg.voicechat().deadGroupName() : "Spectator";
        this.deadGroup = api.groupBuilder()
                .setPersistent(true)
                .setName(name)
                .setType(Group.Type.ISOLATED)
                .build();
    }

    private void startSync() {
        if (syncTask != null) {
            return;
        }
        ProximityChatConfig cfg = plugin.chatConfig();
        long period = cfg != null ? Math.max(1L, cfg.voicechat().syncPeriodTicks()) : 20L;
        this.syncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sync, period, period);
    }

    private void stopSync() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
    }

    private void sync() {
        VoicechatServerApi api = this.serverApi;
        Group group = this.deadGroup;
        if (api == null || group == null) {
            return;
        }
        ProximityChatConfig cfg = plugin.chatConfig();
        if (cfg == null || !cfg.voicechat().enabled()) {
            return;
        }

        ProximityChatConfig.DeadStateConfig dsc = cfg.deadState();
        Objective deadObj = dsc.mode() == ProximityChatConfig.DeadStateConfig.Mode.SCOREBOARD
                ? DeathStatus.lookupObjective(dsc.objective())
                : null;
        ProximityChatConfig.PlayingStateConfig psc = cfg.playingState();
        Objective playingObj = psc.enabled() ? DeathStatus.lookupObjective(psc.objective()) : null;

        for (Player player : Bukkit.getOnlinePlayers()) {
            VoicechatConnection conn = api.getConnectionOf(player.getUniqueId());
            if (conn == null) {
                continue;
            }

            // Spectator pool = dead OR not-actively-playing this round.
            boolean inSpectatorPool = DeathStatus.isInSpectatorPool(player, dsc, deadObj, psc, playingObj);
            Group current = conn.getGroup();
            boolean inDeadGroup = current != null && current.getId().equals(group.getId());

            if (inSpectatorPool && !inDeadGroup) {
                conn.setGroup(group);
            } else if (!inSpectatorPool && inDeadGroup) {
                conn.setGroup(null);
            }
        }
    }

    void shutdown() {
        stopSync();
        VoicechatServerApi api = this.serverApi;
        Group group = this.deadGroup;
        if (api != null && group != null) {
            try {
                api.removeGroup(group.getId());
            } catch (Throwable ignored) {
                // group may already be gone, or API may not support remove
            }
        }
        this.deadGroup = null;
        this.serverApi = null;
    }
}
