package com.alrasin.focusguard;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
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
    /** Home shows Remove / Turn off on each card. */
    private boolean homeEditing;
    /** Last tab ScrollView, and whether the next show() keeps its scroll position (no enter animation). */
    private ScrollView curScroll;
    private boolean keepScroll;
    private Config edit;
    private Limits limits;
    /** The activity while it is in front (main thread only), so the service can redraw it. */
    private static MainActivity front;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.theme(this);
        Ui.backdrop(this);
        limits = new Limits(this);
        Ui.onBack(this, this::back);
    }

    @Override
    protected void onResume() {
        super.onResume();
        front = this;
        Strict.applyDue(this);
        if (screen.equals("home") && !Setup.welcomed(this) && !Setup.done(this)) welcome(Welcome.INTRO, 0);
        else if (screen.equals("welcome")) welcome(welcomePage, 0); // back from a system setting
        else if (isTab(screen)) openTab(screen);
        else if (screen.equals("strict")) strict(); // e.g. back from the device admin page
    }

    /** Welcome page shown while {@link #screen} is "welcome". */
    private int welcomePage;

    void welcome(int page) {
        welcome(page, 0);
    }

    void welcome(int page, int dir) {
        screen = "welcome";
        welcomePage = page;
        Welcome.show(this, page, dir);
    }

    /** Welcome finished or skipped. */
    void leaveWelcome() {
        openTab("home");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (front == this) front = null;
    }

    /** Service bound/unbound: redraw Home so the setup card isn't stuck on "not running". */
    static void healthChanged() {
        if (front == null) return;
        if (front.screen.equals("home")) front.openTab("home");
        else if (front.screen.equals("welcome") && front.welcomePage == Welcome.TURN_ON) front.welcome(Welcome.TURN_ON, 0);
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
        else if (screen.equals("welcome")) welcome(welcomePage, 0);
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
        else if (screen.equals("welcome")) {
            if (welcomePage == Welcome.INTRO || welcomePage == Welcome.TURN_ON) moveTaskToBack(true);
            else welcome(welcomePage - 1, -1);
        } else if (isTab(screen)) openTab("home");
        else if (screen.equals("build")) {
            if (buildCat != null) edit(buildCat);
            else picker();
        } else openTab(tab);
    }

    private void openTab(String t) {
        tab = t;
        if (!t.equals("home")) homeEditing = false;
        if (t.equals("stats")) statsTab();
        else if (t.equals("settings")) settingsTab();
        else home();
    }

    // ---- tabs ---------------------------------------------------------------------------

    private void home() {
        screen = "home";
        LinearLayout root = page(Flavor.PLAY ? "Unswipe" : "Focus Guard");
        Setup.addStatus(this, root);

        List<String> on = new ArrayList<>(), off = new ArrayList<>();
        Set<String> hidden = Targets.hidden(this);
        for (String cat : Targets.all(this)) {
            boolean en = limits.config(cat).enabled;
            if (en && hidden.contains(cat)) Targets.setHidden(this, cat, false); // turned on elsewhere
            else if (hidden.contains(cat)) continue;
            (en ? on : off).add(cat);
        }
        // Header: "YOUR LIMITS  5" + an Edit/Done pill.
        LinearLayout head = Ui.row(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 4), Ui.dp(this, 28), 0, Ui.dp(this, 10));
        head.addView(Ui.overline(this, "Your limits", Ui.MUTED));
        if (!on.isEmpty()) {
            TextView cnt = Ui.badge(this, String.valueOf(on.size()), Ui.MUTED, Ui.SURF2);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.leftMargin = Ui.dp(this, 8);
            head.addView(cnt, cl);
        }
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView editTop = Ui.badge(this, homeEditing ? "Done" : "Edit",
                homeEditing ? Ui.ON_SKY : Ui.SKY, homeEditing ? Ui.SKY : Ui.SKY_BOX);
        editTop.setTextSize(13);
        editTop.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        editTop.setOnClickListener(v -> {
            homeEditing = !homeEditing;
            refreshHome();
        });
        Ui.press(editTop);
        head.addView(editTop);
        root.addView(head, Ui.wide());
        if (homeEditing) {
            TextView hint = Ui.left(this, "Turn off or remove anything you don't want limited.", 13, Ui.DIM);
            hint.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 10));
            root.addView(hint, Ui.wide());
        }

        // One grouped list: a row per limit, then "Add".
        LinearLayout list = listBox();
        for (String cat : on) {
            if (list.getChildCount() > 0) list.addView(rowDivider());
            list.addView(targetRow(cat));
        }
        if (list.getChildCount() > 0) list.addView(rowDivider());
        list.addView(addRow());
        root.addView(list, Ui.wide());

        if (!off.isEmpty()) {
            TextView oh = Ui.overline(this, "Turned off", Ui.MUTED);
            oh.setPadding(Ui.dp(this, 4), Ui.dp(this, 24), 0, Ui.dp(this, 10));
            root.addView(oh);
            LinearLayout ol = listBox();
            for (String cat : off) {
                if (ol.getChildCount() > 0) ol.addView(rowDivider());
                ol.addView(targetRow(cat));
            }
            root.addView(ol, Ui.wide());
        }
        show(root);
        if (!keepScroll) Ui.enter(root);
    }

    /** Top of Home when setup is done: today's minutes as one big number, wins, streak, Focus. */
    void hero(LinearLayout root) {
        Limits limits = new Limits(this);
        android.content.SharedPreferences p = getSharedPreferences("limits", MODE_PRIVATE);
        Stats.Day t = Stats.day(p, Limits.day(Clock.now(this)));
        int streak = Stats.streak(this, p);
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.hero(this, 28));
        int hp = Ui.dp(this, 20);
        box.setPadding(hp, hp, hp, hp);

        LinearLayout top = Ui.row(this);
        TextView status = Ui.badge(this, Setup.rooted(this) ? "Protected · root" : "Protected", Ui.GREEN, Ui.GREEN_BOX);
        status.setCompoundDrawablePadding(Ui.dp(this, 6));
        status.setCompoundDrawablesRelative(dot(Ui.GREEN), null, null, null);
        top.addView(status);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        if (streak > 0) top.addView(Ui.badge(this, streak == 1 ? "1 day streak" : streak + " day streak",
                Ui.AMBER, Ui.AMBER_BOX));
        box.addView(top, Ui.wide());
        box.addView(Ui.space(this, 18));

        // Ring: opens left today across every limit that is on.
        int sumLeft = 0, sumTotal = 0;
        for (String cat : Targets.all(this)) {
            if (!limits.config(cat).enabled) continue;
            int tot = limits.total(cat);
            sumTotal += tot;
            sumLeft += limits.hardBlock(cat) != null || limits.inFocus() ? 0 : Math.min(tot, limits.left(cat));
        }
        float f = sumTotal == 0 ? 0f : sumLeft / (float) sumTotal;
        String rc = limits.inFocus() ? Ui.SKY : sumLeft == 0 ? Ui.RED : f <= 1f / 3 ? Ui.AMBER : Ui.GREEN;
        LinearLayout mid = Ui.row(this);
        Ring ring = new Ring(this);
        ring.set(f, rc, String.valueOf(sumLeft), limits.inFocus() ? "focusing" : sumLeft == 1 ? "open left" : "opens left",
                !keepScroll);
        mid.addView(ring, new LinearLayout.LayoutParams(Ui.dp(this, 136), Ui.dp(this, 136)));
        LinearLayout side = Ui.column(this);
        side.setPadding(Ui.dp(this, 22), 0, 0, 0);
        side.addView(heroNum(String.valueOf((t.seconds + 59) / 60), "min watched today", Ui.WHITE));
        side.addView(Ui.space(this, 12));
        side.addView(heroNum(String.valueOf(t.skips), "times you went back", Ui.GREEN));
        side.addView(Ui.space(this, 12));
        side.addView(heroNum(String.valueOf(Stats.weekSkips(this)), "wins this week", Ui.SKY));
        mid.addView(side, Ui.grow());
        mid.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(mid, Ui.wide());
        box.addView(Ui.space(this, 16));

        String kind = t.opens == 0 && t.skips == 0 ? "Nothing opened yet today. Nice start."
                : t.skips > t.opens ? "You chose to go back more than you opened. Well done."
                : t.skips > 0 ? "Every time you went back was a win."
                : "Every pause is a choice. You are doing fine.";
        box.addView(Ui.left(this, kind, 14, Ui.SKY_SOFT));
        LinearLayout.LayoutParams bp = Ui.wide();
        bp.bottomMargin = Ui.dp(this, 12);
        root.addView(box, bp);

        // Focus now: one choice + one clear button.
        LinearLayout fc = Ui.card(this);
        if (limits.inFocus()) {
            long left = (limits.focusUntil() - limits.now() + 59_999) / 60_000;
            LinearLayout fh = Ui.row(this);
            fh.addView(Ui.title(this, "Focusing", 17), Ui.grow());
            fh.addView(Ui.badge(this, "until " + Limits.hhmm(limits.focusUntil()), Ui.SKY, Ui.SKY_BOX));
            fc.addView(fh, Ui.wide());
            fc.addView(Ui.space(this, 4));
            fc.addView(Ui.left(this, left + " min left. Every limit is closed.", 13, Ui.MUTED));
            fc.addView(Ui.space(this, 12));
            long total = Math.max(1, limits.focusUntil() - limits.focusStart());
            float done = 1f - (limits.focusUntil() - limits.now()) / (float) total;
            fc.addView(bar(Math.max(0f, Math.min(1f, done)), Ui.SKY), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 6)));
        } else {
            fc.addView(Ui.title(this, "Focus now", 17));
            fc.addView(Ui.space(this, 2));
            fc.addView(Ui.left(this, "Close every limit for a while. You can't end it early.", 13, Ui.MUTED));
            fc.addView(Ui.space(this, 14));
            int[] mins = {30, 60, 120};
            String[] names = {"30 min", "1 hour", "2 hours"};
            String[][] parts = {{"30", "min"}, {"1", "hour"}, {"2", "hours"}};
            LinearLayout btns = Ui.row(this);
            for (int i = 0; i < 3; i++) {
                final int k = i;
                LinearLayout go = Ui.column(this);
                go.setGravity(Gravity.CENTER);
                go.setBackground(Ui.round(this, i == 1 ? Ui.SKY : Ui.SKY_BOX, 16));
                go.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 10));
                go.addView(Ui.number(this, parts[i][0], 22, i == 1 ? Ui.ON_SKY : Ui.SKY));
                go.addView(Ui.text(this, parts[i][1], 12, i == 1 ? Ui.ON_SKY : Ui.SKY_SOFT));
                go.setContentDescription(L.t(this, "Focus for " + names[i]));
                Ui.press(go);
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i > 0) bl.leftMargin = Ui.dp(this, 8);
                btns.addView(go, bl);
                go.setOnClickListener(v -> {
                int m = mins[k];
                Ui.confirm(this, L.t(this, "Focus for " + names[k] + "?"),
                        L.t(this, "Shorts, Reels and every app you added stay closed until "
                                + Limits.hhmm(limits.now() + m * 60_000L) + ". You can't end it early."),
                        L.t(this, "Start focus"), false, () -> {
                            limits.startFocus(m);
                            Ui.buzz(v, android.view.HapticFeedbackConstants.CONFIRM);
                            home();
                        });
                });
            }
            fc.addView(btns, Ui.wide());
        }
        root.addView(fc, Ui.wide());
    }

    private LinearLayout heroNum(String big, String small, String color) {
        LinearLayout c = Ui.column(this);
        TextView n = Ui.number(this, big, 22, color);
        n.setGravity(Gravity.START);
        c.addView(n);
        c.addView(Ui.space(this, 4));
        c.addView(Ui.left(this, small, 12, Ui.MUTED));
        return c;
    }

    private GradientDrawable dot(String color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.parseColor(color));
        int d = Ui.dp(this, 7);
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

    /** Glass look: background palette swatches, blur and see-through sliders, reset. */
    private void lookControls(LinearLayout look) {
        look.addView(Ui.space(this, 18));
        look.addView(Ui.left(this, "Background", 13, Ui.DIM));
        look.addView(Ui.space(this, 8));
        LinearLayout sw = new LinearLayout(this);
        FrameLayout[] holders = new FrameLayout[Ui.PALETTES.length];
        TextView[] names = new TextView[Ui.PALETTES.length];
        for (int i = 0; i < Ui.PALETTES.length; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            View tile = new View(this);
            Ui.Backdrop bd = new Ui.Backdrop(i);
            tile.setBackground(bd);
            tile.setClipToOutline(true);
            final int rad = Ui.dp(this, 14);
            tile.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad);
                }
            });
            FrameLayout holder = new FrameLayout(this);
            int ring = Ui.dp(this, 3);
            holder.setPadding(ring, ring, ring, ring);
            android.graphics.drawable.GradientDrawable st = new android.graphics.drawable.GradientDrawable();
            st.setCornerRadius(rad + ring);
            st.setStroke(Ui.dp(this, 2), Color.parseColor(Ui.SKY));
            holder.setBackground(st);
            st.setAlpha(i == Ui.palette ? 255 : 0);
            holder.addView(tile, new FrameLayout.LayoutParams(-1, Ui.dp(this, 64)));
            cell.addView(holder, Ui.wide());
            cell.addView(Ui.space(this, 4));
            TextView name = Ui.text(this, Ui.PALETTES[i], 12, i == Ui.palette ? Ui.SKY_SOFT : Ui.MUTED);
            cell.addView(name);
            holders[i] = holder;
            names[i] = name;
            cell.setContentDescription(Ui.PALETTES[i] + " background");
            cell.setOnClickListener(v -> {
                if (idx == Ui.palette) return;
                Ui.buzz(v, 0);
                Ui.setLook(this, "palette", idx);
                Ui.backdrop(this);
                // Update in place: rebuilding the page made it flicker.
                for (int k = 0; k < holders.length; k++) {
                    holders[k].getBackground().setAlpha(k == idx ? 255 : 0);
                    names[k].setTextColor(Color.parseColor(k == idx ? Ui.SKY_SOFT : Ui.MUTED));
                }
                Ui.refrostAll(getWindow().getDecorView());
            });
            Ui.press(cell);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.leftMargin = Ui.dp(this, 8);
            sw.addView(cell, lp);
        }
        look.addView(sw, Ui.wide());

        look.addView(slider("Blur", "blur", Ui.blur, "None", "Strong", true));
        look.addView(slider("Card see-through", "clear", Ui.clear, "Solid", "Clear", false));

        look.addView(Ui.space(this, 14));
        Button reset = Ui.ghost(this, "Reset look");
        reset.setOnClickListener(v -> {
            Ui.resetLook(this);
            Ui.backdrop(this);
            keepScroll = true;
            settingsTab();
            keepScroll = false;
        });
        look.addView(reset, Ui.wide());
    }

    /** Labelled 0..100 slider for a look setting. live = redraw while dragging, else rebuild on release. */
    private View slider(String label, String key, int value, String low, String high, boolean live) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(this, 18), 0, 0);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.left(this, label, 15, Ui.WHITE), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView val = Ui.text(this, value + "%", 14, Ui.SKY_SOFT);
        head.addView(val);
        box.addView(head, Ui.wide());
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(100);
        sb.setProgress(value);
        android.content.res.ColorStateList sky = android.content.res.ColorStateList.valueOf(Color.parseColor(Ui.SKY));
        sb.setProgressTintList(sky);
        sb.setThumbTintList(sky);
        sb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(Ui.SURF3)));
        sb.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        sb.setContentDescription(label);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean user) {
                val.setText(p + "%");
                if (live && user) {
                    Ui.blur = p;
                    Ui.refrostAll(getWindow().getDecorView());
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar s) {
                Ui.setLook(MainActivity.this, key, s.getProgress());
                if (live) return;
                keepScroll = true;
                settingsTab();
                keepScroll = false;
            }
        });
        box.addView(sb, Ui.wide());
        LinearLayout ends = new LinearLayout(this);
        ends.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        ends.addView(Ui.left(this, low, 12, Ui.DIM), new LinearLayout.LayoutParams(0, -2, 1f));
        ends.addView(Ui.text(this, high, 12, Ui.DIM));
        box.addView(ends, Ui.wide());
        return box;
    }

    private void settingsTab() {
        screen = "settings";
        LinearLayout root = page("Settings");
        Extras.addHomeButtons(this, root);

        root.addView(section("Look"));
        LinearLayout look = group(root);
        look.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        look.addView(Ui.left(this, "Glass: see-through frosted cards over soft colour. Classic: solid dark.", 14,
                Ui.MUTED));
        look.addView(Ui.space(this, 10));
        look.addView(Ui.segmented(this, new String[]{"Glass", "Classic"}, Ui.glass ? 0 : 1, i -> {
            String name = i == 0 ? "glass" : "classic";
            if (name.equals(Ui.themeName(this))) return;
            // Every colour changes, so the page must be rebuilt; let the thumb finish sliding first.
            getWindow().getDecorView().postDelayed(() -> {
                if (!"settings".equals(screen) || name.equals(Ui.themeName(this))) return;
                Ui.setTheme(this, name);
                Ui.backdrop(this);
                keepScroll = true;
                settingsTab();
                keepScroll = false;
            }, 260);
        }), Ui.wide());
        if (Ui.glass) lookControls(look);

        root.addView(section("Pause screen"));
        LinearLayout ps = group(root);
        ps.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 6));
        ps.addView(Ui.left(this, "What the pause asks you to do while you wait.", 14, Ui.MUTED));
        ps.addView(Ui.space(this, 10));
        // Changes apply in place (no page rebuild: that flickered and cut the segmented animation).
        LinearLayout paceBox = new LinearLayout(this);
        paceBox.setOrientation(LinearLayout.VERTICAL);
        ps.addView(Ui.segmented(this, Guide.STYLES, Guide.style(this), i -> {
            Guide.set(this, "guide", i);
            boolean show = i != 1;
            if (show == (paceBox.getVisibility() == View.VISIBLE)) return;
            paceBox.animate().cancel();
            if (show) {
                paceBox.setAlpha(0f);
                paceBox.setVisibility(View.VISIBLE);
                paceBox.animate().alpha(1f).setDuration(200).start();
            } else {
                paceBox.animate().alpha(0f).setDuration(150)
                        .withEndAction(() -> paceBox.setVisibility(View.GONE)).start();
            }
        }), Ui.wide());
        paceBox.addView(Ui.space(this, 14));
        paceBox.addView(Ui.left(this, "Breathing pace", 13, Ui.MUTED));
        paceBox.addView(Ui.space(this, 8));
        TextView paceInfo = Ui.left(this, Guide.PACE_INFO[Guide.pace(this)], 12, Ui.DIM);
        paceBox.addView(Ui.segmented(this, Guide.PACES, Guide.pace(this), i -> {
            Guide.set(this, "pace", i);
            paceInfo.setText(Guide.PACE_INFO[i]);
        }), Ui.wide());
        paceBox.addView(Ui.space(this, 6));
        paceBox.addView(paceInfo);
        paceBox.setVisibility(Guide.style(this) != 1 ? View.VISIBLE : View.GONE);
        ps.addView(paceBox, Ui.wide());
        ps.addView(Ui.space(this, 6));
        ps.addView(toggle("Show a question", Guide.question(this), on -> Guide.set(this, "guide_q", on)));
        ps.addView(divider());
        ps.addView(toggle("Vibrate at each step", Guide.haptic(this), on -> Guide.set(this, "guide_buzz", on)));

        root.addView(section("Wait time"));
        LinearLayout wt = group(root);
        boolean firstWt = true;
        for (String cat : Targets.all(this)) {
            if (!limits.config(cat).enabled) continue;
            if (!firstWt) wt.addView(divider());
            firstWt = false;
            Config cf = limits.config(cat);
            String v = cf.pauseS + " s" + (cf.pauseStepS > 0 ? " +" + cf.pauseStepS : "");
            wt.addView(navRow(Limits.label(this, cat), null, v, () -> edit(cat)));
        }
        if (firstWt) wt.addView(Ui.left(this, "Nothing is limited yet.", 14, Ui.MUTED));

        root.addView(section("Do this instead"));
        LinearLayout g = group(root);
        g.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        g.addView(Ui.left(this, "The pause screen shows one of these at random.", 14, Ui.MUTED));
        g.addView(Ui.space(this, 10));
        java.util.List<String> ideas = Config.swaps(this);
        LinearLayout list = Ui.column(this);
        g.addView(list, Ui.wide());
        // Edited in place (no page rebuild) so the field keeps focus and the keyboard stays up.
        java.util.function.Consumer<String> addRow = new java.util.function.Consumer<String>() {
            @Override
            public void accept(String idea) {
                LinearLayout r = Ui.row(MainActivity.this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setBackground(Ui.round(MainActivity.this, Ui.SURF2, 14));
                r.setPadding(Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 4),
                        Ui.dp(MainActivity.this, 4), Ui.dp(MainActivity.this, 4));
                r.addView(Ui.left(MainActivity.this, idea, 15, Ui.WHITE), Ui.grow());
                TextView x = Ui.text(MainActivity.this, "×", 22, Ui.MUTED);
                x.setPadding(Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 6),
                        Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 6));
                x.setContentDescription(L.t(MainActivity.this, "Remove") + " " + idea);
                Ui.press(x);
                x.setOnClickListener(v -> {
                    int k = list.indexOfChild(r);
                    java.util.List<String> now = Config.swaps(MainActivity.this);
                    if (k >= 0 && k < now.size()) now.remove(k);
                    Config.saveSwaps(MainActivity.this, String.join("\n", now));
                    Ui.buzz(v, android.view.HapticFeedbackConstants.CLOCK_TICK);
                    list.removeView(r);
                });
                r.addView(x);
                LinearLayout.LayoutParams rl = Ui.wide();
                rl.bottomMargin = Ui.dp(MainActivity.this, 8);
                r.setAlpha(0f);
                list.addView(r, rl);
                r.animate().alpha(1f).setDuration(180).setInterpolator(Ui.EASE).start();
            }
        };
        for (String idea : ideas) addRow.accept(idea);
        LinearLayout add = Ui.row(this);
        add.setGravity(Gravity.CENTER_VERTICAL);
        EditText swaps = input("", ideas.isEmpty() ? "e.g. Read 5 pages" : "Add an idea");
        swaps.setBackground(Ui.round(this, Ui.INSET, 14));
        swaps.setSingleLine(true);
        swaps.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        add.addView(swaps, Ui.grow());
        Button saveSwaps = Ui.small(this, "Add");
        saveSwaps.setBackground(Ui.round(this, Ui.SKY, 14));
        saveSwaps.setTextColor(Color.parseColor(Ui.ON_SKY));
        Runnable doAdd = () -> {
            String t = swaps.getText().toString().replace('\n', ' ').trim();
            if (t.isEmpty()) return;
            java.util.List<String> now = Config.swaps(this);
            now.add(t);
            Config.saveSwaps(this, String.join("\n", now));
            addRow.accept(t);
            swaps.setText("");
            swaps.setHint(L.t(this, "Add an idea"));
            Ui.buzz(swaps, android.view.HapticFeedbackConstants.CLOCK_TICK);
        };
        saveSwaps.setOnClickListener(v -> doAdd.run());
        swaps.setOnEditorActionListener((v, id, e) -> {
            doAdd.run();
            return true;
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        al.leftMargin = Ui.dp(this, 8);
        add.addView(saveSwaps, al);
        g.addView(add, Ui.wide());

        root.addView(Ui.space(this, 28));
        String ver = "";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        root.addView(Ui.text(this, Flavor.PLAY ? "Unswipe " + ver : "Focus Guard " + ver, 12, Ui.DIM));
        root.addView(Ui.text(this, "No internet access. Nothing leaves this phone.", 12, Ui.DIM));
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
            row.addView(icon(R.drawable.ic_chevron, Ui.DIM, 18));
            row.setOnClickListener(v -> click.run());
        }
        return row;
    }

    /** Thin line between rows of a {@link #group}. */
    View divider() {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor(Ui.SEP));
        d.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return d;
    }

    private LinearLayout listBox() {
        LinearLayout l = Ui.column(this);
        l.setBackground(Ui.outlined(this, Ui.CARD, 14));
        l.setClipToOutline(true);
        return l;
    }

    /** Divider inset past the icon, like a settings list. */
    private View rowDivider() {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor(Ui.SEP));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = Ui.dp(this, 74);
        d.setLayoutParams(lp);
        return d;
    }

    private View addRow() {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        TextView plus = Ui.text(this, "+", 22, Ui.SKY);
        plus.setTypeface(Ui.weight(500));
        plus.setBackground(Ui.round(this, Ui.SKY_BOX, 13));
        row.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        row.addView(Ui.space(this, 0), new LinearLayout.LayoutParams(Ui.dp(this, 16), 1));
        LinearLayout col = Ui.column(this);
        TextView t = Ui.left(this, "Add an app or group", 16, Ui.SKY);
        t.setTypeface(Ui.weight(600));
        col.addView(t);
        col.addView(Ui.left(this, "Social, games, news… or your own", 13, Ui.DIM));
        row.addView(col, Ui.grow());
        row.setOnClickListener(v -> picker());
        Ui.press(row);
        return row;
    }

    private View targetRow(String cat) {
        Config c = limits.config(cat);
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));

        View ic = iconView(cat);
        if (!c.enabled) ic.setAlpha(0.4f);
        row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        row.addView(Ui.space(this, 0), new LinearLayout.LayoutParams(Ui.dp(this, 16), 1));

        LinearLayout col = Ui.column(this);
        TextView name = Ui.title(this, Limits.label(this, cat), 16);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (!c.enabled) name.setTextColor(Color.parseColor(Ui.MUTED));
        col.addView(name);
        String tag;
        if (Targets.isGroup(cat)) {
            int n = Targets.groupApps(this, cat).size();
            tag = n == 1 ? "1 app" : n + " apps";
        } else tag = "facebook".equals(cat) ? "Website" : "whole".equals(c.mode) || Targets.wholeOnly(cat) ? "Whole app" : "Shorts / Reels";
        int total = limits.total(cat), left = limits.left(cat);
        boolean closed = c.enabled && (limits.hardBlock(cat) != null || left == 0);
        boolean low = c.enabled && !closed && !limits.isUnlocked(cat) && (left <= 2 || left * 3 <= total);
        String state = closed ? Ui.RED : low ? Ui.AMBER : Ui.GREEN;
        String sub = !c.enabled ? tag + " · off"
                : limits.hardBlock(cat) != null ? "Closed: " + limits.hardBlock(cat)
                : closed ? "Used up · back at midnight"
                : limits.isUnlocked(cat) ? "Open now · " + tag
                : tag + " · " + c.openMin + " min";
        TextView st = Ui.left(this, sub, 13, closed ? Ui.RED : low ? Ui.AMBER : Ui.DIM);
        st.setSingleLine(true);
        st.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(st);
        row.addView(col, Ui.grow());
        row.addView(Ui.space(this, 0), new LinearLayout.LayoutParams(Ui.dp(this, 12), 1));

        // Right side: edit action, a small ring of opens left, or a status badge.
        if (homeEditing) row.addView(editAction(cat, c.enabled));
        else if (!c.enabled) row.addView(Ui.badge(this, "Off", Ui.DIM, Ui.SURF2));
        else if (limits.isUnlocked(cat)) row.addView(Ui.badge(this, "Open", Ui.GREEN, Ui.GREEN_BOX));
        else if (total <= 0) row.addView(Ui.badge(this, "Blocked", Ui.RED, Ui.RED_BOX));
        else {
            Ring r = new Ring(this, 4);
            r.numColor(closed ? Ui.RED : Ui.WHITE);
            r.set(left / (float) total, state, String.valueOf(left), "", !keepScroll);
            r.setContentDescription(left + " of " + total + " opens left");
            row.addView(r, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        }
        row.setOnClickListener(v -> edit(cat));
        row.setOnLongClickListener(v -> {
            Ui.buzz(v, android.view.HapticFeedbackConstants.LONG_PRESS);
            cardMenu(cat);
            return true;
        });
        Ui.press(row);
        return row;
    }

    /** Redraw Home in place (same scroll, no animation). */
    private void refreshHome() {
        if (!"home".equals(screen)) {
            openTab("home");
            return;
        }
        keepScroll = true;
        home();
        keepScroll = false;
    }

    /** True for the fixed targets (Shorts, Reels…): they can be turned off, not deleted. */
    private static boolean builtin(String cat) {
        return !cat.startsWith(Limits.APP_PREFIX) && !Targets.isGroup(cat);
    }

    /** Small action in a card's right side while Home is in edit mode. */
    private View editAction(String cat, boolean enabled) {
        if (!enabled) {
            LinearLayout two = Ui.row(this);
            two.addView(actionBadge(cat, "Turn on", Ui.SKY, Ui.SKY_BOX, () -> setOn(cat, true)));
            View rm = actionBadge(cat, "Remove", Ui.RED, Ui.RED_BOX, () -> askRemove(cat));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = Ui.dp(this, 8);
            two.addView(rm, lp);
            return two;
        }
        if (builtin(cat)) return actionBadge(cat, "Turn off", Ui.AMBER, Ui.AMBER_BOX, () -> askOff(cat));
        return actionBadge(cat, "Remove", Ui.RED, Ui.RED_BOX, () -> askRemove(cat));
    }

    private View actionBadge(String cat, String label, String fg, String bg, Runnable act) {
        TextView t = Ui.badge(this, label, fg, bg);
        t.setTextSize(14);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 9));
        t.setOnClickListener(v -> act.run());
        Ui.press(t);
        return t;
    }

    /** Long-press menu of a Home card. */
    private void cardMenu(String cat) {
        boolean on = limits.config(cat).enabled;
        List<String> items = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        items.add(L.t(this, "Change limit"));
        acts.add(() -> edit(cat));
        items.add(L.t(this, on ? "Turn off" : "Turn on"));
        acts.add(() -> {
            if (on) askOff(cat);
            else setOn(cat, true);
        });
        int red = 0;
        if (!builtin(cat) || !on) {
            red = 1 << items.size();
            items.add(L.t(this, Targets.isGroup(cat) ? "Delete group" : builtin(cat) ? "Remove" : "Remove app"));
            acts.add(() -> askRemove(cat));
        }
        Ui.sheet(this, Limits.label(this, cat), null, items.toArray(new String[0]), red,
                w -> acts.get(w).run());
    }

    private String strictNote() {
        return Strict.on(this) ? " Strict mode is on, so this waits before it takes effect." : "";
    }

    private void askOff(String cat) {
        Ui.confirm(this, L.t(this, "Turn off " + Limits.label(this, cat) + "?"),
                L.t(this, "No pause and no limit for it. It moves to \"Turned off\", "
                        + "where you can turn it back on." + strictNote()),
                L.t(this, "Turn off"), true, () -> setOn(cat, false));
    }

    private void setOn(String cat, boolean on) {
        Config c = limits.config(cat).copy();
        c.enabled = on;
        Toast.makeText(this, L.t(this, Strict.save(this, c)), Toast.LENGTH_LONG).show();
        refreshHome();
    }

    private void askRemove(String cat) {
        if (builtin(cat)) {
            Ui.confirm(this, L.t(this, "Remove " + Limits.label(this, cat) + "?"),
                    L.t(this, "It's already off. This takes it off Home. You can add it again with + Add."),
                    L.t(this, "Remove"), true, () -> {
                        // Only reachable while turned off, so nothing gets looser: no strict delay.
                        if (!limits.config(cat).enabled) Targets.setHidden(this, cat, true);
                        refreshHome();
                    });
            return;
        }
        boolean group = Targets.isGroup(cat);
        Ui.confirm(this, L.t(this, (group ? "Delete " : "Remove ") + Limits.label(this, cat) + "?"),
                L.t(this, (group ? "Its apps won't be limited any more." : "It won't be limited any more.")
                        + " You can add it again with + Add." + strictNote()),
                L.t(this, group ? "Delete" : "Remove"), true, () -> {
                    String msg = group ? Strict.removeGroup(this, cat)
                            : Strict.removeApp(this, cat.substring(Limits.APP_PREFIX.length()));
                    Toast.makeText(this, L.t(this, msg), Toast.LENGTH_LONG).show();
                    refreshHome();
                });
    }

    /** Horizontal progress bar filled to {@code f} (0..1). */
    private View bar(float f, String color) {
        LinearLayout track = Ui.row(this);
        track.setBackground(Ui.round(this, Ui.SURF3, 3));
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
        Drawable brand = Brand.of(cat);
        if (brand != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(brand);
            return iv;
        }
        String name = Limits.label(this, cat);
        TextView t = Ui.text(this, name.isEmpty() ? "?" : name.substring(0, 1), 17, Ui.SKY);
        t.setTypeface(Ui.weight(700));
        t.setBackground(Ui.round(this, Ui.SKY_BOX, 10));
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
            box.addView(icon(R.drawable.ic_chevron, Ui.DIM, 20));
            box.setOnClickListener(v -> builder(cat, Targets.groupName(this, cat), apps, false));
            root.addView(box, card());
            TextView hint = Ui.left(this, "Tap to rename or change the apps. All apps in the group share one pause and one opens count.",
                    12, Ui.DIM);
            root.addView(hint);
            root.addView(Ui.space(this, 8));
        }

        // Live preview: the three numbers that matter, big, like a ticket.
        LinearLayout prev = Ui.column(this);
        prev.setBackground(Ui.hero(this, 20));
        int pp = Ui.dp(this, 16);
        prev.setPadding(pp, pp, pp, pp);
        TextView presetName = Ui.left(this, "", 12, Ui.SKY);
        presetName.setTypeface(Ui.weight(600));
        prev.addView(presetName);
        prev.addView(Ui.space(this, 8));
        LinearLayout nums = Ui.row(this);
        TextView[] big = new TextView[3];
        String[] caps = {"pause", "opens a day", "min each"};
        for (int i = 0; i < 3; i++) {
            LinearLayout col = Ui.column(this);
            big[i] = Ui.left(this, "", 26, Ui.WHITE);
            big[i].setTypeface(Ui.weight(600));
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
        presets.setBackground(Ui.round(this, Ui.SURF1, 14));
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
                pb[i].setTypeface(Ui.weight(on ? 600 : 400));
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
        moreT.setTypeface(Ui.weight(600));
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
            remove.setOnClickListener(v -> askRemove(cat));
            root.addView(remove, Ui.wide());
        } else if (Targets.isGroup(cat)) {
            Button remove = Ui.button(this, "Delete group", Ui.CARD, Ui.RED);
            remove.setOnClickListener(v -> askRemove(cat));
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
        g.setBackground(Ui.outlined(this, Ui.CARD, 14));
        int p = Ui.dp(this, 16);
        g.setPadding(p, Ui.dp(this, 2), p, Ui.dp(this, 2));
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

        Set<String> hidden = Targets.hidden(this);
        boolean first = true;
        for (String cat : Limits.BUILTIN) {
            if (!hidden.contains(cat)) continue;
            if (first) root.addView(section("Removed from Home"));
            first = false;
            LinearLayout r = Ui.row(this);
            r.setBackground(Ui.outlined(this, Ui.CARD, 20));
            int p = Ui.dp(this, 16);
            r.setPadding(p, p, p, p);
            r.addView(Ui.title(this, Limits.label(this, cat), 16), Ui.grow());
            r.addView(actionBadge(cat, "Add back", Ui.SKY, Ui.SKY_BOX, () -> {
                Targets.setHidden(this, cat, false);
                setOn(cat, true);
            }));
            root.addView(r, card());
        }

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
            g.setBackground(Ui.round(this, Ui.SKY_BOX, 12));
            t.addView(g, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        }
        t.addView(Ui.space(this, 10));
        TextView n = Ui.left(this, name, 16, Ui.WHITE);
        n.setTypeface(Ui.weight(600));
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
        search.setBackground(Ui.round(this, Ui.SURF1, 24));
        search.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        Drawable lens = getDrawable(R.drawable.ic_search);
        if (lens != null) {
            lens.setTint(Color.parseColor(Ui.DIM));
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
                col.addView(Ui.left(this, L.t(this, "In " + Limits.label(this, taken)), 12, Ui.DIM));
            row.addView(col, Ui.grow());
            if (taken != null) {
                row.setAlpha(0.45f);
            } else if (buildSingle) {
                ImageView go = icon(R.drawable.ic_chevron, Ui.DIM, 20);
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
                    if (!on) g.setStroke(Ui.dp(this, 2), Color.parseColor(Ui.DIM));
                    GradientDrawable rb = Ui.round(this, on ? Ui.SKY_BOX : Ui.CARD, 14);
                    if (on) rb.setStroke(Ui.dp(this, 1), Color.parseColor(Ui.SKY));
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
        box.setBackground(Ui.round(this, Ui.SURF3, 12));
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

    /** Title of the page being built; shown small in the top bar once the large title scrolls away. */
    private String pageTitle;

    LinearLayout page(String title) {
        pageTitle = title;
        LinearLayout root = Ui.column(this);
        int p = Ui.dp(this, 20);
        root.setPadding(p, Ui.dp(this, 52), p, Ui.dp(this, 28));
        root.addView(Ui.largeTitle(this, title));
        root.addView(Ui.space(this, 14));
        return root;
    }

    /** iOS-style bar: small centred title on frosted ground with a hairline; fades in on scroll. */
    private View topBar(ScrollView sv) {
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(Ui.frost(this, Ui.round(this, Ui.NAV, 0), 0));
        TextView t = Ui.text(this, pageTitle == null ? "" : pageTitle, 17, Ui.WHITE);
        t.setTypeface(Ui.weight(600));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(this, 48), 0, Ui.dp(this, 48), 0);
        bar.addView(t, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor(Ui.SEP));
        bar.addView(line, new FrameLayout.LayoutParams(-1, 1, Gravity.BOTTOM));
        int start = Ui.dp(this, 30), span = Ui.dp(this, 26);
        Runnable fade = () -> {
            float a = Math.max(0f, Math.min(1f, (sv.getScrollY() - start) / (float) span));
            bar.setAlpha(a);
            bar.setVisibility(a <= 0f ? View.INVISIBLE : View.VISIBLE);
            t.setTranslationY((1f - a) * Ui.dp(this, 6));
        };
        sv.getViewTreeObserver().addOnScrollChangedListener(fade::run);
        sv.post(fade);
        bar.setAlpha(0f);
        bar.setVisibility(View.INVISIBLE);
        return bar;
    }

    private static int lastTab = -1;

    void show(LinearLayout root) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.TRANSPARENT);
        sv.addView(root);
        if (keepScroll && curScroll != null) {
            int y = curScroll.getScrollY();
            // Scroll before the first frame is drawn: a posted scroll left one frame at the top (flicker).
            sv.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    sv.getViewTreeObserver().removeOnPreDrawListener(this);
                    sv.scrollTo(0, y);
                    return true;
                }
            });
        }
        curScroll = sv;
        if (!keepScroll) {
            sv.setAlpha(0f);
            sv.animate().alpha(1f).setDuration(180).setInterpolator(Ui.EASE).start();
        }
        FrameLayout stack = new FrameLayout(this);
        stack.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        stack.addView(topBar(sv), new FrameLayout.LayoutParams(-1, Ui.dp(this, 44), Gravity.TOP));
        if (!isTab(screen)) {
            Ui.fitInsets(stack);
            setContentView(stack);
            return;
        }
        LinearLayout frame = Ui.column(this);
        frame.setBackgroundColor(Color.TRANSPARENT);
        frame.addView(stack, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        // Floating frosted dock: equal tabs (icon over label), a soft sky pill slides to the open tab.
        LinearLayout dock = Ui.row(this);
        dock.setPadding(Ui.dp(this, 24), Ui.dp(this, 6), Ui.dp(this, 24), Ui.dp(this, 12));
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(Ui.outlined(this, Ui.NAV, 32));
        if (!Ui.glass) bar.setElevation(Ui.dp(this, 8)); // shadows show through translucent fills
        if (Ui.glass) {
            // Specular rim: a bright hairline along the top edge, like light catching glass.
            View rim = new View(this);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF});
            rim.setBackground(g);
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-1, Ui.dp(this, 1), Gravity.TOP);
            rl.leftMargin = rl.rightMargin = Ui.dp(this, 26);
            rl.topMargin = -Ui.dp(this, 5) + 1;
            bar.addView(rim, rl);
        }
        int bp = Ui.dp(this, 5);
        bar.setPadding(bp, bp, bp, bp);
        String[][] tabs = {{"home", "Home"}, {"stats", "Stats"}, {"settings", "Settings"}};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_chart, R.drawable.ic_gear};
        int sel = 0;
        for (int i = 0; i < tabs.length; i++) if (tabs[i][0].equals(screen)) sel = i;
        View pill = new View(this);
        pill.setBackground(Ui.round(this, Ui.glass ? "#26FFFFFF" : "#1A4A66", 26));
        bar.addView(pill, new FrameLayout.LayoutParams(0, Ui.dp(this, 52)));
        LinearLayout row = Ui.row(this);
        for (int i = 0; i < tabs.length; i++) {
            String[] t = tabs[i];
            boolean on = i == sel;
            LinearLayout b = Ui.column(this);
            b.setGravity(Gravity.CENTER);
            b.setContentDescription(L.t(this, t[1]));
            b.addView(icon(icons[i], on ? Ui.SKY : Ui.MUTED, 22),
                    new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
            TextView label = Ui.text(this, t[1], 11, on ? Ui.SKY : Ui.MUTED);
            label.setTypeface(Ui.weight(on ? 600 : 500));
            label.setPadding(0, Ui.dp(this, 2), 0, 0);
            b.addView(label);
            if (!on) {
                b.setOnClickListener(v -> {
                    Ui.buzz(v, android.view.HapticFeedbackConstants.CLOCK_TICK);
                    openTab(t[0]);
                });
                Ui.press(b);
            }
            row.addView(b, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f));
        }
        bar.addView(row, new FrameLayout.LayoutParams(-1, -2));
        final int to = sel, from = lastTab < 0 ? sel : lastTab;
        lastTab = sel;
        bar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int bo, int ol, int ot, int or, int ob) {
                int w = row.getWidth() / tabs.length;
                if (w <= 0) return;
                v.removeOnLayoutChangeListener(this);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pill.getLayoutParams();
                lp.width = w;
                pill.setLayoutParams(lp);
                pill.setTranslationX(from * w);
                if (from != to) pill.animate().translationX(to * w).setDuration(320).setInterpolator(Ui.EASE).start();
                else pill.setTranslationX(to * w);
            }
        });
        dock.addView(bar, Ui.grow());
        frame.addView(dock, Ui.wide());
        Ui.fitInsets(frame, dock);
        setContentView(frame);
    }

    /** Scrolling page with a fixed button at the bottom (builder Save). */
    private void showWithFooter(LinearLayout root, View footer) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.TRANSPARENT);
        sv.addView(root);
        LinearLayout frame = Ui.column(this);
        frame.setBackgroundColor(Color.TRANSPARENT);
        frame.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout foot = Ui.column(this);
        foot.setBackgroundColor(Color.parseColor(Ui.NAV));
        int p = Ui.dp(this, 16);
        foot.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 12));
        foot.addView(footer, Ui.wide());
        frame.addView(foot, Ui.wide());
        Ui.fitInsets(frame);
        setContentView(frame);
    }

    TextView section(String s) {
        TextView t = Ui.left(this, s.toUpperCase(java.util.Locale.ROOT), 13, Ui.MUTED);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), 0, Ui.dp(this, 7));
        return t;
    }

    LinearLayout.LayoutParams card() {
        LinearLayout.LayoutParams lp = Ui.wide();
        lp.bottomMargin = Ui.dp(this, 10);
        return lp;
    }

    EditText input(String text, String hint) {
        EditText e = new EditText(this);
        e.setText(text);
        e.setHint(L.t(this, hint));
        e.setHintTextColor(Color.parseColor(Ui.DIM));
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
            b.setBackground(Ui.round(this, Ui.SURF3, 12));
            b.setTextColor(Color.parseColor(Ui.SKY));
            b.setTypeface(Ui.weight(600));
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
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
        row.addView(Ui.left(this, label, 16, Ui.WHITE), Ui.grow());
        Ui.Switch sw = new Ui.Switch(this, on, c -> {
            sink.set(c);
            if (editChanged != null) editChanged.run();
        });
        sw.setContentDescription(L.t(this, label));
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }

    static List<String> list(String... s) {
        return new ArrayList<>(Arrays.asList(s));
    }
}
