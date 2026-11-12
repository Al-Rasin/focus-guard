package com.alrasin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-target settings, one JSON block per category in prefs "config". Missing fields fall back to
 * the defaults below (= the original fixed rules), so old installs behave exactly as before.
 */
final class Config {
    static final String PREFS = "config";

    boolean enabled = true;
    int pauseS = 5;          // seconds on the pause screen
    int pauseStepS = 0;      // extra seconds per open already used today (growing pause)
    int opens = 6;           // opens per day
    int openMin = 15;        // minutes per open
    int dailyCapMin = 0;     // max minutes per day across opens, 0 = off
    int cooldownMin = 0;     // minimum gap between opens, 0 = off
    int maxVideos = 0;       // end an open after N videos, 0 = off
    int extOpens = 3;        // opens added by "Extend"
    int extMax = 2;          // extends per day
    int extCutoff = 19;      // no extending from this hour on
    boolean askWhy = false;  // must type a reason before "Open"
    boolean timer = true;    // time-left tab during an open
    String mode = "part";    // "part" = only Shorts/Reels screens, "whole" = the whole app
    List<String> schedule = new ArrayList<>(); // "HH:MM-HH:MM" strict-block windows

    final String cat;

    private Config(String cat) {
        this.cat = cat;
    }

    static Config load(Context ctx, String cat) {
        Config c = new Config(cat);
        String raw = prefs(ctx).getString(cat, null);
        if (raw == null) return c;
        try {
            c.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
        }
        return c;
    }

    void save(Context ctx) {
        prefs(ctx).edit().putString(cat, toJson().toString()).apply();
    }

    Config copy() {
        Config c = new Config(cat);
        try {
            c.fromJson(toJson());
        } catch (Exception ignored) {
        }
        return c;
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The user's own "do this instead" ideas, shown on the pause screen (one per line). */
    static List<String> swaps(Context ctx) {
        List<String> out = new ArrayList<>();
        for (String l : prefs(ctx).getString("swaps", "").split("\n"))
            if (!l.trim().isEmpty()) out.add(l.trim());
        return out;
    }

    static String swapsText(Context ctx) {
        return prefs(ctx).getString("swaps", "");
    }

    static void saveSwaps(Context ctx, String text) {
        prefs(ctx).edit().putString("swaps", text.trim()).apply();
    }

    void fromJson(JSONObject j) {
        enabled = j.optBoolean("enabled", enabled);
        pauseS = j.optInt("pause_s", pauseS);
        pauseStepS = j.optInt("pause_step_s", pauseStepS);
        opens = j.optInt("opens", opens);
        openMin = j.optInt("open_min", openMin);
        dailyCapMin = j.optInt("daily_cap_min", dailyCapMin);
        cooldownMin = j.optInt("cooldown_min", cooldownMin);
        maxVideos = j.optInt("max_videos", maxVideos);
        JSONObject e = j.optJSONObject("extend");
        if (e != null) {
            extOpens = e.optInt("opens", extOpens);
            extMax = e.optInt("max", extMax);
            extCutoff = e.optInt("cutoff", extCutoff);
        }
        askWhy = j.optBoolean("ask_why", askWhy);
        timer = j.optBoolean("timer", timer);
        mode = j.optString("mode", mode);
        JSONArray s = j.optJSONArray("schedule");
        if (s != null) {
            schedule.clear();
            for (int i = 0; i < s.length(); i++) {
                String w = s.optString(i);
                if (parseWindow(w) != null) schedule.add(w);
            }
        }
    }

    JSONObject toJson() {
        JSONObject j = new JSONObject();
        try {
            j.put("enabled", enabled);
            j.put("pause_s", pauseS);
            j.put("pause_step_s", pauseStepS);
            j.put("opens", opens);
            j.put("open_min", openMin);
            j.put("daily_cap_min", dailyCapMin);
            j.put("cooldown_min", cooldownMin);
            j.put("max_videos", maxVideos);
            JSONObject e = new JSONObject();
            e.put("opens", extOpens);
            e.put("max", extMax);
            e.put("cutoff", extCutoff);
            j.put("extend", e);
            j.put("ask_why", askWhy);
            j.put("timer", timer);
            j.put("mode", mode);
            j.put("schedule", new JSONArray(schedule));
        } catch (Exception ignored) {
        }
        return j;
    }

    /** Pause length for the next open: base + step per open already used today. */
    int pauseFor(int usedToday) {
        return Math.min(600, pauseS + pauseStepS * usedToday);
    }

    /** "22:00-07:00" → {startMin, endMin}, or null if malformed. */
    static int[] parseWindow(String w) {
        try {
            String[] p = w.trim().split("-");
            return new int[]{minutes(p[0]), minutes(p[1])};
        } catch (Exception e) {
            return null;
        }
    }

    private static int minutes(String hhmm) {
        String[] p = hhmm.trim().split(":");
        int h = Integer.parseInt(p[0]), m = p.length > 1 ? Integer.parseInt(p[1]) : 0;
        if (h < 0 || h > 24 || m < 0 || m > 59) throw new IllegalArgumentException();
        return h * 60 + m;
    }

    /** True if minute-of-day falls in any schedule window (windows may cross midnight). */
    boolean inSchedule(int minuteOfDay) {
        for (String w : schedule) {
            int[] r = parseWindow(w);
            if (r == null || r[0] == r[1]) continue;
            boolean in = r[0] < r[1] ? minuteOfDay >= r[0] && minuteOfDay < r[1]
                    : minuteOfDay >= r[0] || minuteOfDay < r[1];
            if (in) return true;
        }
        return false;
    }

    /**
     * True if this config is at least as strict as {@code o} in every field. Used by strict mode:
     * tightening applies at once, anything else waits.
     */
    boolean stricterOrEqual(Config o) {
        if (!enabled && o.enabled) return false;
        if (pauseS < o.pauseS || pauseStepS < o.pauseStepS) return false;
        if (opens > o.opens || openMin > o.openMin) return false;
        if (cap(dailyCapMin) > cap(o.dailyCapMin)) return false;
        if (cooldownMin < o.cooldownMin) return false;
        if (cap(maxVideos) > cap(o.maxVideos)) return false;
        if (extOpens * extMax > o.extOpens * o.extMax || extCutoff > o.extCutoff) return false;
        if (!askWhy && o.askWhy) return false;
        if ("part".equals(mode) && "whole".equals(o.mode)) return false;
        if (!schedule.containsAll(o.schedule)) return false;
        return true;
    }

    private static int cap(int v) {
        return v <= 0 ? Integer.MAX_VALUE : v;
    }
}
