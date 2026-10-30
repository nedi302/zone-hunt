package com.zonehunt.app.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.zonehunt.app.MainActivity;
import com.zonehunt.app.R;

/**
 * Local event notifications. They are triggered by the game state on the device
 * itself; no Firebase project or additional server is required.
 */
public final class NotificationHelper {

    private static final String CHANNEL_ID = "spiel_ereignisse";
    private static final int ID_START = 1001;
    private static final int ID_HUNT = 1002;
    private static final int ID_END = 1003;
    private static final int ID_ZONE = 2001;

    private NotificationHelper() {
    }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notif_channel_desc));
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }

    public static void gameStarted(Context context, boolean seeker, int hideSeconds) {
        int seconds = Math.max(1, hideSeconds);
        if (seeker) {
            notify(context, ID_START,
                    context.getString(R.string.notif_started_seeker_title),
                    context.getString(R.string.notif_started_seeker_text, seconds),
                    false);
        } else {
            notify(context, ID_START,
                    context.getString(R.string.notif_started_hider_title),
                    context.getString(R.string.notif_started_hider_text, seconds),
                    false);
        }
    }

    public static void huntStarted(Context context, boolean seeker) {
        if (seeker) {
            cancel(context, ID_START);
            notify(context, ID_HUNT,
                    context.getString(R.string.notif_hunt_seeker_title),
                    context.getString(R.string.notif_hunt_seeker_text),
                    false);
        } else {
            cancel(context, ID_START);
            notify(context, ID_HUNT,
                    context.getString(R.string.notif_hunt_hider_title),
                    context.getString(R.string.notif_hunt_hider_text),
                    false);
        }
    }

    public static void zoneShrinking(Context context, int radiusMeters, int percent) {
        String text = percent <= 20
                ? context.getString(R.string.notif_zone_tight, radiusMeters)
                : context.getString(R.string.notif_zone_size, radiusMeters);
        notify(context, ID_ZONE, context.getString(R.string.notif_zone_title), text, false);
    }

    public static void gameEnded(Context context, String title, String text) {
        cancel(context, ID_START);
        cancel(context, ID_HUNT);
        cancel(context, ID_ZONE);
        notify(context, ID_END, title, text, false);
    }

    public static void cancelGameNotifications(Context context) {
        cancel(context, ID_START);
        cancel(context, ID_HUNT);
        cancel(context, ID_ZONE);
        cancel(context, ID_END);
    }

    private static void notify(Context context, int id, String title, String text, boolean silent) {
        if (!canNotify(context)) {
            return;
        }
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_radar)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(silent ? NotificationCompat.PRIORITY_DEFAULT
                        : NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT);
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException ignored) {
        }
    }

    private static void cancel(Context context, int id) {
        try {
            NotificationManagerCompat.from(context).cancel(id);
        } catch (Exception ignored) {
        }
    }

    private static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }
}
