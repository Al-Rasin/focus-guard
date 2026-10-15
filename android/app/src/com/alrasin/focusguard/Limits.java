package com.alrasin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Daily open counts and unlock windows, same rules as the old ScreenZen setup. */
final class Limits {
    static final String SHORTS = "shorts";
    static final String REELS = "reels";

    static final int PAUSE_SECONDS = 5;
    static final int DAILY_OPENS = 6;
    static final long UNLOCK_MS = 15 * 60 * 1000L;
    static final int EXTEND_OPENS = 3;
    static final int MAX_EXTENDS = 2;
    /** Extensions only while at least 5 h of the day remain. */
    static final int EXTEND_CUTOFF_HOUR = 19;

    private final SharedPreferences prefs;

    Limits(Context ctx) {
        prefs = ctx.getSharedPreferences("limits", Context.MODE_PRIVATE);
    }

    static String label(String cat) {
        return SHORTS.equals(cat) ? "YouTube Shorts" : "Facebook Reels";
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    int used(String cat) {
        return prefs.getInt(today() + "_" + cat, 0);
    }

    int extends_(String cat) {
        return prefs.getInt(today() + "_ext_" + cat, 0);
    }

    /** Daily opens including extensions taken today. */
    int total(String cat) {
        return DAILY_OPENS + EXTEND_OPENS * extends_(cat);
    }

    int left(String cat) {
        return Math.max(0, total(cat) - used(cat));
    }

    boolean canExtend(String cat) {
        return left(cat) == 0 && extends_(cat) < MAX_EXTENDS
                && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < EXTEND_CUTOFF_HOUR;
    }

    void extend(String cat) {
        if (canExtend(cat)) prefs.edit().putInt(today() + "_ext_" + cat, extends_(cat) + 1).apply();
    }

    boolean isUnlocked(String cat) {
        return System.currentTimeMillis() < prefs.getLong("until_" + cat, 0);
    }

    long unlockedUntil(String cat) {
        return prefs.getLong("until_" + cat, 0);
    }

    /** Spend one open and start the unlock window. */
    void open(String cat) {
        prefs.edit()
                .putInt(today() + "_" + cat, used(cat) + 1)
                .putLong("until_" + cat, System.currentTimeMillis() + UNLOCK_MS)
                .apply();
    }

    String summary() {
        return "Shorts " + left(SHORTS) + "/" + total(SHORTS) + " left  ·  Reels "
                + left(REELS) + "/" + total(REELS) + " left";
    }
}
