package io.github.manosaba.core;

import io.github.manosaba.core.chat.ProximityChatListener;
import io.github.manosaba.core.command.ManosabaCommand;
import io.github.manosaba.core.config.ProximityChatConfig;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ManosabaCore extends JavaPlugin {

    private volatile ProximityChatConfig chatConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfiguration();

        getServer().getPluginManager().registerEvents(new ProximityChatListener(this), this);

        ManosabaCommand executor = new ManosabaCommand(this);
        PluginCommand cmd = getCommand("manosaba");
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().warning("Command 'manosaba' is not declared in plugin.yml; /manosaba will be unavailable.");
        }

        getLogger().info("ManosabaCore enabled (proximity-chat enabled=" + chatConfig.enabled()
                + ", range=" + chatConfig.range() + ", line-of-sight=" + chatConfig.requireLineOfSight() + ").");
    }

    @Override
    public void onDisable() {
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
        ConfigurationSection section = root.getConfigurationSection(ProximityChatConfig.SECTION);
        if (section == null) {
            section = root.createSection(ProximityChatConfig.SECTION);
        }
        this.chatConfig = ProximityChatConfig.fromSection(section);
    }

    public @Nullable ProximityChatConfig chatConfig() {
        return chatConfig;
    }

    public static @NotNull ManosabaCore instance() {
        return getPlugin(ManosabaCore.class);
    }
}
