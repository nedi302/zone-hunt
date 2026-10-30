package com.zonehunt.app.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * All rules are chosen by the host before a lobby is created.
 */
public class GameSettings {

    public static final int ABSOLUTE_MIN_RADIUS = 30;
    public static final int MAX_PLAYERS = 20;

    public static final String MODE_CLASSIC = "CLASSIC";
    public static final String MODE_ZOMBIE = "ZOMBIE";
    public static final String MODE_ASSASSIN = "ASSASSIN";
    public static final String MODE_ICE = "ICE";

    public int initialRadiusMeters = 350;
    public int shrinkMetersPerMinute = 5;
    public int roundMinutes = 30;
    public int pingIntervalSeconds = 45;
    public int graceSeconds = 30;
    public int hideSeconds = 25;
    /** Alive teammates may see each other's live positions. */
    public boolean teamLive = false;
    /** Enables survival coins and the in-round shop. */
    public boolean shopEnabled = false;

    /** One of the MODE_* constants. Unknown values fall back to classic. */
    public String mode = MODE_CLASSIC;

    /** Zombie mode: newly turned zombies cannot catch for this long. */
    public int zombieConversionSeconds = 15;
    /** Zombie mode: every interval zombies briefly sense all survivors. */
    public int zombieSenseIntervalSeconds = 30;

    /** Assassin mode: distance at which the secret target can be eliminated. */
    public int assassinKillRadiusMeters = 25;
    /** Assassin mode: pings only reveal the player's own target. */
    public boolean assassinTargetPingOnly = true;

    /** Ice mode: radius in which an active teammate can thaw a frozen player. */
    public int iceThawRadiusMeters = 15;
    /** Ice mode: seconds a teammate has to stay close to thaw someone. */
    public int iceThawHoldSeconds = 5;
    /** Ice mode: seconds until a frozen player is eliminated. */
    public int iceMeltSeconds = 60;

    public GameSettings() {
    }

    public GameSettings copy() {
        GameSettings s = new GameSettings();
        s.initialRadiusMeters = initialRadiusMeters;
        s.shrinkMetersPerMinute = shrinkMetersPerMinute;
        s.roundMinutes = roundMinutes;
        s.pingIntervalSeconds = pingIntervalSeconds;
        s.graceSeconds = graceSeconds;
        s.hideSeconds = hideSeconds;
        s.teamLive = teamLive;
        s.shopEnabled = shopEnabled;
        s.mode = mode;
        s.zombieConversionSeconds = zombieConversionSeconds;
        s.zombieSenseIntervalSeconds = zombieSenseIntervalSeconds;
        s.assassinKillRadiusMeters = assassinKillRadiusMeters;
        s.assassinTargetPingOnly = assassinTargetPingOnly;
        s.iceThawRadiusMeters = iceThawRadiusMeters;
        s.iceThawHoldSeconds = iceThawHoldSeconds;
        s.iceMeltSeconds = iceMeltSeconds;
        return s;
    }

    public boolean isClassic() {
        return MODE_CLASSIC.equals(mode);
    }

    public boolean isZombie() {
        return MODE_ZOMBIE.equals(mode);
    }

    public boolean isAssassin() {
        return MODE_ASSASSIN.equals(mode);
    }

    public boolean isIce() {
        return MODE_ICE.equals(mode);
    }

    public int radiusAt(long elapsedMs) {
        double minutes = Math.max(0, elapsedMs) / 60000.0;
        int radius = (int) Math.round(initialRadiusMeters - (shrinkMetersPerMinute * minutes));
        return Math.max(ABSOLUTE_MIN_RADIUS, radius);
    }

    public long roundDurationMs() {
        return roundMinutes * 60_000L;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("radius", initialRadiusMeters);
            o.put("shrink", shrinkMetersPerMinute);
            o.put("round", roundMinutes);
            o.put("ping", pingIntervalSeconds);
            o.put("grace", graceSeconds);
            o.put("hide", hideSeconds);
            o.put("teamLive", teamLive);
            o.put("shop", shopEnabled);
            o.put("mode", mode == null ? MODE_CLASSIC : mode);
            o.put("zombieConversion", zombieConversionSeconds);
            o.put("zombieSense", zombieSenseIntervalSeconds);
            o.put("assassinRadius", assassinKillRadiusMeters);
            o.put("assassinPingOnly", assassinTargetPingOnly);
            o.put("iceThawRadius", iceThawRadiusMeters);
            o.put("iceThawHold", iceThawHoldSeconds);
            o.put("iceMelt", iceMeltSeconds);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static GameSettings fromJson(JSONObject o) {
        GameSettings s = new GameSettings();
        if (o == null) {
            return s;
        }
        s.initialRadiusMeters = clamp(o.optInt("radius", s.initialRadiusMeters), 100, 2000);
        s.shrinkMetersPerMinute = clamp(o.optInt("shrink", s.shrinkMetersPerMinute), 0, 50);
        s.roundMinutes = clamp(o.optInt("round", s.roundMinutes), 5, 120);
        s.pingIntervalSeconds = clamp(o.optInt("ping", s.pingIntervalSeconds), 10, 300);
        s.graceSeconds = clamp(o.optInt("grace", s.graceSeconds), 5, 120);
        s.hideSeconds = clamp(o.optInt("hide", s.hideSeconds), 5, 120);
        s.teamLive = o.optBoolean("teamLive", s.teamLive);
        s.shopEnabled = o.optBoolean("shop", s.shopEnabled);
        s.mode = normalizeMode(o.optString("mode", MODE_CLASSIC));
        s.zombieConversionSeconds = clamp(o.optInt("zombieConversion", s.zombieConversionSeconds), 5, 60);
        s.zombieSenseIntervalSeconds = clamp(o.optInt("zombieSense", s.zombieSenseIntervalSeconds), 15, 120);
        s.assassinKillRadiusMeters = clamp(o.optInt("assassinRadius", s.assassinKillRadiusMeters), 10, 60);
        s.assassinTargetPingOnly = o.optBoolean("assassinPingOnly", s.assassinTargetPingOnly);
        s.iceThawRadiusMeters = clamp(o.optInt("iceThawRadius", s.iceThawRadiusMeters), 5, 40);
        s.iceThawHoldSeconds = clamp(o.optInt("iceThawHold", s.iceThawHoldSeconds), 3, 15);
        s.iceMeltSeconds = clamp(o.optInt("iceMelt", s.iceMeltSeconds), 20, 120);
        return s;
    }

    public static String normalizeMode(String raw) {
        if (MODE_ZOMBIE.equals(raw) || MODE_ASSASSIN.equals(raw) || MODE_ICE.equals(raw)) {
            return raw;
        }
        return MODE_CLASSIC;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
