package com.alrasin.focusguard;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/**
 * Usage stats on the home screen, read from the day keys in prefs "limits"
 * ({@code yyyyMMdd_<cat>} opens, {@code _ext_}, {@code _sec_}, {@code _vid_}, {@code _skip_}).
 * Nothing leaves the phone. Day keys older than {@link #KEEP_DAYS} are pruned.
 */
final class Stats {
    static final int KEEP_DAYS = 60;

    private Stats() {
    }

    /** One day summed over all targets. */
    static final class Day {
        int opens, skips, seconds, videos, extends_;
        boolean any;
    }

    static Day day(SharedPreferences p, String day) {
        Day d = new Day();
        String pre = day + "_";
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String k = e.getKey();
            if (!k.startsWith(pre) || !(e.getValue() instanceof Integer)) continue;
            int v = (Integer) e.getValue();
            String rest = k.substring(pre.length());
            d.any = true;
            if (rest.startsWith("ext_")) d.extends_ += v;
            else if (rest.startsWith("sec_")) d.seconds += v;
            else if (rest.startsWith("vid_")) d.videos += v;
            else if (rest.startsWith("skip_")) d.skips += v;
            else if (!rest.startsWith("why_")) d.opens += v;
        }
        return d;
    }

    private static String dayKey(Calendar c) {
        return Limits.day(c.getTimeInMillis());
    }

    /** Earliest day with any data, or null. */
    private static String firstDay(SharedPreferences p) {
        String first = null;
        for (String k : p.getAll().keySet()) {
            if (k.length() > 9 && k.charAt(8) == '_' && isDigits(k.substring(0, 8))) {
                String d = k.substring(0, 8);
                if (first == null || d.compareTo(first) < 0) first = d;
            }
        }
        return first;
    }

    private static boolean isDigits(String s) {
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    /** Drop day keys older than KEEP_DAYS, at most once a day. */
    static void prune(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("limits", Context.MODE_PRIVATE);
        Calendar c = Clock.calendar(ctx);
        String today = dayKey(c);
        if (today.equals(p.getString("pruned", ""))) return;
        c.add(Calendar.DAY_OF_YEAR, -KEEP_DAYS);
        String cut = dayKey(c);
        SharedPreferences.Editor e = p.edit().putString("pruned", today);
        for (String k : p.getAll().keySet()) {
            if (k.length() > 9 && k.charAt(8) == '_' && isDigits(k.substring(0, 8))
                    && k.substring(0, 8).compareTo(cut) < 0) e.remove(k);
        }
        e.apply();
    }

    /** Days in a row (up to today) without an extension, since the first day with data. */
    static int streak(Context ctx, SharedPreferences p) {
        String first = firstDay(p);
        if (first == null) return 0;
        Calendar c = Clock.calendar(ctx);
        int n = 0;
        for (int i = 0; i < KEEP_DAYS; i++) {
            String k = dayKey(c);
            if (k.compareTo(first) < 0) break;
            if (day(p, k).extends_ > 0) break;
            n++;
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return n;
    }

    private static Day sum(Context ctx, SharedPreferences p, int fromDaysAgo, int days) {
        Calendar c = Clock.calendar(ctx);
        c.add(Calendar.DAY_OF_YEAR, -fromDaysAgo);
        Day s = new Day();
        for (int i = 0; i < days; i++) {
            Day d = day(p, dayKey(c));
            s.opens += d.opens;
            s.skips += d.skips;
            s.seconds += d.seconds;
            s.videos += d.videos;
            s.extends_ += d.extends_;
            s.any |= d.any;
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return s;
    }

    private static String min(int seconds) {
        return (seconds + 59) / 60 + " min";
    }

    static void addSummary(Activity a, LinearLayout root) {
        prune(a);
        SharedPreferences p = a.getSharedPreferences("limits", Context.MODE_PRIVATE);
        if (!(a instanceof MainActivity)) return;
        MainActivity m = (MainActivity) a;
        root.addView(m.section("Today"));

        Day t = day(p, Limits.day(Clock.now(a)));
        LinearLayout tiles = Ui.row(a);
        tiles.addView(tile(a, String.valueOf(t.opens), "opens"), Ui.grow());
        tiles.addView(tile(a, String.valueOf(t.skips), "went back"), Ui.grow());
        tiles.addView(tile(a, String.valueOf((t.seconds + 59) / 60), "minutes"), Ui.grow());
        tiles.addView(tile(a, String.valueOf(streak(a, p)), "day streak"), Ui.grow());
        root.addView(tiles);

        Day week = sum(a, p, 0, 7), before = sum(a, p, 7, 7);
        root.addView(Ui.space(a, 10));
        StringBuilder sb = new StringBuilder();
        sb.append(L.t(a, "Last 7 days: " + week.opens + " opens, " + week.skips + " times you went back, "
                + (week.seconds + 59) / 60 + " min"));
        if (week.videos > 0) sb.append(L.t(a, ", " + week.videos + " videos"));
        sb.append(L.bangla(a) ? "।" : ".");
        if (before.any) {
            int diff = (before.seconds - week.seconds + 30) / 60;
            sb.append(' ').append(L.t(a, diff > 0 ? diff + " min less than the week before."
                    : diff < 0 ? -diff + " min more than the week before." : "Same as the week before."));
        }
        root.addView(Ui.left(a, sb.toString(), 14, Ui.MUTED));

        addChart(a, m, p, root);

        // Per-day list, newest first; days without any data are skipped.
        Calendar c = Clock.calendar(a);
        SimpleDateFormat f = new SimpleDateFormat("EEE d", Locale.US);
        LinearLayout list = Ui.column(a);
        for (int i = 0; i < 7; i++) {
            Day d = day(p, dayKey(c));
            if (d.any && i > 0) {
                TextView r = Ui.left(a, f.format(c.getTime()) + "   " + d.opens + " opens · " + d.skips
                        + " back · " + min(d.seconds) + (d.extends_ > 0 ? " · extended" : ""), 13, "#6B7280");
                list.addView(r);
            }
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        if (list.getChildCount() > 0) {
            root.addView(Ui.space(a, 6));
            root.addView(list);
        }
    }

    private static final String[] BN_DAYS = {"র", "সো", "ম", "বু", "বৃ", "শু", "শ"};

    /** Bar chart of minutes over the last 7 days, oldest left, today in sky blue. */
    private static void addChart(Activity a, MainActivity m, SharedPreferences p, LinearLayout root) {
        root.addView(m.section("Minutes per day"));
        Calendar c = Clock.calendar(a);
        c.add(Calendar.DAY_OF_YEAR, -6);
        int[] mins = new int[7];
        String[] labels = new String[7];
        SimpleDateFormat f = new SimpleDateFormat("EEEEE", Locale.US);
        int max = 1;
        for (int i = 0; i < 7; i++) {
            mins[i] = (day(p, dayKey(c)).seconds + 59) / 60;
            labels[i] = L.bangla(a) ? BN_DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] : f.format(c.getTime());
            max = Math.max(max, mins[i]);
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        LinearLayout box = Ui.column(a);
        box.setBackground(Ui.round(a, Ui.CARD, 14));
        int pad = Ui.dp(a, 14);
        box.setPadding(pad, pad, pad, pad);
        LinearLayout bars = Ui.row(a);
        bars.setGravity(android.view.Gravity.BOTTOM);
        int h = Ui.dp(a, 110);
        for (int i = 0; i < 7; i++) {
            LinearLayout col = Ui.column(a);
            col.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            col.addView(Ui.text(a, mins[i] > 0 ? String.valueOf(mins[i]) : "", 11, Ui.MUTED));
            android.view.View bar = new android.view.View(a);
            bar.setBackground(Ui.round(a, i == 6 ? Ui.SKY : "#3B4A60", 4));
            int bh = mins[i] == 0 ? Ui.dp(a, 3) : Math.max(Ui.dp(a, 6), h * mins[i] / max);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 18), bh);
            lp.topMargin = Ui.dp(a, 4);
            col.addView(bar, lp);
            col.addView(Ui.text(a, labels[i], 12, i == 6 ? Ui.SKY : "#6B7280"));
            bars.addView(col, new LinearLayout.LayoutParams(0, h + Ui.dp(a, 40), 1f));
        }
        box.addView(bars, Ui.wide());
        root.addView(box, Ui.wide());
    }

    private static LinearLayout tile(Context a, String big, String small) {
        LinearLayout box = Ui.column(a);
        box.setBackground(Ui.round(a, Ui.CARD, 12));
        int pad = Ui.dp(a, 10);
        box.setPadding(pad, pad, pad, pad);
        TextView n = Ui.text(a, big, 22, Ui.SKY);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(n);
        box.addView(Ui.text(a, small, 12, Ui.MUTED));
        LinearLayout wrap = Ui.column(a);
        int g = Ui.dp(a, 3);
        wrap.setPadding(g, 0, g, 0);
        wrap.addView(box);
        return wrap;
    }
}
