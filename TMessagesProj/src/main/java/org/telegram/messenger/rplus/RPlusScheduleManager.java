package org.telegram.messenger.rplus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public final class RPlusScheduleManager {

    private static final String ACTION_FIRE = "org.telegram.messenger.rplus.FIRE_SCHEDULE";

    private RPlusScheduleManager() {
    }

    public static RPlusStore.RPlusSchedule schedule(Context context, int account, int mode, String text, ArrayList<TLRPC.MessageEntity> entities, long timestamp) {
        RPlusStore.RPlusSchedule schedule = new RPlusStore.RPlusSchedule();
        schedule.id = System.currentTimeMillis();
        schedule.mode = mode;
        schedule.text = text;
        schedule.entities = RPlusStore.encodeEntities(entities);
        schedule.scheduledAt = timestamp;
        RPlusStore.addSchedule(account, schedule);
        setAlarm(context, account, schedule);
        return schedule;
    }

    public static void cancel(Context context, int account, long scheduleId) {
        RPlusStore.removeSchedule(account, scheduleId);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(alarmIntent(context, account, scheduleId));
    }

    public static void rescheduleAll(Context context) {
        try {
            for (int account : RPlusStore.getAccountsWithSchedules()) {
                for (RPlusStore.RPlusSchedule schedule : RPlusStore.getSchedules(account)) {
                    try {
                        setAlarm(context, account, schedule);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    static void fire(Context context, int account, long scheduleId) {
        RPlusStore.RPlusSchedule schedule = RPlusStore.getSchedule(account, scheduleId);
        if (schedule == null) {
            return;
        }
        RPlusStore.removeSchedule(account, scheduleId);
        if (!RPlusBroadcastService.start(context, account, schedule.mode, schedule.text, schedule.entities)) {
            // Keep it so the next foreground app launch can retry instead of silently losing it.
            RPlusStore.addSchedule(account, schedule);
        }
    }

    private static void setAlarm(Context context, int account, RPlusStore.RPlusSchedule schedule) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = alarmIntent(context, account, schedule.id);
        long triggerAt = Math.max(System.currentTimeMillis() + 1000L, schedule.scheduledAt);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                }
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } catch (Throwable fallbackError) {
                FileLog.e(fallbackError);
            }
        }
    }

    private static PendingIntent alarmIntent(Context context, int account, long scheduleId) {
        Intent intent = new Intent(context, RPlusScheduleReceiver.class)
            .setAction(ACTION_FIRE)
            .putExtra(RPlusScheduleReceiver.EXTRA_ACCOUNT, account)
            .putExtra(RPlusScheduleReceiver.EXTRA_SCHEDULE_ID, scheduleId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, (int) (scheduleId ^ (scheduleId >>> 32)), intent, flags);
    }
}
