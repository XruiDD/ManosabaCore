package io.github.manosaba.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

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
        @NotNull GameStateConfig gameState,
        @NotNull DeadStateConfig deadState
) {

    public static final String SECTION = "proximity-chat";

    public static @NotNull ProximityChatConfig fromSection(@NotNull ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled", true);
        double range = Math.max(0.0D, section.getDouble("range", 48.0D));
        boolean requireLos = section.getBoolean("require-line-of-sight", true);

        GameStateConfig gameState = GameStateConfig.fromSection(section.getConfigurationSection("game-state"));
        DeadStateConfig deadState = DeadStateConfig.fromSection(section.getConfigurationSection("dead"));

        return new ProximityChatConfig(
                enabled,
                range,
                range * range,
                requireLos,
                gameState,
                deadState
        );
    }

    /**
     * Configuration for whether proximity chat should only be active while
     * the Manosaba datapack reports the game as running.
     *
     * <p>Datapack contract:
     * <ul>
     *   <li>objective {@code Gaming} (dummy)</li>
     *   <li>holder {@code manosaba:data} (fakeplayer)</li>
     *   <li>value {@code 1} = game running, {@code 0} = lobby / ended,
     *       {@code -1} = short transition lock (~2 ticks)</li>
     * </ul>
     */
    public record GameStateConfig(
            boolean onlyDuringGame,
            @NotNull String objective,
            @NotNull String holder,
            int inGameValue
    ) {

        public static @NotNull GameStateConfig fromSection(ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            boolean only = section.getBoolean("only-during-game", true);
            String objective = section.getString("objective", "Gaming");
            String holder = section.getString("holder", "manosaba:data");
            int inGameValue = section.getInt("in-game-value", 1);
            return new GameStateConfig(
                    only,
                    objective == null || objective.isEmpty() ? "Gaming" : objective,
                    holder == null || holder.isEmpty() ? "manosaba:data" : holder,
                    inGameValue
            );
        }

        public static @NotNull GameStateConfig defaults() {
            return new GameStateConfig(true, "Gaming", "manosaba:data", 1);
        }
    }

    /**
     * Configuration for the per-player "dead" status read from a dummy
     * scoreboard objective. Note that "dead" is independent from
     * {@code GameMode.SPECTATOR} — a dead player may still be in survival
     * / adventure mode while their {@code dead} score is {@code 1}.
     *
     * <p>Datapack contract:
     * <ul>
     *   <li>objective {@code dead} (dummy, per-player)</li>
     *   <li>holder = player name</li>
     *   <li>value {@code 1} = dead, anything else / unset = alive</li>
     * </ul>
     */
    public record DeadStateConfig(
            @NotNull Mode mode,
            boolean deadHearAll,
            boolean deadSilencedToLiving,
            @NotNull String objective,
            int deadValue
    ) {

        public enum Mode {
            /** Read death state from the datapack {@code dead} dummy objective. */
            SCOREBOARD,
            /** Treat {@link org.bukkit.GameMode#SPECTATOR} as dead. */
            SPECTATOR;

            public static @NotNull Mode parse(String raw, @NotNull Mode fallback) {
                if (raw == null) return fallback;
                try {
                    return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    return fallback;
                }
            }
        }

        public static @NotNull DeadStateConfig fromSection(ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            Mode mode = Mode.parse(section.getString("mode", "scoreboard"), Mode.SCOREBOARD);
            boolean hearAll = section.getBoolean("dead-hear-all", true);
            boolean silenced = section.getBoolean("dead-silenced-to-living", true);
            String objective = section.getString("objective", "dead");
            int deadValue = section.getInt("dead-value", 1);
            return new DeadStateConfig(
                    mode,
                    hearAll,
                    silenced,
                    objective == null || objective.isEmpty() ? "dead" : objective,
                    deadValue
            );
        }

        public static @NotNull DeadStateConfig defaults() {
            return new DeadStateConfig(Mode.SCOREBOARD, true, true, "dead", 1);
        }
    }
}
