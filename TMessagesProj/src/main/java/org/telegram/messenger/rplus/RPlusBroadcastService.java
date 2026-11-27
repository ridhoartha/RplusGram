package org.telegram.messenger.rplus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.RPlusBroadcastActivity;

import java.util.LinkedHashMap;
import java.util.Map;

public class RPlusBroadcastService extends Service implements RPlusBroadcastManager.Listener {

    public static final String ACTION_START = "org.telegram.messenger.rplus.START_BROADCAST";
    public static final String ACTION_PAUSE = "org.telegram.messenger.rplus.PAUSE_BROADCAST";
    public static final String ACTION_RESUME = "org.telegram.messenger.rplus.RESUME_BROADCAST";
    public static final String ACTION_CANCEL = "org.telegram.messenger.rplus.CANCEL_BROADCAST";
    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_ENTITIES = "entities";

    private static final String CHANNEL_ID = "rplus_broadcast";
    private static final int NOTIFICATION_ID = 0x5250001;

    private int currentAccount;
    private RPlusBroadcastManager manager;
    private boolean foregroundStarted;
    private boolean finalNotificationShown;

    public static void start(Context context, int account, int mode, String text, String entities) {
        Intent intent = new Intent(context, RPlusBroadcastService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_ACCOUNT, account);
        intent.putExtra(EXTRA_MODE, mode);
        intent.putExtra(EXTRA_TEXT, text);
        intent.putExtra(EXTRA_ENTITIES, entities);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void pause(Context context, int account) {
        sendAction(context, account, ACTION_PAUSE);
    }

    public static void resume(Context context, int account) {
        sendAction(context, account, ACTION_RESUME);
    }

    public static void cancel(Context context, int account) {
        sendAction(context, account, ACTION_CANCEL);
    }

    private static void sendAction(Context context, int account, String action) {
        Intent intent = new Intent(context, RPlusBroadcastService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_ACCOUNT, account);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int account = intent.getIntExtra(EXTRA_ACCOUNT, -1);
        if (account < 0 || account >= org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT) {
            stopSelf();
            return START_NOT_STICKY;
        }
        attachAccount(account);
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            int mode = intent.getIntExtra(EXTRA_MODE, RPlusStore.MODE_GCAST);
            String text = intent.getStringExtra(EXTRA_TEXT);
            String encodedEntities = intent.getStringExtra(EXTRA_ENTITIES);
            manager.addListener(this);
            ensureForeground();
            if (!manager.start(mode, text, RPlusStore.decodeEntities(encodedEntities))) {
                postImmediateError("Broadcast tidak dapat dimulai", RPlusStore.isLimitLocked(account)
                    ? RPlusStore.getLimitReason(account)
                    : "Tidak ada target atau pesan masih kosong");
            }
        } else if (ACTION_PAUSE.equals(action)) {
            if (!manager.isRunning()) {
                stopSelf();
                return START_NOT_STICKY;
            }
            ensureForeground();
            manager.pause();
            updateNotification();
        } else if (ACTION_RESUME.equals(action)) {
            if (!manager.isRunning()) {
                stopSelf();
                return START_NOT_STICKY;
            }
            ensureForeground();
            manager.resume();
            updateNotification();
        } else if (ACTION_CANCEL.equals(action)) {
            if (!manager.isRunning()) {
                stopSelf();
                return START_NOT_STICKY;
            }
            ensureForeground();
            manager.cancel();
            updateNotification();
        }
        return START_NOT_STICKY;
    }

    private void attachAccount(int account) {
        if (manager != null && currentAccount != account) {
            manager.removeListener(this);
        }
        currentAccount = account;
        manager = RPlusBroadcastManager.getInstance(account);
        manager.addListener(this);
    }

    private void ensureForeground() {
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, buildNotification());
            foregroundStarted = true;
        }
    }

    private Notification buildNotification() {
        RPlusBroadcastManager snapshot = manager;
        String title = snapshot.getMode() == RPlusStore.MODE_GCAST ? "Gcast berjalan" : "Ucast berjalan";
        String text = snapshot.isRunning()
            ? snapshot.getProgressText() + (snapshot.getCurrentTarget() != null ? " · " + snapshot.getCurrentTarget() : "")
            : "Menyiapkan broadcast";
        if (snapshot.isPaused() && snapshot.getPauseReason() != null) {
            text = "Dijeda · " + snapshot.getPauseReason();
        }

        PendingIntent openIntent = PendingIntent.getActivity(
            this,
            100,
            new Intent(this, RPlusBroadcastActivity.class)
                .putExtra(RPlusBroadcastActivity.EXTRA_ACCOUNT, currentAccount)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentFlags()
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(Math.max(1, snapshot.getTotal()), Math.min(snapshot.getIndex(), Math.max(1, snapshot.getTotal())), snapshot.getTotal() == 0);

        if (snapshot.isPaused()) {
            builder.addAction(0, "Lanjutkan", serviceAction(ACTION_RESUME, 201));
        } else {
            builder.addAction(0, "Jeda", serviceAction(ACTION_PAUSE, 202));
        }
        builder.addAction(0, "Batalkan", serviceAction(ACTION_CANCEL, 203));
        return builder.build();
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, RPlusBroadcastService.class)
            .setAction(action)
            .putExtra(EXTRA_ACCOUNT, currentAccount);
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentFlags()
        );
    }

    private static int pendingIntentFlags() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private void updateNotification() {
        if (!foregroundStarted) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void postCompletion() {
        if (finalNotificationShown) {
            return;
        }
        finalNotificationShown = true;
        String modeName = manager.getMode() == RPlusStore.MODE_GCAST ? "Gcast" : "Ucast";
        int sent = manager.getSent();
        int failed = manager.getFailed();
        StringBuilder summary = new StringBuilder();
        summary.append("Berhasil terkirim ke ").append(sent)
            .append(manager.getMode() == RPlusStore.MODE_GCAST ? " grup." : " pengguna.");
        if (failed > 0) {
            summary.append(" ").append(failed).append(" gagal");
            LinkedHashMap<String, Integer> reasons = manager.getFailureReasons();
            if (!reasons.isEmpty()) {
                Map.Entry<String, Integer> first = reasons.entrySet().iterator().next();
                summary.append(": ").append(first.getValue()).append(" ").append(first.getKey().toLowerCase());
            }
            summary.append(".");
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle(modeName + " selesai")
            .setContentText(summary)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                this,
                101,
                new Intent(this, RPlusBroadcastActivity.class)
                    .putExtra(RPlusBroadcastActivity.EXTRA_ACCOUNT, currentAccount)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentFlags()
            ));
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        foregroundStarted = false;
        stopSelf();
    }

    private void postImmediateError(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID + 2, builder.build());
        stopForeground(true);
        foregroundStarted = false;
        stopSelf();
    }

    @Override
    public void onBroadcastStateChanged(int account) {
        if (account != currentAccount || manager == null) {
            return;
        }
        if (manager.isRunning()) {
            ensureForeground();
            updateNotification();
        } else if (foregroundStarted) {
            postCompletion();
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "R+ Broadcast",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Progres dan hasil broadcast R+ Gram");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (manager != null) {
            manager.removeListener(this);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
