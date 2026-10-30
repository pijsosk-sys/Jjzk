package com.volkyz.relay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Holds the relay for as long as the user is playing. Without a foreground service Android stops
 * the process once Minecraft takes the screen, and the proxy dies mid-session.
 */
public class ProxyService extends Service {

    public static final String ACTION_START = "com.volkyz.relay.START";
    public static final String ACTION_STOP = "com.volkyz.relay.STOP";
    private static final String CHANNEL = "relay";
    private static final int NOTIFICATION = 1;

    private PowerManager.WakeLock wakeLock;

    public static void start(Context context, String remote, int port, boolean online) {
        Intent intent = new Intent(context, ProxyService.class)
                .setAction(ACTION_START)
                .putExtra("remote", remote)
                .putExtra("port", port)
                .putExtra("online", online);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, ProxyService.class).setAction(ACTION_STOP));
    }

    @Override public void onCreate() {
        super.onCreate();
        bridge.Bridge.init(getFilesDir().getAbsolutePath());
        bridge.Bridge.setLogger(LogBus.get());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_STOP : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            bridge.Bridge.stop();
            releaseWakeLock();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        String remote = intent.getStringExtra("remote");
        int port = intent.getIntExtra("port", 19132);
        boolean online = intent.getBooleanExtra("online", true);

        startForeground(NOTIFICATION, notification(remote, port));
        holdWakeLock();

        new Thread(() -> {
            try {
                bridge.Bridge.start(remote, port, online);
            } catch (Exception failure) {
                LogBus.get().log("start failed: " + failure.getMessage());
                stopSelf();
            }
        }, "relay-start").start();

        return START_STICKY;
    }

    private Notification notification(String remote, int port) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "Relay", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Relay running")
                .setContentText("127.0.0.1:" + port + " → " + remote)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }

    private void holdWakeLock() {
        if (wakeLock != null) return;
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "relay:session");
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    @Override public void onDestroy() {
        bridge.Bridge.stop();
        releaseWakeLock();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
