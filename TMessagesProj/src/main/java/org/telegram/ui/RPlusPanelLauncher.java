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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.rplus.RPlusBroadcastManager;
import org.telegram.messenger.rplus.RPlusScheduleManager;
import org.telegram.messenger.rplus.RPlusStore;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Right-edge drawer tab for the R+ broadcast screen. It is a visible, draggable
 * tab rather than a floating circular button.
 */
public final class RPlusPanelLauncher {

    private static final String TAG = "rplus_handle";
    private static final int BRAND_TOP = 0xff9a6bff;
    private static final int BRAND_BOTTOM = 0xff6a3ed6;
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

        final FrameLayout tab = new FrameLayout(activity);
        tab.setElevation(dp(10));

        boolean dark = Theme.isCurrentThemeDark();
        int tabTop = dark ? 0xff2b1d40 : 0xfff7f2ff;
        int tabBottom = dark ? 0xff1f1530 : 0xffece2ff;
        int stroke = dark ? 0x669a6bff : 0x449a6bff;
        int contentColor = dark ? 0xffc4aaff : 0xff6f42d9;

        GradientDrawable tabBackground = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{tabTop, tabBottom}
        );
        tabBackground.setCornerRadii(new float[]{
            dp(18), dp(18),
            0, 0,
            0, 0,
            dp(18), dp(18)
        });
        tabBackground.setStroke(dp(1), stroke);
        GradientDrawable tabMask = new GradientDrawable();
        tabMask.setColor(Color.WHITE);
        tabMask.setCornerRadii(new float[]{
            dp(18), dp(18),
            0, 0,
            0, 0,
            dp(18), dp(18)
        });
        tab.setBackground(new RippleDrawable(
            ColorStateList.valueOf(0x339a6bff),
            tabBackground,
            tabMask
        ));

        View grip = new View(activity);
        GradientDrawable gripBackground = new GradientDrawable();
        gripBackground.setColor(contentColor);
        gripBackground.setCornerRadius(dp(2));
        grip.setBackground(gripBackground);
        FrameLayout.LayoutParams gripParams = new FrameLayout.LayoutParams(
            dp(3),
            dp(44),
            Gravity.LEFT | Gravity.CENTER_VERTICAL
        );
        gripParams.leftMargin = dp(7);
        tab.addView(grip, gripParams);

        ImageView chevron = new ImageView(activity);
        chevron.setImageResource(R.drawable.ic_ab_back);
        chevron.setColorFilter(contentColor);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams chevronParams = new FrameLayout.LayoutParams(
            dp(22),
            dp(22),
            Gravity.CENTER
        );
        chevronParams.leftMargin = dp(4);
        tab.addView(chevron, chevronParams);

        final View dot = new View(activity);
        GradientDrawable dotBackground = new GradientDrawable();
        dotBackground.setShape(GradientDrawable.OVAL);
        dotBackground.setColor(BADGE_ALERT);
        dotBackground.setStroke(dp(1), 0xccffffff);
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
                            int index = Math.min(manager.getCompleted(), total);
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

        FrameLayout.LayoutParams tabParams = new FrameLayout.LayoutParams(
            dp(40),
            dp(156),
            Gravity.RIGHT | Gravity.CENTER_VERTICAL
        );
        container.addView(tab, tabParams);

        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
            dp(9),
            dp(9),
            Gravity.RIGHT | Gravity.TOP
        );
        dotParams.topMargin = dp(8);
        dotParams.rightMargin = dp(2);
        container.addView(dot, dotParams);

        final float[] downX = new float[1];
        final boolean[] moved = new boolean[1];
        container.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    moved[0] = false;
                    tab.animate().cancel();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downX[0];
                    if (dx < -dp(4)) {
                        moved[0] = true;
                    }
                    if (dx < 0) {
                        tab.setTranslationX(Math.max(dx, -dp(20)));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float dx = event.getRawX() - downX[0];
                    boolean open = moved[0] && dx < -dp(12);
                    tab.animate().translationX(0).setDuration(140).start();
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
            64,
            168,
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
        source.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        source.animate().scaleX(.97f).scaleY(.97f).setDuration(70).withEndAction(() -> {
            source.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
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
