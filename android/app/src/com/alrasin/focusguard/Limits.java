package com.alrasin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Daily open counts and unlock windows. The rules come from {@link Config} per category; pref
 * keys (yyyyMMdd_cat, yyyyMMdd_ext_cat, until_cat) are the same as before so counts survive updates.
 */
final class Limits {
    static final String SHORTS = "shorts";
    static final String REELS = "reels";
    /** facebook.com in a browser, outside Reels and Messages. */
    static final String FACEBOOK = "facebook";
    static final String INSTAGRAM = "instagram";
    static final String TIKTOK = "tiktok";
    /** Whole-app targets: "app:" + package name. */
    static final String APP_PREFIX = "app:";

    static final String[] BUILTIN = {SHORTS, REELS, FACEBOOK, INSTAGRAM, TIKTOK};

    private final Context ctx;
    private final SharedPreferences prefs;

    Limits(Context ctx) {
        this.ctx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        prefs = ctx.getSharedPreferences("limits", Context.MODE_PRIVATE);
    }

    Config config(String cat) {
        return Config.load(ctx, cat);
    }

    static String label(String cat) {
        switch (cat) {
            case SHORTS: return "YouTube Shorts";
            case REELS: return "Facebook Reels";
            case FACEBOOK: return "Facebook";
            case INSTAGRAM: return "Instagram Reels";
            case TIKTOK: return "TikTok";
            default: return cat.startsWith(APP_PREFIX) ? cat.substring(APP_PREFIX.length()) : cat;
        }
    }

    /** Like {@link #label(String)}, but app targets show the app's own name. */
    static String label(Context ctx, String cat) {
        if (cat.startsWith(APP_PREFIX)) {
            String pkg = cat.substring(APP_PREFIX.length());
            try {
                PackageManager pm = ctx.getPackageManager();
                return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (Exception ignored) {
                return pkg;
            }
        }
        return label(cat);
    }

    /** Today's key part, on the tamper-proof {@link Clock}. */
    String today() {
        return day(Clock.now(ctx));
    }

    static String day(long ms) {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(ms));
    }

    long now() {
        return Clock.now(ctx);
    }

    int used(String cat) {
        return prefs.getInt(today() + "_" + cat, 0);
    }

    int extends_(String cat) {
        return prefs.getInt(today() + "_ext_" + cat, 0);
    }

    /** Daily opens including extensions taken today. */
    int total(String cat) {
        Config c = config(cat);
        return c.opens + c.extOpens * extends_(cat);
    }

    int left(String cat) {
        return Math.max(0, total(cat) - used(cat));
    }

    int pauseSeconds(String cat) {
        return config(cat).pauseFor(used(cat));
    }

    boolean canExtend(String cat) {
        Config c = config(cat);
        return left(cat) == 0 && c.extOpens > 0 && extends_(cat) < c.extMax
                && Clock.calendar(ctx).get(Calendar.HOUR_OF_DAY) < c.extCutoff
                && hardBlock(cat) == null;
    }

    void extend(String cat) {
        if (canExtend(cat)) prefs.edit().putInt(today() + "_ext_" + cat, extends_(cat) + 1).apply();
    }

    boolean isUnlocked(String cat) {
        return now() < unlockedUntil(cat);
    }

    long unlockedUntil(String cat) {
        return prefs.getLong("until_" + cat, 0);
    }

    /** Seconds spent inside opens today (counted by the timer tab while it is on screen). */
    int secondsToday(String cat) {
        return prefs.getInt(today() + "_sec_" + cat, 0);
    }

    void addSeconds(String cat, int s) {
        prefs.edit().putInt(today() + "_sec_" + cat, secondsToday(cat) + s).apply();
    }

    /** Wall time of the last open, for the cooldown. */
    long lastOpen(String cat) {
        return prefs.getLong("last_" + cat, 0);
    }

    /**
     * Why this target can't be opened at all right now, ignoring the opens count (schedule,
     * daily time cap, cooldown), or null.
     */
    String hardBlock(String cat) {
        Config c = config(cat);
        Calendar now = Clock.calendar(ctx);
        if (c.inSchedule(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)))
            return "blocked by your schedule";
        if (c.dailyCapMin > 0 && secondsToday(cat) >= c.dailyCapMin * 60)
            return "daily time used up";
        return null;
    }

    /** Minutes still to wait before the next open, 0 if none. */
    int cooldownLeftMin(String cat) {
        Config c = config(cat);
        if (c.cooldownMin <= 0) return 0;
        long next = Math.max(lastOpen(cat), unlockedUntil(cat)) + c.cooldownMin * 60_000L;
        long wait = next - now();
        if (wait <= 0 || lastOpen(cat) == 0) return 0;
        return (int) ((wait + 59_999) / 60_000);
    }

    /** Length of the next open in ms: open_min, cut to what is left of the daily cap. */
    long openMs(String cat) {
        Config c = config(cat);
        long ms = Math.max(1, c.openMin) * 60_000L;
        if (c.dailyCapMin > 0) ms = Math.min(ms, Math.max(0, c.dailyCapMin * 60L - secondsToday(cat)) * 1000L);
        return ms;
    }

    /** Spend one open and start the unlock window. */
    void open(String cat) {
        open(cat, openMs(cat));
    }

    /** Spend one open for a session the user picked; never longer than openMs(). */
    void open(String cat, long ms) {
        long now = now();
        prefs.edit()
                .putInt(today() + "_" + cat, used(cat) + 1)
                .putLong("until_" + cat, now + Math.min(ms, openMs(cat)))
                .putLong("last_" + cat, now)
                .putInt("vid_now_" + cat, 0)
                .apply();
    }

    /** End the running open now (video limit reached, etc.). */
    void endOpen(String cat) {
        prefs.edit().putLong("until_" + cat, 0).apply();
    }

    /** Videos seen in the running open. */
    int videosNow(String cat) {
        return prefs.getInt("vid_now_" + cat, 0);
    }

    void addVideo(String cat) {
        prefs.edit().putInt("vid_now_" + cat, videosNow(cat) + 1)
                .putInt(today() + "_vid_" + cat, prefs.getInt(today() + "_vid_" + cat, 0) + 1).apply();
    }

    /** "Go back" taps today: urges resisted, for stats. */
    int skips(String cat) {
        return prefs.getInt(today() + "_skip_" + cat, 0);
    }

    void addSkip(String cat) {
        prefs.edit().putInt(today() + "_skip_" + cat, skips(cat) + 1).apply();
    }

    void saveWhy(String cat, String why) {
        prefs.edit().putString("why_" + cat, why).apply();
    }

    String why(String cat) {
        return prefs.getString("why_" + cat, "");
    }

    /** Short line for the notification: every enabled target's opens left. */
    String summary() {
        StringBuilder sb = new StringBuilder();
        for (String cat : Targets.all(ctx)) {
            if (!config(cat).enabled) continue;
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(label(ctx, cat)).append(' ').append(left(cat)).append('/').append(total(cat));
        }
        return sb.append(" left").toString();
    }
}
