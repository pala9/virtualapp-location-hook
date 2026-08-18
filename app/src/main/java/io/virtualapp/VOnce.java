package io.virtualapp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 简单的"一次性标记"工具，替代已不可用的 Once 库
 */
public class VOnce {
    public static final String THIS_APP_INSTALL = "THIS_APP_INSTALL";
    private static final String PREFS_NAME = "once_flags";
    private static SharedPreferences prefs;

    public static void initialise(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean beenDone(String tag) {
        return prefs != null && prefs.getBoolean(tag, false);
    }

    public static boolean beenDone(String group, String tag) {
        return beenDone(group + "_" + tag);
    }

    public static void markDone(String tag) {
        if (prefs != null) {
            prefs.edit().putBoolean(tag, true).apply();
        }
    }

    public static void markDone(String group, String tag) {
        markDone(group + "_" + tag);
    }
}
