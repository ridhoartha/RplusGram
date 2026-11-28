package org.telegram.messenger.rplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RPlusStore {

    public static final int MODE_GCAST = 1;
    public static final int MODE_UCAST = 2;

    public static final int DELAY_FAST = 0;
    public static final int DELAY_NORMAL = 1;
    public static final int DELAY_SAFE = 2;

    private static final String PREFS_NAME = "rplus_broadcast";
    private static final int MAX_HISTORY = 100;
    private static final int MAX_ALERTS = 100;
    private static final int MAX_TEMPLATES = 30;
    private static final Type PEER_LIST_TYPE = new TypeToken<ArrayList<RPlusPeer>>() {}.getType();
    private static final Type TEMPLATE_LIST_TYPE = new TypeToken<ArrayList<RPlusTemplate>>() {}.getType();
    private static final Type HISTORY_LIST_TYPE = new TypeToken<ArrayList<RPlusHistory>>() {}.getType();
    private static final Type ALERT_LIST_TYPE = new TypeToken<ArrayList<RPlusAlert>>() {}.getType();
    private static final Type SCHEDULE_LIST_TYPE = new TypeToken<ArrayList<RPlusSchedule>>() {}.getType();

    private static final Gson GSON = new Gson();

    private RPlusStore() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String key(int account, String name) {
        return "a" + account + "_" + name;
    }

    private static <T> ArrayList<T> readList(int account, String name, Type type) {
        String value = prefs().getString(key(account, name), null);
        if (TextUtils.isEmpty(value)) {
            return new ArrayList<>();
        }
        try {
            ArrayList<T> result = GSON.fromJson(value, type);
            return result != null ? result : new ArrayList<>();
        } catch (Throwable e) {
            FileLog.e(e);
            return new ArrayList<>();
        }
    }

    private static void writeList(int account, String name, Object value) {
        prefs().edit().putString(key(account, name), GSON.toJson(value)).apply();
    }

    public static ArrayList<RPlusPeer> getGroupBlacklist(int account) {
        return readList(account, "group_blacklist", PEER_LIST_TYPE);
    }

    public static ArrayList<RPlusPeer> getUserBlacklist(int account) {
        return readList(account, "user_blacklist", PEER_LIST_TYPE);
    }

    public static boolean isGroupBlacklisted(int account, long dialogId) {
        return containsPeer(getGroupBlacklist(account), dialogId);
    }

    public static boolean isUserBlacklisted(int account, long dialogId) {
        return containsPeer(getUserBlacklist(account), dialogId);
    }

    public static void setGroupBlacklisted(int account, long dialogId, String name, boolean blacklisted) {
        updateBlacklist(account, true, dialogId, name, blacklisted);
    }

    public static void setUserBlacklisted(int account, long dialogId, String name, boolean blacklisted) {
        updateBlacklist(account, false, dialogId, name, blacklisted);
    }

    private static void updateBlacklist(int account, boolean group, long dialogId, String name, boolean blacklisted) {
        String storageKey = group ? "group_blacklist" : "user_blacklist";
        ArrayList<RPlusPeer> peers = readList(account, storageKey, PEER_LIST_TYPE);
        for (int i = peers.size() - 1; i >= 0; i--) {
            if (peers.get(i).id == dialogId) {
                peers.remove(i);
            }
        }
        if (blacklisted) {
            RPlusPeer peer = new RPlusPeer();
            peer.id = dialogId;
            peer.name = name;
            peer.date = System.currentTimeMillis();
            peers.add(0, peer);
        }
        writeList(account, storageKey, peers);
    }

    private static boolean containsPeer(List<RPlusPeer> peers, long dialogId) {
        for (RPlusPeer peer : peers) {
            if (peer.id == dialogId) {
                return true;
            }
        }
        return false;
    }

    public static ArrayList<RPlusTemplate> getTemplates(int account, int mode) {
        return readList(account, mode == MODE_GCAST ? "gcast_templates" : "ucast_templates", TEMPLATE_LIST_TYPE);
    }

    public static RPlusTemplate saveTemplate(int account, int mode, String name, String text, ArrayList<TLRPC.MessageEntity> entities) {
        ArrayList<RPlusTemplate> templates = getTemplates(account, mode);
        RPlusTemplate template = new RPlusTemplate();
        template.id = System.currentTimeMillis();
        template.name = TextUtils.isEmpty(name) ? (mode == MODE_GCAST ? "Gcast draft" : "Ucast draft") : name;
        template.text = text != null ? text : "";
        template.entities = encodeEntities(entities);
        template.updatedAt = System.currentTimeMillis();
        templates.add(0, template);
        while (templates.size() > MAX_TEMPLATES) {
            templates.remove(templates.size() - 1);
        }
        writeList(account, mode == MODE_GCAST ? "gcast_templates" : "ucast_templates", templates);
        return template;
    }

    public static void deleteTemplate(int account, int mode, long id) {
        ArrayList<RPlusTemplate> templates = getTemplates(account, mode);
        for (int i = templates.size() - 1; i >= 0; i--) {
            if (templates.get(i).id == id) {
                templates.remove(i);
            }
        }
        writeList(account, mode == MODE_GCAST ? "gcast_templates" : "ucast_templates", templates);
    }

    public static ArrayList<RPlusHistory> getHistory(int account) {
        return readList(account, "history", HISTORY_LIST_TYPE);
    }

    public static void addHistory(int account, RPlusHistory history) {
        ArrayList<RPlusHistory> items = getHistory(account);
        items.add(0, history);
        while (items.size() > MAX_HISTORY) {
            items.remove(items.size() - 1);
        }
        writeList(account, "history", items);
    }

    public static ArrayList<RPlusAlert> getAlerts(int account) {
        return readList(account, "alerts", ALERT_LIST_TYPE);
    }

    public static void addAlert(int account, RPlusAlert alert) {
        ArrayList<RPlusAlert> alerts = getAlerts(account);
        for (int i = alerts.size() - 1; i >= 0; i--) {
            RPlusAlert item = alerts.get(i);
            if (item.dialogId == alert.dialogId && item.messageId == alert.messageId) {
                alerts.remove(i);
            }
        }
        alerts.add(0, alert);
        while (alerts.size() > MAX_ALERTS) {
            alerts.remove(alerts.size() - 1);
        }
        writeList(account, "alerts", alerts);
    }

    public static void clearAlerts(int account) {
        writeList(account, "alerts", new ArrayList<RPlusAlert>());
    }

    public static ArrayList<RPlusSchedule> getSchedules(int account) {
        return readList(account, "schedules", SCHEDULE_LIST_TYPE);
    }

    public static void addSchedule(int account, RPlusSchedule schedule) {
        ArrayList<RPlusSchedule> schedules = getSchedules(account);
        schedules.add(schedule);
        writeList(account, "schedules", schedules);
    }

    public static RPlusSchedule getSchedule(int account, long id) {
        for (RPlusSchedule schedule : getSchedules(account)) {
            if (schedule.id == id) {
                return schedule;
            }
        }
        return null;
    }

    public static void removeSchedule(int account, long id) {
        ArrayList<RPlusSchedule> schedules = getSchedules(account);
        for (int i = schedules.size() - 1; i >= 0; i--) {
            if (schedules.get(i).id == id) {
                schedules.remove(i);
            }
        }
        writeList(account, "schedules", schedules);
    }

    public static ArrayList<Integer> getAccountsWithSchedules() {
        ArrayList<Integer> accounts = new ArrayList<>();
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated() && !getSchedules(account).isEmpty()) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    public static int getDelayMode(int account) {
        int mode = prefs().getInt(key(account, "delay_mode"), DELAY_NORMAL);
        return mode < DELAY_FAST || mode > DELAY_SAFE ? DELAY_NORMAL : mode;
    }

    public static void setDelayMode(int account, int mode) {
        if (mode < DELAY_FAST || mode > DELAY_SAFE) {
            mode = DELAY_NORMAL;
        }
        prefs().edit().putInt(key(account, "delay_mode"), mode).apply();
    }

    public static long getLimitLockUntil(int account) {
        return prefs().getLong(key(account, "limit_lock_until"), 0);
    }

    public static String getLimitReason(int account) {
        return prefs().getString(key(account, "limit_reason"), "");
    }

    public static void setLimitLock(int account, long until, String reason) {
        prefs().edit()
            .putLong(key(account, "limit_lock_until"), until)
            .putString(key(account, "limit_reason"), reason)
            .apply();
    }

    public static void clearLimitLock(int account) {
        prefs().edit()
            .remove(key(account, "limit_lock_until"))
            .remove(key(account, "limit_reason"))
            .apply();
    }

    public static boolean isLimitLocked(int account) {
        return getLimitLockUntil(account) > System.currentTimeMillis();
    }

    public static String encodeEntities(ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return "";
        }
        ArrayList<String> encoded = new ArrayList<>();
        for (TLRPC.MessageEntity entity : entities) {
            try {
                NativeByteBuffer buffer = new NativeByteBuffer(entity.getObjectSize());
                entity.serializeToStream(buffer);
                int length = buffer.position();
                byte[] bytes = new byte[length];
                buffer.position(0);
                buffer.buffer.get(bytes);
                encoded.add(Base64.encodeToString(bytes, Base64.NO_WRAP));
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return GSON.toJson(encoded);
    }

    public static ArrayList<TLRPC.MessageEntity> decodeEntities(String value) {
        ArrayList<TLRPC.MessageEntity> result = new ArrayList<>();
        if (TextUtils.isEmpty(value)) {
            return result;
        }
        try {
            ArrayList<String> encoded = GSON.fromJson(value, new TypeToken<ArrayList<String>>() {}.getType());
            if (encoded == null) {
                return result;
            }
            for (String item : encoded) {
                byte[] bytes = Base64.decode(item, Base64.NO_WRAP);
                NativeByteBuffer buffer = new NativeByteBuffer(bytes.length);
                buffer.writeBytes(bytes);
                buffer.position(0);
                int constructor = buffer.readInt32(true);
                TLRPC.MessageEntity entity = TLRPC.MessageEntity.TLdeserialize(buffer, constructor, true);
                if (entity != null) {
                    result.add(entity);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return result;
    }

    public static final class RPlusPeer {
        public long id;
        public String name;
        public long date;
    }

    public static final class RPlusTemplate {
        public long id;
        public String name;
        public String text;
        public String entities;
        public long updatedAt;
    }

    public static final class RPlusHistory {
        public long id;
        public int mode;
        public long startedAt;
        public long finishedAt;
        public int total;
        public int sent;
        public int failed;
        public int skipped;
        public boolean cancelled;
        public boolean floodWait;
        public LinkedHashMap<String, Integer> failureReasons = new LinkedHashMap<>();
    }

    public static final class RPlusAlert {
        public long dialogId;
        public int messageId;
        public String chatName;
        public String senderName;
        public String text;
        public boolean mention;
        public boolean reply;
        public long date;
    }

    public static final class RPlusSchedule {
        public long id;
        public int mode;
        public String text;
        public String entities;
        public long scheduledAt;
    }
}
