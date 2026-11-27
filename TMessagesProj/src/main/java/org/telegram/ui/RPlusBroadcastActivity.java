package org.telegram.ui;

import android.app.AlarmManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.rplus.RPlusBroadcastManager;
import org.telegram.messenger.rplus.RPlusBroadcastService;
import org.telegram.messenger.rplus.RPlusMessageAlerts;
import org.telegram.messenger.rplus.RPlusScheduleManager;
import org.telegram.messenger.rplus.RPlusStore;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.LineProgressView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * R+ Gram broadcast control screen.
 *
 * <p>The screen follows the regular Telegram layout: a native {@link ActionBar}, theme driven
 * colors and full width list cards. Every screen is rebuilt from scratch, so there is no shared
 * mutable view state between sections.</p>
 */
public class RPlusBroadcastActivity extends BasePermissionsActivity implements RPlusBroadcastManager.Listener {

    public static final String EXTRA_ACCOUNT = "rplus_account";

    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_GCAST = 1;
    private static final int SCREEN_UCAST = 2;
    private static final int SCREEN_BLACKLIST = 3;
    private static final int SCREEN_HISTORY = 4;
    private static final int SCREEN_ALERTS = 5;

    private static final int MAX_MESSAGE_LENGTH = 4096;

    private RPlusBroadcastManager broadcastManager;
    private ActionBar actionBar;
    private FrameLayout rootView;
    private LinearLayout headerHolder;
    private FrameLayout contentContainer;
    private ScrollView contentScroll;
    private int currentScreen = SCREEN_HOME;
    private int composerMode = RPlusStore.MODE_GCAST;

    private EditText composer;
    private TextView previewView;
    private TextView counterView;
    private TextView targetSummaryView;

    private View activeCard;
    private TextView activeStatusView;
    private TextView activeProgressView;
    private TextView activeStatsView;
    private TextView pauseResumeButton;
    private LineProgressView activeProgressBar;
    private final Runnable activeCardRefresh = this::updateActiveCardUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(Theme.isCurrentThemeDark() ? R.style.Theme_TMessages_Dark : R.style.Theme_TMessages);
        super.onCreate(savedInstanceState);
        currentAccount = getIntent().getIntExtra(EXTRA_ACCOUNT, UserConfig.selectedAccount);
        if (!UserConfig.isValidAccount(currentAccount)) {
            finish();
            return;
        }
        broadcastManager = RPlusBroadcastManager.getInstance(currentAccount);
        RPlusMessageAlerts.install();
        RPlusScheduleManager.rescheduleAll(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        });
        setupWindow();
        buildLayout();
        showScreen(SCREEN_HOME, false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int account = intent.getIntExtra(EXTRA_ACCOUNT, currentAccount);
        if (!UserConfig.isValidAccount(account) || account == currentAccount) {
            return;
        }
        if (broadcastManager != null) {
            broadcastManager.removeListener(this);
        }
        currentAccount = account;
        broadcastManager = RPlusBroadcastManager.getInstance(account);
        broadcastManager.addListener(this);
        showScreen(SCREEN_HOME);
    }

    private void setupWindow() {
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        AndroidUtilities.enableEdgeToEdge(window);
        AndroidUtilities.setLightNavigationBar(this, !Theme.isCurrentThemeDark());
    }

    private void buildLayout() {
        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(color(Theme.key_windowBackgroundGray));
        setContentView(rootView);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        rootView.addView(shell, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        int actionBarColor = color(Theme.key_actionBarDefault);
        AndroidUtilities.setLightStatusBar(getWindow(), !AndroidUtilities.isDarkColor(actionBarColor));

        headerHolder = new LinearLayout(this);
        headerHolder.setOrientation(LinearLayout.VERTICAL);
        headerHolder.setBackgroundColor(actionBarColor);
        shell.addView(headerHolder, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        actionBar = new ActionBar(this);
        actionBar.setOccupyStatusBar(false);
        actionBar.setBackgroundColor(actionBarColor);
        actionBar.setItemsColor(color(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsBackgroundColor(color(Theme.key_actionBarDefaultSelector), false);
        actionBar.setTitleColor(color(Theme.key_actionBarDefaultTitle));
        actionBar.setSubtitleColor(color(Theme.key_actionBarDefaultSubtitle));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    handleBackPressed();
                }
            }
        });
        headerHolder.addView(actionBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        contentContainer = new FrameLayout(this);
        shell.addView(contentContainer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, windowInsets) -> {
            Insets system = AndroidUtilities.getDefaultWindowInsets(windowInsets, false);
            Insets withIme = AndroidUtilities.getDefaultWindowInsets(windowInsets, true);
            headerHolder.setPadding(system.left, system.top, system.right, 0);
            contentContainer.setPadding(system.left, 0, system.right, withIme.bottom);
            return windowInsets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (broadcastManager != null) {
            broadcastManager.addListener(this);
        }
        if (currentScreen == SCREEN_HOME
            && broadcastManager != null
            && broadcastManager.isRunning() != (activeCard != null)) {
            showScreen(SCREEN_HOME, false);
        } else if (currentScreen == SCREEN_HOME && activeCard != null) {
            updateActiveCardUi();
        }
    }

    @Override
    protected void onPause() {
        saveDraftSilently();
        if (broadcastManager != null) {
            broadcastManager.removeListener(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (broadcastManager != null) {
            broadcastManager.removeListener(this);
        }
        super.onDestroy();
    }

    private void handleBackPressed() {
        if (currentScreen == SCREEN_GCAST || currentScreen == SCREEN_UCAST) {
            saveDraftSilently();
        }
        if (currentScreen != SCREEN_HOME) {
            showScreen(SCREEN_HOME);
            return;
        }
        finish();
    }

    @Override
    public void onBroadcastStateChanged(int account) {
        if (account != currentAccount || broadcastManager == null) {
            return;
        }
        if (currentScreen == SCREEN_HOME) {
            if (broadcastManager.isRunning() != (activeCard != null)) {
                showScreen(SCREEN_HOME, false);
            } else {
                updateActiveCardUi();
            }
        } else {
            updateActiveCardUi();
        }
    }

    private void showScreen(int screen) {
        showScreen(screen, true);
    }

    private void showScreen(int screen, boolean animate) {
        hideKeyboard();
        currentScreen = screen;
        if (activeCard != null) {
            activeCard.removeCallbacks(activeCardRefresh);
        }
        contentContainer.removeAllViews();
        contentScroll = null;
        composer = null;
        previewView = null;
        counterView = null;
        targetSummaryView = null;
        activeCard = null;
        activeStatusView = null;
        activeProgressView = null;
        activeStatsView = null;
        pauseResumeButton = null;
        activeProgressBar = null;

        switch (screen) {
            case SCREEN_GCAST:
            case SCREEN_UCAST:
                buildComposer();
                break;
            case SCREEN_BLACKLIST:
                buildBlacklist();
                break;
            case SCREEN_HISTORY:
                buildHistory();
                break;
            case SCREEN_ALERTS:
                buildAlerts();
                break;
            case SCREEN_HOME:
            default:
                buildHome();
                break;
        }
        contentContainer.animate().cancel();
        if (animate) {
            contentContainer.setAlpha(0f);
            contentContainer.setTranslationY(dp(6));
            contentContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .start();
        } else {
            contentContainer.setAlpha(1f);
            contentContainer.setTranslationY(0f);
        }
    }

    private void openComposer(int mode) {
        composerMode = mode;
        showScreen(mode == RPlusStore.MODE_GCAST ? SCREEN_GCAST : SCREEN_UCAST);
    }

    // ---------------------------------------------------------------------------------------
    // Home
    // ---------------------------------------------------------------------------------------

    private void buildHome() {
        setActionBar("R+ Gram", "Pusat broadcast");

        LinearLayout body = createBody();

        if (broadcastManager.isRunning()) {
            body.addView(buildActiveCard(), fullWidth(top(8)));
        }
        body.addView(buildAccountCard(), fullWidth(top(broadcastManager.isRunning() ? 10 : 8)));

        addHeader(body, "Broadcast");
        LinearLayout broadcastCard = newCard();
        broadcastCard.addView(buildRow(
            R.drawable.msg_groups,
            "Broadcast grup & channel",
            "Grup dan channel tempat Anda bisa mengirim",
            null,
            true,
            () -> openComposer(RPlusStore.MODE_GCAST)
        ));
        broadcastCard.addView(divider(56));
        broadcastCard.addView(buildRow(
            R.drawable.msg_usersearch,
            "Broadcast pengguna",
            "Hingga 500 chat terbaru, tanpa bot",
            null,
            true,
            () -> openComposer(RPlusStore.MODE_UCAST)
        ));
        body.addView(broadcastCard);

        addHeader(body, "Lainnya");
        int blacklisted = RPlusStore.getGroupBlacklist(currentAccount).size()
            + RPlusStore.getUserBlacklist(currentAccount).size();
        int alerts = RPlusStore.getAlerts(currentAccount).size();

        LinearLayout otherCard = newCard();
        otherCard.addView(buildRow(
            R.drawable.msg_block,
            "Blacklist",
            "Chat yang tidak ikut broadcast",
            blacklisted > 0 ? String.valueOf(blacklisted) : null,
            true,
            () -> showScreen(SCREEN_BLACKLIST)
        ));
        otherCard.addView(divider(56));
        otherCard.addView(buildRow(
            R.drawable.msg_stats,
            "Riwayat",
            "Broadcast dan jadwal tersimpan",
            null,
            true,
            () -> showScreen(SCREEN_HISTORY)
        ));
        otherCard.addView(divider(56));
        otherCard.addView(buildRow(
            R.drawable.msg_notifications,
            "Pesan",
            "Mention dan balasan terbaru",
            alerts > 0 ? (alerts > 99 ? "99+" : String.valueOf(alerts)) : null,
            true,
            () -> showScreen(SCREEN_ALERTS)
        ));
        body.addView(otherCard);

        addFooter(body, "R+ Gram bukan aplikasi resmi Telegram. Broadcast dapat menyebabkan "
            + "pembatasan akun; gunakan hanya pada chat yang Anda kelola atau penerima yang "
            + "menyetujui.");
    }

    private View buildAccountCard() {
        LinearLayout card = newCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        TLRPC.User self = MessagesController.getInstance(currentAccount)
            .getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        String name = self != null ? UserObject.getUserName(self) : "Akun Telegram";
        if (TextUtils.isEmpty(name)) {
            name = "Akun Telegram";
        }

        BackupImageView avatar = new BackupImageView(this);
        avatar.setRoundRadius(dp(26));
        avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (self != null) {
            avatar.setForUserOrChat(self, new AvatarDrawable());
        } else {
            avatar.setImageDrawable(new AvatarDrawable());
        }
        card.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(16), 0, 0, 0);

        TextView nameView = text(name, 17, color(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setTypeface(AndroidUtilities.bold());
        info.addView(nameView);

        TextView statusView = text(
            self != null && !TextUtils.isEmpty(self.username) ? "@" + self.username : "Akun terhubung",
            14,
            color(Theme.key_windowBackgroundWhiteGrayText2)
        );
        statusView.setPadding(0, dp(2), 0, 0);
        info.addView(statusView);

        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return card;
    }

    private View buildActiveCard() {
        LinearLayout card = newCard();
        card.setPadding(dp(16), dp(16), dp(16), dp(8));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(
            broadcastManager.getMode() == RPlusStore.MODE_GCAST
                ? "Broadcast grup aktif"
                : "Broadcast pengguna aktif",
            16,
            accent()
        );
        title.setTypeface(AndroidUtilities.bold());
        topRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        activeProgressView = text("", 12, color(Theme.key_windowBackgroundWhiteGrayText2));
        topRow.addView(activeProgressView);
        card.addView(topRow);

        activeProgressBar = new LineProgressView(this);
        activeProgressBar.setContentDescription("Progres broadcast");
        activeProgressBar.setProgressColor(accentButton());
        activeProgressBar.setBackColor(color(Theme.key_divider));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(4)
        );
        barParams.topMargin = dp(12);
        card.addView(activeProgressBar, barParams);

        activeStatusView = text("", 14, color(Theme.key_windowBackgroundWhiteBlackText));
        activeStatusView.setPadding(0, dp(8), 0, 0);
        activeStatusView.setMaxLines(3);
        card.addView(activeStatusView);

        activeStatsView = text("", 12, color(Theme.key_windowBackgroundWhiteGrayText2));
        activeStatsView.setPadding(0, dp(6), 0, 0);
        card.addView(activeStatsView);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(2), 0, 0);

        pauseResumeButton = textButton(
            broadcastManager.isPaused() ? "Lanjutkan" : "Jeda",
            accent(),
            () -> {
                if (broadcastManager.isPaused()) {
                    if (RPlusStore.isLimitLocked(currentAccount)) {
                        notifyMessage("Broadcast masih dikunci sementara", true);
                        return;
                    }
                    RPlusBroadcastService.resume(this, currentAccount);
                } else {
                    RPlusBroadcastService.pause(this, currentAccount);
                }
            }
        );
        controls.addView(pauseResumeButton, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(48)
        ));

        View spacer = new View(this);
        controls.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        controls.addView(textButton("Batalkan", danger(), this::confirmCancelBroadcast),
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        card.addView(controls);
        activeCard = card;
        updateActiveCardUi();
        return card;
    }

    private void updateActiveCardUi() {
        if (activeCard == null) {
            return;
        }
        int total = Math.max(broadcastManager.getTotal(), 0);
        int index = Math.min(broadcastManager.getIndex(), total);
        if (activeProgressView != null) {
            activeProgressView.setText(index + " / " + total);
        }
        if (activeProgressBar != null) {
            activeProgressBar.setProgress(total <= 0 ? 0f : index / (float) total, true);
            activeProgressBar.setContentDescription("Progres broadcast " + index + " dari " + total);
        }
        if (activeStatusView != null) {
            String state;
            if (broadcastManager.isPaused()) {
                String reason = broadcastManager.getPauseReason();
                state = TextUtils.isEmpty(reason) ? "Dijeda" : "Dijeda · " + reason;
            } else if (broadcastManager.getCurrentTarget() != null) {
                state = "Mengirim ke " + broadcastManager.getCurrentTarget();
            } else {
                state = "Menyiapkan target…";
            }
            activeStatusView.setText(state);
        }
        if (activeStatsView != null) {
            activeStatsView.setText("Terkirim " + broadcastManager.getSent()
                + " · Gagal " + broadcastManager.getFailed()
                + " · Dilewati " + broadcastManager.getSkipped());
        }
        if (pauseResumeButton != null) {
            boolean locked = RPlusStore.isLimitLocked(currentAccount);
            pauseResumeButton.setEnabled(!locked);
            pauseResumeButton.setAlpha(locked ? .5f : 1f);
            pauseResumeButton.setText(locked
                ? "Tunggu"
                : (broadcastManager.isPaused() ? "Lanjutkan" : "Jeda"));
            if (activeCard != null) {
                activeCard.removeCallbacks(activeCardRefresh);
                if (locked) {
                    long remaining = Math.max(
                        1000,
                        RPlusStore.getLimitLockUntil(currentAccount) - System.currentTimeMillis() + 500
                    );
                    activeCard.postDelayed(activeCardRefresh, remaining);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Composer
    // ---------------------------------------------------------------------------------------

    private void buildComposer() {
        final boolean isGroup = composerMode == RPlusStore.MODE_GCAST;
        RPlusBroadcastManager.TargetSnapshot snapshot =
            RPlusBroadcastManager.buildTargets(currentAccount, composerMode);

        setActionBar(
            isGroup ? "Broadcast grup & channel" : "Broadcast pengguna",
            snapshot.targets.size() + " target"
        );

        LinearLayout body = createBody();

        if (broadcastManager.isRunning()) {
            body.addView(buildNotice(
                R.drawable.msg_info,
                "Broadcast lain sedang berjalan",
                "Selesaikan atau batalkan broadcast aktif sebelum memulai yang baru.",
                accent()
            ), fullWidth(top(12)));
        }
        if (RPlusStore.isLimitLocked(currentAccount)) {
            String limitReason = RPlusStore.getLimitReason(currentAccount);
            body.addView(buildNotice(
                R.drawable.msg_info,
                "Broadcast dikunci sementara",
                TextUtils.isEmpty(limitReason)
                    ? "Akun dibatasi sementara oleh Telegram."
                    : limitReason,
                danger()
            ), fullWidth(top(12)));
        }

        body.addView(buildTargetCard(snapshot, isGroup), fullWidth(top(12)));

        addHeader(body, "Pesan");
        body.addView(buildEditorCard(isGroup), fullWidth(top(0)));

        addHeader(body, "Pratinjau");
        body.addView(buildPreviewCard(), fullWidth(top(0)));

        TextView startButton = primaryButton(
            isGroup ? "Mulai broadcast grup" : "Mulai broadcast pengguna",
            this::confirmStart
        );
        if (broadcastManager.isRunning() || snapshot.targets.isEmpty()) {
            startButton.setAlpha(.5f);
            startButton.setEnabled(false);
        }

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(12), dp(16), dp(12));
        bar.setBackgroundColor(color(Theme.key_windowBackgroundWhite));
        bar.addView(startButton, new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1
        ));

        View scheduleButton = tonalIconButton(
            R.drawable.msg_calendar,
            "Jadwalkan broadcast",
            this::scheduleCurrent
        );
        LinearLayout.LayoutParams scheduleParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        scheduleParams.setMarginStart(dp(8));
        bar.addView(scheduleButton, scheduleParams);

        LinearLayout barWrapper = new LinearLayout(this);
        barWrapper.setOrientation(LinearLayout.VERTICAL);
        barWrapper.setBackgroundColor(color(Theme.key_windowBackgroundWhite));
        View barDivider = new View(this);
        barDivider.setBackgroundColor(color(Theme.key_divider));
        barWrapper.addView(barDivider, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Math.max(1, dp(0.5f))
        ));
        barWrapper.addView(bar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        contentContainer.addView(barWrapper, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ));
        final ScrollView scrollForBar = contentScroll;
        if (scrollForBar != null && scrollForBar.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) scrollForBar.getLayoutParams()).bottomMargin = dp(80);
            scrollForBar.setLayoutParams(scrollForBar.getLayoutParams());
        }
        barWrapper.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int height = bottom - top;
            if (scrollForBar == null || height <= 0) {
                return;
            }
            ViewGroup.LayoutParams params = scrollForBar.getLayoutParams();
            if (params instanceof FrameLayout.LayoutParams
                && ((FrameLayout.LayoutParams) params).bottomMargin != height) {
                ((FrameLayout.LayoutParams) params).bottomMargin = height;
                scrollForBar.setLayoutParams(params);
            }
        });

        updatePreview();
    }

    private View buildTargetCard(RPlusBroadcastManager.TargetSnapshot snapshot, boolean isGroup) {
        LinearLayout card = newCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(isGroup ? R.drawable.msg_groups : R.drawable.msg_usersearch);
        icon.setColorFilter(accent());
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconParams.setMarginEnd(dp(16));
        card.addView(icon, iconParams);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);

        targetSummaryView = text(
            snapshot.targets.size() + (isGroup ? " grup & channel" : " pengguna"),
            16,
            color(Theme.key_windowBackgroundWhiteBlackText)
        );
        targetSummaryView.setTypeface(AndroidUtilities.bold());
        info.addView(targetSummaryView);

        StringBuilder detail = new StringBuilder();
        if (snapshot.blacklisted > 0) {
            detail.append(snapshot.blacklisted).append(" dikecualikan");
        }
        if (snapshot.unavailable > 0) {
            if (detail.length() > 0) {
                detail.append(" · ");
            }
            detail.append(snapshot.unavailable).append(" tidak tersedia");
        }
        if (detail.length() == 0) {
            detail.append(snapshot.targets.isEmpty()
                ? (isGroup
                    ? "Belum ada grup/channel yang bisa dijangkau. Ikuti grup atau pastikan Anda punya hak posting di channel."
                    : "Belum ada chat pengguna yang bisa dijangkau.")
                : "Siap dikirim");
        }
        TextView detailView = text(detail, 14, color(Theme.key_windowBackgroundWhiteGrayText2));
        detailView.setPadding(0, dp(2), 0, 0);
        info.addView(detailView);

        if (snapshot.targets.isEmpty()) {
            TextView reload = textButton("Muat ulang target", accent(), () -> {
                saveDraftSilently();
                showScreen(currentScreen);
            });
            reload.setGravity(Gravity.START);
            reload.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            );
            reloadParams.topMargin = dp(4);
            info.addView(reload, reloadParams);
        }

        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return card;
    }

    private View buildEditorCard(boolean isGroup) {
        LinearLayout card = newCard();
        FrameLayout editorBox = new FrameLayout(this);

        composer = new EditText(this);
        composer.setBackground(null);
        composer.setTextSize(16);
        composer.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
        composer.setHintTextColor(color(Theme.key_windowBackgroundWhiteHintText));
        composer.setHint(isGroup ? "Tulis pesan untuk grup…" : "Tulis pesan untuk pengguna…");
        composer.setGravity(Gravity.TOP | Gravity.START);
        composer.setInputType(InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.setMinLines(4);
        composer.setMaxLines(10);
        composer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_MESSAGE_LENGTH)});
        composer.setPadding(dp(16), dp(12), dp(16), dp(32));
        composer.addTextChangedListener(new TextWatcher() {
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
        editorBox.addView(composer, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        counterView = text("0 / " + MAX_MESSAGE_LENGTH, 12, color(Theme.key_windowBackgroundWhiteGrayText2));
        FrameLayout.LayoutParams counterParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.END | Gravity.BOTTOM
        );
        counterParams.setMarginEnd(dp(16));
        counterParams.bottomMargin = dp(12);
        editorBox.addView(counterView, counterParams);

        card.addView(editorBox, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ArrayList<RPlusStore.RPlusTemplate> templates = RPlusStore.getTemplates(currentAccount, composerMode);
        if (!templates.isEmpty() && !TextUtils.isEmpty(templates.get(0).text)) {
            RPlusStore.RPlusTemplate template = templates.get(0);
            CharSequence restored = ChatActivityEnterView.applyMessageEntities(
                RPlusStore.decodeEntities(template.entities),
                template.text,
                composer.getPaint().getFontMetricsInt()
            );
            composer.setText(TextUtils.isEmpty(restored) ? template.text : restored);
            composer.setSelection(composer.length());
        }
        return card;
    }

    private View buildPreviewCard() {
        LinearLayout card = newCard();
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        previewView = text("", 16, color(Theme.key_windowBackgroundWhiteBlackText));
        previewView.setLineSpacing(dp(2), 1.05f);
        previewView.setMinHeight(dp(48));
        card.addView(previewView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return card;
    }

    private void updatePreview() {
        if (composer == null || previewView == null) {
            return;
        }
        String raw = composer.getText().toString();
        if (counterView != null) {
            counterView.setText(raw.length() + " / " + MAX_MESSAGE_LENGTH);
        }
        if (TextUtils.isEmpty(raw.trim())) {
            previewView.setText("Pratinjau pesan akan muncul di sini.");
            previewView.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText2));
            return;
        }
        previewView.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
        ArrayList<TLRPC.MessageEntity> entities = collectEntities();
        CharSequence formatted = ChatActivityEnterView.applyMessageEntities(
            entities,
            raw,
            previewView.getPaint().getFontMetricsInt()
        );
        previewView.setText(TextUtils.isEmpty(formatted) ? raw : formatted);
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

    private boolean ensureComposerText() {
        if (composer == null || TextUtils.isEmpty(composer.getText().toString().trim())) {
            notifyMessage("Tulis pesan terlebih dahulu", true);
            return false;
        }
        return true;
    }

    private String draftName() {
        return composerMode == RPlusStore.MODE_GCAST ? "Draft Gcast" : "Draft Ucast";
    }

    private void saveDraftSilently() {
        if (composer == null) {
            return;
        }
        String message = composer.getText().toString();
        if (TextUtils.isEmpty(message.trim())) {
            return;
        }
        ArrayList<RPlusStore.RPlusTemplate> templates =
            RPlusStore.getTemplates(currentAccount, composerMode);
        if (!templates.isEmpty() && message.equals(templates.get(0).text)) {
            return;
        }
        RPlusStore.saveTemplate(
            currentAccount,
            composerMode,
            draftName(),
            message,
            collectEntities()
        );
    }

    private void scheduleCurrent() {
        if (!ensureComposerText()) {
            return;
        }
        if (RPlusStore.isLimitLocked(currentAccount)) {
            showLimitDialog();
            return;
        }
        if (!ensureExactAlarmPermission()) {
            return;
        }
        openSchedulePicker();
    }

    private boolean ensureExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < 31) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager == null || alarmManager.canScheduleExactAlarms()) {
            return true;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Izinkan jadwal tepat waktu");
        builder.setMessage("Android membatasi alarm latar belakang. Tanpa izin ini, broadcast "
            + "terjadwal bisa terlambat atau gagal dimulai saat aplikasi ditutup.");
        builder.setNegativeButton("Tetap jadwalkan", (dialog, which) -> openSchedulePicker());
        builder.setPositiveButton("Buka pengaturan", (dialog, which) -> {
            try {
                startActivity(new Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())
                ));
            } catch (Throwable e) {
                notifyMessage("Pengaturan alarm tidak tersedia di perangkat ini", true);
            }
        });
        builder.show();
        return false;
    }

    private void openSchedulePicker() {
        if (composer == null) {
            return;
        }
        hideKeyboard();
        AlertsCreator.createScheduleDatePickerDialog(
            this,
            0,
            (notify, scheduleDate, scheduleRepeatPeriod) -> {
                if (composer == null) {
                    return;
                }
                if (scheduleDate == 0 || scheduleDate == 0x7ffffffe) {
                    notifyMessage("Pilih tanggal dan waktu terlebih dahulu", true);
                    return;
                }
                long when = scheduleDate * 1000L;
                if (when <= System.currentTimeMillis()) {
                    notifyMessage("Waktu jadwal harus di masa depan", true);
                    return;
                }
                ArrayList<TLRPC.MessageEntity> entities = collectEntities();
                RPlusStore.saveTemplate(
                    currentAccount,
                    composerMode,
                    draftName(),
                    composer.getText().toString(),
                    entities
                );
                RPlusScheduleManager.schedule(
                    this,
                    currentAccount,
                    composerMode,
                    composer.getText().toString(),
                    entities,
                    when
                );
                notifyMessage("Broadcast dijadwalkan", false);
            }
        );
    }

    private void confirmStart() {
        if (!ensureComposerText()) {
            return;
        }
        if (RPlusStore.isLimitLocked(currentAccount)) {
            showLimitDialog();
            return;
        }
        if (broadcastManager.isRunning()) {
            notifyMessage("Masih ada broadcast aktif", true);
            return;
        }
        RPlusBroadcastManager.TargetSnapshot snapshot =
            RPlusBroadcastManager.buildTargets(currentAccount, composerMode);
        if (snapshot.targets.isEmpty()) {
            notifyMessage("Tidak ada target yang bisa dijangkau", true);
            return;
        }
        String unit = composerMode == RPlusStore.MODE_GCAST ? "grup/channel" : "pengguna";
        hideKeyboard();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Mulai broadcast?");
        builder.setMessage("Pesan akan dikirim ke " + snapshot.targets.size() + " " + unit + ".\n\n"
            + "Fitur ini dapat menyebabkan akun dibatasi, dibekukan, atau dihapus oleh Telegram. "
            + "Gunakan hanya pada chat yang Anda kelola atau penerima yang telah menyetujui. "
            + "Pesan dikirim dengan jeda otomatis 3–5 detik untuk mengurangi risiko pembatasan. "
            + "Risiko penggunaan ditanggung pengguna.");
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton("Mulai", (dialog, which) -> {
            if (composer == null) {
                return;
            }
            String message = composer.getText().toString();
            ArrayList<TLRPC.MessageEntity> entities = collectEntities();
            RPlusStore.saveTemplate(currentAccount, composerMode, draftName(), message, entities);
            boolean started = RPlusBroadcastService.start(
                this,
                currentAccount,
                composerMode,
                message,
                RPlusStore.encodeEntities(entities)
            );
            if (started) {
                final long requestedAt = System.currentTimeMillis();
                showScreen(SCREEN_HOME);
                if (rootView != null) {
                    rootView.postDelayed(() -> {
                        if (!isFinishing()
                            && !broadcastManager.isRunning()
                            && broadcastManager.getStartedAt() < requestedAt) {
                            notifyMessage(
                                "Broadcast tidak dimulai. Periksa notifikasi atau target.",
                                true
                            );
                        }
                    }, 3000);
                }
            } else {
                notifyMessage("Layanan broadcast tidak dapat dimulai", true);
            }
        });
        builder.show();
    }

    private void confirmCancelBroadcast() {
        showConfirm(
            "Batalkan broadcast?",
            "Pesan yang sudah terkirim tidak dapat ditarik kembali.",
            "Batalkan",
            () -> RPlusBroadcastService.cancel(this, currentAccount)
        );
    }

    // ---------------------------------------------------------------------------------------
    // Blacklist
    // ---------------------------------------------------------------------------------------

    private void buildBlacklist() {
        setActionBar("Blacklist", "Chat yang tidak ikut broadcast");
        LinearLayout body = createBody();

        ArrayList<RPlusStore.RPlusPeer> groups = RPlusStore.getGroupBlacklist(currentAccount);
        ArrayList<RPlusStore.RPlusPeer> users = RPlusStore.getUserBlacklist(currentAccount);

        addHeader(body, "Grup");
        if (groups.isEmpty()) {
            body.addView(emptyState(
                R.drawable.msg_groups,
                "Belum ada grup dikecualikan",
                "Buka profil grup, lalu ketuk ikon R+ di samping nama untuk mengecualikannya."
            ));
        } else {
            LinearLayout card = newCard();
            for (int i = 0; i < groups.size(); i++) {
                if (i > 0) {
                    card.addView(divider(70));
                }
                card.addView(buildPeerRow(groups.get(i), true));
            }
            body.addView(card);
        }

        addHeader(body, "Pengguna");
        if (users.isEmpty()) {
            body.addView(emptyState(
                R.drawable.msg_usersearch,
                "Belum ada pengguna dikecualikan",
                "Buka profil pengguna, lalu ketuk ikon R+ di samping nama untuk mengecualikannya."
            ));
        } else {
            LinearLayout card = newCard();
            for (int i = 0; i < users.size(); i++) {
                if (i > 0) {
                    card.addView(divider(70));
                }
                card.addView(buildPeerRow(users.get(i), false));
            }
            body.addView(card);
        }

        addFooter(body, "Chat dalam blacklist tetap menerima pesan biasa, tetapi tidak akan "
            + "menerima broadcast.");
    }

    private View buildPeerRow(RPlusStore.RPlusPeer peer, boolean isGroup) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(ripple(null));

        String peerName = TextUtils.isEmpty(peer.name) ? "Tanpa nama" : peer.name;
        TLObject peerObject;
        if (isGroup) {
            peerObject = MessagesController.getInstance(currentAccount).getChat(-peer.id);
        } else {
            peerObject = MessagesController.getInstance(currentAccount).getUser(peer.id);
        }
        View peerAvatar;
        if (peerObject != null) {
            BackupImageView imageView = new BackupImageView(this);
            imageView.setRoundRadius(dp(20));
            imageView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            imageView.setForUserOrChat(peerObject, new AvatarDrawable());
            peerAvatar = imageView;
        } else {
            peerAvatar = buildLetterAvatar(peerName);
        }
        row.addView(peerAvatar, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView nameView = text(peerName, 16, color(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setMaxLines(1);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1
        );
        nameParams.setMarginStart(dp(16));
        row.addView(nameView, nameParams);

        TextView remove = textButton("Hapus", danger(), () -> confirmRemovePeer(peer, isGroup));
        row.addView(remove, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(48)
        ));
        row.setOnClickListener(v -> confirmRemovePeer(peer, isGroup));
        return row;
    }

    private void confirmRemovePeer(RPlusStore.RPlusPeer peer, boolean isGroup) {
        showConfirm(
            "Hapus dari blacklist?",
            (TextUtils.isEmpty(peer.name) ? "Chat ini" : peer.name)
                + " akan kembali menerima broadcast.",
            "Hapus",
            () -> {
                if (isGroup) {
                    RPlusStore.setGroupBlacklisted(currentAccount, peer.id, peer.name, false);
                } else {
                    RPlusStore.setUserBlacklisted(currentAccount, peer.id, peer.name, false);
                }
                showScreen(SCREEN_BLACKLIST);
            }
        );
    }

    private View buildLetterAvatar(String name) {
        String initial = "?";
        if (!TextUtils.isEmpty(name)) {
            initial = new String(Character.toChars(name.codePointAt(0))).toUpperCase(Locale.ROOT);
        }
        TextView avatar = text(initial, 17, accent());
        avatar.setTypeface(AndroidUtilities.bold());
        avatar.setGravity(Gravity.CENTER);
        avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        avatar.setBackground(rounded(Theme.multAlpha(accentButton(), .16f), 20));
        return avatar;
    }

    // ---------------------------------------------------------------------------------------
    // History
    // ---------------------------------------------------------------------------------------

    private void buildHistory() {
        setActionBar("Riwayat", "Broadcast tersimpan di perangkat");
        LinearLayout body = createBody();

        ArrayList<RPlusStore.RPlusSchedule> schedules = RPlusStore.getSchedules(currentAccount);
        if (!schedules.isEmpty()) {
            addHeader(body, "Jadwal");
            for (RPlusStore.RPlusSchedule schedule : schedules) {
                body.addView(buildScheduleCard(schedule), fullWidth(top(6)));
            }
        }

        ArrayList<RPlusStore.RPlusHistory> history = RPlusStore.getHistory(currentAccount);
        addHeader(body, "Riwayat broadcast");
        if (history.isEmpty()) {
            body.addView(emptyState(
                R.drawable.msg_stats,
                "Belum ada riwayat",
                "Broadcast yang selesai atau dibatalkan akan muncul di sini."
            ));
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy · HH:mm", new Locale("id", "ID"));
        for (RPlusStore.RPlusHistory item : history) {
            body.addView(buildHistoryCard(item, format), fullWidth(top(6)));
        }
        addFooter(body, "Riwayat hanya tersimpan di perangkat ini dan tidak diunggah ke server.");
    }

    private View buildScheduleCard(RPlusStore.RPlusSchedule schedule) {
        LinearLayout card = newCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(12), dp(12), dp(12));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);

        TextView title = text(
            schedule.mode == RPlusStore.MODE_GCAST ? "Broadcast grup & channel" : "Broadcast pengguna",
            16,
            color(Theme.key_windowBackgroundWhiteBlackText)
        );
        title.setTypeface(AndroidUtilities.bold());
        info.addView(title);

        SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy · HH:mm", new Locale("id", "ID"));
        TextView date = text(
            "Dijadwalkan " + format.format(new Date(schedule.scheduledAt)),
            12,
            color(Theme.key_windowBackgroundWhiteGrayText2)
        );
        date.setPadding(0, dp(2), 0, 0);
        info.addView(date);

        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        card.addView(
            textButton("Batalkan", danger(), () -> showConfirm(
                "Batalkan jadwal?",
                "Broadcast terjadwal ini tidak akan dikirim.",
                "Batalkan",
                () -> {
                    RPlusScheduleManager.cancel(this, currentAccount, schedule.id);
                    showScreen(SCREEN_HISTORY);
                }
            )),
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48))
        );
        return card;
    }

    private View buildHistoryCard(RPlusStore.RPlusHistory item, SimpleDateFormat format) {
        LinearLayout card = newCard();
        card.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(
            item.mode == RPlusStore.MODE_GCAST ? "Broadcast grup & channel" : "Broadcast pengguna",
            16,
            color(Theme.key_windowBackgroundWhiteBlackText)
        );
        title.setTypeface(AndroidUtilities.bold());
        topRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        topRow.addView(chip(
            item.cancelled ? "Dibatalkan" : "Selesai",
            item.cancelled ? danger() : accent()
        ));
        card.addView(topRow);

        TextView date = text(
            format.format(new Date(item.startedAt)),
            12,
            color(Theme.key_windowBackgroundWhiteGrayText2)
        );
        date.setPadding(0, dp(2), 0, 0);
        card.addView(date);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(12), 0, 0);
        addStat(stats, "Terkirim", item.sent, accent());
        addStat(stats, "Gagal", item.failed, item.failed > 0 ? danger() : color(Theme.key_windowBackgroundWhiteGrayText2));
        addStat(stats, "Dilewati", item.skipped, color(Theme.key_windowBackgroundWhiteGrayText2));
        card.addView(stats);

        if (item.failureReasons != null && !item.failureReasons.isEmpty()) {
            Map.Entry<String, Integer> reason = item.failureReasons.entrySet().iterator().next();
            TextView failure = text(
                "Penyebab gagal: " + reason.getKey() + " (" + reason.getValue() + ")",
                12,
                color(Theme.key_windowBackgroundWhiteGrayText2)
            );
            failure.setPadding(0, dp(8), 0, 0);
            card.addView(failure);
        }
        return card;
    }

    private void addStat(LinearLayout parent, String label, int value, int statColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView valueView = text(String.valueOf(value), 17, statColor);
        valueView.setTypeface(AndroidUtilities.bold());
        box.addView(valueView);
        box.addView(text(label, 12, color(Theme.key_windowBackgroundWhiteGrayText2)));
        parent.addView(box, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    // ---------------------------------------------------------------------------------------
    // Alerts
    // ---------------------------------------------------------------------------------------

    private void buildAlerts() {
        setActionBar("Pesan", "Mention dan balasan");
        LinearLayout body = createBody();

        ArrayList<RPlusStore.RPlusAlert> alerts = RPlusStore.getAlerts(currentAccount);
        if (alerts.isEmpty()) {
            body.addView(emptyState(
                R.drawable.msg_mention,
                "Belum ada mention",
                "Saat seseorang menyebut atau membalas Anda, pesannya akan muncul di sini."
            ));
            return;
        }

        LinearLayout clearCard = newCard();
        clearCard.addView(buildRow(
            R.drawable.msg_delete,
            "Bersihkan semua",
            null,
            null,
            false,
            this::confirmClearAlerts
        ));
        body.addView(clearCard, fullWidth(top(12)));

        SimpleDateFormat format = new SimpleDateFormat("dd MMM · HH:mm", new Locale("id", "ID"));
        for (RPlusStore.RPlusAlert alert : alerts) {
            LinearLayout card = newCard();
            card.setPadding(dp(16), dp(12), dp(16), dp(12));

            TextView title = text(
                TextUtils.isEmpty(alert.chatName) ? "Pesan baru" : alert.chatName,
                16,
                color(Theme.key_windowBackgroundWhiteBlackText)
            );
            title.setTypeface(AndroidUtilities.bold());
            card.addView(title);

            String meta = (alert.mention ? "Mention" : "Balasan")
                + (TextUtils.isEmpty(alert.senderName) ? "" : " · " + alert.senderName)
                + " · " + format.format(new Date(alert.date));
            TextView metaView = text(meta, 12, color(Theme.key_windowBackgroundWhiteGrayText2));
            metaView.setPadding(0, dp(2), 0, 0);
            card.addView(metaView);

            TextView content = text(
                TextUtils.isEmpty(alert.text) ? "Pesan tanpa teks" : alert.text,
                14,
                color(Theme.key_windowBackgroundWhiteBlackText)
            );
            content.setPadding(0, dp(8), 0, 0);
            content.setMaxLines(5);
            content.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(content);

            body.addView(card, fullWidth(top(8)));
        }
    }

    private void confirmClearAlerts() {
        showConfirm(
            "Bersihkan semua pesan?",
            "Daftar mention dan balasan akan dihapus dari perangkat ini.",
            "Bersihkan",
            () -> {
                RPlusStore.clearAlerts(currentAccount);
                showScreen(SCREEN_ALERTS);
            }
        );
    }

    // ---------------------------------------------------------------------------------------
    // Shared UI building blocks
    // ---------------------------------------------------------------------------------------

    private void setActionBar(CharSequence title, CharSequence subtitle) {
        actionBar.setTitle(title);
        actionBar.setSubtitle(subtitle);
    }

    private LinearLayout createBody() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        contentScroll = scroll;
        contentContainer.addView(scroll, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return body;
    }

    private void addHeader(LinearLayout body, String title) {
        TextView header = text(title, 14, color(Theme.key_windowBackgroundWhiteBlueHeader));
        header.setTypeface(AndroidUtilities.bold());
        header.setPadding(dp(4), dp(24), dp(4), dp(8));
        body.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void addFooter(LinearLayout body, CharSequence value) {
        TextView footer = text(value, 12, color(Theme.key_windowBackgroundWhiteGrayText2));
        footer.setLineSpacing(dp(2), 1f);
        footer.setPadding(dp(8), dp(16), dp(8), dp(8));
        body.addView(footer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(color(Theme.key_windowBackgroundWhite), 12));
        card.setClipToOutline(true);
        return card;
    }

    private View buildRow(int iconRes, CharSequence title, CharSequence subtitle,
                          CharSequence value, boolean chevron, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(ripple(null));

        if (iconRes != 0) {
            ImageView icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setImageResource(iconRes);
            icon.setColorFilter(color(Theme.key_windowBackgroundWhiteGrayIcon));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
            iconParams.setMarginEnd(dp(16));
            row.addView(icon, iconParams);
        }

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = text(title, 16, color(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subtitleView = text(subtitle, 14, color(Theme.key_windowBackgroundWhiteGrayText2));
            subtitleView.setPadding(0, dp(2), 0, 0);
            subtitleView.setMaxLines(2);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(subtitleView);
        }
        row.addView(texts, new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1
        ));

        if (!TextUtils.isEmpty(value)) {
            TextView valueView = text(value, 14, color(Theme.key_windowBackgroundWhiteGrayText2));
            valueView.setPadding(dp(8), 0, chevron ? dp(4) : dp(2), 0);
            row.addView(valueView);
        }

        if (chevron) {
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_ab_back);
            if (!LocaleController.isRTL) {
                arrow.setRotation(180);
            }
            arrow.setColorFilter(color(Theme.key_windowBackgroundWhiteGrayText2));
            arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(16), dp(16)));
        }

        if (action != null) {
            row.setOnClickListener(v -> action.run());
        }
        return row;
    }

    private View buildNotice(int iconRes, CharSequence title, CharSequence message, int titleColor) {
        LinearLayout card = newCard();
        card.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(titleColor);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconParams.setMarginEnd(dp(8));
        topRow.addView(icon, iconParams);

        TextView titleView = text(title, 14, titleColor);
        titleView.setTypeface(AndroidUtilities.bold());
        topRow.addView(titleView, new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1
        ));
        card.addView(topRow);

        TextView messageView = text(message, 14, color(Theme.key_windowBackgroundWhiteGrayText2));
        messageView.setPadding(0, dp(8), 0, 0);
        messageView.setLineSpacing(dp(2), 1f);
        card.addView(messageView);
        return card;
    }

    private View emptyState(int iconRes, CharSequence title, CharSequence subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(48), dp(24), dp(24));
        box.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(color(Theme.key_windowBackgroundWhiteGrayText2));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView titleView = text(title, 16, color(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, dp(16), 0, 0);
        box.addView(titleView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitleView = text(subtitle, 14, color(Theme.key_windowBackgroundWhiteGrayText2));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setLineSpacing(dp(2), 1f);
        subtitleView.setPadding(0, dp(6), 0, 0);
        box.addView(subtitleView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        return box;
    }

    private TextView chip(CharSequence value, int chipColor) {
        TextView chip = text(value, 12, chipColor);
        chip.setTypeface(AndroidUtilities.bold());
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(4), dp(8), dp(4));
        chip.setBackground(rounded(Theme.multAlpha(chipColor, .14f), 8));
        return chip;
    }

    private TextView primaryButton(CharSequence label, Runnable action) {
        int buttonColor = accentButton();
        boolean darkText = !AndroidUtilities.isDarkColor(buttonColor);
        TextView button = text(label, 14, darkText ? 0xff111827 : Color.WHITE);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        GradientDrawable background = rounded(buttonColor, 12);
        button.setBackground(new RippleDrawable(
            ColorStateList.valueOf(darkText ? 0x22000000 : 0x33ffffff),
            background,
            rounded(0xffffffff, 12)
        ));
        button.setOnClickListener(v -> action.run());
        button.setMinHeight(dp(48));
        return button;
    }

    private View tonalIconButton(int iconRes, CharSequence contentDescription, Runnable action) {
        FrameLayout box = new FrameLayout(this);
        box.setContentDescription(contentDescription);
        box.setBackground(new RippleDrawable(
            ColorStateList.valueOf(color(Theme.key_listSelector)),
            rounded(Theme.multAlpha(accentButton(), .12f), 12),
            rounded(0xffffffff, 12)
        ));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent());
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        box.setOnClickListener(v -> action.run());
        return box;
    }

    private TextView textButton(CharSequence label, int textColor, Runnable action) {
        TextView button = text(label, 14, textColor);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(ripple(null));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void showConfirm(CharSequence title, CharSequence message,
                             CharSequence positive, Runnable action) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(positive, (dialog, which) -> action.run());
        builder.show();
    }

    private void showLimitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Broadcast dikunci sementara");
        String limitReason = RPlusStore.getLimitReason(currentAccount);
        builder.setMessage("Akun ini terindikasi terkena pembatasan Telegram. "
            + (TextUtils.isEmpty(limitReason)
                ? "Akun dibatasi sementara oleh Telegram."
                : limitReason)
            + "\n\nJangan mencoba mengirim paksa.");
        builder.setPositiveButton("Buka @SpamBot", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/spambot"));
            startActivity(intent);
        });
        if (BuildVars.DEBUG_VERSION) {
            builder.setNeutralButton("Reset uji", (dialog, which) -> {
                RPlusStore.clearLimitLock(currentAccount);
                notifyMessage("Status limit lokal direset", false);
                showScreen(SCREEN_HOME);
            });
        }
        builder.setNegativeButton(LocaleController.getString(R.string.Close), null);
        builder.show();
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
            focus.clearFocus();
        }
    }

    private TextView text(CharSequence value, float size, int textColor) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor);
        view.setIncludeFontPadding(false);
        return view;
    }

    private View divider(int startInsetDp) {
        View divider = new View(this);
        divider.setBackgroundColor(color(Theme.key_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Math.max(1, dp(0.5f))
        );
        params.setMarginStart(dp(startInsetDp));
        divider.setLayoutParams(params);
        return divider;
    }

    private LinearLayout.LayoutParams fullWidth(LinearLayout.LayoutParams params) {
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        return params;
    }

    private LinearLayout.LayoutParams top(int valueDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(valueDp);
        return params;
    }

    private GradientDrawable rounded(int backgroundColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(backgroundColor);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private Drawable ripple(Drawable content) {
        return new RippleDrawable(
            ColorStateList.valueOf(color(Theme.key_listSelector)),
            content,
            null
        );
    }

    private int accent() {
        return color(Theme.key_windowBackgroundWhiteBlueText);
    }

    private int accentButton() {
        return color(Theme.key_featuredStickers_addButton);
    }

    private int danger() {
        return color(Theme.key_text_RedRegular);
    }

    private int color(int key) {
        return Theme.getColor(key);
    }

    private int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    private void notifyMessage(CharSequence text, boolean error) {
        if (rootView != null && rootView.isAttachedToWindow()) {
            try {
                BulletinFactory factory = BulletinFactory.of(rootView, null);
                Bulletin bulletin = error
                    ? factory.createErrorBulletin(text)
                    : factory.createSuccessBulletin(text);
                bulletin.show();
                return;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
