package com.alrasin.focusguard;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    /** The activity while it is in front (main thread only), so the service can redraw it. */
    private static MainActivity front;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        limits = new Limits(this);
        Ui.onBack(this, this::back);
    }

    @Override
    protected void onResume() {
        super.onResume();
        front = this;
        Strict.applyDue(this);
        if (isTab(screen)) openTab(screen);
        else if (screen.equals("strict")) strict(); // e.g. back from the device admin page
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (front == this) front = null;
    }

    /** Service bound/unbound: redraw Home so the setup card isn't stuck on "not running". */
    static void healthChanged() {
        if (front != null && front.screen.equals("home")) front.openTab("home");
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
        else if (screen.equals("build")) renderBuild();
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
        else if (screen.equals("build")) {
            if (buildCat != null) edit(buildCat);
            else picker();
        } else openTab(tab);
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
        LinearLayout root = page(Flavor.PLAY ? "Unswipe" : "Focus Guard");
        Setup.addStatus(this, root);

        LinearLayout head = Ui.row(this);
        head.addView(section("Your limits"), Ui.grow());
        TextView addTop = Ui.text(this, "+ Add", 14, Ui.SKY);
        addTop.setTypeface(Typeface.DEFAULT_BOLD);
        addTop.setPadding(Ui.dp(this, 12), Ui.dp(this, 20), 0, Ui.dp(this, 8));
        addTop.setOnClickListener(v -> picker());
        head.addView(addTop);
        root.addView(head, Ui.wide());
        for (String cat : Targets.all(this)) root.addView(targetCard(cat), card());

        // Dashed "add" card, like an empty slot.
        LinearLayout add = Ui.row(this);
        GradientDrawable ab = Ui.round(this, Ui.BG, 20);
        ab.setStroke(Ui.dp(this, 2), Color.parseColor(Ui.LINE), Ui.dp(this, 8), Ui.dp(this, 6));
        add.setBackground(ab);
        int ap = Ui.dp(this, 16);
        add.setPadding(ap, ap, ap, ap);
        TextView plus = Ui.text(this, "+", 24, Ui.BG);
        plus.setTypeface(Typeface.DEFAULT_BOLD);
        plus.setBackground(Ui.round(this, Ui.SKY, 14));
        add.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        add.addView(Ui.space(this, 0), new LinearLayout.LayoutParams(Ui.dp(this, 14), 1));
        LinearLayout ac = Ui.column(this);
        TextView at = Ui.left(this, "Add apps or a group", 16, Ui.WHITE);
        at.setTypeface(Typeface.DEFAULT_BOLD);
        ac.addView(at);
        ac.addView(Ui.left(this, "Social, games, news… or make your own", 13, Ui.MUTED));
        add.addView(ac, Ui.grow());
        add.setOnClickListener(v -> picker());
        root.addView(add, card());
        show(root);
    }

    /** Top card on Home when setup is done: status, today at a glance, a kind line, Focus now. */
    void hero(LinearLayout root) {
        Limits limits = new Limits(this);
        android.content.SharedPreferences p = getSharedPreferences("limits", MODE_PRIVATE);
        Stats.Day t = Stats.day(p, Limits.day(Clock.now(this)));
        int streak = Stats.streak(this, p);
        LinearLayout box = Ui.column(this);
        GradientDrawable hb = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#17324A"), Color.parseColor("#141B26")});
        hb.setCornerRadius(Ui.dp(this, 24));
        hb.setStroke(Ui.dp(this, 1), Color.parseColor("#23405A"));
        box.setBackground(hb);
        int hp = Ui.dp(this, 18);
        box.setPadding(hp, hp, hp, hp);

        TextView status = Ui.left(this, Setup.rooted(this) ? "Protection on · root watchdog"
                : "Protection on", 13, Ui.GREEN);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setCompoundDrawablePadding(Ui.dp(this, 8));
        status.setCompoundDrawablesRelative(dot(Ui.GREEN), null, null, null);
        GradientDrawable sb = Ui.round(this, "#14301F", 20);
        status.setBackground(sb);
        status.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        box.addView(status, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(Ui.space(this, 14));

        LinearLayout nums = Ui.row(this);
        String[][] tiles = {{String.valueOf((t.seconds + 59) / 60), "min today"},
                {String.valueOf(t.skips), "went back"}, {String.valueOf(streak), "day streak"}};
        for (int i = 0; i < tiles.length; i++) {
            LinearLayout.LayoutParams tl = Ui.grow();
            if (i > 0) tl.leftMargin = Ui.dp(this, 8);
            nums.addView(heroNum(tiles[i][0], tiles[i][1]), tl);
        }
        box.addView(nums, Ui.wide());
        box.addView(Ui.space(this, 14));

        String kind = t.opens == 0 ? "No short videos today. Keep it up."
                : t.skips > 0 ? "You went back " + t.skips + " times today. Well done."
                : streak > 1 ? streak + " days in a row without extending."
                : "Every pause is a choice. You are doing fine.";
        box.addView(Ui.left(this, kind, 14, Ui.MUTED));
        LinearLayout.LayoutParams bp = Ui.wide();
        bp.bottomMargin = Ui.dp(this, 10);
        root.addView(box, bp);

        // Focus now: its own card under the hero.
        LinearLayout fc = Ui.card(this);
        if (limits.inFocus()) {
            long left = (limits.focusUntil() - limits.now() + 59_999) / 60_000;
            TextView f = Ui.left(this, "Focus session until " + Limits.hhmm(limits.focusUntil()), 17, Ui.SKY);
            f.setTypeface(Typeface.DEFAULT_BOLD);
            fc.addView(f);
            fc.addView(Ui.left(this, left + " min left. Every limit is closed.", 13, Ui.MUTED));
            fc.addView(Ui.space(this, 10));
            long total = Math.max(1, limits.focusUntil() - limits.focusStart());
            float done = 1f - (limits.focusUntil() - limits.now()) / (float) total;
            fc.addView(bar(Math.max(0f, Math.min(1f, done)), Ui.SKY), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 6)));
        } else {
            LinearLayout fh = Ui.row(this);
            TextView fi = Ui.text(this, "◎", 20, Ui.SKY);
            fi.setBackground(Ui.round(this, "#16303F", 12));
            fh.addView(fi, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
            fh.addView(Ui.space(this, 0), new LinearLayout.LayoutParams(Ui.dp(this, 12), 1));
            LinearLayout ft = Ui.column(this);
            TextView f = Ui.left(this, "Focus now", 17, Ui.WHITE);
            f.setTypeface(Typeface.DEFAULT_BOLD);
            ft.addView(f);
            ft.addView(Ui.left(this, "Close every limit for a while. Can't be undone.", 13, Ui.MUTED));
            fh.addView(ft, Ui.grow());
            fc.addView(fh, Ui.wide());
            fc.addView(Ui.space(this, 12));
            LinearLayout row = Ui.row(this);
            int[] mins = {30, 60, 120};
            String[] names = {"30 min", "1 hour", "2 hours"};
            for (int i = 0; i < mins.length; i++) {
                int m = mins[i];
                String name = names[i];
                TextView b = Ui.chip(this, name, false);
                b.setTextColor(Color.parseColor(Ui.SKY));
                b.setTypeface(Typeface.DEFAULT_BOLD);
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
            fc.addView(row, Ui.wide());
        }
        root.addView(fc, bp);
    }

    private LinearLayout heroNum(String big, String small) {
        LinearLayout c = Ui.column(this);
        c.setBackground(Ui.round(this, "#0F1A24", 16));
        c.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        TextView n = Ui.left(this, big, 26, Ui.WHITE);
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
        Extras.addHomeButtons(this, root);

        root.addView(section("Do this instead"));
        LinearLayout g = group(root);
        g.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        g.addView(Ui.left(this, "Your own ideas, one per line. The pause screen shows one at random.",
                14, Ui.MUTED));
        g.addView(Ui.space(this, 10));
        EditText swaps = input(Config.swapsText(this), "Read 5 pages\nWalk 10 minutes\nCall a friend");
        swaps.setBackground(Ui.round(this, Ui.BG, 10));
        swaps.setSingleLine(false);
        swaps.setMinLines(3);
        swaps.setGravity(Gravity.TOP | Gravity.START);
        g.addView(swaps, Ui.wide());
        g.addView(Ui.space(this, 10));
        Button saveSwaps = Ui.small(this, "Save ideas");
        saveSwaps.setBackground(Ui.round(this, Ui.SKY, 14));
        saveSwaps.setTextColor(Color.parseColor(Ui.BG));
        saveSwaps.setOnClickListener(v -> {
            Config.saveSwaps(this, swaps.getText().toString());
            Toast.makeText(this, L.t(this, "Saved"), Toast.LENGTH_SHORT).show();
        });
        g.addView(saveSwaps, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.space(this, 28));
        String ver = "";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        root.addView(Ui.text(this, Flavor.PLAY ? "Unswipe " + ver : "Focus Guard " + ver, 12, "#6B7280"));
        root.addView(Ui.text(this, "No internet access. Nothing leaves this phone.", 12, "#6B7280"));
        show(root);
    }

    /** List row inside a {@link #group}: title, optional grey line under it, value or chevron. */
    View navRow(String title, String sub, String value, Runnable click) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        LinearLayout col = Ui.column(this);
        col.addView(Ui.left(this, title, 16, Ui.WHITE));
        if (sub != null) col.addView(Ui.left(this, sub, 13, Ui.MUTED));
        row.addView(col, Ui.grow());
        if (value != null) {
            TextView v = Ui.text(this, value, 14, Ui.SKY);
            v.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 4), 0);
            row.addView(v);
        }
        if (click != null) {
            row.addView(icon(R.drawable.ic_chevron, "#6B7280", 18));
            row.setOnClickListener(v -> click.run());
        }
        return row;
    }

    /** Thin line between rows of a {@link #group}. */
    View divider() {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor("#2B3648"));
        d.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return d;
    }

    private View targetCard(String cat) {
        Config c = limits.config(cat);
        LinearLayout box = Ui.card(this);

        LinearLayout row = Ui.row(this);
        View ic = iconView(cat);
        FrameLayout sq = new FrameLayout(this);
        sq.setBackground(Ui.round(this, "#111827", 14));
        int ip = Ui.dp(this, 6);
        sq.setPadding(ip, ip, ip, ip);
        sq.addView(ic, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        row.addView(sq, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        row.addView(Ui.space(this, 0), new LinearLayout.LayoutParams(Ui.dp(this, 14), 1));

        LinearLayout col = Ui.column(this);
        TextView name = Ui.left(this, Limits.label(this, cat), 17, c.enabled ? Ui.WHITE : Ui.MUTED);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(name);
        String tag;
        if (Targets.isGroup(cat)) {
            int n = Targets.groupApps(this, cat).size();
            tag = "Group · " + (n == 1 ? "1 app" : n + " apps");
        } else tag = "whole".equals(c.mode) || Targets.wholeOnly(cat) ? "Whole app" : "Shorts / Reels only";
        col.addView(Ui.left(this, tag + " · " + c.opens + " × " + c.openMin + " min", 13, Ui.MUTED));
        row.addView(col, Ui.grow());

        // Status pill.
        String pill, color, fill;
        if (!c.enabled) { pill = "Off"; color = Ui.DIM; fill = "#1A2230"; }
        else if (limits.isUnlocked(cat)) { pill = "Open now"; color = Ui.GREEN; fill = "#14301F"; }
        else if (limits.hardBlock(cat) != null || limits.left(cat) == 0) { pill = "Closed"; color = Ui.RED; fill = "#3A1A1E"; }
        else { pill = limits.left(cat) + " left"; color = Ui.SKY; fill = "#16303F"; }
        TextView pt = Ui.text(this, pill, 12, color);
        pt.setTypeface(Typeface.DEFAULT_BOLD);
        pt.setBackground(Ui.round(this, fill, 20));
        pt.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        row.addView(pt);
        box.addView(row);

        int used = limits.used(cat), total = limits.total(cat);
        if (c.enabled && limits.hardBlock(cat) != null) {
            box.addView(Ui.space(this, 10));
            box.addView(Ui.left(this, "Closed: " + limits.hardBlock(cat), 13, Ui.RED));
        } else if (c.enabled && total > 0) {
            box.addView(Ui.space(this, 14));
            box.addView(segBar(used, total), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 6)));
            box.addView(Ui.space(this, 6));
            box.addView(Ui.left(this, "Opened " + used + " of " + total + " today", 12, Ui.DIM));
        }
        box.setOnClickListener(v -> edit(cat));
        return box;
    }

    /** Opens bar: one segment per open (up to 12), else a continuous bar. */
    private View segBar(int used, int total) {
        String color = used >= total ? Ui.RED : Ui.SKY;
        if (total > 12) return bar(Math.min(1f, used / (float) total), color);
        LinearLayout r = Ui.row(this);
        for (int i = 0; i < total; i++) {
            View s = new View(this);
            s.setBackground(Ui.round(this, i < used ? color : "#2B3648", 3));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) l.leftMargin = Ui.dp(this, 4);
            r.addView(s, l);
        }
        return r;
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
        if (Targets.isGroup(cat)) {
            View f = folderIcon(Targets.groupApps(this, cat));
            if (f != null) return f;
        }
        for (String pkg : Targets.packages(this, cat)) {
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

        if (Targets.isGroup(cat)) {
            Set<String> apps = Targets.groupApps(this, cat);
            LinearLayout box = Ui.row(this);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setBackground(Ui.round(this, Ui.CARD, 16));
            int p = Ui.dp(this, 14);
            box.setPadding(p, p, p, p);
            box.addView(stack(apps, 6), Ui.grow());
            TextView n = Ui.text(this, apps.size() == 1 ? "1 app" : apps.size() + " apps", 13, Ui.MUTED);
            n.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 4), 0);
            box.addView(n);
            box.addView(icon(R.drawable.ic_chevron, "#6B7280", 20));
            box.setOnClickListener(v -> builder(cat, Targets.groupName(this, cat), apps, false));
            root.addView(box, card());
            TextView hint = Ui.left(this, "Tap to rename or change the apps. All apps in the group share one pause and one opens count.",
                    12, "#6B7280");
            root.addView(hint);
            root.addView(Ui.space(this, 8));
        }

        // Live preview: the three numbers that matter, big, like a ticket.
        LinearLayout prev = Ui.column(this);
        android.graphics.drawable.GradientDrawable pg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#17324A"), Color.parseColor("#141B26")});
        pg.setCornerRadius(Ui.dp(this, 20));
        pg.setStroke(Ui.dp(this, 1), Color.parseColor("#23405A"));
        prev.setBackground(pg);
        int pp = Ui.dp(this, 16);
        prev.setPadding(pp, pp, pp, pp);
        TextView presetName = Ui.left(this, "", 12, Ui.SKY);
        presetName.setTypeface(Typeface.DEFAULT_BOLD);
        prev.addView(presetName);
        prev.addView(Ui.space(this, 8));
        LinearLayout nums = Ui.row(this);
        TextView[] big = new TextView[3];
        String[] caps = {"pause", "opens a day", "min each"};
        for (int i = 0; i < 3; i++) {
            LinearLayout col = Ui.column(this);
            big[i] = Ui.left(this, "", 26, Ui.WHITE);
            big[i].setTypeface(Typeface.DEFAULT_BOLD);
            col.addView(big[i]);
            col.addView(Ui.left(this, caps[i], 12, Ui.MUTED));
            nums.addView(col, Ui.grow());
        }
        prev.addView(nums, Ui.wide());
        prev.addView(Ui.space(this, 8));
        TextView total = Ui.left(this, "", 13, Ui.MUTED);
        prev.addView(total);
        root.addView(prev, card());

        LinearLayout g = group(root);
        g.addView(toggle("On", edit.enabled, b -> edit.enabled = b));
        if (Targets.hasMode(cat)) {
            String part = cat.equals(Limits.SHORTS) ? "Only Shorts" : "Only Reels";
            g.addView(Ui.left(this, "Block", 16, Ui.WHITE));
            g.addView(Ui.space(this, 6));
            g.addView(Ui.segmented(this, new String[]{part, "Whole app"}, "whole".equals(edit.mode) ? 1 : 0,
                    i -> edit.mode = i == 1 ? "whole" : "part"), Ui.wide());
            g.addView(Ui.space(this, 10));
        }

        root.addView(section("Presets"));
        String[] names = {"Gentle", "Balanced", "Strict"};
        LinearLayout presets = Ui.row(this);
        presets.setBackground(Ui.round(this, "#161C26", 14));
        int tp = Ui.dp(this, 4);
        presets.setPadding(tp, tp, tp, tp);
        TextView[] pb = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            int which = i;
            TextView b = Ui.text(this, names[i], 14, Ui.MUTED);
            b.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            b.setOnClickListener(v -> {
                preset(edit, which);
                renderEdit(cat);
            });
            presets.addView(b, Ui.grow());
            pb[i] = b;
        }
        root.addView(presets, Ui.wide());
        root.addView(Ui.space(this, 6));
        root.addView(Ui.left(this, "A preset fills in the numbers below. Nothing changes until you save.", 12, Ui.DIM));
        // Highlight the preset that matches the numbers, and keep the preview current.
        editChanged = () -> {
            int match = presetMatch(edit);
            for (int i = 0; i < pb.length; i++) {
                boolean on = i == match;
                pb[i].setBackground(on ? Ui.round(this, Ui.SKY, 11) : null);
                pb[i].setTextColor(Color.parseColor(on ? Ui.BG : Ui.MUTED));
                pb[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            }
            presetName.setText(L.t(this, (match < 0 ? "Custom" : names[match]).toUpperCase()
                    + (edit.enabled ? "" : " · OFF")));
            big[0].setText(edit.pauseS + " s");
            big[1].setText(edit.opens == 0 ? L.t(this, "None") : String.valueOf(edit.opens));
            big[2].setText(edit.opens == 0 ? "–" : String.valueOf(edit.openMin));
            int day = edit.opens * edit.openMin;
            if (edit.dailyCapMin > 0) day = Math.min(day, edit.dailyCapMin);
            total.setText(edit.opens == 0 ? L.t(this, "Always closed")
                    : L.t(this, "Up to " + day + " min a day"));
        };
        editChanged.run();

        root.addView(section("Limit"));
        g = group(root);
        g.addView(stepper("Pause length", "s", 0, 600, 1, () -> edit.pauseS, v -> edit.pauseS = v, null));
        g.addView(stepper("Opens per day", "", 0, 50, 1, () -> edit.opens, v -> edit.opens = v, "none"));
        g.addView(stepper("Minutes per open", "min", 1, 120, 1, () -> edit.openMin, v -> edit.openMin = v, null));

        // Everything else folds away: most people only touch the three numbers above.
        LinearLayout adv = Ui.column(this);
        adv.setVisibility(editAdvanced ? View.VISIBLE : View.GONE);
        LinearLayout more = Ui.row(this);
        more.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        TextView moreT = Ui.left(this, L.t(this, "More settings").toUpperCase(), 13, Ui.SKY);
        moreT.setTypeface(Typeface.DEFAULT_BOLD);
        more.addView(moreT, Ui.grow());
        ImageView chev = icon(R.drawable.ic_chevron, Ui.SKY, 20);
        chev.setRotation(editAdvanced ? 270 : 90);
        more.addView(chev);
        more.setOnClickListener(v -> {
            editAdvanced = !editAdvanced;
            adv.setVisibility(editAdvanced ? View.VISIBLE : View.GONE);
            chev.setRotation(editAdvanced ? 270 : 90);
        });
        root.addView(more, Ui.wide());
        root.addView(adv, Ui.wide());

        adv.addView(section("Pause"));
        g = group(adv);
        g.addView(stepper("Longer each open by", "s", 0, 60, 1, () -> edit.pauseStepS, v -> edit.pauseStepS = v, "off"));
        g.addView(toggle("Ask why before opening", edit.askWhy, b -> edit.askWhy = b));

        adv.addView(section("Opens"));
        g = group(adv);
        g.addView(stepper("Max minutes per day", "min", 0, 600, 5, () -> edit.dailyCapMin, v -> edit.dailyCapMin = v, "off"));
        g.addView(stepper("Wait between opens", "min", 0, 240, 5, () -> edit.cooldownMin, v -> edit.cooldownMin = v, "off"));
        if (!Targets.wholeOnly(cat) && !Limits.FACEBOOK.equals(cat))
            g.addView(stepper("End open after videos", "", 0, 200, 1, () -> edit.maxVideos, v -> edit.maxVideos = v, "off"));
        g.addView(toggle("Time-left tab", edit.timer, b -> edit.timer = b));

        adv.addView(section("Extend (after the last open)"));
        g = group(adv);
        g.addView(stepper("Opens added", "", 0, 20, 1, () -> edit.extOpens, v -> edit.extOpens = v, "off"));
        g.addView(stepper("Extends per day", "", 0, 10, 1, () -> edit.extMax, v -> edit.extMax = v, "off"));
        g.addView(stepper("Only before", ":00", 0, 24, 1, () -> edit.extCutoff, v -> edit.extCutoff = v, null));

        adv.addView(section("Blocked hours"));
        adv.addView(Ui.left(this, "No opens at all in these windows, e.g. 22:00-07:00. One per line.", 14, Ui.MUTED));
        adv.addView(Ui.space(this, 8));
        EditText sched = input(String.join("\n", edit.schedule), "22:00-07:00");
        sched.setSingleLine(false);
        sched.setMinLines(2);
        adv.addView(sched, Ui.wide());

        Button save = Ui.pill(this, "Save");
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
        root.addView(Ui.space(this, 24));
        if (cat.startsWith(Limits.APP_PREFIX)) {
            Button remove = Ui.button(this, "Remove this app", Ui.CARD, Ui.RED);
            remove.setOnClickListener(v -> {
                Toast.makeText(this, L.t(this, Strict.removeApp(this, cat.substring(Limits.APP_PREFIX.length()))),
                        Toast.LENGTH_LONG).show();
                openTab("home");
            });
            root.addView(remove, Ui.wide());
        } else if (Targets.isGroup(cat)) {
            Button remove = Ui.button(this, "Delete group", Ui.CARD, Ui.RED);
            remove.setOnClickListener(v -> {
                Toast.makeText(this, L.t(this, Strict.removeGroup(this, cat)), Toast.LENGTH_LONG).show();
                openTab("home");
            });
            root.addView(remove, Ui.wide());
        }
        root.addView(Ui.space(this, 16));
        showWithFooter(root, save);
    }

    /** 0 gentle, 1 balanced, 2 strict. Leaves on/off, mode, schedule and timer alone. */
    /** Runs after any stepper/toggle change on the edit screen (preset highlight + summary). */
    private Runnable editChanged;
    /** More settings open on the edit screen (kept across preset taps). */
    private boolean editAdvanced;

    /** Which preset the settings equal right now, or -1 (custom). */
    static int presetMatch(Config c) {
        int[] now = {c.pauseS, c.pauseStepS, c.opens, c.openMin, c.cooldownMin, c.extOpens, c.extMax,
                c.askWhy ? 1 : 0};
        for (int i = 0; i < PRESETS.length; i++) if (java.util.Arrays.equals(PRESETS[i], now)) return i;
        return -1;
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

    // ---- add a limit: ready-made groups, own group, one app ------------------------------

    /** Ready-made groups: name, Android app category (-1 = none), known packages. */
    private static final String[] KIND_NAMES = {"Social", "Short video", "Games", "Video", "Shopping",
            "News", "Browsers"};
    private static final String[] KIND_ICONS = {"💬", "🎬", "🎮", "📺", "🛍️", "📰", "🌐"};
    private static final String[] KIND_HINTS = {"X, Reddit, Snapchat…", "Likee, Moj, Kwai…",
            "Every game", "Netflix, streaming…", "Amazon, Daraz, Temu…", "News and magazines",
            "Chrome, Brave, Firefox…"};
    private static final int[] KIND_CATS = {ApplicationInfo.CATEGORY_SOCIAL, -1, ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_VIDEO, -1, ApplicationInfo.CATEGORY_NEWS, -1};
    private static final String[][] KIND_PKGS = {
            {"com.twitter.android", "com.reddit.frontpage", "com.snapchat.android", "com.pinterest",
                    "com.instagram.barcelona", "com.linkedin.android", "com.tumblr", "com.bereal.ft",
                    "com.vkontakte.android", "com.quora.android"},
            {"video.like", "com.kwai.video", "com.kwai.bulldog", "in.mohalla.video", "com.eterno.shortvideos",
                    "co.triller.droid", "com.bigo.live", "sg.bigo.live", "com.lemon8.android"},
            {},
            {"com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus",
                    "in.startv.hotstar", "tv.twitch.android.app", "com.hbo.hbonow", "com.vimeo.android.videoapp",
                    "com.dailymotion.dailymotion", "com.mxtech.videoplayer.ad"},
            {"com.amazon.mShop.android.shopping", "com.daraz.android", "com.alibaba.aliexpresshd",
                    "com.einnovation.temu", "com.zzkko", "com.ebay.mobile", "com.flipkart.android",
                    "com.etsy.android", "com.lazada.android", "com.myntra.android", "com.meesho.supply",
                    "com.shopee.ph", "com.shopee.id", "com.shopee.my", "com.shopee.th", "com.shopee.vn",
                    "com.contextlogic.wish", "com.walmart.android"},
            {"com.google.android.apps.magazines", "flipboard.app", "bbc.mobile.news.ww", "com.cnn.mobile.android.phone",
                    "com.nytimes.android", "com.opera.app.news", "com.newsbreak.newsbreakapp"},
            {"com.android.chrome", "com.chrome.beta", "com.brave.browser", "org.mozilla.firefox",
                    "org.mozilla.focus", "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
                    "com.microsoft.emmx", "com.sec.android.app.sbrowser", "com.mi.globalbrowser",
                    "com.android.browser", "com.UCMobile.intl", "com.uc.browser.en",
                    "com.duckduckgo.mobile.android", "com.vivaldi.browser", "com.kiwibrowser.browser",
                    "com.ecosia.android", "org.torproject.torbrowser", "com.yandex.browser"},
    };
    /** Chat apps the social category would sweep in; never pre-selected. */
    private static final Set<String> MESSENGERS = new HashSet<>(Arrays.asList(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
            "org.thunderdog.challegram", "com.facebook.orca", "com.facebook.mlite", "org.thoughtcrime.securesms",
            "com.viber.voip", "com.imo.android.imoim", "com.imo.android.imous", "jp.naver.line.android",
            "com.discord", "com.google.android.apps.messaging", "com.skype.raider", "com.Slack",
            "com.microsoft.teams", "us.zoom.videomeetings", "com.google.android.gm", "com.tencent.mm",
            "com.kakao.talk", "com.google.android.apps.tachyon"));

    /** A launcher app on this phone. */
    private static final class App {
        final String pkg, label;
        final int category;
        final boolean system;

        App(String pkg, String label, int category, boolean system) {
            this.pkg = pkg;
            this.label = label;
            this.category = category;
            this.system = system;
        }
    }

    /** Launcher apps, sorted by name, without us. */
    private List<App> installed() {
        PackageManager pm = getPackageManager();
        Map<String, App> m = new HashMap<>();
        for (ResolveInfo r : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || m.containsKey(pkg)) continue;
            ApplicationInfo ai = r.activityInfo.applicationInfo;
            m.put(pkg, new App(pkg, r.loadLabel(pm).toString(), ai.category,
                    (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0));
        }
        List<App> out = new ArrayList<>(m.values());
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    /**
     * Apps of a ready-made group that are on this phone and still free. The Android category only
     * counts for user-installed apps (Contacts says "social") not listed in another group (Brave too).
     */
    private Set<String> kindApps(int k, List<App> apps, Map<String, String> owners) {
        Set<String> known = new HashSet<>(Arrays.asList(KIND_PKGS[k]));
        Set<String> listed = new HashSet<>();
        for (String[] ps : KIND_PKGS) listed.addAll(Arrays.asList(ps));
        Set<String> out = new HashSet<>();
        for (App a : apps) {
            if (builtinOwner(a.pkg) != null || owners.containsKey(a.pkg) || MESSENGERS.contains(a.pkg)) continue;
            if (known.contains(a.pkg) || (KIND_CATS[k] >= 0 && a.category == KIND_CATS[k]
                    && !a.system && !listed.contains(a.pkg))) out.add(a.pkg);
        }
        return out;
    }

    private void picker() {
        screen = "pick";
        LinearLayout root = page("Add a limit");
        root.addView(Ui.left(this, "One limit covers every app in it: the pause, the opens and the minutes are shared.",
                14, Ui.MUTED));
        List<App> apps = installed();
        Map<String, String> owners = Targets.owners(this);

        root.addView(section("Ready-made groups"));
        // Groups with apps on this phone first; empty ones stay at the end, dimmed.
        List<Integer> order = new ArrayList<>();
        List<Set<String>> sets = new ArrayList<>();
        for (int k = 0; k < KIND_NAMES.length; k++) sets.add(kindApps(k, apps, owners));
        for (int k = 0; k < KIND_NAMES.length; k++) if (!sets.get(k).isEmpty()) order.add(k);
        for (int k = 0; k < KIND_NAMES.length; k++) if (sets.get(k).isEmpty()) order.add(k);
        LinearLayout grid = null;
        for (int i = 0; i < order.size(); i++) {
            int kk = order.get(i), k = i;
            Set<String> sel = sets.get(kk);
            View tile = tile(KIND_ICONS[kk], KIND_NAMES[kk], sel.isEmpty() ? L.t(this, "None on this phone")
                    : L.t(this, sel.size() == 1 ? "1 app" : sel.size() + " apps"), KIND_HINTS[kk], sel);
            tile.setOnClickListener(v -> builder(null, L.t(this, KIND_NAMES[kk]), sel, false));
            if (k % 2 == 0) {
                grid = Ui.row(this);
                root.addView(grid, card());
            }
            LinearLayout.LayoutParams lp = Ui.grow();
            if (k % 2 == 1) lp.leftMargin = Ui.dp(this, 8);
            grid.addView(tile, lp);
            if (k == KIND_NAMES.length - 1 && k % 2 == 0) {
                // A bare View with wrap_content takes all the height it is offered; give it none.
                LinearLayout.LayoutParams f = lp(8);
                f.height = 0;
                grid.addView(new View(this), f);
            }
        }

        root.addView(section("Make your own"));
        LinearLayout own = Ui.row(this);
        View custom = tile("✚", "Custom group", null, "Pick any apps, give it a name", null);
        custom.setOnClickListener(v -> builder(null, "", new HashSet<>(), false));
        own.addView(custom, Ui.grow());
        View one = tile("◉", "One app", null, "Its own pause and opens", null);
        one.setOnClickListener(v -> builder(null, "", new HashSet<>(), true));
        own.addView(one, lp(8));
        root.addView(own, card());
        show(root);
    }

    private LinearLayout.LayoutParams lp(int leftDp) {
        LinearLayout.LayoutParams lp = Ui.grow();
        lp.leftMargin = Ui.dp(this, leftDp);
        return lp;
    }

    /** Chooser tile: emoji badge or the apps' icons, bold name, sky count, muted hint. live = sel non-empty or null. */
    private View tile(String glyph, String name, String count, String hint, Set<String> sel) {
        boolean live = sel == null || !sel.isEmpty();
        LinearLayout t = Ui.card(this);
        t.setMinimumHeight(Ui.dp(this, 132));
        if (sel != null && !sel.isEmpty()) {
            t.addView(stack(sel, 3));
        } else {
            TextView g = Ui.text(this, glyph, 18, Ui.SKY);
            g.setBackground(Ui.round(this, "#16303F", 12));
            t.addView(g, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        }
        t.addView(Ui.space(this, 10));
        TextView n = Ui.left(this, name, 16, Ui.WHITE);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        t.addView(n);
        if (count != null) t.addView(Ui.left(this, count, 13, live ? Ui.SKY : Ui.DIM));
        t.addView(Ui.space(this, 2));
        t.addView(Ui.left(this, hint, 12, Ui.DIM));
        if (!live) t.setAlpha(0.55f);
        return t;
    }

    // ---- group builder ------------------------------------------------------------------

    /** Builder state: group being edited (null = new), name, chosen apps, one-app mode. */
    private String buildCat, buildName;
    private Set<String> buildSel = new HashSet<>();
    private boolean buildSingle;

    private void builder(String cat, String name, Set<String> sel, boolean single) {
        buildCat = cat;
        buildName = name;
        buildSel = new HashSet<>(sel);
        buildSingle = single;
        renderBuild();
    }

    /** The builtin target that already covers a package (YouTube → Shorts…), or null. */
    private static String builtinOwner(String pkg) {
        if (Targets.YOUTUBE.contains(pkg)) return Limits.SHORTS;
        if (Targets.FACEBOOK.contains(pkg)) return Limits.REELS;
        if (Targets.INSTAGRAM.contains(pkg)) return Limits.INSTAGRAM;
        if (Targets.TIKTOK.contains(pkg)) return Limits.TIKTOK;
        return null;
    }

    private void renderBuild() {
        screen = "build";
        String title = buildSingle ? "Pick an app" : buildCat == null ? "New group" : "Edit group";
        LinearLayout root = page(title);
        PackageManager pm = getPackageManager();
        Map<String, String> owners = Targets.owners(this);

        if (!buildSingle) {
            root.addView(section("Name"));
            EditText name = input(buildName, "e.g. Evening apps");
            name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            name.setSingleLine(true);
            name.addTextChangedListener(watcher(s -> buildName = s));
            root.addView(name, Ui.wide());
            root.addView(section("Apps"));
        } else {
            root.addView(Ui.left(this, "The whole app gets its own pause and opens limit.", 14, Ui.MUTED));
            root.addView(Ui.space(this, 12));
        }

        EditText search = input("", "Search apps");
        search.setBackground(Ui.round(this, "#161C26", 24));
        search.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        Drawable lens = getDrawable(R.drawable.ic_search);
        if (lens != null) {
            lens.setTint(Color.parseColor("#6B7280"));
            int d = Ui.dp(this, 20);
            lens.setBounds(0, 0, d, d);
            search.setCompoundDrawablesRelative(lens, null, null, null);
            search.setCompoundDrawablePadding(Ui.dp(this, 10));
        }
        root.addView(search, Ui.wide());
        root.addView(Ui.space(this, 8));

        TextView count = Ui.left(this, "", 13, Ui.SKY);
        Runnable recount = () -> count.setText(L.t(this, buildSel.size() == 1 ? "1 app chosen"
                : buildSel.size() + " apps chosen"));
        if (!buildSingle) {
            recount.run();
            root.addView(count);
            root.addView(Ui.space(this, 6));
        }

        // Chosen apps first, then the rest A-Z; apps taken by another limit last.
        List<App> apps = installed();
        List<App> order = new ArrayList<>();
        for (App a : apps) if (buildSel.contains(a.pkg)) order.add(a);
        for (App a : apps) if (!buildSel.contains(a.pkg) && takenBy(a.pkg, owners) == null) order.add(a);
        for (App a : apps) if (!buildSel.contains(a.pkg) && takenBy(a.pkg, owners) != null) order.add(a);

        LinearLayout list = Ui.column(this);
        List<View> rows = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (App a : order) {
            String taken = takenBy(a.pkg, owners);
            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.round(this, Ui.CARD, 14));
            int p = Ui.dp(this, 12);
            row.setPadding(p, Ui.dp(this, 10), p, Ui.dp(this, 10));
            try {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(pm.getApplicationIcon(a.pkg));
                row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
                row.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 12), 1));
            } catch (Exception ignored) {
            }
            LinearLayout col = Ui.column(this);
            TextView label = Ui.left(this, "", 16, Ui.WHITE);
            label.setText(a.label);
            col.addView(label);
            if (taken != null)
                col.addView(Ui.left(this, L.t(this, "In " + Limits.label(this, taken)), 12, "#6B7280"));
            row.addView(col, Ui.grow());
            if (taken != null) {
                row.setAlpha(0.45f);
            } else if (buildSingle) {
                ImageView go = icon(R.drawable.ic_chevron, "#6B7280", 20);
                row.addView(go);
                row.setOnClickListener(v -> {
                    Targets.addApp(this, a.pkg);
                    edit(Limits.APP_PREFIX + a.pkg);
                });
            } else {
                ImageView box = new ImageView(this);
                int bp = Ui.dp(this, 3);
                box.setPadding(bp, bp, bp, bp);
                Runnable paint = () -> {
                    boolean on = buildSel.contains(a.pkg);
                    GradientDrawable g = Ui.round(this, on ? Ui.SKY : Ui.CARD, 13);
                    if (!on) g.setStroke(Ui.dp(this, 2), Color.parseColor("#6B7280"));
                    GradientDrawable rb = Ui.round(this, on ? "#16303F" : Ui.CARD, 14);
                    if (on) rb.setStroke(Ui.dp(this, 1), Color.parseColor("#2C5A75"));
                    row.setBackground(rb);
                    box.setBackground(g);
                    Drawable d = getDrawable(R.drawable.ic_check);
                    if (d != null) d.setTint(Color.parseColor(Ui.BG));
                    box.setImageDrawable(on ? d : null);
                };
                paint.run();
                row.addView(box, new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
                row.setOnClickListener(v -> {
                    if (!buildSel.remove(a.pkg)) buildSel.add(a.pkg);
                    paint.run();
                    recount.run();
                });
            }
            list.addView(row, card());
            rows.add(row);
            keys.add((a.label + " " + a.pkg).toLowerCase());
        }
        root.addView(list, Ui.wide());
        search.addTextChangedListener(watcher(s -> {
            String q = s.trim().toLowerCase();
            for (int i = 0; i < rows.size(); i++)
                rows.get(i).setVisibility(q.isEmpty() || keys.get(i).contains(q) ? View.VISIBLE : View.GONE);
        }));

        if (buildSingle) {
            show(root);
            return;
        }
        Button save = Ui.pill(this, buildCat == null ? "Next: set the limit" : "Save");
        save.setOnClickListener(v -> {
            String n = buildName == null ? "" : buildName.trim();
            if (n.isEmpty()) {
                Toast.makeText(this, L.t(this, "Give the group a name"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (buildSel.isEmpty()) {
                Toast.makeText(this, L.t(this, "Pick at least one app"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (buildCat == null) {
                edit(Targets.addGroup(this, n, buildSel));
            } else {
                Targets.renameGroup(this, buildCat, n);
                Toast.makeText(this, L.t(this, Strict.setGroupApps(this, buildCat, buildSel)),
                        Toast.LENGTH_LONG).show();
                edit(buildCat);
            }
        });
        showWithFooter(root, save);
    }

    /** Target that already owns a package (other than the group being built), or null. */
    private String takenBy(String pkg, Map<String, String> owners) {
        String b = builtinOwner(pkg);
        if (b != null) return b;
        String o = owners.get(pkg);
        return o == null || o.equals(buildCat) ? null : o;
    }

    interface StrSink {
        void set(String s);
    }

    private static TextWatcher watcher(StrSink sink) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { sink.set(e.toString()); }
        };
    }

    /** A tinted vector icon, sizeDp square. */
    ImageView icon(int res, String color, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(Color.parseColor(color));
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp)));
        return iv;
    }

    /** Overlapping app icons of a group (at most {@code max}), then "+N". */
    private View stack(Set<String> pkgs, int max) {
        PackageManager pm = getPackageManager();
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        List<String> sorted = new ArrayList<>(pkgs);
        Collections.sort(sorted);
        int shown = 0;
        int s = Ui.dp(this, 34);
        for (String pkg : sorted) {
            if (shown == max) break;
            try {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(pm.getApplicationIcon(pkg));
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(Color.parseColor(Ui.CARD));
                iv.setBackground(ring);
                int b = Ui.dp(this, 2);
                iv.setPadding(b, b, b, b);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
                if (shown > 0) lp.leftMargin = -Ui.dp(this, 10);
                row.addView(iv, lp);
                shown++;
            } catch (Exception ignored) {
            }
        }
        if (pkgs.size() > shown) {
            TextView more = Ui.text(this, "+" + (pkgs.size() - shown), 12, Ui.SKY);
            more.setPadding(Ui.dp(this, 8), 0, 0, 0);
            row.addView(more);
        }
        return row;
    }

    /** 2×2 mini icons for a group's card, like a home-screen folder. */
    private View folderIcon(Set<String> pkgs) {
        PackageManager pm = getPackageManager();
        List<String> sorted = new ArrayList<>(pkgs);
        Collections.sort(sorted);
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.round(this, "#2B3648", 12));
        int p = Ui.dp(this, 3);
        box.setPadding(p, p, p, p);
        LinearLayout row = null;
        int n = 0;
        for (String pkg : sorted) {
            if (n == 4) break;
            Drawable d;
            try {
                d = pm.getApplicationIcon(pkg);
            } catch (Exception e) {
                continue;
            }
            if (n % 2 == 0) {
                row = Ui.row(this);
                box.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            }
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(d);
            iv.setPadding(p / 2, p / 2, p / 2, p / 2);
            row.addView(iv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            n++;
        }
        if (n == 0) return null;
        if (n % 2 == 1) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        if (n <= 2) box.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        return box;
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
        String[][] tabs = {{"home", "Home"}, {"stats", "Stats"}, {"settings", "Settings"}};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_chart, R.drawable.ic_gear};
        int ti = 0;
        for (String[] t : tabs) {
            boolean on = t[0].equals(screen);
            String col = on ? Ui.SKY : "#6B7280";
            LinearLayout b = Ui.column(this);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 10));
            ImageView ic = icon(icons[ti++], on ? Ui.BG : "#6B7280", 22);
            // Selected tab: icon in a sky pill, like Material 3.
            FrameLayout pill = new FrameLayout(this);
            if (on) pill.setBackground(Ui.round(this, Ui.SKY, 16));
            pill.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4));
            pill.addView(ic, new FrameLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
            // Vertical LinearLayout defaults children to match_parent width; keep the pill snug.
            b.addView(pill, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            b.addView(Ui.space(this, 4));
            TextView label = Ui.text(this, t[1], 12, col);
            if (on) label.setTypeface(Typeface.DEFAULT_BOLD);
            b.addView(label);
            b.setOnClickListener(v -> openTab(t[0]));
            bar.addView(b, Ui.grow());
        }
        frame.addView(bar, Ui.wide());
        Ui.fitInsets(frame);
        setContentView(frame);
    }

    /** Scrolling page with a fixed button at the bottom (builder Save). */
    private void showWithFooter(LinearLayout root, View footer) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor(Ui.BG));
        sv.addView(root);
        LinearLayout frame = Ui.column(this);
        frame.setBackgroundColor(Color.parseColor(Ui.BG));
        frame.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout foot = Ui.column(this);
        foot.setBackgroundColor(Color.parseColor("#131821"));
        int p = Ui.dp(this, 16);
        foot.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 12));
        foot.addView(footer, Ui.wide());
        frame.addView(foot, Ui.wide());
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

    static List<String> list(String... s) {
        return new ArrayList<>(Arrays.asList(s));
    }
}
