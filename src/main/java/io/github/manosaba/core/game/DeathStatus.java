package io.github.manosaba.core.game;

import io.github.manosaba.core.config.ProximityChatConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared helpers for reading the Manosaba datapack-managed scoreboard state
 * (game-running, per-player dead, per-player Playing) from Bukkit.
 *
 * <p>All reads go through the main scoreboard. Bukkit's scoreboard is not
 * formally thread-safe; these methods perform a single objective lookup +
 * single int read which is safe enough in practice for both async chat
 * dispatch and main-thread polling.</p>
 */
public final class DeathStatus {

    private DeathStatus() {
    }

    public static @Nullable Objective lookupObjective(@NotNull String name) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return null;
        }
        Scoreboard board = manager.getMainScoreboard();
        return board.getObjective(name);
    }

    public static boolean isGameRunning(@NotNull ProximityChatConfig.GameStateConfig gsc) {
        Objective objective = lookupObjective(gsc.objective());
        if (objective == null) {
            return false;
        }
        @SuppressWarnings("deprecation")
        Score score = objective.getScore(gsc.holder());
        return score.isScoreSet() && score.getScore() == gsc.inGameValue();
    }

    public static boolean isDead(@NotNull Player player,
                                 @NotNull ProximityChatConfig.DeadStateConfig dsc,
                                 @Nullable Objective deadObj) {
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

    /**
     * Whether {@code player} is actively participating in the current round.
     *
     * <p>When the {@code playing} feature is disabled, or the datapack
     * objective is missing, the method returns {@code true} (permissive
     * fallback so chat keeps working without the datapack).</p>
     */
    public static boolean isPlaying(@NotNull Player player,
                                    @NotNull ProximityChatConfig.PlayingStateConfig psc,
                                    @Nullable Objective playingObj) {
        if (!psc.enabled()) {
            return true;
        }
        if (playingObj == null) {
            return true;
        }
        @SuppressWarnings("deprecation")
        Score score = playingObj.getScore(player.getName());
        return score.isScoreSet() && score.getScore() == psc.playingValue();
    }

    /**
     * Whether {@code player} should sit in the voicechat spectator group.
     * True when the player is dead OR not actively playing the round.
     */
    public static boolean isInSpectatorPool(@NotNull Player player,
                                            @NotNull ProximityChatConfig.DeadStateConfig dsc,
                                            @Nullable Objective deadObj,
                                            @NotNull ProximityChatConfig.PlayingStateConfig psc,
                                            @Nullable Objective playingObj) {
        if (isDead(player, dsc, deadObj)) {
            return true;
        }
        return !isPlaying(player, psc, playingObj);
    }
}
