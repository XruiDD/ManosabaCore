package io.github.manosaba.core;

import io.github.manosaba.core.chat.ProximityChatListener;
import io.github.manosaba.core.command.ManosabaCommand;
import io.github.manosaba.core.config.ProximityChatConfig;
import io.github.manosaba.core.talkbubbles.TalkBubblesBridge;
import io.github.manosaba.core.voice.VoicechatBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ManosabaCore extends JavaPlugin {

    private volatile ProximityChatConfig chatConfig;
    private VoicechatBridge voicechatBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfiguration();

        // Outgoing: lets the server actually send the bubble packet.
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, TalkBubblesBridge.CHANNEL);
        // Incoming (no-op handler): Bukkit only advertises a channel to clients via
        // the vanilla 'minecraft:register' handshake packet when it is registered as
        // INCOMING. Without this, the TalkBubbles mod's ClientPlayNetworking.canSend
        // check returns false and the client falls back to its legacy chat-text path.
        Bukkit.getMessenger().registerIncomingPluginChannel(this, TalkBubblesBridge.CHANNEL,
                (channel, player, message) -> {
                    // Server is S2C-only for this protocol; ignore any C2S traffic.
                });

        getServer().getPluginManager().registerEvents(new ProximityChatListener(this), this);

        ManosabaCommand executor = new ManosabaCommand(this);
        PluginCommand cmd = getCommand("manosaba");
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().warning("Command 'manosaba' is not declared in plugin.yml; /manosaba will be unavailable.");
        }

        this.voicechatBridge = new VoicechatBridge(this);
        this.voicechatBridge.enable();

        getLogger().info("ManosabaCore enabled (proximity-chat enabled=" + chatConfig.enabled()
                + ", range=" + chatConfig.range() + ", line-of-sight=" + chatConfig.requireLineOfSight()
                + ", only-during-game=" + chatConfig.gameState().onlyDuringGame()
                + ", voicechat=" + chatConfig.voicechat().enabled() + ").");
    }

    @Override
    public void onDisable() {
        if (voicechatBridge != null) {
            voicechatBridge.disable();
            voicechatBridge = null;
        }
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this, TalkBubblesBridge.CHANNEL);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this, TalkBubblesBridge.CHANNEL);
        getLogger().info("ManosabaCore disabled.");
    }

    /**
     * Re-reads {@code config.yml} from disk and atomically publishes a new
     * typed configuration snapshot. Safe to call from the main thread; the
     * async chat listener will pick up the new snapshot on its next event.
     */
    public void reloadConfiguration() {
        reloadConfig();
        FileConfiguration root = getConfig();

        ConfigurationSection chatSection = root.getConfigurationSection(ProximityChatConfig.SECTION);
        if (chatSection == null) {
            chatSection = root.createSection(ProximityChatConfig.SECTION);
        }
        ConfigurationSection voicechatSection = root.getConfigurationSection(ProximityChatConfig.VOICECHAT_SECTION);

        this.chatConfig = ProximityChatConfig.fromSection(chatSection, voicechatSection);
    }

    public @Nullable ProximityChatConfig chatConfig() {
        return chatConfig;
    }

    public static @NotNull ManosabaCore instance() {
        return getPlugin(ManosabaCore.class);
    }
}
