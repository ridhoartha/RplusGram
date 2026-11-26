package org.telegram.messenger.rplus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RPlusBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            RPlusScheduleManager.rescheduleAll(context);
        }
    }
}
