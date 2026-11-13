package com.alrasin.focusguard;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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

    /** Times the user went back from a pause in the last 7 days (today included). */
    static int weekSkips(Context ctx) {
        return sum(ctx, ctx.getSharedPreferences("limits", Context.MODE_PRIVATE), 0, 7).skips;
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
        root.addView(weekCard(a, week, before), Ui.wide());

        addChart(a, m, p, root);

        root.addView(m.section("Today"));
        Day t = day(p, Limits.day(Clock.now(a)));
        int streak = streak(a, p);
        LinearLayout r1 = Ui.row(a), r2 = Ui.row(a);
        r1.addView(tile(a, String.valueOf((t.seconds + 59) / 60), "minutes", Ui.WHITE), Ui.grow());
        r1.addView(gap(a));
        r1.addView(tile(a, String.valueOf(t.opens), t.opens == 1 ? "open" : "opens", Ui.WHITE), Ui.grow());
        r2.addView(tile(a, String.valueOf(t.skips), "went back", Ui.GREEN), Ui.grow());
        r2.addView(gap(a));
        r2.addView(tile(a, String.valueOf(streak), streak == 1 ? "day streak" : "days streak", Ui.AMBER), Ui.grow());
        root.addView(r1, Ui.wide());
        LinearLayout.LayoutParams lp2 = Ui.wide();
        lp2.topMargin = Ui.dp(a, 10);
        root.addView(r2, lp2);

        // Per-day list, newest first; days without any data are skipped.
        Calendar c = Clock.calendar(a);
        SimpleDateFormat f = new SimpleDateFormat("EEEE, d MMM", Locale.US);
        LinearLayout list = Ui.card(a);
        list.setPadding(Ui.dp(a, 16), Ui.dp(a, 2), Ui.dp(a, 16), Ui.dp(a, 2));
        for (int i = 0; i < 7; i++) {
            Day d = day(p, dayKey(c));
            if (d.any && i > 0) {
                if (list.getChildCount() > 0) {
                    android.view.View line = new android.view.View(a);
                    line.setBackgroundColor(android.graphics.Color.parseColor(Ui.LINE));
                    list.addView(line, new LinearLayout.LayoutParams(-1, 1));
                }
                LinearLayout r = Ui.row(a);
                r.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 14));
                LinearLayout txt = Ui.column(a);
                txt.addView(Ui.title(a, f.format(c.getTime()), 15));
                txt.addView(Ui.left(a, d.opens + (d.opens == 1 ? " open" : " opens") + " · " + d.skips
                        + " went back" + (d.extends_ > 0 ? " · extended" : ""), 13, Ui.DIM));
                r.addView(txt, Ui.grow());
                r.addView(Ui.number(a, String.valueOf((d.seconds + 59) / 60), 18, Ui.WHITE));
                r.addView(Ui.text(a, " min", 13, Ui.MUTED));
                list.addView(r, Ui.wide());
            }
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        if (list.getChildCount() > 0) {
            root.addView(m.section("Earlier this week"));
            root.addView(list, Ui.wide());
        }
    }

    private static android.view.View gap(Context a) {
        android.view.View v = new android.view.View(a);
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 10), 1));
        return v;
    }

    /** Hero: minutes in the last 7 days, change vs the week before, and how often the pause won. */
    private static LinearLayout weekCard(Context a, Day week, Day before) {
        LinearLayout card = Ui.column(a);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{android.graphics.Color.parseColor("#13324A"), android.graphics.Color.parseColor(Ui.SURF1)});
        g.setCornerRadius(Ui.dp(a, 28));
        g.setStroke(Ui.dp(a, 1), android.graphics.Color.parseColor("#1F3B52"));
        card.setBackground(g);
        int pad = Ui.dp(a, 20);
        card.setPadding(pad, pad, pad, pad);

        LinearLayout top = Ui.row(a);
        top.addView(Ui.overline(a, "Last 7 days", Ui.SKY), Ui.grow());
        if (before.any) {
            int diff = (before.seconds - week.seconds + 30) / 60;
            if (diff > 0) top.addView(Ui.badge(a, "▼ " + diff + " min", Ui.GREEN, Ui.GREEN_BOX));
            else if (diff < 0) top.addView(Ui.badge(a, "▲ " + -diff + " min", Ui.RED, Ui.RED_BOX));
            else top.addView(Ui.badge(a, "Same", Ui.MUTED, Ui.SURF2));
        }
        card.addView(top, Ui.wide());
        card.addView(Ui.space(a, 10));

        LinearLayout big = Ui.row(a);
        big.setGravity(android.view.Gravity.BOTTOM);
        big.addView(Ui.number(a, String.valueOf((week.seconds + 59) / 60), 52, Ui.WHITE));
        TextView u = Ui.left(a, " min", 18, Ui.MUTED);
        u.setPadding(0, 0, 0, Ui.dp(a, 6));
        big.addView(u);
        card.addView(big);
        card.addView(Ui.left(a, before.any ? "compared with the week before" : "on short videos and limited apps",
                13, Ui.MUTED));
        card.addView(Ui.space(a, 16));

        android.view.View line = new android.view.View(a);
        line.setBackgroundColor(android.graphics.Color.parseColor("#1F3B52"));
        card.addView(line, new LinearLayout.LayoutParams(-1, 1));
        card.addView(Ui.space(a, 14));
        LinearLayout nums = Ui.row(a);
        nums.addView(heroNum(a, String.valueOf(week.skips), "went back", Ui.GREEN), Ui.grow());
        nums.addView(heroNum(a, String.valueOf(week.opens), "opened", Ui.WHITE), Ui.grow());
        int avg = (week.seconds + 59) / 60 / 7;
        nums.addView(heroNum(a, String.valueOf(avg), "min a day", Ui.WHITE), Ui.grow());
        card.addView(nums, Ui.wide());

        int tries = week.skips + week.opens;
        if (tries > 0) {
            card.addView(Ui.space(a, 14));
            int pct = Math.round(100f * week.skips / tries);
            String msg = pct >= 50 ? "The pause won " + pct + "% of the time. That's real self-control."
                    : pct > 0 ? "You went back " + pct + "% of the time. Each one counts."
                    : "Next time, try \"Go back\" once. It gets easier.";
            card.addView(Ui.left(a, msg, 14, Ui.SKY_SOFT));
        }
        return card;
    }

    private static LinearLayout heroNum(Context a, String big, String small, String color) {
        LinearLayout c = Ui.column(a);
        TextView n = Ui.number(a, big, 22, color);
        n.setGravity(android.view.Gravity.START);
        c.addView(n);
        c.addView(Ui.space(a, 4));
        c.addView(Ui.left(a, small, 12, Ui.MUTED));
        return c;
    }

    /** Bar chart of minutes over the last 7 days, oldest left, today in sky blue. */
    private static void addChart(Activity a, MainActivity m, SharedPreferences p, LinearLayout root) {
        Calendar c = Clock.calendar(a);
        c.add(Calendar.DAY_OF_YEAR, -6);
        int[] mins = new int[7];
        String[] labels = new String[7];
        SimpleDateFormat f = new SimpleDateFormat("EEE", Locale.US);
        int max = 1;
        for (int i = 0; i < 7; i++) {
            mins[i] = (day(p, dayKey(c)).seconds + 59) / 60;
            labels[i] = i == 6 ? "Today" : f.format(c.getTime());
            max = Math.max(max, mins[i]);
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        root.addView(m.section("Minutes per day"));
        LinearLayout box = Ui.card(a);
        box.setPadding(Ui.dp(a, 12), Ui.dp(a, 18), Ui.dp(a, 12), Ui.dp(a, 14));
        LinearLayout bars = Ui.row(a);
        bars.setGravity(android.view.Gravity.BOTTOM);
        int h = Ui.dp(a, 120);
        for (int i = 0; i < 7; i++) {
            boolean today = i == 6;
            LinearLayout col = Ui.column(a);
            col.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            TextView v = Ui.text(a, mins[i] > 0 ? String.valueOf(mins[i]) : "", 11, today ? Ui.SKY : Ui.MUTED);
            v.setFontFeatureSettings("tnum");
            v.setTypeface(Ui.weight(today ? 600 : 400));
            col.addView(v);
            android.view.View bar = new android.view.View(a);
            bar.setBackground(Ui.round(a, today ? Ui.SKY : mins[i] == 0 ? Ui.SURF2 : Ui.LINE2, 8));
            int bh = mins[i] == 0 ? Ui.dp(a, 4) : Math.max(Ui.dp(a, 8), h * mins[i] / max);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 24), bh);
            lp.topMargin = Ui.dp(a, 6);
            lp.bottomMargin = Ui.dp(a, 8);
            col.addView(bar, lp);
            TextView l = Ui.text(a, labels[i], 11, today ? Ui.SKY : Ui.DIM);
            l.setTypeface(Ui.weight(today ? 600 : 400));
            col.addView(l);
            bars.addView(col, new LinearLayout.LayoutParams(0, h + Ui.dp(a, 48), 1f));
        }
        box.addView(bars, Ui.wide());
        root.addView(box, Ui.wide());
    }

    private static LinearLayout tile(Context a, String big, String small, String color) {
        LinearLayout box = Ui.card(a);
        box.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        TextView n = Ui.number(a, big, 28, color);
        n.setGravity(android.view.Gravity.START);
        box.addView(n);
        box.addView(Ui.space(a, 4));
        box.addView(Ui.left(a, small, 13, Ui.MUTED));
        return box;
    }
}
