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

    /**
     * Weekly report text for the last 7 days (ending yesterday) compared with the 7 before, or null
     * if already sent this week or there's no data. Neutral tone: facts, no scolding.
     */
    static String weekly(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("limits", Context.MODE_PRIVATE);
        Calendar c = Clock.calendar(ctx);
        String week = c.get(Calendar.YEAR) + "w" + c.get(Calendar.WEEK_OF_YEAR);
        String sent = p.getString("weekly_sent", "");
        if (week.equals(sent)) return null;
        if (sent.isEmpty()) { // first run: start counting from next week, never report a partial week
            p.edit().putString("weekly_sent", week).apply();
            return null;
        }
        Day last = sum(ctx, p, 1, 7), before = sum(ctx, p, 8, 7);
        p.edit().putString("weekly_sent", week).apply();
        if (!last.any) return null;
        int m = (last.seconds + 59) / 60, mb = (before.seconds + 59) / 60;
        String s = "Last week: %s min, %s opens, went back %s times."
                .replaceFirst("%s", String.valueOf(m))
                .replaceFirst("%s", String.valueOf(last.opens))
                .replaceFirst("%s", String.valueOf(last.skips));
        String cmp = null;
        if (before.any && mb > m) cmp = "%s min less than the week before.".replace("%s", String.valueOf(mb - m));
        else if (before.any && m > mb) cmp = "%s min more than the week before.".replace("%s", String.valueOf(m - mb));
        return cmp == null ? L.t(ctx, s) : L.t(ctx, s) + " " + L.t(ctx, cmp);
    }

    private static String min(int seconds) {
        return (seconds + 59) / 60 + " min";
    }

    static void addSummary(Activity a, LinearLayout root) {
        prune(a);
        SharedPreferences p = a.getSharedPreferences("limits", Context.MODE_PRIVATE);
        if (!(a instanceof MainActivity)) return;
        MainActivity m = (MainActivity) a;
        Day week = sum(a, p, 0, 7), before = sum(a, p, 7, 7);
        root.addView(weekCard(a, week, before));

        root.addView(m.section("Today"));
        Day t = day(p, Limits.day(Clock.now(a)));
        LinearLayout tiles = Ui.row(a);
        tiles.addView(tile(a, String.valueOf(t.opens), "opens"), Ui.grow());
        tiles.addView(tile(a, String.valueOf(t.skips), "went back"), Ui.grow());
        tiles.addView(tile(a, String.valueOf((t.seconds + 59) / 60), "minutes"), Ui.grow());
        tiles.addView(tile(a, String.valueOf(streak(a, p)), "day streak"), Ui.grow());
        root.addView(tiles);

        addChart(a, m, p, root);

        // Per-day list, newest first; days without any data are skipped.
        Calendar c = Clock.calendar(a);
        SimpleDateFormat f = new SimpleDateFormat("EEEE d MMM", Locale.US);
        LinearLayout list = Ui.card(a);
        list.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 4));
        for (int i = 0; i < 7; i++) {
            Day d = day(p, dayKey(c));
            if (d.any && i > 0) {
                if (list.getChildCount() > 0) {
                    android.view.View line = new android.view.View(a);
                    line.setBackgroundColor(android.graphics.Color.parseColor(Ui.LINE));
                    list.addView(line, new LinearLayout.LayoutParams(-1, 1));
                }
                LinearLayout r = Ui.row(a);
                r.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 12));
                LinearLayout txt = Ui.column(a);
                txt.addView(Ui.left(a, f.format(c.getTime()), 15, Ui.WHITE));
                txt.addView(Ui.left(a, d.opens + " opens · went back " + d.skips
                        + (d.extends_ > 0 ? " · extended" : ""), 12, Ui.DIM));
                r.addView(txt, Ui.grow());
                TextView mm = Ui.text(a, min(d.seconds), 15, Ui.SKY);
                mm.setTypeface(Typeface.DEFAULT_BOLD);
                r.addView(mm);
                list.addView(r, Ui.wide());
            }
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        if (list.getChildCount() > 0) {
            root.addView(m.section("Earlier this week"));
            root.addView(list, Ui.wide());
        }
    }

    /** Big "last 7 days" card: minutes, opens/back line and a change-vs-last-week pill. */
    private static LinearLayout weekCard(Context a, Day week, Day before) {
        LinearLayout card = Ui.card(a);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{android.graphics.Color.parseColor("#17324A"), android.graphics.Color.parseColor("#141B26")});
        g.setCornerRadius(Ui.dp(a, 20));
        g.setStroke(Ui.dp(a, 1), android.graphics.Color.parseColor("#23405A"));
        card.setBackground(g);
        int pad = Ui.dp(a, 18);
        card.setPadding(pad, pad, pad, pad);
        TextView lbl = Ui.left(a, "LAST 7 DAYS", 12, Ui.SKY);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setLetterSpacing(0.08f);
        card.addView(lbl);
        LinearLayout big = Ui.row(a);
        big.setGravity(android.view.Gravity.BOTTOM);
        TextView n = Ui.left(a, String.valueOf((week.seconds + 59) / 60), 40, Ui.WHITE);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        big.addView(n);
        TextView u = Ui.left(a, " min", 16, Ui.MUTED);
        u.setPadding(0, 0, 0, Ui.dp(a, 8));
        big.addView(u);
        card.addView(big);
        String line = week.opens + " opens · went back " + week.skips + (week.skips == 1 ? " time" : " times")
                + (week.videos > 0 ? " · " + week.videos + " videos" : "");
        card.addView(Ui.left(a, line, 14, Ui.MUTED));
        if (before.any) {
            int diff = (before.seconds - week.seconds + 30) / 60;
            String s = diff > 0 ? "▼ " + diff + " min less than the week before"
                    : diff < 0 ? "▲ " + -diff + " min more than the week before" : "Same as the week before";
            String fg = diff > 0 ? Ui.GREEN : diff < 0 ? Ui.RED : Ui.MUTED;
            String bg = diff > 0 ? "#163A2A" : diff < 0 ? "#3A1C1F" : Ui.CARD;
            TextView pill = Ui.text(a, s, 13, fg);
            pill.setBackground(Ui.round(a, bg, 14));
            pill.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = Ui.dp(a, 12);
            card.addView(pill, lp);
        }
        return card;
    }

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
            labels[i] = f.format(c.getTime());
            max = Math.max(max, mins[i]);
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        LinearLayout box = Ui.column(a);
        box.setBackground(Ui.round(a, Ui.CARD, 20));
        int pad = Ui.dp(a, 16);
        box.setPadding(pad, pad, pad, pad);
        LinearLayout bars = Ui.row(a);
        bars.setGravity(android.view.Gravity.BOTTOM);
        int h = Ui.dp(a, 110);
        for (int i = 0; i < 7; i++) {
            LinearLayout col = Ui.column(a);
            col.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            col.addView(Ui.text(a, mins[i] > 0 ? String.valueOf(mins[i]) : "", 11, Ui.MUTED));
            android.view.View bar = new android.view.View(a);
            bar.setBackground(Ui.round(a, i == 6 ? Ui.SKY : "#3B4A60", 6));
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
        box.setBackground(Ui.round(a, Ui.CARD, 16));
        int pad = Ui.dp(a, 12);
        box.setPadding(pad, Ui.dp(a, 14), pad, Ui.dp(a, 14));
        TextView n = Ui.text(a, big, 24, Ui.WHITE);
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
