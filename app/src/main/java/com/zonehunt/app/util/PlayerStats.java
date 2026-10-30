package com.zonehunt.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Local, device-wide player statistics and one-time hints.
 *
 * Distances are accumulated from GPS fixes while a round is running. The
 * calorie value is a walking estimate (about 55 kcal per kilometre) and is
 * intentionally shown as an approximation.
 */
public final class PlayerStats {

    private static final String PREFS = "zonenjagd_stats";
    private static final String KEY_TOTAL_DISTANCE = "total_distance_m";
    private static final String KEY_INTRO_PREFIX = "intro_seen_";
    private static final double KCAL_PER_KM = 55.0;

    private PlayerStats() {
    }

    public static double totalDistanceMeters(Context context) {
        return prefs(context).getFloat(KEY_TOTAL_DISTANCE, 0f);
    }

    public static int totalCalories(Context context) {
        return caloriesForMeters(totalDistanceMeters(context));
    }

    public static void addDistance(Context context, double meters) {
        if (meters <= 0) {
            return;
        }
        SharedPreferences p = prefs(context);
        double total = p.getFloat(KEY_TOTAL_DISTANCE, 0f) + meters;
        p.edit().putFloat(KEY_TOTAL_DISTANCE, (float) total).apply();
    }

    public static int caloriesForMeters(double meters) {
        return (int) Math.round(Math.max(0, meters) / 1000.0 * KCAL_PER_KM);
    }

    public static String formatKilometers(Context context, double meters) {
        return String.format(Locale.getDefault(), "%.1f", Math.max(0, meters) / 1000.0);
    }

    public static boolean hasSeenIntro(Context context, String playerName) {
        return prefs(context).getBoolean(introKey(playerName), false);
    }

    public static void markIntroSeen(Context context, String playerName) {
        prefs(context).edit().putBoolean(introKey(playerName), true).apply();
    }

    private static String introKey(String playerName) {
        String clean = playerName == null ? "" : playerName.trim().toLowerCase(Locale.GERMANY);
        clean = clean.replaceAll("[^a-z0-9äöüß_-]", "_");
        if (clean.isEmpty()) {
            clean = "spieler";
        }
        return KEY_INTRO_PREFIX + clean;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
