package com.zonehunt.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class LobbySnapshot {

    public static final String STATE_LOBBY = "LOBBY";
    public static final String STATE_RUNNING = "RUNNING";
    public static final String STATE_ENDED = "ENDED";

    public String code = "";
    public String gameId = "";
    public String hostId = "";
    public String hostName = "";
    public String state = STATE_LOBBY;
    public String seekerMode = "RANDOM";
    public String seekerId = "";
    public long createdAt = 0;
    public long updatedAt = 0;
    public GameSettings settings = new GameSettings();
    public final List<Player> players = new ArrayList<>();

    public Player findPlayer(String id) {
        for (Player p : players) {
            if (p.id != null && p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    public boolean hasPlayer(String id) {
        return findPlayer(id) != null;
    }

    public int aliveHiders() {
        int count = 0;
        for (Player p : players) {
            if (p.isHider() && p.alive) {
                count++;
            }
        }
        return count;
    }

    /** Zombie mode: uninfected survivors that are still in the round. */
    public int aliveSurvivors() {
        int count = 0;
        for (Player p : players) {
            if (p.isSurvivor() && p.alive) {
                count++;
            }
        }
        return count;
    }

    /** Zombie mode: initial zombies plus every turned survivor. */
    public int aliveZombies() {
        int count = 0;
        for (Player p : players) {
            if (p.isZombie() && p.alive) {
                count++;
            }
        }
        return count;
    }

    /** Ice mode: hiders that are neither frozen nor eliminated. */
    public int activeHiders() {
        int count = 0;
        for (Player p : players) {
            if (p.isSurvivor() && p.alive && !p.frozen) {
                count++;
            }
        }
        return count;
    }

    /** Ice mode: hiders currently frozen and waiting for help. */
    public int frozenHiders() {
        int count = 0;
        for (Player p : players) {
            if (p.isHider() && p.alive && p.frozen) {
                count++;
            }
        }
        return count;
    }

    /** Assassin mode: players still alive. */
    public int aliveAssassins() {
        int count = 0;
        for (Player p : players) {
            if (p.alive && p.isAssassin()) {
                count++;
            }
        }
        return count;
    }

    public Player seeker() {
        for (Player p : players) {
            if (p.isSeeker()) {
                return p;
            }
        }
        return null;
    }

    /**
     * The selected seekers are stored as a comma separated list in seekerId so
     * old lobby documents stay readable. Multiple seekers are supported.
     */
    public List<String> seekerIds() {
        List<String> result = new ArrayList<>();
        if (seekerId == null || seekerId.isEmpty()) {
            return result;
        }
        for (String part : seekerId.split(",")) {
            String id = part == null ? "" : part.trim();
            if (!id.isEmpty() && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    public boolean isSeekerSelected(String id) {
        return id != null && seekerIds().contains(id);
    }

    public void setSeekerIds(List<String> ids) {
        StringBuilder builder = new StringBuilder();
        if (ids != null) {
            for (String id : ids) {
                if (id == null || id.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(id);
            }
        }
        seekerId = builder.toString();
    }

    public int aliveSeekers() {
        int count = 0;
        for (Player p : players) {
            if (p.isSeeker() && p.alive) {
                count++;
            }
        }
        return count;
    }

    public LobbySnapshot copy() {
        LobbySnapshot s = new LobbySnapshot();
        s.code = code;
        s.gameId = gameId;
        s.hostId = hostId;
        s.hostName = hostName;
        s.state = state;
        s.seekerMode = seekerMode;
        s.seekerId = seekerId;
        s.createdAt = createdAt;
        s.updatedAt = updatedAt;
        s.settings = settings == null ? new GameSettings() : settings.copy();
        for (Player p : players) {
            s.players.add(p.copy());
        }
        return s;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("v", 1);
        o.put("code", code);
        o.put("gameId", gameId);
        o.put("hostId", hostId);
        o.put("hostName", hostName);
        o.put("state", state);
        o.put("seekerMode", seekerMode);
        o.put("seekerId", seekerId);
        o.put("createdAt", createdAt);
        o.put("updatedAt", updatedAt);
        o.put("settings", settings.toJson());
        JSONArray arr = new JSONArray();
        for (Player p : players) {
            arr.put(p.toLobbyJson());
        }
        o.put("players", arr);
        return o;
    }

    public static LobbySnapshot fromJson(JSONObject o) throws JSONException {
        LobbySnapshot s = new LobbySnapshot();
        s.code = o.optString("code", "");
        s.gameId = o.optString("gameId", "");
        s.hostId = o.optString("hostId", "");
        s.hostName = o.optString("hostName", "");
        s.state = o.optString("state", STATE_LOBBY);
        s.seekerMode = o.optString("seekerMode", "RANDOM");
        s.seekerId = o.optString("seekerId", "");
        s.createdAt = o.optLong("createdAt", 0);
        s.updatedAt = o.optLong("updatedAt", 0);
        s.settings = GameSettings.fromJson(o.optJSONObject("settings"));
        JSONArray arr = o.optJSONArray("players");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p != null) {
                    s.players.add(Player.fromLobbyJson(p));
                }
            }
        }
        return s;
    }
}
