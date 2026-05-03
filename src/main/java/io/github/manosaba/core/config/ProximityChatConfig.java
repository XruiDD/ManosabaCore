package io.github.manosaba.core.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
        boolean spectatorsHearAll,
        boolean spectatorsSilencedToActive,
        @NotNull GameStateConfig gameState,
        @NotNull DeadStateConfig deadState,
        @NotNull PlayingStateConfig playingState,
        @NotNull ChatFormatConfig chatFormat,
        @NotNull TalkBubblesConfig talkBubbles,
        @NotNull VoicechatConfig voicechat
) {

    public static final String SECTION = "proximity-chat";
    public static final String VOICECHAT_SECTION = "voicechat";

    public static @NotNull ProximityChatConfig fromSection(@NotNull ConfigurationSection chatSection,
                                                           ConfigurationSection voicechatSection) {
        boolean enabled = chatSection.getBoolean("enabled", true);
        double range = Math.max(0.0D, chatSection.getDouble("range", 48.0D));
        boolean requireLos = chatSection.getBoolean("require-line-of-sight", true);
        boolean spectatorsHearAll = chatSection.getBoolean("spectators-hear-all", true);
        boolean spectatorsSilenced = chatSection.getBoolean("spectators-silenced-to-active", true);

        GameStateConfig gameState = GameStateConfig.fromSection(chatSection.getConfigurationSection("game-state"));
        DeadStateConfig deadState = DeadStateConfig.fromSection(chatSection.getConfigurationSection("dead"));
        PlayingStateConfig playingState = PlayingStateConfig.fromSection(chatSection.getConfigurationSection("playing"));
        ChatFormatConfig chatFormat = ChatFormatConfig.fromSection(chatSection.getConfigurationSection("chat-format"));
        TalkBubblesConfig talkBubbles = TalkBubblesConfig.fromSection(chatSection.getConfigurationSection("talkbubbles"));
        VoicechatConfig voicechat = VoicechatConfig.fromSection(voicechatSection);

        return new ProximityChatConfig(
                enabled,
                range,
                range * range,
                requireLos,
                spectatorsHearAll,
                spectatorsSilenced,
                gameState,
                deadState,
                playingState,
                chatFormat,
                talkBubbles,
                voicechat
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
            String objective = section.getString("objective", "dead");
            int deadValue = section.getInt("dead-value", 1);
            return new DeadStateConfig(
                    mode,
                    objective == null || objective.isEmpty() ? "dead" : objective,
                    deadValue
            );
        }

        public static @NotNull DeadStateConfig defaults() {
            return new DeadStateConfig(Mode.SCOREBOARD, "dead", 1);
        }
    }

    /**
     * Configuration for the per-player "actively playing this round" status.
     *
     * <p>Datapack contract:
     * <ul>
     *   <li>objective {@code Playing} (dummy, per-player), holder = player name</li>
     *   <li>{@code 1} = actively in this round</li>
     *   <li>{@code 0} = not in round (lobby idle / mid-join / didn't ready)</li>
     *   <li>{@code 2} = lobby minigame (separate state machine)</li>
     * </ul>
     * Players whose score does NOT equal {@link #playingValue()} are
     * "observers": they get the observer chat channel and join the same
     * voicechat spectator group as dead players.
     */
    public record PlayingStateConfig(
            boolean enabled,
            @NotNull String objective,
            int playingValue
    ) {

        public static @NotNull PlayingStateConfig fromSection(ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            boolean enabled = section.getBoolean("enabled", true);
            String objective = section.getString("objective", "Playing");
            int value = section.getInt("playing-value", 1);
            return new PlayingStateConfig(
                    enabled,
                    objective == null || objective.isEmpty() ? "Playing" : objective,
                    value
            );
        }

        public static @NotNull PlayingStateConfig defaults() {
            return new PlayingStateConfig(true, "Playing", 1);
        }
    }

    /**
     * Full chat-line render template plus the three channel prefixes.
     *
     * <p>The {@link #template()} is a MiniMessage string with three
     * placeholders that are filled in per chat event: {@code <prefix>},
     * {@code <sender>}, {@code <message>}. The vanilla
     * {@code chat.type.text} translation is NOT used — when this config
     * is enabled the listener installs a renderer that fully owns the
     * chat line.</p>
     *
     * <p>Prefixes are pre-parsed once and cached as {@link Component}
     * instances to avoid re-deserialization on every chat event. The
     * template itself is kept as a string because its placeholders need
     * substitution at render time.</p>
     */
    public record ChatFormatConfig(
            boolean enabled,
            @NotNull String template,
            @NotNull Component alive,
            @NotNull Component dead,
            @NotNull Component observer,
            @NotNull Component global,
            @NotNull Component lobby
    ) {

        public static final String DEFAULT_TEMPLATE =
                "<prefix><sender><dark_gray> » </dark_gray><message>";
        public static final String DEFAULT_ALIVE    =
                "<dark_gray>「</dark_gray><green>附近</green><dark_gray>」</dark_gray> ";
        public static final String DEFAULT_DEAD     =
                "<dark_gray>「</dark_gray><dark_purple>死亡</dark_purple><dark_gray>」</dark_gray> ";
        public static final String DEFAULT_OBSERVER =
                "<dark_gray>「</dark_gray><gray>旁观</gray><dark_gray>」</dark_gray> ";
        public static final String DEFAULT_GLOBAL   =
                "<dark_gray>「</dark_gray><gold>全部</gold><dark_gray>」</dark_gray> ";
        public static final String DEFAULT_LOBBY    =
                "<dark_gray>「</dark_gray><aqua>大厅</aqua><dark_gray>」</dark_gray> ";

        public static @NotNull ChatFormatConfig fromSection(ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            boolean enabled = section.getBoolean("enabled", true);
            String template = section.getString("template", DEFAULT_TEMPLATE);
            if (template == null || template.isEmpty()) {
                template = DEFAULT_TEMPLATE;
            }
            Component alive    = parse(section.getString("alive",    DEFAULT_ALIVE),    DEFAULT_ALIVE);
            Component dead     = parse(section.getString("dead",     DEFAULT_DEAD),     DEFAULT_DEAD);
            Component observer = parse(section.getString("observer", DEFAULT_OBSERVER), DEFAULT_OBSERVER);
            Component global   = parse(section.getString("global",   DEFAULT_GLOBAL),   DEFAULT_GLOBAL);
            Component lobby    = parse(section.getString("lobby",    DEFAULT_LOBBY),    DEFAULT_LOBBY);
            return new ChatFormatConfig(enabled, template, alive, dead, observer, global, lobby);
        }

        public static @NotNull ChatFormatConfig defaults() {
            return new ChatFormatConfig(
                    true,
                    DEFAULT_TEMPLATE,
                    parse(DEFAULT_ALIVE,    DEFAULT_ALIVE),
                    parse(DEFAULT_DEAD,     DEFAULT_DEAD),
                    parse(DEFAULT_OBSERVER, DEFAULT_OBSERVER),
                    parse(DEFAULT_GLOBAL,   DEFAULT_GLOBAL),
                    parse(DEFAULT_LOBBY,    DEFAULT_LOBBY)
            );
        }

        private static @NotNull Component parse(String raw, @NotNull String fallback) {
            if (raw == null || raw.isEmpty()) {
                return Component.empty();
            }
            try {
                return MiniMessage.miniMessage().deserialize(raw);
            } catch (RuntimeException ex) {
                return MiniMessage.miniMessage().deserialize(fallback);
            }
        }
    }

    /**
     * Configuration for the TalkBubbles (Fabric) client mod integration.
     * The plugin always sends the raw, un-prefixed message text via the
     * mod's plugin-message channel — vanilla clients are silently skipped
     * because they do not register the channel during the
     * {@code minecraft:register} handshake.
     */
    public record TalkBubblesConfig(
            boolean enabled
    ) {

        public static @NotNull TalkBubblesConfig fromSection(ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            return new TalkBubblesConfig(section.getBoolean("enabled", true));
        }

        public static @NotNull TalkBubblesConfig defaults() {
            return new TalkBubblesConfig(true);
        }
    }

    /**
     * Configuration for the Simple Voice Chat (henkelmax) integration.
     * The integration moves dead players into a persistent ISOLATED group
     * so they can only voice-chat with each other.
     */
    public record VoicechatConfig(
            boolean enabled,
            @NotNull String deadGroupName,
            long syncPeriodTicks
    ) {

        public static @NotNull VoicechatConfig fromSection(ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            boolean enabled = section.getBoolean("enabled", true);
            String name = section.getString("dead-group-name", "Spectator");
            long period = Math.max(1L, section.getLong("sync-period-ticks", 20L));
            return new VoicechatConfig(
                    enabled,
                    name == null || name.isEmpty() ? "Spectator" : name,
                    period
            );
        }

        public static @NotNull VoicechatConfig defaults() {
            return new VoicechatConfig(true, "Spectator", 20L);
        }
    }
}
