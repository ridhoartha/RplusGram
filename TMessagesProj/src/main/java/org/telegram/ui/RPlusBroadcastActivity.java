package org.telegram.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.rplus.RPlusBroadcastManager;
import org.telegram.messenger.rplus.RPlusBroadcastService;
import org.telegram.messenger.rplus.RPlusMessageAlerts;
import org.telegram.messenger.rplus.RPlusScheduleManager;
import org.telegram.messenger.rplus.RPlusStore;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BasePermissionsActivity;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityEnterView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class RPlusBroadcastActivity extends BasePermissionsActivity implements RPlusBroadcastManager.Listener {

    public static final String EXTRA_ACCOUNT = "rplus_account";

    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_GCAST = 1;
    private static final int SCREEN_UCAST = 2;
    private static final int SCREEN_BLACKLIST = 3;
    private static final int SCREEN_HISTORY = 4;
    private static final int SCREEN_ALERTS = 5;

    private int currentAccount;
    private RPlusBroadcastManager broadcastManager;
    private FrameLayout rootView;
    private FrameLayout panel;
    private View scrimView;
    private LinearLayout content;
    private TextView topProgressView;
    private TextView alertBadgeView;
    private int currentScreen = SCREEN_HOME;
    private int composerMode;
    private EditText composer;
    private TextView previewView;
    private TextView targetSummaryView;
    private TextView activeStatusView;
    private TextView pauseResumeButton;
    private boolean closing;

    private final int accentColor = 0xff9a6bff;
    private final int accentColorLight = 0xff8a5cf5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(R.style.Theme_TMessages_Transparent);
        super.onCreate(savedInstanceState);
        currentAccount = getIntent().getIntExtra(EXTRA_ACCOUNT, UserConfig.selectedAccount);
        if (currentAccount < 0 || currentAccount >= UserConfig.MAX_ACCOUNT_COUNT) {
            finish();
            return;
        }
        broadcastManager = RPlusBroadcastManager.getInstance(currentAccount);
        RPlusMessageAlerts.install();
        RPlusScheduleManager.rescheduleAll(this);
        setupWindow();
        buildRoot();
        showHome();
    }

    private void setupWindow() {
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Theme.isCurrentThemeDark() ? 0xcc120b1d : 0xccf7f2ff);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(
                Theme.isCurrentThemeDark() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }
    }

    private void buildRoot() {
        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(0x99000000);
        rootView.setOnClickListener(v -> closePanel());
        setContentView(rootView);

        scrimView = new View(this);
        scrimView.setBackgroundColor(0x33000000);
        scrimView.setOnClickListener(v -> closePanel());
        rootView.addView(scrimView, frameMatch());

        panel = new FrameLayout(this);
        panel.setClickable(true);
        panel.setElevation(AndroidUtilities.dp(18));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(isDark() ? 0xff171021 : 0xfffbf9ff);
        panelBackground.setCornerRadius(AndroidUtilities.dp(22));
        panelBackground.setStroke(AndroidUtilities.dp(1), isDark() ? 0xff3a2852 : 0xffe3d8fb);
        panel.setBackground(panelBackground);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            AndroidUtilities.dp(280),
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        panelParams.gravity = Gravity.LEFT;
        panelParams.topMargin = AndroidUtilities.dp(8);
        panelParams.bottomMargin = AndroidUtilities.dp(8);
        rootView.addView(panel, panelParams);
        rootView.post(this::updatePanelWidth);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        panel.addView(content, frameMatch());

        topProgressView = new TextView(this);
        topProgressView.setTextColor(Color.WHITE);
        topProgressView.setTextSize(12);
        topProgressView.setTypeface(AndroidUtilities.bold());
        topProgressView.setGravity(Gravity.CENTER);
        topProgressView.setPadding(dp(12), dp(7), dp(12), dp(7));
        topProgressView.setBackground(rounded(accentColor, 18));
        topProgressView.setVisibility(View.GONE);
        topProgressView.setOnClickListener(v -> showHome());
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        progressParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(18);
        panel.addView(topProgressView, progressParams);

        FrameLayout closeButtonContainer = new FrameLayout(this);
        TextView closeButton = new TextView(this);
        closeButton.setText("›");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(30);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setBackground(rounded(accentColor, 14));
        closeButton.setOnClickListener(v -> closePanel());
        closeButtonContainer.addView(closeButton, frameMatch());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(34), dp(64));
        closeParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        closeParams.rightMargin = -dp(26);
        panel.addView(closeButtonContainer, closeParams);

        panel.setTranslationX(-AndroidUtilities.displaySize.x * .8f);
        panel.animate().translationX(0).setDuration(280).start();
    }

    private void updatePanelWidth() {
        if (rootView == null || panel == null) {
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        lp.width = Math.round(rootView.getWidth() * 0.75f);
        panel.setLayoutParams(lp);
        panel.setTranslationX(-lp.width * 1.05f);
        panel.animate().translationX(0).setDuration(280).start();
    }

    private void closePanel() {
        if (closing) {
            return;
        }
        closing = true;
        panel.animate().translationX(-panel.getWidth() * 1.05f).setDuration(230).withEndAction(this::finish).start();
    }

    private void showHome() {
        currentScreen = SCREEN_HOME;
        composer = null;
        previewView = null;
        content.removeAllViews();
        content.addView(buildTopBar("R+ COMMAND", null, true), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(64)
        ));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(4), dp(18), dp(20));
        scrollView.addView(body, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        content.addView(scrollView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1
        ));

        body.addView(buildAccountCard());
        body.addView(space(14));

        if (broadcastManager.isRunning()) {
            body.addView(buildActiveBroadcastCard());
            body.addView(space(14));
        }

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(buildMenuTile("GCAST", "Broadcast ke grup", "G", () -> showComposer(RPlusStore.MODE_GCAST)), weightedTileParams());
        row1.addView(space(12));
        row1.addView(buildMenuTile("UCAST", "Broadcast ke pengguna", "U", () -> showComposer(RPlusStore.MODE_UCAST)), weightedTileParams());
        body.addView(row1);
        body.addView(space(12));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(buildMenuTile("BLACKLIST", "Kelola pengecualian", "B", this::showBlacklist), weightedTileParams());
        row2.addView(space(12));
        row2.addView(buildMenuTile("RIWAYAT", "Hasil broadcast", "H", this::showHistory), weightedTileParams());
        body.addView(row2);
        body.addView(space(16));

        TextView disclaimer = text(
            "Broadcast dapat menyebabkan pembatasan akun. Gunakan hanya pada chat yang Anda kelola atau penerima yang menyetujui. R+ GRAM bukan aplikasi resmi Telegram.",
            11,
            mutedTextColor()
        );
        disclaimer.setLineSpacing(0, 1.15f);
        disclaimer.setPadding(dp(4), dp(8), dp(4), 0);
        body.addView(disclaimer);
        updateProgressUi();
    }

    private View buildTopBar(String title, String backTitle, boolean showBell) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(12), dp(6));

        if (!showBell) {
            TextView back = new TextView(this);
            back.setText("‹");
            back.setTextSize(34);
            back.setTextColor(textColor());
            back.setGravity(Gravity.CENTER);
            back.setOnClickListener(v -> showHome());
            bar.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));
        }

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = text(title, 18, textColor());
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setLetterSpacing(.08f);
        titles.addView(titleView);
        if (!TextUtils.isEmpty(backTitle)) {
            TextView sub = text(backTitle, 11, mutedTextColor());
            titles.addView(sub);
        }
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.leftMargin = showBell ? dp(4) : dp(2);
        bar.addView(titles, titleParams);

        if (showBell) {
            FrameLayout bellContainer = new FrameLayout(this);
            TextView bell = text("●", 20, accentColor);
            bell.setGravity(Gravity.CENTER);
            bell.setOnClickListener(v -> showAlerts());
            bellContainer.addView(bell, frameMatch());
            alertBadgeView = text("", 9, Color.WHITE);
            alertBadgeView.setGravity(Gravity.CENTER);
            alertBadgeView.setBackground(rounded(0xffff5d7d, 9));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(18), dp(18));
            badgeParams.gravity = Gravity.RIGHT | Gravity.TOP;
            badgeParams.topMargin = dp(2);
            badgeParams.rightMargin = dp(1);
            bellContainer.addView(alertBadgeView, badgeParams);
            bar.addView(bellContainer, new LinearLayout.LayoutParams(dp(44), dp(44)));
            updateAlertBadge();
        }
        return bar;
    }

    private View buildAccountCard() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(isDark() ? 0xff261937 : 0xffffffff, 18));

        TLRPC.User self = org.telegram.messenger.MessagesController.getInstance(currentAccount)
            .getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        String name = self != null ? UserObject.getUserName(self) : "Akun Telegram";
        String username = self != null && !TextUtils.isEmpty(self.username) ? "@" + self.username : "Terhubung";

        TextView avatar = text(name.isEmpty() ? "R" : name.substring(0, 1).toUpperCase(Locale.ROOT), 22, Color.WHITE);
        avatar.setTypeface(AndroidUtilities.bold());
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(accentColor, 28));
        card.addView(avatar, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);
        TextView nameView = text(name, 16, textColor());
        nameView.setTypeface(AndroidUtilities.bold());
        info.addView(nameView);
        info.addView(text(username, 12, mutedTextColor()));
        TextView plan = text("R+ FREE · MASA UJI", 10, accentColor);
        plan.setTypeface(AndroidUtilities.bold());
        plan.setPadding(0, dp(5), 0, 0);
        info.addView(plan);
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return card;
    }

    private View buildMenuTile(String title, String subtitle, String glyph, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(14), dp(14), dp(14), dp(14));
        tile.setBackground(rounded(isDark() ? 0xff211630 : 0xfff1eaff, 18));
        tile.setOnClickListener(v -> {
            v.animate().scaleX(.97f).scaleY(.97f).setDuration(70).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                action.run();
            }).start();
        });
        TextView glyphView = text(glyph, 22, Color.WHITE);
        glyphView.setGravity(Gravity.CENTER);
        glyphView.setTypeface(AndroidUtilities.bold());
        glyphView.setBackground(rounded(accentColor, 15));
        tile.addView(glyphView, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView titleView = text(title, 14, textColor());
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setLetterSpacing(.05f);
        titleView.setPadding(0, dp(12), 0, 0);
        tile.addView(titleView);
        TextView subtitleView = text(subtitle, 11, mutedTextColor());
        subtitleView.setPadding(0, dp(3), 0, 0);
        tile.addView(subtitleView);
        return tile;
    }

    private LinearLayout.LayoutParams weightedTileParams() {
        return new LinearLayout.LayoutParams(0, dp(126), 1);
    }

    private void showComposer(int mode) {
        currentScreen = mode == RPlusStore.MODE_GCAST ? SCREEN_GCAST : SCREEN_UCAST;
        composerMode = mode;
        content.removeAllViews();
        String title = mode == RPlusStore.MODE_GCAST ? "GCAST" : "UCAST";
        String subtitle = mode == RPlusStore.MODE_GCAST ? "Broadcast grup" : "Maksimal 500 chat terbaru";
        content.addView(buildTopBar(title, subtitle, false), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(64)
        ));

        RPlusBroadcastManager.TargetSnapshot snapshot = RPlusBroadcastManager.buildTargets(currentAccount, mode);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(4), dp(18), dp(22));
        scroll.addView(body, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        content.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1
        ));

        targetSummaryView = text("", 12, textColor());
        targetSummaryView.setPadding(dp(12), dp(10), dp(12), dp(10));
        targetSummaryView.setBackground(rounded(isDark() ? 0xff211630 : 0xfff4efff, 14));
        targetSummaryView.setText(
            "Target siap: " + snapshot.targets.size()
                + "  •  Blacklist: " + snapshot.blacklisted
                + (snapshot.unavailable > 0 ? "  •  Tidak tersedia: " + snapshot.unavailable : "")
        );
        body.addView(targetSummaryView);
        body.addView(space(12));

        TextView composerLabel = sectionLabel("PESAN BROADCAST");
        body.addView(composerLabel);
        composer = new EditText(this);
        composer.setTextSize(16);
        composer.setTextColor(textColor());
        composer.setHintTextColor(hintColor());
        composer.setHint("Tulis pesan Telegram di sini…");
        composer.setGravity(Gravity.TOP | Gravity.START);
        composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.setMinLines(4);
        composer.setMaxLines(8);
        composer.setPadding(dp(12), dp(10), dp(12), dp(10));
        composer.setBackground(rounded(isDark() ? 0xff211630 : 0xffffffff, 14));
        body.addView(composer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        composer.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        ArrayList<RPlusStore.RPlusTemplate> templates = RPlusStore.getTemplates(currentAccount, mode);
        if (!templates.isEmpty() && !TextUtils.isEmpty(templates.get(0).text)) {
            RPlusStore.RPlusTemplate template = templates.get(0);
            CharSequence restored = ChatActivityEnterView.applyMessageEntities(
                RPlusStore.decodeEntities(template.entities),
                template.text,
                composer.getPaint().getFontMetricsInt()
            );
            composer.setText(restored);
            composer.setSelection(composer.length());
        }

        body.addView(space(12));
        body.addView(sectionLabel("PREVIEW"));
        previewView = text("", 15, textColor());
        previewView.setLineSpacing(dp(2), 1.08f);
        previewView.setPadding(dp(12), dp(10), dp(12), dp(10));
        previewView.setBackground(rounded(isDark() ? 0xff2a1b3d : 0xfff4efff, 14));
        body.addView(previewView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        updatePreview();

        body.addView(space(16));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(actionButton("SIMPAN", false, this::saveCurrentTemplate), weightButtonParams());
        actions.addView(space(8));
        actions.addView(actionButton("JADWAL", false, this::scheduleCurrent), weightButtonParams());
        body.addView(actions);
        body.addView(space(8));
        body.addView(actionButton("MULAI BROADCAST", true, this::confirmStart), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48)
        ));

        if (RPlusStore.isLimitLocked(currentAccount)) {
            TextView lock = text(
                "Broadcast dikunci sementara: " + RPlusStore.getLimitReason(currentAccount),
                12,
                0xffff6b81
            );
            lock.setPadding(dp(8), dp(12), dp(8), 0);
            body.addView(lock);
        }
    }

    private void updatePreview() {
        if (composer == null || previewView == null) {
            return;
        }
        ArrayList<TLRPC.MessageEntity> entities = collectEntities();
        CharSequence text = ChatActivityEnterView.applyMessageEntities(
            entities,
            composer.getText().toString(),
            previewView.getPaint().getFontMetricsInt()
        );
        previewView.setText(TextUtils.isEmpty(text) ? "Preview pesan akan muncul di sini." : text);
    }

    private ArrayList<TLRPC.MessageEntity> collectEntities() {
        if (composer == null) {
            return new ArrayList<>();
        }
        CharSequence[] value = {new SpannableStringBuilder(composer.getText())};
        ArrayList<TLRPC.MessageEntity> result = MediaDataController.getInstance(currentAccount)
            .getEntities(value, true, false);
        return result == null ? new ArrayList<>() : result;
    }

    private void saveCurrentTemplate() {
        if (composer == null || TextUtils.isEmpty(composer.getText())) {
            toast("Tulis pesan terlebih dahulu");
            return;
        }
        ArrayList<TLRPC.MessageEntity> entities = collectEntities();
        RPlusStore.saveTemplate(
            currentAccount,
            composerMode,
            composerMode == RPlusStore.MODE_GCAST ? "Draft Gcast" : "Draft Ucast",
            composer.getText().toString(),
            entities
        );
        toast("Draft tersimpan");
    }

    private void scheduleCurrent() {
        if (composer == null || TextUtils.isEmpty(composer.getText())) {
            toast("Tulis pesan terlebih dahulu");
            return;
        }
        if (RPlusStore.isLimitLocked(currentAccount)) {
            showLimitDialog();
            return;
        }
        final Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePicker = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                TimePickerDialog timePicker = new TimePickerDialog(
                    this,
                    (timeView, hourOfDay, minute) -> {
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        calendar.set(Calendar.MINUTE, minute);
                        calendar.set(Calendar.SECOND, 0);
                        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                            toast("Waktu jadwal harus di masa depan");
                            return;
                        }
                        ArrayList<TLRPC.MessageEntity> entities = collectEntities();
                        RPlusStore.saveTemplate(
                            currentAccount,
                            composerMode,
                            composerMode == RPlusStore.MODE_GCAST ? "Draft Gcast" : "Draft Ucast",
                            composer.getText().toString(),
                            entities
                        );
                        RPlusScheduleManager.schedule(
                            this,
                            currentAccount,
                            composerMode,
                            composer.getText().toString(),
                            entities,
                            calendar.getTimeInMillis()
                        );
                        Toast.makeText(
                            this,
                            "Broadcast dijadwalkan",
                            Toast.LENGTH_SHORT
                        ).show();
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                );
                timePicker.show();
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePicker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePicker.show();
    }

    private void confirmStart() {
        if (composer == null || TextUtils.isEmpty(composer.getText())) {
            toast("Tulis pesan terlebih dahulu");
            return;
        }
        if (RPlusStore.isLimitLocked(currentAccount)) {
            showLimitDialog();
            return;
        }
        if (broadcastManager.isRunning()) {
            toast("Masih ada broadcast aktif");
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Mulai broadcast?");
        builder.setMessage(
            "Fitur ini dapat menyebabkan akun dibatasi, dibekukan, atau dihapus oleh Telegram. "
                + "Gunakan hanya pada chat yang Anda kelola atau penerima yang telah menyetujui. "
                + "Risiko penggunaan ditanggung pengguna."
        );
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton("Setuju & mulai", (dialog, which) -> {
            ArrayList<TLRPC.MessageEntity> entities = collectEntities();
            RPlusStore.saveTemplate(
                currentAccount,
                composerMode,
                composerMode == RPlusStore.MODE_GCAST ? "Draft Gcast" : "Draft Ucast",
                composer.getText().toString(),
                entities
            );
            RPlusBroadcastService.start(
                this,
                currentAccount,
                composerMode,
                composer.getText().toString(),
                RPlusStore.encodeEntities(entities)
            );
        });
        builder.show();
    }

    private void showBlacklist() {
        currentScreen = SCREEN_BLACKLIST;
        content.removeAllViews();
        content.addView(buildTopBar("BLACKLIST", "Pengecualian broadcast", false), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(64)
        ));
        final int[] tab = {0};
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(18), dp(6), dp(18), dp(6));
        TextView groups = actionButton("GRUP", true, () -> {
            tab[0] = 0;
            showBlacklistTab(0, tabs);
        });
        TextView users = actionButton("PENGGUNA", false, () -> {
            tab[0] = 1;
            showBlacklistTab(1, tabs);
        });
        tabs.addView(groups, weightButtonParams());
        tabs.addView(space(8));
        tabs.addView(users, weightButtonParams());
        content.addView(tabs);
        showBlacklistTab(tab[0], tabs);
    }

    private void showBlacklistTab(int tab, LinearLayout tabs) {
        while (content.getChildCount() > 2) {
            content.removeViewAt(2);
        }
        ViewGroup tabParent = (ViewGroup) tabs.getParent();
        for (int i = 0; i < tabs.getChildCount(); i++) {
            View child = tabs.getChildAt(i);
            if (child instanceof TextView && child != null && child.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                if (((LinearLayout.LayoutParams) child.getLayoutParams()).weight > 0) {
                    ((TextView) child).setTextColor(i == 0 ? (tab == 0 ? Color.WHITE : textColor()) : (tab == 1 ? Color.WHITE : textColor()));
                    child.setBackground(rounded(tab == (i == 0 ? 0 : 1) ? accentColor : (isDark() ? 0xff211630 : 0xfff1eaff), 12));
                }
            }
        }
        ArrayList<RPlusStore.RPlusPeer> items = tab == 0
            ? RPlusStore.getGroupBlacklist(currentAccount)
            : RPlusStore.getUserBlacklist(currentAccount);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(4), dp(18), dp(20));
        scroll.addView(list, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        content.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1
        ));
        if (items.isEmpty()) {
            TextView empty = text(
                tab == 0 ? "Belum ada grup dalam blacklist." : "Belum ada pengguna dalam blacklist.",
                13,
                mutedTextColor()
            );
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(42), 0, 0);
            list.addView(empty);
            return;
        }
        for (RPlusStore.RPlusPeer peer : items) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(8), dp(10));
            row.setBackground(rounded(isDark() ? 0xff211630 : 0xffffffff, 14));
            TextView name = text(peer.name, 14, textColor());
            name.setTypeface(AndroidUtilities.bold());
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView remove = actionButton("HAPUS", false, () -> {
                RPlusStore.setGroupBlacklisted(currentAccount, peer.id, peer.name, false);
                RPlusStore.setUserBlacklisted(currentAccount, peer.id, peer.name, false);
                showBlacklistTab(tab, tabs);
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(82), dp(36)));
            list.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            list.addView(space(8));
        }
    }

    private void showHistory() {
        currentScreen = SCREEN_HISTORY;
        content.removeAllViews();
        content.addView(buildTopBar("RIWAYAT", "Broadcast tersimpan lokal", false), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(64)
        ));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(6), dp(18), dp(20));
        scroll.addView(list, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        content.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1
        ));
        ArrayList<RPlusStore.RPlusSchedule> schedules = RPlusStore.getSchedules(currentAccount);
        if (!schedules.isEmpty()) {
            TextView scheduledHeader = sectionLabel("JADWAL BERIKUTNYA");
            list.addView(scheduledHeader);
            SimpleDateFormat scheduleFormat = new SimpleDateFormat("dd MMM yyyy · HH:mm", new Locale("id", "ID"));
            for (RPlusStore.RPlusSchedule schedule : schedules) {
                LinearLayout scheduleRow = new LinearLayout(this);
                scheduleRow.setGravity(Gravity.CENTER_VERTICAL);
                scheduleRow.setPadding(dp(12), dp(10), dp(8), dp(10));
                scheduleRow.setBackground(rounded(isDark() ? 0xff211630 : 0xffffffff, 14));
                LinearLayout scheduleInfo = new LinearLayout(this);
                scheduleInfo.setOrientation(LinearLayout.VERTICAL);
                TextView scheduleTitle = text(
                    schedule.mode == RPlusStore.MODE_GCAST ? "GCAST TERJADWAL" : "UCAST TERJADWAL",
                    12,
                    accentColor
                );
                scheduleTitle.setTypeface(AndroidUtilities.bold());
                scheduleInfo.addView(scheduleTitle);
                scheduleInfo.addView(text(scheduleFormat.format(new Date(schedule.scheduledAt)), 11, mutedTextColor()));
                scheduleRow.addView(scheduleInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView cancelSchedule = actionButton("BATAL", false, () -> {
                    RPlusScheduleManager.cancel(this, currentAccount, schedule.id);
                    showHistory();
                });
                scheduleRow.addView(cancelSchedule, new LinearLayout.LayoutParams(dp(76), dp(36)));
                list.addView(scheduleRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                list.addView(space(8));
            }
            list.addView(space(6));
        }
        ArrayList<RPlusStore.RPlusHistory> history = RPlusStore.getHistory(currentAccount);
        if (history.isEmpty()) {
            TextView empty = text("Belum ada riwayat broadcast.", 13, mutedTextColor());
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(42), 0, 0);
            list.addView(empty);
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy · HH:mm", new Locale("id", "ID"));
        for (RPlusStore.RPlusHistory item : history) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackground(rounded(isDark() ? 0xff211630 : 0xffffffff, 14));
            TextView title = text(
                (item.mode == RPlusStore.MODE_GCAST ? "GCAST" : "UCAST")
                    + (item.cancelled ? " · DIBATALKAN" : " · SELESAI"),
                13,
                item.cancelled ? 0xffffb84d : accentColor
            );
            title.setTypeface(AndroidUtilities.bold());
            card.addView(title);
            card.addView(text(format.format(new Date(item.startedAt)), 11, mutedTextColor()));
            TextView stats = text(
                "Terkirim " + item.sent + "  •  Gagal " + item.failed + "  •  Blacklist " + item.skipped,
                12,
                textColor()
            );
            stats.setPadding(0, dp(8), 0, 0);
            card.addView(stats);
            if (item.failureReasons != null && !item.failureReasons.isEmpty()) {
                Map.Entry<String, Integer> reason = item.failureReasons.entrySet().iterator().next();
                card.addView(text(
                    reason.getValue() + " gagal karena " + reason.getKey().toLowerCase(Locale.ROOT),
                    11,
                    mutedTextColor()
                ));
            }
            list.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            list.addView(space(9));
        }
    }

    private void showAlerts() {
        currentScreen = SCREEN_ALERTS;
        content.removeAllViews();
        content.addView(buildTopBar("PESAN", "Mention dan reply", false), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(64)
        ));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(6), dp(18), dp(20));
        scroll.addView(list, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        content.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1
        ));
        ArrayList<RPlusStore.RPlusAlert> alerts = RPlusStore.getAlerts(currentAccount);
        if (alerts.isEmpty()) {
            TextView empty = text("Belum ada mention atau reply baru.", 13, mutedTextColor());
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(42), 0, 0);
            list.addView(empty);
            return;
        }
        TextView clear = actionButton("BERSIHKAN", false, () -> {
            RPlusStore.clearAlerts(currentAccount);
            showAlerts();
        });
        list.addView(clear, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(40)
        ));
        list.addView(space(8));
        SimpleDateFormat format = new SimpleDateFormat("dd MMM · HH:mm", new Locale("id", "ID"));
        for (RPlusStore.RPlusAlert alert : alerts) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackground(rounded(isDark() ? 0xff211630 : 0xffffffff, 14));
            TextView title = text(
                (alert.mention ? "MENTION" : "REPLY") + " · " + alert.chatName,
                12,
                accentColor
            );
            title.setTypeface(AndroidUtilities.bold());
            card.addView(title);
            card.addView(text(alert.senderName + " · " + format.format(new Date(alert.date)), 11, mutedTextColor()));
            TextView body = text(TextUtils.isEmpty(alert.text) ? "Pesan tanpa teks" : alert.text, 13, textColor());
            body.setPadding(0, dp(8), 0, 0);
            card.addView(body);
            list.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            list.addView(space(9));
        }
    }

    private View buildActiveBroadcastCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(isDark() ? 0xff2a1840 : 0xffeee7ff, 16));
        TextView title = text(
            broadcastManager.getMode() == RPlusStore.MODE_GCAST ? "GCAST AKTIF" : "UCAST AKTIF",
            12,
            accentColor
        );
        title.setTypeface(AndroidUtilities.bold());
        card.addView(title);
        activeStatusView = text("", 13, textColor());
        activeStatusView.setPadding(0, dp(8), 0, 0);
        card.addView(activeStatusView);
        LinearLayout controls = new LinearLayout(this);
        controls.setPadding(0, dp(10), 0, 0);
        pauseResumeButton = actionButton(broadcastManager.isPaused() ? "LANJUTKAN" : "JEDA", false, () -> {
            if (broadcastManager.isPaused()) {
                RPlusBroadcastService.resume(this, currentAccount);
            } else {
                RPlusBroadcastService.pause(this, currentAccount);
            }
        });
        controls.addView(pauseResumeButton, weightButtonParams());
        controls.addView(space(8));
        controls.addView(actionButton("BATALKAN", true, () -> confirmCancelBroadcast()), weightButtonParams());
        card.addView(controls);
        updateActiveCard();
        return card;
    }

    private void updateActiveCard() {
        if (activeStatusView != null) {
            String state = broadcastManager.isPaused()
                ? "Dijeda · " + (broadcastManager.getPauseReason() != null ? broadcastManager.getPauseReason() : "")
                : broadcastManager.getCurrentTarget() != null
                    ? "Mengirim ke " + broadcastManager.getCurrentTarget()
                    : "Bersiap…";
            activeStatusView.setText(broadcastManager.getProgressText() + "\n" + state);
        }
        if (pauseResumeButton != null) {
            pauseResumeButton.setText(broadcastManager.isPaused() ? "LANJUTKAN" : "JEDA");
        }
    }

    private void confirmCancelBroadcast() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Batalkan broadcast?");
        builder.setMessage("Pesan yang sudah terkirim tidak dapat ditarik kembali.");
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton("Batalkan", (dialog, which) -> RPlusBroadcastService.cancel(this, currentAccount));
        builder.show();
    }

    private void showLimitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Broadcast dikunci sementara");
        builder.setMessage(
            "Akun ini terindikasi terkena pembatasan Telegram. "
                + RPlusStore.getLimitReason(currentAccount)
                + ". Jangan mencoba mengirim paksa."
        );
        builder.setPositiveButton("Buka @SpamBot", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/spambot"));
            startActivity(intent);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Close), null);
        builder.show();
    }

    private void updateProgressUi() {
        if (topProgressView == null) {
            return;
        }
        if (broadcastManager.isRunning()) {
            topProgressView.setText(broadcastManager.getProgressText());
            topProgressView.setVisibility(View.VISIBLE);
        } else {
            topProgressView.setVisibility(View.GONE);
        }
        updateActiveCard();
    }

    private void updateAlertBadge() {
        if (alertBadgeView == null) {
            return;
        }
        int count = RPlusStore.getAlerts(currentAccount).size();
        alertBadgeView.setText(count > 99 ? "99+" : count > 0 ? String.valueOf(count) : "");
        alertBadgeView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBroadcastStateChanged(int account) {
        if (account != currentAccount) {
            return;
        }
        updateProgressUi();
        if ((currentScreen == SCREEN_HOME || currentScreen == SCREEN_GCAST || currentScreen == SCREEN_UCAST)
            && !broadcastManager.isRunning() && currentScreen == SCREEN_HOME) {
            showHome();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (broadcastManager != null) {
            broadcastManager.addListener(this);
        }
        updateProgressUi();
        updateAlertBadge();
    }

    @Override
    protected void onPause() {
        if (broadcastManager != null) {
            broadcastManager.removeListener(this);
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (currentScreen == SCREEN_HOME) {
            closePanel();
        } else {
            showHome();
        }
    }

    private TextView actionButton(String label, boolean primary, Runnable action) {
        TextView button = text(label, 11, primary ? Color.WHITE : accentColor);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        button.setLetterSpacing(.04f);
        button.setBackground(rounded(primary ? accentColor : (isDark() ? 0xff2b1b40 : 0xffe9ddff), 12));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private TextView sectionLabel(String value) {
        TextView label = text(value, 10, mutedTextColor());
        label.setTypeface(AndroidUtilities.bold());
        label.setLetterSpacing(.09f);
        label.setPadding(dp(2), 0, 0, dp(7));
        return label;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
    }

    private View space(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private FrameLayout.LayoutParams frameMatch() {
        return new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private LinearLayout.LayoutParams weightButtonParams() {
        return new LinearLayout.LayoutParams(0, dp(40), 1);
    }

    private int textColor() {
        return isDark() ? 0xfff6f0ff : 0xff21162d;
    }

    private int mutedTextColor() {
        return isDark() ? 0xffad9ec1 : 0xff766687;
    }

    private int hintColor() {
        return isDark() ? 0xff79698e : 0xffa091b3;
    }

    private boolean isDark() {
        return Theme.isCurrentThemeDark();
    }

    private int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
