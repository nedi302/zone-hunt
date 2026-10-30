package com.zonehunt.app.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One chat line. Scope ALL is visible to everybody, TEAM only to players with
 * the same role (all hiders or all seekers).
 */
public class ChatMessage {

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_TEAM = "TEAM";

    public String id = "";
    public String senderId = "";
    public String senderName = "";
    public String role = "";
    public String scope = SCOPE_ALL;
    public String text = "";
    public long timestamp = 0;

    public ChatMessage() {
    }

    public boolean isTeam() {
        return SCOPE_TEAM.equals(scope);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("msgId", id);
        o.put("senderId", senderId);
        o.put("name", senderName);
        o.put("role", role);
        o.put("scope", scope);
        o.put("text", text);
        o.put("ts", timestamp);
        return o;
    }

    public static ChatMessage fromJson(JSONObject o) {
        ChatMessage m = new ChatMessage();
        if (o == null) {
            return m;
        }
        m.id = o.optString("msgId", "");
        m.senderId = o.optString("senderId", o.optString("pid", ""));
        m.senderName = o.optString("name", "Player");
        m.role = o.optString("role", "");
        m.scope = SCOPE_TEAM.equals(o.optString("scope", "")) ? SCOPE_TEAM : SCOPE_ALL;
        m.text = o.optString("text", "");
        m.timestamp = o.optLong("ts", 0);
        return m;
    }
}
