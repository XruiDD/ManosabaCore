package io.github.manosaba.core.command;

import io.github.manosaba.core.ManosabaCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ManosabaCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("reload", "version", "help");

    private static final String PERMISSION_RELOAD = "manosaba.command.reload";

    private final ManosabaCore plugin;

    public ManosabaCommand(@NotNull ManosabaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "version" -> handleVersion(sender);
            case "help" -> sendHelp(sender);
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand: " + args[0], NamedTextColor.RED));
                sendHelp(sender);
            }
        }
        return true;
    }

    private void handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sender.sendMessage(Component.text("You do not have permission to reload.", NamedTextColor.RED));
            return;
        }
        try {
            plugin.reloadConfiguration();
            sender.sendMessage(Component.text("ManosabaCore configuration reloaded.", NamedTextColor.GREEN));
        } catch (RuntimeException ex) {
            sender.sendMessage(Component.text("Reload failed: " + ex.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Configuration reload failed: " + ex);
        }
    }

    private void handleVersion(@NotNull CommandSender sender) {
        String version = plugin.getPluginMeta().getVersion();
        sender.sendMessage(Component.text("ManosabaCore v" + version, NamedTextColor.AQUA));
    }

    private static void sendHelp(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("ManosabaCore commands:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /manosaba reload  ", NamedTextColor.YELLOW)
                .append(Component.text("- reload configuration", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /manosaba version ", NamedTextColor.YELLOW)
                .append(Component.text("- show plugin version", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /manosaba help    ", NamedTextColor.YELLOW)
                .append(Component.text("- show this help", NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>(SUBCOMMANDS.size());
            for (String s : SUBCOMMANDS) {
                if (s.startsWith(prefix)) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }
}
