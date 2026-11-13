package com.alrasin.focusguard;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Launcher: tabs Home (targets) / Stats / Settings, per-target settings, app picker. */
public class MainActivity extends Activity {
    /** Which screen is up: "home", "stats", "settings", "edit:<cat>", "pick", "strict". */
    private String screen = "home";
    /** Last tab shown; Back from a sub-screen returns to it. */
    private String tab = "home";
    private Config edit;
    private Limits limits;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        limits = new Limits(this);
        Ui.onBack(this, this::back);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Strict.applyDue(this);
        if (isTab(screen)) openTab(screen);
        else if (screen.equals("strict")) strict(); // e.g. back from the device admin page
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (Backup.onResult(this, req, res, data)) refresh();
        else super.onActivityResult(req, res, data);
    }

    private static boolean isTab(String s) {
        return s.equals("home") || s.equals("stats") || s.equals("settings");
    }

    /** Re-render the current screen (after a strict-mode or language change). */
    void refresh() {
        if (screen.equals("strict")) strict();
        else if (screen.startsWith("edit:")) renderEdit(screen.substring(5));
        else if (screen.equals("pick")) picker();
        else openTab(isTab(screen) ? screen : tab);
    }

    void strict() {
        screen = "strict";
        Strict.screen(this);
    }

    @Override
    public void onBackPressed() {
        back();
    }

    private void back() {
        if (screen.equals("home")) moveTaskToBack(true);
        else if (isTab(screen)) openTab("home");
        else openTab(tab);
    }

    private void openTab(String t) {
        tab = t;
        if (t.equals("stats")) statsTab();
        else if (t.equals("settings")) settingsTab();
        else home();
    }

    // ---- tabs ---------------------------------------------------------------------------

    private void home() {
        screen = "home";
        LinearLayout root = page("Focus Guard");
        Setup.addStatus(this, root);

        root.addView(section("Targets"));
        for (String cat : Targets.all(this)) root.addView(targetCard(cat), card());

        Button add = Ui.small(this, "+ Add an app");
        add.setTextColor(Color.parseColor(Ui.SKY));
        add.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        add.setOnClickListener(v -> picker());
        root.addView(add, Ui.wide());
        show(root);
    }

    /** Top card on Home when setup is done: status, today at a glance, a kind line, Focus now. */
    void hero(LinearLayout root) {
        Limits limits = new Limits(this);
        android.content.SharedPreferences p = getSharedPreferences("limits", MODE_PRIVATE);
        Stats.Day t = Stats.day(p, Limits.day(Clock.now(this)));
        int streak = Stats.streak(this, p);
        LinearLayout box = Setup.box(this);

        TextView status = Ui.left(this, Setup.rooted(this) ? "Protection is on (root watchdog)"
                : "Protection is on", 13, Ui.GREEN);
        status.setCompoundDrawablePadding(Ui.dp(this, 8));
        status.setCompoundDrawablesRelative(dot(Ui.GREEN), null, null, null);
        box.addView(status);
        box.addView(Ui.space(this, 10));

        LinearLayout nums = Ui.row(this);
        nums.addView(heroNum(String.valueOf((t.seconds + 59) / 60), "min today"), Ui.grow());
        nums.addView(heroNum(String.valueOf(t.skips), "went back"), Ui.grow());
        nums.addView(heroNum(String.valueOf(streak), "day streak"), Ui.grow());
        box.addView(nums, Ui.wide());
        box.addView(Ui.space(this, 10));

        String kind = t.opens == 0 ? "No short videos today. Keep it up."
                : t.skips > 0 ? "You went back " + t.skips + " times today. Well done."
                : streak > 1 ? streak + " days in a row without extending."
                : "Every pause is a choice. You are doing fine.";
        box.addView(Ui.left(this, kind, 14, Ui.MUTED));

        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#2B3544"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 1));
        lp.topMargin = lp.bottomMargin = Ui.dp(this, 12);
        box.addView(line, lp);

        if (limits.inFocus()) {
            long left = (limits.focusUntil() - limits.now() + 59_999) / 60_000;
            TextView f = Ui.left(this, "Focus session until " + Limits.hhmm(limits.focusUntil()), 16, Ui.SKY);
            f.setTypeface(Typeface.DEFAULT_BOLD);
            box.addView(f);
            box.addView(Ui.left(this, left + " min left. Every target is closed.", 13, Ui.MUTED));
        } else {
            TextView f = Ui.left(this, "Focus now", 16, Ui.WHITE);
            f.setTypeface(Typeface.DEFAULT_BOLD);
            box.addView(f);
            box.addView(Ui.left(this, "Close every target for a while. Can't be undone.", 13, Ui.MUTED));
            box.addView(Ui.space(this, 8));
            LinearLayout row = Ui.row(this);
            int[] mins = {30, 60, 120};
            String[] names = {"30 min", "1 hour", "2 hours"};
            for (int i = 0; i < mins.length; i++) {
                int m = mins[i];
                String name = names[i];
                Button b = Ui.button(this, name, "#16303F", Ui.SKY);
                b.setTextSize(14);
                b.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
                b.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                        .setTitle(L.t(this, "Focus for " + name + "?"))
                        .setMessage(L.t(this, "Shorts, Reels and every app you added stay closed until "
                                + Limits.hhmm(limits.now() + m * 60_000L) + ". You can't end it early."))
                        .setPositiveButton(L.t(this, "Start"), (d, w) -> {
                            limits.startFocus(m);
                            home();
                        })
                        .setNegativeButton(L.t(this, "Cancel"), null).show());
                LinearLayout.LayoutParams bl = Ui.grow();
                if (i > 0) bl.leftMargin = Ui.dp(this, 8);
                row.addView(b, bl);
            }
            box.addView(row, Ui.wide());
        }
        LinearLayout.LayoutParams bp = Ui.wide();
        bp.bottomMargin = Ui.dp(this, 8);
        root.addView(box, bp);
    }

    private LinearLayout heroNum(String big, String small) {
        LinearLayout c = Ui.column(this);
        TextView n = Ui.left(this, big, 28, Ui.WHITE);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(n);
        c.addView(Ui.left(this, small, 12, Ui.MUTED));
        return c;
    }

    private GradientDrawable dot(String color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.parseColor(color));
        int d = Ui.dp(this, 8);
        g.setSize(d, d);
        g.setBounds(0, 0, d, d);
        return g;
    }

    private void statsTab() {
        screen = "stats";
        LinearLayout root = page("Stats");
        Stats.addSummary(this, root);
        show(root);
    }

    private void settingsTab() {
        screen = "settings";
        LinearLayout root = page("Settings");
        root.addView(section("Do this instead"));
        root.addView(Ui.left(this, "Your own ideas, one per line. The pause screen shows one at random.",
                14, Ui.MUTED));
        root.addView(Ui.space(this, 8));
        EditText swaps = input(Config.swapsText(this), "Read 5 pages\nWalk 10 minutes\nCall a friend");
        swaps.setSingleLine(false);
        swaps.setMinLines(3);
        swaps.setGravity(Gravity.TOP | Gravity.START);
        root.addView(swaps, Ui.wide());
        root.addView(Ui.space(this, 8));
        Button saveSwaps = Ui.small(this, "Save ideas");
        saveSwaps.setOnClickListener(v -> {
            Config.saveSwaps(this, swaps.getText().toString());
            Toast.makeText(this, L.t(this, "Saved"), Toast.LENGTH_SHORT).show();
        });
        root.addView(saveSwaps);

        Extras.addHomeButtons(this, root);
        show(root);
    }

    private View targetCard(String cat) {
        Config c = limits.config(cat);
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.round(this, Ui.CARD, 16));
        int p = Ui.dp(this, 14);
        box.setPadding(p, p, p, p);

        LinearLayout row = Ui.row(this);
        row.addView(iconView(cat), new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        row.addView(Ui.space(this, 0), new LinearLayout.LayoutParams(Ui.dp(this, 12), 1));

        LinearLayout col = Ui.column(this);
        TextView name = Ui.left(this, Limits.label(this, cat), 17, Ui.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(name);
        String sub;
        int used = limits.used(cat), total = limits.total(cat);
        if (!c.enabled) sub = "Not watched";
        else if (limits.hardBlock(cat) != null) sub = "Closed: " + limits.hardBlock(cat);
        else sub = "Opened " + used + " of " + total + " today"
                + ("whole".equals(c.mode) || Targets.wholeOnly(cat) ? " · whole app" : "");
        col.addView(Ui.left(this, sub, 13, c.enabled ? Ui.MUTED : "#6B7280"));
        row.addView(col, Ui.grow());

        // Status pill.
        String pill, color;
        if (!c.enabled) { pill = "Off"; color = "#6B7280"; }
        else if (limits.isUnlocked(cat)) { pill = "Open now"; color = Ui.GREEN; }
        else if (limits.hardBlock(cat) != null || limits.left(cat) == 0) { pill = "Closed"; color = Ui.RED; }
        else { pill = limits.left(cat) + " left"; color = Ui.SKY; }
        TextView pt = Ui.text(this, pill, 12, color);
        pt.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable pb = Ui.round(this, Ui.BG, 20);
        pb.setStroke(Ui.dp(this, 1), Color.parseColor(color));
        pt.setBackground(pb);
        pt.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        row.addView(pt);
        box.addView(row);

        // Opens used of today's total.
        if (c.enabled && total > 0) {
            box.addView(Ui.space(this, 12));
            box.addView(bar(Math.min(1f, used / (float) total),
                    used >= total ? Ui.RED : Ui.SKY), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 6)));
        }
        box.setOnClickListener(v -> edit(cat));
        return box;
    }

    /** Horizontal progress bar filled to {@code f} (0..1). */
    private View bar(float f, String color) {
        LinearLayout track = Ui.row(this);
        track.setBackground(Ui.round(this, "#2B3648", 3));
        View fill = new View(this);
        fill.setBackground(Ui.round(this, color, 3));
        track.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, f));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - f));
        return track;
    }

    /** App icon of the first installed package of the target, else a letter in a circle. */
    private View iconView(String cat) {
        PackageManager pm = getPackageManager();
        for (String pkg : Targets.packages(cat)) {
            try {
                Drawable d = pm.getApplicationIcon(pkg);
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(d);
                return iv;
            } catch (Exception ignored) {
            }
        }
        String name = Limits.label(this, cat);
        TextView t = Ui.text(this, name.isEmpty() ? "?" : name.substring(0, 1), 18, Ui.BG);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.parseColor(Ui.SKY));
        t.setBackground(g);
        return t;
    }

    // ---- edit ---------------------------------------------------------------------------

    private void edit(String cat) {
        edit = limits.config(cat).copy();
        renderEdit(cat);
    }

    /** Edit screen for the unsaved {@link #edit} copy (presets re-render it). */
    private void renderEdit(String cat) {
        screen = "edit:" + cat;
        LinearLayout root = page(Limits.label(this, cat));
        Strict.addPendingNote(this, root, cat);

        LinearLayout g = group(root);
        g.addView(toggle("On", edit.enabled, b -> edit.enabled = b));
        if (Targets.hasMode(cat)) {
            String part = cat.equals(Limits.SHORTS) ? "Only Shorts" : "Only Reels";
            g.addView(choice("Block", part, "Whole app", "whole".equals(edit.mode),
                    whole -> edit.mode = whole ? "whole" : "part"));
        }

        root.addView(section("Presets"));
        LinearLayout presets = Ui.row(this);
        String[] names = {"Gentle", "Balanced", "Strict"};
        Button[] pb = new Button[names.length];
        for (int i = 0; i < names.length; i++) {
            int which = i;
            Button b = Ui.small(this, names[i]);
            b.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            b.setOnClickListener(v -> {
                preset(edit, which);
                renderEdit(cat);
            });
            LinearLayout.LayoutParams lp = Ui.grow();
            if (i > 0) lp.leftMargin = Ui.dp(this, 8);
            presets.addView(b, lp);
            pb[i] = b;
        }
        root.addView(presets, Ui.wide());
        root.addView(Ui.space(this, 6));
        TextView summary = Ui.left(this, "", 14, Ui.SKY);
        root.addView(summary);
        root.addView(Ui.left(this, "A preset fills in the numbers below. Nothing changes until you save.", 13, "#6B7280"));
        // Highlight the preset that matches the numbers, and keep the one-line summary current.
        editChanged = () -> {
            int match = presetMatch(edit);
            for (int i = 0; i < pb.length; i++) {
                pb[i].setBackground(Ui.round(this, i == match ? Ui.SKY : Ui.CARD, 14));
                pb[i].setTextColor(Color.parseColor(i == match ? Ui.BG : Ui.WHITE));
            }
            summary.setText(summary(edit));
        };
        editChanged.run();

        root.addView(section("Pause"));
        g = group(root);
        g.addView(stepper("Pause length", "s", 0, 600, 1, () -> edit.pauseS, v -> edit.pauseS = v, null));
        g.addView(stepper("Longer each open by", "s", 0, 60, 1, () -> edit.pauseStepS, v -> edit.pauseStepS = v, "off"));
        g.addView(toggle("Ask why before opening", edit.askWhy, b -> edit.askWhy = b));

        root.addView(section("Opens"));
        g = group(root);
        g.addView(stepper("Opens per day", "", 0, 50, 1, () -> edit.opens, v -> edit.opens = v, "none"));
        g.addView(stepper("Minutes per open", "min", 1, 120, 1, () -> edit.openMin, v -> edit.openMin = v, null));
        g.addView(stepper("Max minutes per day", "min", 0, 600, 5, () -> edit.dailyCapMin, v -> edit.dailyCapMin = v, "off"));
        g.addView(stepper("Wait between opens", "min", 0, 240, 5, () -> edit.cooldownMin, v -> edit.cooldownMin = v, "off"));
        if (!Targets.wholeOnly(cat) && !Limits.FACEBOOK.equals(cat))
            g.addView(stepper("End open after videos", "", 0, 200, 1, () -> edit.maxVideos, v -> edit.maxVideos = v, "off"));
        g.addView(toggle("Time-left tab", edit.timer, b -> edit.timer = b));

        root.addView(section("Extend (after the last open)"));
        g = group(root);
        g.addView(stepper("Opens added", "", 0, 20, 1, () -> edit.extOpens, v -> edit.extOpens = v, "off"));
        g.addView(stepper("Extends per day", "", 0, 10, 1, () -> edit.extMax, v -> edit.extMax = v, "off"));
        g.addView(stepper("Only before", ":00", 0, 24, 1, () -> edit.extCutoff, v -> edit.extCutoff = v, null));

        root.addView(section("Blocked hours"));
        root.addView(Ui.left(this, "No opens at all in these windows, e.g. 22:00-07:00. One per line.", 14, Ui.MUTED));
        root.addView(Ui.space(this, 8));
        EditText sched = input(String.join("\n", edit.schedule), "22:00-07:00");
        sched.setSingleLine(false);
        sched.setMinLines(2);
        root.addView(sched, Ui.wide());

        root.addView(Ui.space(this, 24));
        Button save = Ui.button(this, "Save", Ui.SKY, Ui.BG);
        save.setOnClickListener(v -> {
            List<String> windows = new ArrayList<>();
            for (String w : sched.getText().toString().split("[\\n,]")) {
                if (w.trim().isEmpty()) continue;
                if (Config.parseWindow(w) == null) {
                    Toast.makeText(this, L.t(this, "Not a time window: " + w.trim()), Toast.LENGTH_LONG).show();
                    return;
                }
                windows.add(w.trim().replace(" ", ""));
            }
            edit.schedule = windows;
            Toast.makeText(this, L.t(this, Strict.save(this, edit)), Toast.LENGTH_LONG).show();
            openTab("home");
        });
        root.addView(save, Ui.wide());

        if (cat.startsWith(Limits.APP_PREFIX)) {
            root.addView(Ui.space(this, 12));
            Button remove = Ui.button(this, "Remove this app", Ui.CARD, Ui.RED);
            remove.setOnClickListener(v -> {
                Toast.makeText(this, L.t(this, Strict.removeApp(this, cat.substring(Limits.APP_PREFIX.length()))),
                        Toast.LENGTH_LONG).show();
                openTab("home");
            });
            root.addView(remove, Ui.wide());
        }
        show(root);
    }

    /** 0 gentle, 1 balanced, 2 strict. Leaves on/off, mode, schedule and timer alone. */
    /** Runs after any stepper/toggle change on the edit screen (preset highlight + summary). */
    private Runnable editChanged;

    /** Which preset the settings equal right now, or -1 (custom). */
    static int presetMatch(Config c) {
        int[] now = {c.pauseS, c.pauseStepS, c.opens, c.openMin, c.cooldownMin, c.extOpens, c.extMax,
                c.askWhy ? 1 : 0};
        for (int i = 0; i < PRESETS.length; i++) if (java.util.Arrays.equals(PRESETS[i], now)) return i;
        return -1;
    }

    /** "10 s pause · 6 opens/day · 10 min each" in the app language. */
    private String summary(Config c) {
        String opens = c.opens == 0 ? L.t(this, "no opens") : L.t(this, c.opens + " opens/day");
        return L.t(this, c.pauseS + " s pause") + " · " + opens
                + (c.opens == 0 ? "" : " · " + L.t(this, c.openMin + " min each"));
    }

    /** Gentle, Balanced, Strict: pause, step, opens, min, cooldown, extOpens, extMax, askWhy. */
    static final int[][] PRESETS = {
            {5, 0, 10, 15, 0, 3, 2, 0},
            {10, 5, 6, 10, 30, 3, 1, 0},
            {30, 10, 3, 5, 60, 0, 0, 1},
    };

    static void preset(Config c, int which) {
        int[] p = PRESETS[which];
        c.pauseS = p[0];
        c.pauseStepS = p[1];
        c.opens = p[2];
        c.openMin = p[3];
        c.cooldownMin = p[4];
        c.extOpens = p[5];
        c.extMax = p[6];
        c.askWhy = p[7] == 1;
    }

    /** Rounded card box added to root; settings rows go inside. */
    LinearLayout group(LinearLayout root) {
        LinearLayout g = Ui.column(this);
        g.setBackground(Ui.round(this, Ui.CARD, 14));
        int p = Ui.dp(this, 14);
        g.setPadding(p, Ui.dp(this, 6), p, Ui.dp(this, 6));
        root.addView(g, Ui.wide());
        return g;
    }

    // ---- app picker ---------------------------------------------------------------------

    private void picker() {
        screen = "pick";
        LinearLayout root = page("Add an app");
        root.addView(Ui.left(this, "The whole app gets the pause and opens limit.", 14, Ui.MUTED));
        root.addView(Ui.space(this, 8));
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        Set<String> picked = Targets.apps(this);
        List<String[]> rows = new ArrayList<>();
        for (ResolveInfo r : apps) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || picked.contains(pkg) || builtin(pkg)) continue;
            rows.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(rows, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String last = null;
        for (String[] r : rows) {
            if (r[1].equals(last)) continue;
            last = r[1];
            LinearLayout row = Ui.row(this);
            row.setBackground(Ui.round(this, Ui.CARD, 12));
            int p = Ui.dp(this, 12);
            row.setPadding(p, p, p, p);
            try {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(pm.getApplicationIcon(r[1]));
                row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
                row.addView(Ui.space(this, 0), new LinearLayout.LayoutParams(Ui.dp(this, 12), 1));
            } catch (Exception ignored) {
            }
            LinearLayout col = Ui.column(this);
            col.addView(Ui.left(this, r[0], 16, Ui.WHITE));
            col.addView(Ui.left(this, r[1], 12, "#6B7280"));
            row.addView(col, Ui.grow());
            row.addView(Ui.text(this, "+", 22, Ui.SKY));
            String pkg = r[1];
            row.setOnClickListener(v -> {
                Targets.addApp(this, pkg);
                edit(Limits.APP_PREFIX + pkg);
            });
            root.addView(row, card());
        }
        show(root);
    }

    private static boolean builtin(String pkg) {
        return Targets.YOUTUBE.contains(pkg) || Targets.FACEBOOK.contains(pkg)
                || Targets.INSTAGRAM.contains(pkg) || Targets.TIKTOK.contains(pkg);
    }

    // ---- widgets ------------------------------------------------------------------------

    interface BoolSink {
        void set(boolean b);
    }

    LinearLayout page(String title) {
        LinearLayout root = Ui.column(this);
        int p = Ui.dp(this, 20);
        root.setPadding(p, p, p, p);
        TextView t = Ui.left(this, title, 26, Ui.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(t);
        root.addView(Ui.space(this, 12));
        return root;
    }

    void show(LinearLayout root) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.parseColor(Ui.BG));
        sv.addView(root);
        if (!isTab(screen)) {
            Ui.fitInsets(sv);
            setContentView(sv);
            return;
        }
        LinearLayout frame = Ui.column(this);
        frame.setBackgroundColor(Color.parseColor(Ui.BG));
        frame.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor(Ui.CARD));
        frame.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        LinearLayout bar = Ui.row(this);
        bar.setBackgroundColor(Color.parseColor("#131821"));
        String[][] tabs = {{"home", "⌂", "Home"}, {"stats", "▦", "Stats"}, {"settings", "⚙", "Settings"}};
        for (String[] t : tabs) {
            boolean on = t[0].equals(screen);
            String col = on ? Ui.SKY : "#6B7280";
            LinearLayout b = Ui.column(this);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 10));
            b.addView(Ui.text(this, t[1], 18, col));
            TextView label = Ui.text(this, t[2], 12, col);
            if (on) label.setTypeface(Typeface.DEFAULT_BOLD);
            b.addView(label);
            b.setOnClickListener(v -> openTab(t[0]));
            bar.addView(b, Ui.grow());
        }
        frame.addView(bar, Ui.wide());
        Ui.fitInsets(frame);
        setContentView(frame);
    }

    TextView section(String s) {
        TextView t = Ui.left(this, L.t(this, s).toUpperCase(), 13, Ui.SKY);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 8));
        return t;
    }

    LinearLayout.LayoutParams card() {
        LinearLayout.LayoutParams lp = Ui.wide();
        lp.bottomMargin = Ui.dp(this, 8);
        return lp;
    }

    EditText input(String text, String hint) {
        EditText e = new EditText(this);
        e.setText(text);
        e.setHint(L.t(this, hint));
        e.setHintTextColor(Color.parseColor("#6B7280"));
        e.setTextColor(Color.WHITE);
        e.setBackground(Ui.round(this, Ui.CARD, 10));
        int p = Ui.dp(this, 12);
        e.setPadding(p, p, p, p);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return e;
    }

    private View stepper(String label, String unit, int min, int max, int step,
                         IntSupplier get, IntConsumer set, String zero) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        row.addView(Ui.left(this, label, 16, Ui.WHITE), Ui.grow());
        TextView value = Ui.text(this, "", 16, Ui.SKY);
        value.setMinWidth(Ui.dp(this, 72));
        Runnable paint = () -> {
            int v = get.getAsInt();
            value.setText(v == 0 && zero != null ? L.t(this, zero) : L.t(this, (v + (unit.isEmpty() ? "" : " " + unit)).replace(" :", ":")));
            if (editChanged != null) editChanged.run();
        };
        paint.run();
        Button minus = Ui.small(this, "−"), plus = Ui.small(this, "+");
        for (Button b : new Button[]{minus, plus}) {
            b.setBackground(Ui.round(this, "#2B3648", 12));
            b.setTextColor(Color.parseColor(Ui.SKY));
            b.setTypeface(Typeface.DEFAULT_BOLD);
        }
        minus.setOnClickListener(v -> {
            set.accept(Math.max(min, get.getAsInt() - step));
            paint.run();
        });
        plus.setOnClickListener(v -> {
            set.accept(Math.min(max, get.getAsInt() + step));
            paint.run();
        });
        // Long press jumps by 10 steps.
        minus.setOnLongClickListener(v -> {
            set.accept(Math.max(min, get.getAsInt() - step * 10));
            paint.run();
            return true;
        });
        plus.setOnLongClickListener(v -> {
            set.accept(Math.min(max, get.getAsInt() + step * 10));
            paint.run();
            return true;
        });
        row.addView(minus);
        row.addView(value);
        row.addView(plus);
        return row;
    }

    View toggle(String label, boolean on, BoolSink sink) {
        Switch s = new Switch(this);
        s.setText(L.t(this, label));
        s.setTextSize(16);
        s.setTextColor(Color.WHITE);
        s.setChecked(on);
        s.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        s.setOnCheckedChangeListener((b, c) -> {
            sink.set(c);
            if (editChanged != null) editChanged.run();
        });
        return s;
    }

    private View choice(String label, String a, String b, boolean bOn, BoolSink sink) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        row.addView(Ui.left(this, label, 16, Ui.WHITE), Ui.grow());
        Button ba = Ui.small(this, a), bb = Ui.small(this, b);
        Runnable paint = () -> {
            boolean on = "whole".equals(edit.mode);
            ba.setBackground(Ui.round(this, on ? "#2B3648" : Ui.SKY, 14));
            ba.setTextColor(Color.parseColor(on ? Ui.WHITE : Ui.BG));
            bb.setBackground(Ui.round(this, on ? Ui.SKY : "#2B3648", 14));
            bb.setTextColor(Color.parseColor(on ? Ui.BG : Ui.WHITE));
        };
        ba.setOnClickListener(v -> {
            sink.set(false);
            paint.run();
        });
        bb.setOnClickListener(v -> {
            sink.set(true);
            paint.run();
        });
        paint.run();
        row.addView(ba);
        row.addView(Ui.space(this, 0), new LinearLayout.LayoutParams(Ui.dp(this, 6), 1));
        row.addView(bb);
        return row;
    }

    static List<String> list(String... s) {
        return new ArrayList<>(Arrays.asList(s));
    }
}
