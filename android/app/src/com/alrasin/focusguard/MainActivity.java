package com.alrasin.focusguard;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

/** Launcher screen: targets with opens left, per-target settings, app picker, swap ideas. */
public class MainActivity extends Activity {
    /** Which screen is up: "home", "edit:<cat>", "pick". Back returns to home. */
    private String screen = "home";
    private Config edit;
    private Limits limits;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        limits = new Limits(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Strict.applyDue(this);
        if (screen.equals("home")) home();
    }

    /** Re-render the current screen (after a strict-mode change). */
    void refresh() {
        if (screen.equals("strict")) strict();
        else if (screen.startsWith("edit:")) edit(screen.substring(5));
        else home();
    }

    void strict() {
        screen = "strict";
        Strict.screen(this);
    }

    @Override
    public void onBackPressed() {
        if (screen.equals("home")) super.onBackPressed();
        else home();
    }

    // ---- home ---------------------------------------------------------------------------

    private void home() {
        screen = "home";
        LinearLayout root = page("Focus Guard");
        Setup.addStatus(this, root);

        root.addView(section("Targets"));
        for (String cat : Targets.all(this)) root.addView(targetCard(cat), card());

        Button add = Ui.small(this, "+ Add an app");
        add.setOnClickListener(v -> picker());
        root.addView(add, Ui.wide());

        Stats.addSummary(this, root);

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
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        });
        root.addView(saveSwaps);

        Extras.addHomeButtons(this, root);
        show(root);
    }

    private View targetCard(String cat) {
        Config c = limits.config(cat);
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.round(this, Ui.CARD, 12));
        int p = Ui.dp(this, 14);
        row.setPadding(p, p, p, p);

        LinearLayout col = Ui.column(this);
        TextView name = Ui.left(this, Limits.label(this, cat), 17, Ui.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(name);
        String sub;
        if (!c.enabled) sub = "Off";
        else if (limits.isUnlocked(cat)) sub = "Open now · " + limits.left(cat) + "/" + limits.total(cat) + " left";
        else if (limits.hardBlock(cat) != null) sub = "Closed: " + limits.hardBlock(cat);
        else sub = limits.left(cat) + " of " + limits.total(cat) + " opens left today"
                    + ("whole".equals(c.mode) || Targets.wholeOnly(cat) ? " · whole app" : "");
        col.addView(Ui.left(this, sub, 14, c.enabled ? Ui.MUTED : "#6B7280"));
        row.addView(col, Ui.grow());
        row.addView(Ui.text(this, "›", 24, Ui.SKY));
        row.setOnClickListener(v -> edit(cat));
        return row;
    }

    // ---- edit ---------------------------------------------------------------------------

    private void edit(String cat) {
        screen = "edit:" + cat;
        edit = limits.config(cat).copy();
        LinearLayout root = page(Limits.label(this, cat));
        Strict.addPendingNote(this, root, cat);

        root.addView(toggle("On", edit.enabled, b -> edit.enabled = b));
        if (Targets.hasMode(cat)) {
            String part = cat.equals(Limits.SHORTS) ? "Only Shorts" : "Only Reels";
            root.addView(choice("Block", part, "Whole app", "whole".equals(edit.mode),
                    whole -> edit.mode = whole ? "whole" : "part"));
        }

        root.addView(section("Pause"));
        root.addView(stepper("Pause length", "s", 0, 600, 1, () -> edit.pauseS, v -> edit.pauseS = v, null));
        root.addView(stepper("Longer each open by", "s", 0, 60, 1, () -> edit.pauseStepS, v -> edit.pauseStepS = v, "off"));
        root.addView(toggle("Ask why before opening", edit.askWhy, b -> edit.askWhy = b));

        root.addView(section("Opens"));
        root.addView(stepper("Opens per day", "", 0, 50, 1, () -> edit.opens, v -> edit.opens = v, "none"));
        root.addView(stepper("Minutes per open", "min", 1, 120, 1, () -> edit.openMin, v -> edit.openMin = v, null));
        root.addView(stepper("Max minutes per day", "min", 0, 600, 5, () -> edit.dailyCapMin, v -> edit.dailyCapMin = v, "off"));
        root.addView(stepper("Wait between opens", "min", 0, 240, 5, () -> edit.cooldownMin, v -> edit.cooldownMin = v, "off"));
        if (!Targets.wholeOnly(cat) && !Limits.FACEBOOK.equals(cat))
            root.addView(stepper("End open after videos", "", 0, 200, 1, () -> edit.maxVideos, v -> edit.maxVideos = v, "off"));
        root.addView(toggle("Time-left tab", edit.timer, b -> edit.timer = b));

        root.addView(section("Extend (after the last open)"));
        root.addView(stepper("Opens added", "", 0, 20, 1, () -> edit.extOpens, v -> edit.extOpens = v, "off"));
        root.addView(stepper("Extends per day", "", 0, 10, 1, () -> edit.extMax, v -> edit.extMax = v, "off"));
        root.addView(stepper("Only before", ":00", 0, 24, 1, () -> edit.extCutoff, v -> edit.extCutoff = v, null));

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
                    Toast.makeText(this, "Not a time window: " + w.trim(), Toast.LENGTH_LONG).show();
                    return;
                }
                windows.add(w.trim().replace(" ", ""));
            }
            edit.schedule = windows;
            Toast.makeText(this, Strict.save(this, edit), Toast.LENGTH_LONG).show();
            home();
        });
        root.addView(save, Ui.wide());

        if (cat.startsWith(Limits.APP_PREFIX)) {
            root.addView(Ui.space(this, 12));
            Button remove = Ui.button(this, "Remove this app", Ui.CARD, Ui.RED);
            remove.setOnClickListener(v -> {
                Toast.makeText(this, Strict.removeApp(this, cat.substring(Limits.APP_PREFIX.length())),
                        Toast.LENGTH_LONG).show();
                home();
            });
            root.addView(remove, Ui.wide());
        }
        show(root);
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
            row.setBackground(Ui.round(this, Ui.CARD, 10));
            int p = Ui.dp(this, 12);
            row.setPadding(p, p, p, p);
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
        setContentView(sv);
    }

    TextView section(String s) {
        TextView t = Ui.left(this, s.toUpperCase(), 13, Ui.SKY);
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
        e.setHint(hint);
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
            value.setText(v == 0 && zero != null ? zero : v + (unit.isEmpty() ? "" : " " + unit).replace(" :", ":"));
        };
        paint.run();
        Button minus = Ui.small(this, "−"), plus = Ui.small(this, "+");
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
        s.setText(label);
        s.setTextSize(16);
        s.setTextColor(Color.WHITE);
        s.setChecked(on);
        s.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        s.setOnCheckedChangeListener((b, c) -> sink.set(c));
        return s;
    }

    private View choice(String label, String a, String b, boolean bOn, BoolSink sink) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        row.addView(Ui.left(this, label, 16, Ui.WHITE), Ui.grow());
        Button ba = Ui.small(this, a), bb = Ui.small(this, b);
        Runnable paint = () -> {
            boolean on = "whole".equals(edit.mode);
            ba.setBackground(Ui.round(this, on ? Ui.CARD : Ui.SKY, 14));
            ba.setTextColor(Color.parseColor(on ? Ui.WHITE : Ui.BG));
            bb.setBackground(Ui.round(this, on ? Ui.SKY : Ui.CARD, 14));
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
