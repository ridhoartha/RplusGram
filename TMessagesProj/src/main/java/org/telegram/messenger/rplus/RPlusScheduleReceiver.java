package org.telegram.messenger.rplus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RPlusScheduleReceiver extends BroadcastReceiver {

    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_SCHEDULE_ID = "schedule_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        int account = intent.getIntExtra(EXTRA_ACCOUNT, -1);
        long scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, 0);
        if (account >= 0 && scheduleId != 0) {
            RPlusScheduleManager.fire(context, account, scheduleId);
        }
    }
}
