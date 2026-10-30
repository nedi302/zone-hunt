package com.zonehunt.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import com.zonehunt.app.util.Geo;

/**
 * A device-local recording of one round. Positions are stored per player as
 * timestamped samples relative to the start of the round.
 */
public class GameReplay {

    public static final int VERSION = 3;
    public static final int MAX_SAMPLES_PER_PLAYER = 6000;

    public String id = "";
    public String gameId = "";
    public String code = "";
    public long startedAt = 0;
    public long endedAt = 0;
    public long durationMs = 0;
    public long hidePhaseMs = 0;
    public long roundDurationMs = 0;
    public String mode = GameSettings.MODE_CLASSIC;
    public long zoneSeed = Long.MIN_VALUE;
    public String endReason = "";
    public String winner = "";
    public double centerLat = 0;
    public double centerLng = 0;
    public int initialRadiusMeters = 0;
    public int shrinkMetersPerMinute = 0;
    public final List<PlayerTrack> players = new ArrayList<>();

    public static class Sample {
        public final long t;
        public final double lat;
        public final double lng;

        public Sample(long t, double lat, double lng) {
            this.t = t;
            this.lat = lat;
            this.lng = lng;
        }
    }

    public static class PlayerTrack {
        public String id = "";
        public String name = "";
        public String role = "";
        public final List<Sample> samples = new ArrayList<>();
        private long lastT = -1;
        private double lastLat;
        private double lastLng;

        public synchronized void add(long t, double lat, double lng) {
            if (samples.size() >= MAX_SAMPLES_PER_PLAYER) {
                return;
            }
            long cleanT = Math.max(0, t);
            if (lastT >= 0) {
                long delta = cleanT - lastT;
                if (delta < 0) {
                    return;
                }
                if (delta < 1000) {
                    return;
                }
                double moved = Geo.distanceMeters(lastLat, lastLng, lat, lng);
                if (moved < 1.5 && delta < 8000) {
                    return;
                }
            }
            samples.add(new Sample(cleanT, lat, lng));
            lastT = cleanT;
            lastLat = lat;
            lastLng = lng;
        }

        public synchronized void addRestored(long t, double lat, double lng) {
            samples.add(new Sample(Math.max(0, t), lat, lng));
            lastT = Math.max(0, t);
            lastLat = lat;
            lastLng = lng;
        }

        /** Index of the latest sample at or before t, or -1. */
        public int indexAt(long t) {
            int low = 0;
            int high = samples.size() - 1;
            int result = -1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (samples.get(mid).t <= t) {
                    result = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return result;
        }
    }

    public synchronized PlayerTrack findTrack(String playerId) {
        for (PlayerTrack track : players) {
            if (track.id.equals(playerId)) {
                return track;
            }
        }
        return null;
    }

    public synchronized PlayerTrack addPlayer(String playerId, String playerName, String playerRole) {
        PlayerTrack existing = findTrack(playerId);
        if (existing != null) {
            existing.name = playerName;
            existing.role = playerRole;
            return existing;
        }
        PlayerTrack track = new PlayerTrack();
        track.id = playerId;
        track.name = playerName;
        track.role = playerRole;
        players.add(track);
        return track;
    }

    public synchronized void addSample(String playerId, long t, double lat, double lng) {
        PlayerTrack track = findTrack(playerId);
        if (track != null) {
            track.add(t, lat, lng);
        }
    }

    public synchronized boolean hasData() {
        for (PlayerTrack track : players) {
            if (!track.samples.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public synchronized long maxSampleTime() {
        long max = 0;
        for (PlayerTrack track : players) {
            if (!track.samples.isEmpty()) {
                max = Math.max(max, track.samples.get(track.samples.size() - 1).t);
            }
        }
        return max;
    }

    public synchronized int sampleCount() {
        int count = 0;
        for (PlayerTrack track : players) {
            count += track.samples.size();
        }
        return count;
    }

    public PlayerTrack seeker() {
        for (PlayerTrack track : players) {
            if ("SEEKER".equals(track.role)) {
                return track;
            }
        }
        return null;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("v", VERSION);
        o.put("id", id);
        o.put("gameId", gameId);
        o.put("code", code);
        o.put("startedAt", startedAt);
        o.put("endedAt", endedAt);
        o.put("durationMs", durationMs);
        o.put("hidePhaseMs", hidePhaseMs);
        o.put("roundDurationMs", roundDurationMs);
        o.put("mode", mode == null ? GameSettings.MODE_CLASSIC : mode);
        if (zoneSeed != Long.MIN_VALUE) {
            o.put("zoneSeed", zoneSeed);
        }
        o.put("endReason", endReason);
        o.put("winner", winner);
        o.put("centerLat", round6(centerLat));
        o.put("centerLng", round6(centerLng));
        o.put("initialRadius", initialRadiusMeters);
        o.put("shrink", shrinkMetersPerMinute);
        JSONArray trackArray = new JSONArray();
        synchronized (this) {
            for (PlayerTrack track : players) {
                JSONObject p = new JSONObject();
                p.put("id", track.id);
                p.put("name", track.name);
                p.put("role", track.role);
                JSONArray sampleArray = new JSONArray();
                for (Sample sample : track.samples) {
                    JSONArray s = new JSONArray();
                    s.put(sample.t);
                    s.put(round6(sample.lat));
                    s.put(round6(sample.lng));
                    sampleArray.put(s);
                }
                p.put("samples", sampleArray);
                trackArray.put(p);
            }
        }
        o.put("players", trackArray);
        return o;
    }

    public static GameReplay fromJson(JSONObject o) throws JSONException {
        GameReplay replay = new GameReplay();
        replay.id = o.optString("id", "");
        replay.gameId = o.optString("gameId", "");
        replay.code = o.optString("code", "");
        replay.startedAt = o.optLong("startedAt", 0);
        replay.endedAt = o.optLong("endedAt", 0);
        replay.durationMs = o.optLong("durationMs", 0);
        replay.hidePhaseMs = o.optLong("hidePhaseMs", 0);
        replay.roundDurationMs = o.optLong("roundDurationMs", 0);
        replay.mode = GameSettings.normalizeMode(o.optString("mode", GameSettings.MODE_CLASSIC));
        replay.zoneSeed = o.optLong("zoneSeed", Long.MIN_VALUE);
        replay.endReason = o.optString("endReason", "");
        replay.winner = o.optString("winner", "");
        replay.centerLat = o.optDouble("centerLat", 0);
        replay.centerLng = o.optDouble("centerLng", 0);
        replay.initialRadiusMeters = o.optInt("initialRadius", 0);
        replay.shrinkMetersPerMinute = o.optInt("shrink", 0);
        JSONArray trackArray = o.optJSONArray("players");
        if (trackArray != null) {
            for (int i = 0; i < trackArray.length(); i++) {
                JSONObject p = trackArray.optJSONObject(i);
                if (p == null) {
                    continue;
                }
                PlayerTrack track = replay.addPlayer(
                        p.optString("id", ""),
                        p.optString("name", "Player"),
                        p.optString("role", ""));
                JSONArray samples = p.optJSONArray("samples");
                if (samples != null) {
                    for (int j = 0; j < samples.length(); j++) {
                        JSONArray s = samples.optJSONArray(j);
                        if (s != null) {
                            track.addRestored(s.optLong(0, 0), s.optDouble(1, 0), s.optDouble(2, 0));
                        }
                    }
                }
            }
        }
        return replay;
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
