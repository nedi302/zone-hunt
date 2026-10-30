package com.zonehunt.app.model;

import android.os.SystemClock;

public class GameRuntime {

    public final LobbySnapshot lobby;
    public final double centerLat;
    public final double centerLng;
    public final long startedElapsedMs;
    public final long startedWallMs;
    public final long durationMs;
    public final long hidePhaseMs;
    public final ZoneGeometry zoneGeometry;

    public volatile String winner = "";
    public volatile String endReason = "";
    public volatile boolean ended = false;

    public GameRuntime(LobbySnapshot lobby, double centerLat, double centerLng, long startedElapsedMs) {
        this.lobby = lobby;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.startedElapsedMs = startedElapsedMs;
        this.startedWallMs = System.currentTimeMillis();
        GameSettings settings = lobby.settings == null ? new GameSettings() : lobby.settings;
        this.durationMs = settings.roundDurationMs();
        this.hidePhaseMs = Math.max(5, Math.min(120, settings.hideSeconds)) * 1000L;
        this.zoneGeometry = new ZoneGeometry(centerLat, centerLng,
                settings.initialRadiusMeters, settings.shrinkMetersPerMinute,
                settings.roundDurationMs(), ZoneGeometry.seedFor(lobby.gameId));
    }

    public long elapsedMs() {
        return Math.max(0, SystemClock.elapsedRealtime() - startedElapsedMs);
    }

    /** Time since the hunting phase began; stays at zero during the hide phase. */
    public long huntingElapsedMs() {
        return Math.max(0, elapsedMs() - hidePhaseMs);
    }

    public boolean inHidePhase() {
        return !ended && elapsedMs() < hidePhaseMs;
    }

    public long hideRemainingMs() {
        return Math.max(0, hidePhaseMs - elapsedMs());
    }

    public long remainingMs() {
        return Math.max(0, durationMs - huntingElapsedMs());
    }

    public ZoneGeometry.Frame zoneFrame() {
        return zoneGeometry.frameAt(huntingElapsedMs());
    }

    public int currentRadiusMeters() {
        return zoneFrame().current.radiusMeters;
    }

    public Player player(String id) {
        return lobby.findPlayer(id);
    }
}
