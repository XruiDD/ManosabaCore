package io.github.manosaba.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable typed view of the {@code proximity-chat} configuration section.
 * A new instance is built on every reload and published atomically so the
 * async chat listener always sees a consistent snapshot.
 */
public record ProximityChatConfig(
        boolean enabled,
        double range,
        double rangeSquared,
        boolean requireLineOfSight,
        boolean spectatorsHearAll,
        boolean spectatorsSilencedToOthers
) {

    public static final String SECTION = "proximity-chat";

    public static @NotNull ProximityChatConfig fromSection(@NotNull ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled", true);
        double range = Math.max(0.0D, section.getDouble("range", 48.0D));
        boolean requireLos = section.getBoolean("require-line-of-sight", true);

        ConfigurationSection spec = section.getConfigurationSection("spectator");
        boolean spectatorsHearAll = spec == null || spec.getBoolean("spectators-hear-all", true);
        boolean spectatorsSilencedToOthers = spec == null || spec.getBoolean("spectators-silenced-to-others", true);

        return new ProximityChatConfig(
                enabled,
                range,
                range * range,
                requireLos,
                spectatorsHearAll,
                spectatorsSilencedToOthers
        );
    }
}
