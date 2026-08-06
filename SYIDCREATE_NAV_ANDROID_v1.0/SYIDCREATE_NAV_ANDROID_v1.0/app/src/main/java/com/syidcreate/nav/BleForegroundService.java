package com.syidcreate.nav;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public final class BleForegroundService extends Service implements BleClient.Listener {
    private static final String CHANNEL_ID = "syidcreate_ble";
    private static final int NOTIFICATION_ID = 1201;
    public static final String ACTION_START = "com.syidcreate.nav.START";
    public static final String ACTION_STOP = "com.syidcreate.nav.STOP";
    private static volatile String latestNavigation = "Menunggu Google Maps";

    public static void start(Context context) {
        Intent intent = new Intent(context, BleForegroundService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, BleForegroundService.class).setAction(ACTION_STOP));
    }

    public static void reportNavigation(Context context, String navigation) {
        latestNavigation = navigation;
        start(context);
    }

    public static String latestNavigation() {
        return latestNavigation;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        SyidCreateApp.ble(this).addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            SyidCreateApp.ble(this).stop();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification(SyidCreateApp.ble(this).getState()));
        SyidCreateApp.ble(this).startAutoConnect();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        SyidCreateApp.ble(this).removeListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onBleState(String state, boolean connected) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(state));
    }

    @Override
    public void onBleLog(String line) {
        // MainActivity receives the detailed log directly from BleClient.
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Koneksi SYIDCREATE",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Menjaga koneksi BLE SYIDCREATE NAV tetap aktif");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String state) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Intent stopIntent = new Intent(this, BleForegroundService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("SYIDCREATE NAV • " + state)
                .setContentText(latestNavigation)
                .setStyle(new Notification.BigTextStyle().bigText(latestNavigation))
                .setContentIntent(openPendingIntent)
                .addAction(new Notification.Action.Builder(null, "Hentikan", stopPendingIntent).build())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }
}
