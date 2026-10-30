package com.zonehunt.app.util;

import android.content.Context;

import java.util.Locale;

import com.zonehunt.app.R;

public final class Geo {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private Geo() {
    }

    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    public static String formatDistance(int meters) {
        if (meters < 1000) {
            return meters + " m";
        }
        return String.format(Locale.getDefault(), "%.1f km", meters / 1000.0);
    }

    public static String formatClock(long millis) {
        long total = Math.max(0, millis / 1000);
        long minutes = total / 60;
        long seconds = total % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    public static String formatDuration(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    public static String ago(Context context, long elapsedMs) {
        if (elapsedMs <= 0) {
            return context.getString(R.string.time_no_ping);
        }
        long seconds = Math.max(0, elapsedMs / 1000);
        if (seconds < 2) {
            return context.getString(R.string.time_just_now);
        }
        if (seconds < 60) {
            return context.getString(R.string.time_seconds_ago, seconds);
        }
        return context.getString(R.string.time_minutes_ago, seconds / 60);
    }

    public static double latitudeOffset(double meters) {
        return meters / 111_320.0;
    }

    public static double longitudeOffset(double latitude, double meters) {
        return meters / (111_320.0 * Math.max(0.2, Math.cos(Math.toRadians(latitude))));
    }
}
