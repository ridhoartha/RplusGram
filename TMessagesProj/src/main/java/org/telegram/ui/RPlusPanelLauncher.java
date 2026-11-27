package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.rplus.RPlusBroadcastManager;
import org.telegram.messenger.rplus.RPlusScheduleManager;
import org.telegram.messenger.rplus.RPlusStore;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Slim right-edge handle for the R+ broadcast screen. Drag it to the left (or tap it)
 * instead of showing a floating circular button over the chat list.
 */
public final class RPlusPanelLauncher {

    private static final String TAG = "rplus_handle";
    private static final int BRAND = 0xff7a4fe0;
    private static final int BADGE_ALERT = 0xffd92d43;
    private static final int BADGE_RUNNING = 0xff2f8fd8;

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

        final View handle = new View(activity);
        GradientDrawable handleBackground = new GradientDrawable();
        handleBackground.setColor(BRAND);
        handleBackground.setCornerRadius(dp(2));
        handle.setBackground(handleBackground);

        final View dot = new View(activity);
        GradientDrawable dotBackground = new GradientDrawable();
        dotBackground.setShape(GradientDrawable.OVAL);
        dotBackground.setColor(BADGE_ALERT);
        dot.setBackground(dotBackground);
        dot.setVisibility(View.GONE);

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
                        int alerts = 0;
                        if (manager.isRunning()) {
                            delay = 1000L;
                            int total = Math.max(manager.getTotal(), 0);
                            int index = Math.min(manager.getIndex(), total);
                            ((GradientDrawable) dot.getBackground()).setColor(BADGE_RUNNING);
                            dot.setVisibility(View.VISIBLE);
                            setContentDescription("R+ Broadcast berjalan, " + index + " dari " + total
                                + ". Geser ke kiri untuk membuka.");
                        } else {
                            alerts = RPlusStore.getAlerts(account).size();
                            if (alerts > 0) {
                                ((GradientDrawable) dot.getBackground()).setColor(BADGE_ALERT);
                                dot.setVisibility(View.VISIBLE);
                                setContentDescription("R+ Broadcast, " + alerts
                                    + " pesan baru. Geser ke kiri untuk membuka.");
                            } else {
                                dot.setVisibility(View.GONE);
                                setContentDescription("Geser ke kiri untuk membuka R+ Broadcast");
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
        container.setContentDescription("Geser ke kiri untuk membuka R+ Broadcast");
        container.setClickable(true);

        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(
            dp(4),
            dp(56),
            Gravity.RIGHT | Gravity.CENTER_VERTICAL
        );
        handleParams.rightMargin = dp(1);
        container.addView(handle, handleParams);

        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
            dp(8),
            dp(8),
            Gravity.RIGHT | Gravity.TOP
        );
        dotParams.topMargin = dp(12);
        dotParams.rightMargin = dp(4);
        container.addView(dot, dotParams);

        final float[] downX = new float[1];
        final boolean[] moved = new boolean[1];
        container.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    moved[0] = false;
                    handle.animate().cancel();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downX[0];
                    if (dx < -dp(4)) {
                        moved[0] = true;
                    }
                    if (dx < 0) {
                        handle.setTranslationX(Math.max(dx, -dp(20)));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float dx = event.getRawX() - downX[0];
                    boolean open = moved[0] && dx < -dp(12);
                    handle.animate().translationX(0).setDuration(120).start();
                    if (open || (!moved[0] && event.getActionMasked() == MotionEvent.ACTION_UP)) {
                        v.performClick();
                    }
                    return true;
                }
            }
            return false;
        });
        container.setOnClickListener(v -> open(activity, v));

        parent.addView(container, LayoutHelper.createFrame(
            48,
            72,
            Gravity.RIGHT | Gravity.CENTER_VERTICAL,
            0,
            0,
            0,
            0
        ));
        container.bringToFront();
    }

    private static void open(Activity activity, View source) {
        int account = UserConfig.selectedAccount;
        if (!UserConfig.isValidAccount(account)
            || !UserConfig.getInstance(account).isClientActivated()) {
            return;
        }
        source.animate().scaleX(.96f).scaleY(.96f).setDuration(70).withEndAction(() -> {
            source.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            try {
                activity.startActivity(new Intent(activity, RPlusBroadcastActivity.class)
                    .putExtra(RPlusBroadcastActivity.EXTRA_ACCOUNT, account)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }).start();
    }

    private static int dp(float value) {
        return AndroidUtilities.dp(value);
    }
}
