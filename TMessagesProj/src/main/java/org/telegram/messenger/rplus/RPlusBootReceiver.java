package org.telegram.messenger.rplus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.FileLog;

public class RPlusBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
            || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            try {
                RPlusScheduleManager.rescheduleAll(context);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }
}
