package com.zonehunt.app.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class Player {

    public String id = "";
    public String name = "";
    public String role = "";
    public boolean alive = true;
    public boolean connected = true;

    public double lat = 0;
    public double lng = 0;
    public float accuracy = 0;
    public boolean hasLocation = false;
    public long lastSeenElapsed = 0;

    public String catchHash = "";
    public String eliminatedReason = "";

    /** Zombie mode: this survivor has joined the horde. */
    public boolean infected = false;
    /** Ice mode: this hider is frozen until a teammate thaws them. */
    public boolean frozen = false;
    /** Assassin mode: eliminations scored by this player. */
    public int kills = 0;
    /** Assassin mode: the secret target this player has to eliminate. */
    public String targetId = "";
    /** Hunting time at which the player was turned into a zombie. */
    public long infectedAtHuntMs = 0;
    /** Hunting time at which the player was frozen. */
    public long frozenSinceHuntMs = 0;

    public Player() {
    }

    public Player copy() {
        Player p = new Player();
        p.id = id;
        p.name = name;
        p.role = role;
        p.alive = alive;
        p.connected = connected;
        p.lat = lat;
        p.lng = lng;
        p.accuracy = accuracy;
        p.hasLocation = hasLocation;
        p.lastSeenElapsed = lastSeenElapsed;
        p.catchHash = catchHash;
        p.eliminatedReason = eliminatedReason;
        p.infected = infected;
        p.frozen = frozen;
        p.kills = kills;
        p.targetId = targetId;
        p.infectedAtHuntMs = infectedAtHuntMs;
        p.frozenSinceHuntMs = frozenSinceHuntMs;
        return p;
    }

    public boolean isSeeker() {
        return "SEEKER".equals(role);
    }

    public boolean isHider() {
        return "HIDER".equals(role);
    }

    public boolean isAssassin() {
        return "ASSASSIN".equals(role);
    }

    /** Zombies are the initial seekers plus every turned survivor. */
    public boolean isZombie() {
        return isSeeker() || infected;
    }

    /** Uninfected hider: the survivor team in zombie mode. */
    public boolean isSurvivor() {
        return isHider() && !infected;
    }

    /**
     * Team key used for team chat, notifications and team colours. Zombie
     * mode keeps turned players in one team even though their base role is
     * still HIDER.
     */
    public String teamKey() {
        if (isAssassin()) {
            return "ASSASSIN";
        }
        if (isZombie()) {
            return "ZOMBIE";
        }
        if (isSeeker()) {
            return "SEEKER";
        }
        return "HIDER";
    }

    public String initials() {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            return "?";
        }
        String[] parts = clean.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.GERMANY);
        }
        String first = parts[0].substring(0, 1);
        String second = parts[parts.length - 1].substring(0, 1);
        return (first + second).toUpperCase(Locale.GERMANY);
    }

    public JSONObject toLobbyJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        if (role != null && !role.isEmpty()) {
            o.put("role", role);
        }
        if (infected) {
            o.put("infected", true);
        }
        if (frozen) {
            o.put("frozen", true);
        }
        if (kills > 0) {
            o.put("kills", kills);
        }
        if (targetId != null && !targetId.isEmpty()) {
            o.put("targetId", targetId);
        }
        if (infectedAtHuntMs > 0) {
            o.put("infectedAt", infectedAtHuntMs);
        }
        if (frozenSinceHuntMs > 0) {
            o.put("frozenSince", frozenSinceHuntMs);
        }
        return o;
    }

    public static Player fromLobbyJson(JSONObject o) {
        Player p = new Player();
        p.id = o.optString("id", "");
        p.name = o.optString("name", "Player");
        p.role = o.optString("role", "");
        p.infected = o.optBoolean("infected", false);
        p.frozen = o.optBoolean("frozen", false);
        p.kills = o.optInt("kills", 0);
        p.targetId = o.optString("targetId", "");
        p.infectedAtHuntMs = o.optLong("infectedAt", 0);
        p.frozenSinceHuntMs = o.optLong("frozenSince", 0);
        return p;
    }
}
