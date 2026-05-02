package io.github.manosaba.core.voice;

import io.github.manosaba.core.ManosabaCore;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bukkit-side entry point for the Simple Voice Chat (henkelmax) integration.
 *
 * <p>This class is deliberately free of any {@code de.maxhenkel.voicechat.*}
 * imports so it can be loaded on servers that do <em>not</em> have the
 * Simple Voice Chat plugin installed. All SVC-touching code lives in
 * {@link ManosabaVoicechatPlugin}, which the JVM will only resolve when
 * {@link #enable()} actually invokes its static {@code register} method.</p>
 */
public final class VoicechatBridge {

    private static final String SVC_PLUGIN_NAME = "voicechat";

    private final ManosabaCore plugin;
    private @Nullable Runnable disposer;

    public VoicechatBridge(@NotNull ManosabaCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        if (Bukkit.getPluginManager().getPlugin(SVC_PLUGIN_NAME) == null) {
            plugin.getLogger().info("Simple Voice Chat plugin not present — voicechat integration disabled.");
            return;
        }
        try {
            this.disposer = ManosabaVoicechatPlugin.register(plugin);
            plugin.getLogger().info("Simple Voice Chat integration registered.");
        } catch (Throwable t) {
            this.disposer = null;
            plugin.getLogger().warning("Failed to register Simple Voice Chat integration: " + t);
        }
    }

    public void disable() {
        if (disposer != null) {
            try {
                disposer.run();
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to unregister Simple Voice Chat integration: " + t);
            }
            disposer = null;
        }
    }
}
