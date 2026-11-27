package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.rplus.RPlusBroadcastManager;
import org.telegram.messenger.rplus.RPlusScheduleManager;
import org.telegram.messenger.rplus.RPlusStore;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Small floating shortcut that opens the R+ broadcast control screen.
 */
public final class RPlusPanelLauncher {

    private static final String TAG = "rplus_handle";
    private static final int BRAND = 0xff7a4fe0;
    private static final int BADGE_COLOR = 0xffd92d43;

    private RPlusPanelLauncher() {
    }

    public static void attach(Activity activity, ViewGroup parent) {
        if (activity == null || parent == null || parent.findViewWithTag(TAG) != null) {
            return;
        }
        try {
            RPlusScheduleManager.rescheduleAll(activity);
        } catch (Throwable e) {
            FileLog.e(e);
        }

        final TextView button = new TextView(activity);
        button.setText("R+");
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        button.setElevation(dp(6));
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setShape(GradientDrawable.OVAL);
        buttonBackground.setColor(BRAND);
        GradientDrawable buttonMask = new GradientDrawable();
        buttonMask.setShape(GradientDrawable.OVAL);
        buttonMask.setColor(Color.WHITE);
        button.setBackground(new RippleDrawable(
            ColorStateList.valueOf(0x33ffffff),
            buttonBackground,
            buttonMask
        ));

        final TextView badge = new TextView(activity);
        badge.setTextSize(9);
        badge.setTextColor(Color.WHITE);
        badge.setTypeface(AndroidUtilities.bold());
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(18));
        badge.setMinHeight(dp(18));
        badge.setPadding(dp(4), dp(1), dp(4), dp(1));
        badge.setElevation(dp(8));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setShape(GradientDrawable.OVAL);
        badgeBackground.setColor(BADGE_COLOR);
        badge.setBackground(badgeBackground);
        badge.setVisibility(View.GONE);

        FrameLayout container = new FrameLayout(activity) {
            private final Handler handler = new Handler(Looper.getMainLooper());
            private final Runnable updater = new Runnable() {
                @Override
                public void run() {
                    int account = UserConfig.selectedAccount;
                    if (!UserConfig.isValidAccount(account)) {
                        setVisibility(View.GONE);
                        handler.postDelayed(this, 5000);
                        return;
                    }
                    boolean visible = UserConfig.getInstance(account).isClientActivated()
                        && !SharedConfig.appLocked;
                    setVisibility(visible ? View.VISIBLE : View.GONE);
                    long delay = 5000L;
                    if (visible) {
                        RPlusBroadcastManager manager = RPlusBroadcastManager.getInstance(account);
                        if (manager.isRunning()) {
                            delay = 1000L;
                            int total = Math.max(manager.getTotal(), 0);
                            int index = Math.min(manager.getIndex(), total);
                            badge.setText(index + "/" + total);
                            badge.setVisibility(View.VISIBLE);
                            setContentDescription("R+ Broadcast, " + index + " dari " + total);
                        } else {
                            int alerts = RPlusStore.getAlerts(account).size();
                            if (alerts > 0) {
                                badge.setText(alerts > 99 ? "99+" : String.valueOf(alerts));
                                badge.setVisibility(View.VISIBLE);
                                setContentDescription("R+ Broadcast, " + alerts + " pesan baru");
                            } else {
                                badge.setVisibility(View.GONE);
                                setContentDescription("Buka R+ Broadcast");
                            }
                        }
                    }
                    handler.postDelayed(this, delay);
                }
            };

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                handler.removeCallbacks(updater);
                handler.post(updater);
            }

            @Override
            protected void onWindowVisibilityChanged(int visibility) {
                super.onWindowVisibilityChanged(visibility);
                handler.removeCallbacks(updater);
                if (visibility == View.VISIBLE) {
                    handler.post(updater);
                }
            }

            @Override
            protected void onDetachedFromWindow() {
                handler.removeCallbacks(updater);
                super.onDetachedFromWindow();
            }
        };
        container.setTag(TAG);
        container.setVisibility(View.GONE);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        container.setContentDescription("Buka R+ Broadcast");
        container.addView(button, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));

        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.RIGHT | Gravity.TOP
        );
        badgeParams.topMargin = -dp(2);
        badgeParams.rightMargin = -dp(2);
        container.addView(badge, badgeParams);

        container.setOnClickListener(v -> {
            int account = UserConfig.selectedAccount;
            if (!UserConfig.isValidAccount(account)
                || !UserConfig.getInstance(account).isClientActivated()) {
                return;
            }
            v.animate().scaleX(.92f).scaleY(.92f).setDuration(70).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                activity.startActivity(new Intent(activity, RPlusBroadcastActivity.class)
                    .putExtra(RPlusBroadcastActivity.EXTRA_ACCOUNT, account)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }).start();
        });

        parent.addView(container, LayoutHelper.createFrame(
            48,
            48,
            Gravity.RIGHT | Gravity.CENTER_VERTICAL,
            0,
            0,
            8,
            0
        ));
        container.bringToFront();
    }

    private static int dp(float value) {
        return AndroidUtilities.dp(value);
    }
}
