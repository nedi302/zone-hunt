package com.zonehunt.app.model;

import java.util.Locale;

/**
 * A fake map marker bought in the shop. Only seekers render it; for them it
 * looks exactly like a normal frozen ping marker carrying the buyer's name.
 */
public class DecoyMarker {

    public String id = "";
    public String ownerId = "";
    public String ownerName = "";
    public double lat = 0;
    public double lng = 0;
    /** Hunting time in ms at which the decoy disappears. */
    public long untilHuntMs = 0;

    public DecoyMarker copy() {
        DecoyMarker d = new DecoyMarker();
        d.id = id;
        d.ownerId = ownerId;
        d.ownerName = ownerName;
        d.lat = lat;
        d.lng = lng;
        d.untilHuntMs = untilHuntMs;
        return d;
    }

    public String initials() {
        String clean = ownerName == null ? "" : ownerName.trim();
        if (clean.isEmpty()) {
            return "?";
        }
        String[] parts = clean.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length()))
                    .toUpperCase(Locale.GERMANY);
        }
        String first = parts[0].substring(0, 1);
        String second = parts[parts.length - 1].substring(0, 1);
        return (first + second).toUpperCase(Locale.GERMANY);
    }
}
