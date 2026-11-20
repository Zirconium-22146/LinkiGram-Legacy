package com.radolyn.ayugram.utils;

import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.concurrent.ConcurrentHashMap;

import app.nimarkogram.messenger.NimarkoConfig;

/**
 * "Unread mode": the chat is opened and read locally, but the read receipt is never
 * sent, so the other person keeps seeing a single check mark instead of two.
 */
public final class AyuUnreadPreferences {
    private static final String CHAT_OVERRIDE_PREFIX = "unreadModeChat_";
    private static final long FORCE_WINDOW_MS = 5000L;

    private static final ConcurrentHashMap<Long, Boolean> chatOverrides = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> forcedReads = new ConcurrentHashMap<>();
    private static volatile Boolean hasOverrides;

    private AyuUnreadPreferences() {
    }

    public static boolean isEnabled() {
        return NimarkoConfig.keepChatsUnread;
    }

    /** Per-chat choice, or null when the chat simply follows the global switch. */
    public static Boolean getChatOverride(long dialogId) {
        if (!anyChatOverrides()) {
            return null;
        }
        Boolean cached = chatOverrides.get(dialogId);
        if (cached != null) {
            return cached;
        }
        SharedPreferences preferences = NimarkoConfig.getPreferences();
        String key = CHAT_OVERRIDE_PREFIX + dialogId;
        if (!preferences.contains(key)) {
            return null;
        }
        boolean value = preferences.getBoolean(key, false);
        chatOverrides.put(dialogId, value);
        return value;
    }

    public static void setChatOverride(long dialogId, boolean suppress) {
        chatOverrides.put(dialogId, suppress);
        hasOverrides = true;
        NimarkoConfig.getPreferences().edit()
                .putBoolean(CHAT_OVERRIDE_PREFIX + dialogId, suppress)
                .apply();
    }

    public static void clearChatOverrides() {
        SharedPreferences preferences = NimarkoConfig.getPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(CHAT_OVERRIDE_PREFIX)) {
                editor.remove(key);
            }
        }
        editor.apply();
        chatOverrides.clear();
        hasOverrides = false;
    }

    /** True when read receipts for this dialog should not be sent. */
    public static boolean shouldSuppressRead(long dialogId) {
        if (dialogId == 0) {
            return isEnabled();
        }
        // Read receipts are a two-person thing. In groups and channels nobody sees the
        // tick, and blocking their read position would only make unread counters differ
        // between devices, so unread mode stays out of them.
        if (!org.telegram.messenger.DialogObject.isUserDialog(dialogId)
                && !org.telegram.messenger.DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        if (!isEnabled() && !anyChatOverrides()) {
            return false;
        }
        Boolean override = getChatOverride(dialogId);
        return override != null ? override : isEnabled();
    }

    /** One deliberately sent read receipt, e.g. from "mark as read". */
    public static void forceReadOnce(long dialogId) {
        forcedReads.put(dialogId, SystemClock.elapsedRealtime() + FORCE_WINDOW_MS);
    }

    public static boolean isForced(long dialogId) {
        Long until = forcedReads.get(dialogId);
        if (until == null) {
            return false;
        }
        if (until < SystemClock.elapsedRealtime()) {
            forcedReads.remove(dialogId, until);
            return false;
        }
        return true;
    }

    public static boolean anyChatOverrides() {
        Boolean known = hasOverrides;
        if (known != null) {
            return known;
        }
        boolean found = false;
        for (String key : NimarkoConfig.getPreferences().getAll().keySet()) {
            if (key.startsWith(CHAT_OVERRIDE_PREFIX)) {
                found = true;
                break;
            }
        }
        hasOverrides = found;
        return found;
    }
}
