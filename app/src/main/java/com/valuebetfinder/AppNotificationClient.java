package com.valuebetfinder;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import java.util.HashSet;
import java.util.Set;

public final class AppNotificationClient {
    private static final String CHANNEL_ID = "arbiedge_urgent_arb_v3";
    private static final String PREFS = "arbiedge_notifications";
    private static final String SEEN_IDS = "seen_ids";
    private static final int REQUEST_CODE = 7001;
    private static final long[] VIBRATION_PATTERN = {0L, 350L, 180L, 350L};

    private AppNotificationClient() { }

    public static void requestPermissionIfNeeded(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE);
        }
    }

    public static void showIfNew(Context context, AppNotification item) {
        if (item == null || item.getId() == null || item.getId().trim().isEmpty()) return;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(preferences.getStringSet(SEEN_IDS, new HashSet<String>()));
        if (seen.contains(item.getId())) return;
        seen.add(item.getId());
        preferences.edit().putStringSet(SEEN_IDS, seen).apply();

        ensureChannel(context);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Intent openApp = new Intent(context, ArbOnlyActivity.class)
                .putExtra(ArbOnlyActivity.EXTRA_OPEN_SCREEN, "opportunities")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openAppIntent = PendingIntent.getActivity(
                context,
                Math.abs(item.getId().hashCode()),
                openApp,
                pendingFlags);
        builder.setSmallIcon(R.drawable.arbiedge_mark)
                .setContentTitle(item.getTitle())
                .setContentText(item.getMessage())
                .setStyle(new Notification.BigTextStyle().bigText(item.getMessage()))
                .setContentIntent(openAppIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setSound(alertSoundUri(context))
                .setVibrate(VIBRATION_PATTERN)
                .setLights(Color.GREEN, 700, 500);

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(Math.abs(item.getId().hashCode()), builder.build());
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        Uri sound = alertSoundUri(context);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "ArbiEdge hitni arb", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Glasan alarm kada stigne nova arbitrazna prilika");
        channel.setSound(sound, audioAttributes);
        channel.enableVibration(true);
        channel.setVibrationPattern(VIBRATION_PATTERN);
        channel.enableLights(true);
        channel.setLightColor(Color.GREEN);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    private static Uri alertSoundUri(Context context) {
        return Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.arbiedge_alert);
    }
}
