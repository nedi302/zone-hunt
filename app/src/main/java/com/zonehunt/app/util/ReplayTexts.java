package com.zonehunt.app.util;

import android.content.Context;

import com.zonehunt.app.R;
import com.zonehunt.app.model.GameReplay;
import com.zonehunt.app.model.GameSettings;

/**
 * Mode-aware labels for the replay list and the replay player.
 */
public final class ReplayTexts {

    private ReplayTexts() {
    }

    public static int modeLabelRes(String mode) {
        String normalized = GameSettings.normalizeMode(mode);
        if (GameSettings.MODE_ZOMBIE.equals(normalized)) {
            return R.string.mode_zombie;
        }
        if (GameSettings.MODE_ASSASSIN.equals(normalized)) {
            return R.string.mode_assassin;
        }
        if (GameSettings.MODE_ICE.equals(normalized)) {
            return R.string.mode_ice;
        }
        return R.string.mode_classic;
    }

    public static String modeLabel(Context context, String mode) {
        return context.getString(modeLabelRes(mode));
    }

    /** Human readable role for a recorded track. */
    public static int roleLabelRes(String mode, String role) {
        String normalized = GameSettings.normalizeMode(mode);
        boolean seeker = "SEEKER".equals(role);
        if (GameSettings.MODE_ASSASSIN.equals(normalized)) {
            return R.string.game_role_assassin_lower;
        }
        if (GameSettings.MODE_ZOMBIE.equals(normalized)) {
            return seeker ? R.string.game_role_zombie_lower : R.string.game_role_survivor_lower;
        }
        if (GameSettings.MODE_ICE.equals(normalized)) {
            return seeker ? R.string.game_role_ice_hunter_lower : R.string.game_role_hider_lower;
        }
        return seeker ? R.string.game_role_seeker_lower : R.string.game_role_hider_lower;
    }

    /** Short legend suffix for the hunter track, or 0 when no suffix is needed. */
    public static int legendSuffixRes(String mode, String role) {
        String normalized = GameSettings.normalizeMode(mode);
        if (!"SEEKER".equals(role)) {
            return 0;
        }
        if (GameSettings.MODE_ZOMBIE.equals(normalized)) {
            return R.string.replay_legend_zombie_suffix;
        }
        if (GameSettings.MODE_ICE.equals(normalized)) {
            return R.string.replay_legend_ice_suffix;
        }
        if (GameSettings.MODE_CLASSIC.equals(normalized)) {
            return R.string.replay_legend_seeker_suffix;
        }
        return 0;
    }

    /** Winner line for a finished round. */
    public static String outcome(Context context, GameReplay replay) {
        String winner = replay == null || replay.winner == null ? "" : replay.winner;
        switch (winner) {
            case "SEEKER":
                return context.getString(R.string.replay_winner_seeker);
            case "HIDERS":
                return context.getString(R.string.replay_winner_hiders);
            case "ZOMBIES":
                return context.getString(R.string.replay_winner_zombies_full);
            case "SURVIVORS":
                return context.getString(R.string.replay_winner_survivors_full);
            case "ICE":
                return context.getString(R.string.replay_winner_ice_hunter);
            default:
                if (winner.startsWith("ASSASSIN:")) {
                    String id = winner.substring("ASSASSIN:".length());
                    GameReplay.PlayerTrack track = replay.findTrack(id);
                    String name = track == null ? "?" : track.name;
                    return context.getString(R.string.replay_result_assassin, name);
                }
                return context.getString(R.string.replay_aborted);
        }
    }
}
