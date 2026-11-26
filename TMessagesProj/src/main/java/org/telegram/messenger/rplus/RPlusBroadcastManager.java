package org.telegram.messenger.rplus;

import android.text.TextUtils;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RPlusBroadcastManager {

    public interface Listener {
        void onBroadcastStateChanged(int account);
    }

    private static final long TARGET_DELAY_MS = 2000L;
    private static final long SPAM_GUARD_MS = 24L * 60L * 60L * 1000L;
    private static final RPlusBroadcastManager[] INSTANCES = new RPlusBroadcastManager[UserConfig.MAX_ACCOUNT_COUNT];

    public static RPlusBroadcastManager getInstance(int account) {
        synchronized (RPlusBroadcastManager.class) {
            RPlusBroadcastManager result = INSTANCES[account];
            if (result == null) {
                result = new RPlusBroadcastManager(account);
                INSTANCES[account] = result;
            }
            return result;
        }
    }

    private final int currentAccount;
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private final LinkedHashMap<String, Integer> failureReasons = new LinkedHashMap<>();
    private ArrayList<RPlusTarget> targets = new ArrayList<>();
    private ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
    private boolean running;
    private boolean paused;
    private boolean cancelRequested;
    private boolean limitPaused;
    private int mode;
    private int index;
    private int sent;
    private int failed;
    private int skipped;
    private long startedAt;
    private long finishedAt;
    private String text;
    private String currentTarget;
    private String pauseReason;

    private RPlusBroadcastManager(int account) {
        currentAccount = account;
    }

    public synchronized void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        ArrayList<Listener> copy;
        synchronized (this) {
            copy = new ArrayList<>(listeners);
        }
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener listener : copy) {
                try {
                    listener.onBroadcastStateChanged(currentAccount);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public synchronized boolean start(int mode, String text, ArrayList<TLRPC.MessageEntity> entities) {
        if (running || TextUtils.isEmpty(text) || RPlusStore.isLimitLocked(currentAccount)) {
            return false;
        }
        TargetSnapshot snapshot = buildTargets(currentAccount, mode);
        if (snapshot.targets.isEmpty()) {
            return false;
        }
        this.mode = mode;
        this.text = text;
        this.entities = entities != null ? new ArrayList<>(entities) : new ArrayList<>();
        this.targets = snapshot.targets;
        this.skipped = snapshot.blacklisted;
        this.index = 0;
        this.sent = 0;
        this.failed = 0;
        this.startedAt = System.currentTimeMillis();
        this.finishedAt = 0;
        this.running = true;
        this.paused = false;
        this.cancelRequested = false;
        this.limitPaused = false;
        this.pauseReason = null;
        this.currentTarget = null;
        failureReasons.clear();
        notifyListeners();
        sendNext();
        return true;
    }

    private void sendNext() {
        final int targetIndex;
        final RPlusTarget target;
        synchronized (this) {
            if (!running || paused || cancelRequested) {
                return;
            }
            if (index >= targets.size()) {
                finish(false);
                return;
            }
            if (RPlusStore.isLimitLocked(currentAccount)) {
                pauseReason = RPlusStore.getLimitReason(currentAccount);
                limitPaused = true;
                paused = true;
                notifyListeners();
                return;
            }
            targetIndex = index++;
            target = targets.get(targetIndex);
            currentTarget = target.name;
            notifyListeners();
        }

        if (!ApplicationLoader.isNetworkOnline()) {
            synchronized (this) {
                index = targetIndex;
                currentTarget = null;
                paused = true;
                pauseReason = "Menunggu koneksi internet";
                notifyListeners();
            }
            return;
        }

        MessagesController controller = MessagesController.getInstance(currentAccount);
        TLRPC.InputPeer inputPeer = controller.getInputPeer(target.dialogId);
        if (inputPeer == null || inputPeer instanceof TLRPC.TL_inputPeerEmpty) {
            recordFailure("Chat tidak tersedia");
            scheduleNext();
            return;
        }

        TLRPC.TL_messages_sendMessage request = new TLRPC.TL_messages_sendMessage();
        request.peer = inputPeer;
        request.message = text;
        request.random_id = Utilities.random.nextLong();
        request.background = true;
        if (entities != null && !entities.isEmpty()) {
            request.entities = new ArrayList<>(entities);
            request.flags |= 8;
        }

        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                handleSendError(error.text, target);
                return;
            }
            if (response instanceof TLRPC.Updates) {
                try {
                    MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) response, false);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            synchronized (RPlusBroadcastManager.this) {
                sent++;
                currentTarget = null;
            }
            notifyListeners();
            scheduleNext();
        }), null, ConnectionsManager.RequestFlagDoNotWaitFloodWait | ConnectionsManager.RequestFlagCanCompress | ConnectionsManager.RequestFlagInvokeAfter);
    }

    private void handleSendError(String error, RPlusTarget target) {
        if (error == null) {
            error = "UNKNOWN";
        }
        if (error.startsWith("FLOOD_WAIT_")) {
            int seconds = 60;
            try {
                seconds = Integer.parseInt(error.substring("FLOOD_WAIT_".length()));
            } catch (Throwable ignore) {
            }
            synchronized (this) {
                currentTarget = null;
                paused = true;
                limitPaused = true;
                pauseReason = "Telegram meminta jeda " + seconds + " detik";
                RPlusStore.setLimitLock(currentAccount, System.currentTimeMillis() + seconds * 1000L, pauseReason);
            }
            notifyListeners();
            return;
        }
        if ("PEER_FLOOD".equals(error) || "USER_RESTRICTED".equals(error)) {
            synchronized (this) {
                currentTarget = null;
                paused = true;
                limitPaused = true;
                pauseReason = "Akun terindikasi dibatasi Telegram";
                RPlusStore.setLimitLock(currentAccount, System.currentTimeMillis() + SPAM_GUARD_MS, pauseReason);
            }
            notifyListeners();
            return;
        }
        recordFailure(reasonForError(error));
        scheduleNext();
    }

    private void recordFailure(String reason) {
        synchronized (this) {
            failed++;
            currentTarget = null;
            Integer count = failureReasons.get(reason);
            failureReasons.put(reason, count == null ? 1 : count + 1);
        }
        notifyListeners();
    }

    private void scheduleNext() {
        AndroidUtilities.runOnUIThread(() -> {
            synchronized (RPlusBroadcastManager.this) {
                if (!running || paused || cancelRequested) {
                    return;
                }
            }
            sendNext();
        }, TARGET_DELAY_MS);
    }

    public synchronized void pause() {
        if (running && !paused) {
            paused = true;
            pauseReason = "Dijeda oleh pengguna";
            notifyListeners();
        }
    }

    public synchronized void resume() {
        if (!running || !paused) {
            return;
        }
        if (RPlusStore.isLimitLocked(currentAccount)) {
            pauseReason = RPlusStore.getLimitReason(currentAccount);
            notifyListeners();
            return;
        }
        paused = false;
        limitPaused = false;
        pauseReason = null;
        notifyListeners();
        AndroidUtilities.runOnUIThread(this::sendNext);
    }

    public synchronized void cancel() {
        if (!running) {
            return;
        }
        cancelRequested = true;
        paused = false;
        notifyListeners();
        finish(true);
    }

    private void finish(boolean cancelled) {
        RPlusStore.RPlusHistory history;
        synchronized (this) {
            if (!running) {
                return;
            }
            running = false;
            paused = false;
            finishedAt = System.currentTimeMillis();
            currentTarget = null;
            history = new RPlusStore.RPlusHistory();
            history.id = startedAt;
            history.mode = mode;
            history.startedAt = startedAt;
            history.finishedAt = finishedAt;
            history.total = targets.size() + skipped;
            history.sent = sent;
            history.failed = failed;
            history.skipped = skipped;
            history.cancelled = cancelled;
            history.floodWait = limitPaused;
            history.failureReasons = new LinkedHashMap<>(failureReasons);
        }
        RPlusStore.addHistory(currentAccount, history);
        notifyListeners();
    }

    public static String reasonForError(String error) {
        if (TextUtils.isEmpty(error)) {
            return "Kesalahan tidak diketahui";
        }
        if ("CHAT_WRITE_FORBIDDEN".equals(error)
            || "CHAT_ADMIN_REQUIRED".equals(error)
            || "CHAT_RESTRICTED".equals(error)
            || "CHAT_FORBIDDEN".equals(error)
            || "CHAT_SEND_PLAIN_FORBIDDEN".equals(error)
            || "CHANNEL_PRIVATE".equals(error)) {
            return "Akses chat sedang ditutup";
        }
        if ("USER_IS_BLOCKED".equals(error) || "YOU_BLOCKED_USER".equals(error)) {
            return "Chat diblokir";
        }
        if ("INPUT_USER_DEACTIVATED".equals(error) || "USER_DEACTIVATED".equals(error)) {
            return "Akun pengguna tidak aktif";
        }
        if ("PEER_ID_INVALID".equals(error)) {
            return "Chat tidak tersedia";
        }
        return error;
    }

    public static TargetSnapshot buildTargets(int account, int mode) {
        TargetSnapshot snapshot = new TargetSnapshot();
        ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>(MessagesController.getInstance(account).getAllDialogs());
        Collections.sort(dialogs, (a, b) -> Integer.compare(b.date, a.date));
        LongSparseArray<RPlusTarget> unique = new LongSparseArray<>();
        long selfId = UserConfig.getInstance(account).getClientUserId();

        for (TLRPC.Dialog dialog : dialogs) {
            long dialogId = dialog.id;
            if (DialogObject.isEncryptedDialog(dialogId)) {
                continue;
            }
            if (mode == RPlusStore.MODE_GCAST) {
                if (!DialogObject.isChatDialog(dialogId)) {
                    continue;
                }
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
                if (chat == null || ChatObject.isNotInChat(chat)
                    || ChatObject.isChannel(chat) && !chat.megagroup
                    || chat.deactivated
                    || !ChatObject.canSendPlain(chat)) {
                    snapshot.unavailable++;
                    continue;
                }
                if (RPlusStore.isGroupBlacklisted(account, dialogId)) {
                    snapshot.blacklisted++;
                    continue;
                }
                if (unique.get(dialogId) == null) {
                    RPlusTarget target = new RPlusTarget();
                    target.dialogId = dialogId;
                    target.name = DialogObject.getDialogTitle(chat);
                    target.date = dialog.date;
                    unique.put(dialogId, target);
                }
            } else {
                if (!DialogObject.isUserDialog(dialogId)) {
                    continue;
                }
                TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
                if (user == null || user.id == selfId || user.bot || user.support || user.deleted) {
                    continue;
                }
                if (RPlusStore.isUserBlacklisted(account, dialogId)) {
                    snapshot.blacklisted++;
                    continue;
                }
                if (unique.get(dialogId) == null) {
                    RPlusTarget target = new RPlusTarget();
                    target.dialogId = dialogId;
                    target.name = UserObject.getUserName(user);
                    target.date = dialog.date;
                    unique.put(dialogId, target);
                }
            }
        }

        for (int i = 0; i < unique.size(); i++) {
            if (mode == RPlusStore.MODE_UCAST && snapshot.targets.size() >= 500) {
                break;
            }
            snapshot.targets.add(unique.valueAt(i));
        }
        snapshot.totalDiscoverable = snapshot.targets.size() + snapshot.blacklisted + snapshot.unavailable;
        return snapshot;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public synchronized boolean isLimitPaused() {
        return limitPaused;
    }

    public synchronized int getMode() {
        return mode;
    }

    public synchronized int getIndex() {
        return index;
    }

    public synchronized int getTotal() {
        return targets.size();
    }

    public synchronized int getSent() {
        return sent;
    }

    public synchronized int getFailed() {
        return failed;
    }

    public synchronized int getSkipped() {
        return skipped;
    }

    public synchronized String getCurrentTarget() {
        return currentTarget;
    }

    public synchronized String getPauseReason() {
        return pauseReason;
    }

    public synchronized long getStartedAt() {
        return startedAt;
    }

    public synchronized LinkedHashMap<String, Integer> getFailureReasons() {
        return new LinkedHashMap<>(failureReasons);
    }

    public synchronized String getProgressText() {
        return (mode == RPlusStore.MODE_GCAST ? "GCAST" : "UCAST") + " - " + Math.min(index, targets.size()) + "/" + targets.size();
    }

    public static final class RPlusTarget {
        public long dialogId;
        public String name;
        public int date;
    }

    public static final class TargetSnapshot {
        public final ArrayList<RPlusTarget> targets = new ArrayList<>();
        public int blacklisted;
        public int unavailable;
        public int totalDiscoverable;
    }
}
