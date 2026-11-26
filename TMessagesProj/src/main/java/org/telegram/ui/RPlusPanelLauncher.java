package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.rplus.RPlusBroadcastManager;
import org.telegram.messenger.rplus.RPlusStore;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public final class RPlusPanelLauncher {

    private RPlusPanelLauncher() {
    }

    public static void attach(Activity activity, ViewGroup parent) {
        if (activity == null || parent == null || parent.findViewWithTag("rplus_handle") != null) {
            return;
        }
        TextView handle = new TextView(activity) {
            private final Handler handler = new Handler(Looper.getMainLooper());
            private final Runnable updater = new Runnable() {
                @Override
                public void run() {
                    int account = UserConfig.selectedAccount;
                    boolean visible = UserConfig.getInstance(account).isClientActivated() && !SharedConfig.appLocked;
                    setVisibility(visible ? VISIBLE : GONE);
                    if (visible) {
                        RPlusBroadcastManager manager = RPlusBroadcastManager.getInstance(account);
                        int alerts = RPlusStore.getAlerts(account).size();
                        String text = manager.isRunning()
                            ? "R+\n" + Math.min(manager.getIndex(), manager.getTotal()) + "/" + manager.getTotal()
                            : alerts > 0 ? "R+\n" + (alerts > 99 ? "99+" : alerts) : "R+\nCTRL";
                        setText(text);
                    }
                    handler.postDelayed(this, 1000);
                }
            };

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                handler.removeCallbacks(updater);
                handler.post(updater);
            }

            @Override
            protected void onDetachedFromWindow() {
                handler.removeCallbacks(updater);
                super.onDetachedFromWindow();
            }
        };
        handle.setTag("rplus_handle");
        handle.setText("R+\nCTRL");
        handle.setTextColor(Color.WHITE);
        handle.setTextSize(11);
        handle.setTypeface(AndroidUtilities.bold());
        handle.setGravity(Gravity.CENTER);
        handle.setPadding(AndroidUtilities.dp(3), AndroidUtilities.dp(8), AndroidUtilities.dp(3), AndroidUtilities.dp(8));
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Theme.isCurrentThemeDark() ? 0xff9a6bff : 0xff8054ea, 0xff6035c8}
        );
        background.setCornerRadii(new float[]{
            AndroidUtilities.dp(16), AndroidUtilities.dp(16),
            0, 0, 0, 0,
            AndroidUtilities.dp(16), AndroidUtilities.dp(16)
        });
        handle.setBackground(background);
        handle.setElevation(AndroidUtilities.dp(8));
        handle.setOnClickListener(v -> {
            int account = UserConfig.selectedAccount;
            if (!UserConfig.getInstance(account).isClientActivated()) {
                return;
            }
            v.animate().scaleX(.92f).scaleY(.92f).setDuration(70).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                activity.startActivity(new Intent(activity, RPlusBroadcastActivity.class)
                    .putExtra(RPlusBroadcastActivity.EXTRA_ACCOUNT, account)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }).start();
        });
        parent.addView(handle, LayoutHelper.createFrame(42, 92, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
        handle.bringToFront();
    }
}
