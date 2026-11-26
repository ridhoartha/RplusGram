package org.telegram.messenger.rplus;

import android.text.TextUtils;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public final class RPlusMessageAlerts implements NotificationCenter.NotificationCenterDelegate {

    private static volatile RPlusMessageAlerts instance;

    public static void install() {
        if (instance != null) {
            return;
        }
        synchronized (RPlusMessageAlerts.class) {
            if (instance == null) {
                instance = new RPlusMessageAlerts();
                for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                    NotificationCenter.getInstance(account).addObserver(
                        instance,
                        NotificationCenter.didReceiveNewMessages
                    );
                }
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.didReceiveNewMessages || args == null || args.length < 4) {
            return;
        }
        long dialogId = (Long) args[0];
        ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
        boolean scheduled = (Boolean) args[2];
        int mode = (Integer) args[3];
        if (scheduled || mode != 0 || messages == null || messages.isEmpty()) {
            return;
        }
        for (MessageObject message : messages) {
            if (message == null || message.isOutOwner() || message.messageOwner == null) {
                continue;
            }
            boolean mention = message.messageOwner.mentioned;
            boolean reply = message.replyMessageObject != null && message.replyMessageObject.isOutOwner();
            if (mention || reply) {
                addAlert(account, dialogId, message, mention, reply);
            } else if (message.isReply() && message.messageOwner.reply_to != null
                && message.messageOwner.reply_to.reply_to_msg_id != 0) {
                final int replyToId = message.messageOwner.reply_to.reply_to_msg_id;
                MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
                    TLRPC.Message replied = MessagesStorage.getInstance(account).getMessage(dialogId, replyToId);
                    if (replied != null && replied.out) {
                        addAlert(account, dialogId, message, false, true);
                    }
                });
            }
        }
    }

    private void addAlert(int account, long dialogId, MessageObject message, boolean mention, boolean reply) {
        RPlusStore.RPlusAlert alert = new RPlusStore.RPlusAlert();
        alert.dialogId = dialogId;
        alert.messageId = message.getId();
        alert.chatName = nameForDialog(account, dialogId);
        alert.senderName = nameForPeer(account, MessageObject.getPeerId(message.messageOwner.from_id));
        CharSequence text = message.messageText;
        if (text == null) {
            text = message.caption;
        }
        alert.text = text == null ? "" : text.toString().trim();
        if (alert.text.length() > 220) {
            alert.text = alert.text.substring(0, 220) + "…";
        }
        alert.mention = mention;
        alert.reply = reply;
        alert.date = message.messageOwner.date > 0
            ? message.messageOwner.date * 1000L
            : System.currentTimeMillis();
        RPlusStore.addAlert(account, alert);
    }

    private String nameForDialog(int account, long dialogId) {
        if (DialogObject.isUserDialog(dialogId)) {
            return nameForPeer(account, dialogId);
        }
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        if (chat != null) {
            return DialogObject.getDialogTitle(chat);
        }
        return "Chat";
    }

    private String nameForPeer(int account, long peerId) {
        if (peerId > 0) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(peerId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else if (peerId < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-peerId);
            if (chat != null) {
                return DialogObject.getDialogTitle(chat);
            }
        }
        return "Pengguna";
    }
}
