package com.alrasin.focusguard;

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Full-screen pause shown before a target. While the wait runs only the guide is on screen (the
 * orb acts out each step); when it ends the choices rise in: open for a while, or go back.
 * When nothing can be opened it becomes a calm "closed" screen with a live countdown.
 */
public class PauseActivity extends Activity {
    /** elapsedRealtime of the last onCreate; GuardService checks the pause screen really opened. */
    static volatile long createdAt;
    private static final int[] CHOICES_MIN = {1, 5, 10, 15, 30, 60};
    /** Rotated one per pause, so the screen doesn't turn into wallpaper the eye skips. */
    private static final String[] QUESTIONS = {
            "Take a breath. Do you really need this now?",
            "What were you about to do before this?",
            "How do you want to feel in 15 minutes?",
            "Is this a choice, or a habit?",
            "Are you bored, tired or avoiding something?",
            "Will you remember this video tomorrow?",
            "What would make the next hour good?",
    };
    private static final String INK = "#0A0E1F";

    private String cat, pkg;
    private CountDownTimer timer;
    private ValueAnimator breath;
    private Guide guide;
    private boolean counted, whyOk;
    /** Open / extend controls: dimmed and disabled until "why" is written. */
    private final List<View> gated = new ArrayList<>();
    private TextView hint;
    private final Handler clock = new Handler(Looper.getMainLooper());
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.theme(this);
        Ui.backdrop(this);
        createdAt = SystemClock.elapsedRealtime();
        Ui.onBack(this, this::goBack);
        cat = getIntent().getStringExtra("cat");
        pkg = getIntent().getStringExtra("pkg");
        if (cat == null) {
            finish();
            return;
        }
        Limits limits = new Limits(this);
        int left = limits.left(cat);
        String blocked = limits.hardBlock(cat);
        int cooldown = limits.cooldownLeftMin(cat);
        String label = Limits.label(this, cat);

        LinearLayout root = Ui.column(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 24);
        root.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 20));

        if (blocked != null || cooldown > 0 || (left == 0 && !limits.canExtend(cat))) {
            closed(root, limits, label, blocked, cooldown);
            return;
        }
        open(root, limits, label, left);
    }

    // ---------------------------------------------------------------- pause (can open)

    private void open(LinearLayout root, Limits limits, String label, int left) {
        Config cfg = limits.config(cat);
        boolean extending = left == 0;
        root.addView(header(label, extending ? "limit reached"
                : left + " of " + limits.total(cat) + " left"), wrap());
        root.addView(flex());

        if (Guide.question(this)) {
            TextView q = Ui.text(this, question(), 24, Ui.WHITE);
            q.setTypeface(Ui.weight(300));
            q.setLineSpacing(0, 1.12f);
            q.setLetterSpacing(-0.01f);
            root.addView(q, Ui.wide());
            root.addView(Ui.space(this, 28));
        }

        // Guide: the orb moves the way the word says; the ring fills as the wait runs out.
        int seconds = limits.pauseSeconds(cat);
        int size = Math.min(Ui.dp(this, 300), (int) (getResources().getDisplayMetrics().heightPixels * 0.36f));
        Guide guide = new Guide(this, Guide.sequence(this));
        guide.wait(0, String.valueOf(seconds));
        this.guide = guide;
        root.addView(guide, new LinearLayout.LayoutParams(size, size));
        root.addView(Ui.space(this, 22));
        TextView word = Ui.text(this, "", 32, Ui.WHITE);
        word.setTypeface(Ui.weight(300));
        word.setLetterSpacing(-0.01f);
        root.addView(word);
        root.addView(Ui.space(this, 6));
        TextView sub = Ui.text(this, "", 15, Ui.MUTED);
        root.addView(sub);
        boolean haptic = Guide.haptic(this);
        guide.listen((ph, i) -> {
            if (word.getText().length() == 0) {
                word.setText(L.t(this, ph.word));
                sub.setText(L.t(this, ph.sub));
                return;
            }
            if (haptic) Ui.buzz(guide, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            for (TextView tv : new TextView[]{word, sub}) {
                String next = L.t(this, tv == word ? ph.word : ph.sub);
                tv.animate().alpha(0f).translationY(-Ui.dp(this, 6)).setDuration(200).withEndAction(() -> {
                    tv.setText(next);
                    tv.setTranslationY(Ui.dp(this, 8));
                    tv.animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(Ui.EASE).start();
                }).start();
            }
        });
        // A faint wash over the background follows the orb.
        int bgFrom = Ui.glass ? Color.TRANSPARENT : Color.parseColor(Ui.BG),
                bgTo = Color.parseColor(Ui.glass ? "#140A84FF" : "#0C1A24");
        android.animation.ArgbEvaluator argb = new android.animation.ArgbEvaluator();
        breath = ValueAnimator.ofFloat(0f, 1f);
        breath.setDuration(1000);
        breath.setRepeatCount(ValueAnimator.INFINITE);
        breath.addUpdateListener(an -> {
            if (scroll != null) scroll.setBackgroundColor((int) argb.evaluate(guide.breath(), bgFrom, bgTo));
        });
        breath.start();
        guide.start();

        // While waiting: only a quiet line where the choices will appear.
        root.addView(Ui.space(this, 28));
        TextView waitHint = Ui.text(this, "", 13, Ui.DIM);
        waitHint.setFontFeatureSettings("tnum");
        root.addView(waitHint);

        // Decision panel: hidden until the wait is over.
        LinearLayout panel = Ui.column(this);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setVisibility(View.GONE);
        root.addView(panel, Ui.wide());

        int wins = Stats.weekSkips(this);
        if (wins > 0) {
            panel.addView(Ui.badge(this, "✓  You went back " + wins + "× this week", Ui.GREEN, Ui.GREEN_BOX), wrap());
            panel.addView(Ui.space(this, 4));
        }
        addSwap(panel);
        panel.addView(Ui.space(this, 20));

        EditText why = null;
        whyOk = extending || !cfg.askWhy;
        if (extending) {
            TextView t = Ui.text(this, "Daily limit reached · "
                    + (cfg.extMax - limits.extends_(cat)) + " extensions left", 13, Ui.DIM);
            panel.addView(t);
            panel.addView(Ui.space(this, 10));
            Button ext = Ui.ghost(this, "Extend +" + cfg.extOpens + " opens");
            ext.setOnClickListener(v -> {
                // Extending may have closed (cutoff hour) while this screen was up: leave instead.
                if (!limits.canExtend(cat)) {
                    goBack();
                    return;
                }
                // New opens still go through a fresh pause.
                limits.extend(cat);
                recreate();
            });
            gated.add(ext);
            panel.addView(ext, Ui.wide());
        } else {
            if (!whyOk) {
                why = whyField();
                panel.addView(why, Ui.wide());
                panel.addView(Ui.space(this, 8));
                panel.addView(quickWhy(why), Ui.wide());
                panel.addView(Ui.space(this, 18));
            }
            hint = Ui.overline(this, "", Ui.DIM);
            hint.setGravity(Gravity.CENTER);
            panel.addView(hint, Ui.wide());
            panel.addView(Ui.space(this, 10));
            // Session length: a few short choices plus the full length.
            long maxMs = limits.openMs(cat);
            int maxMin = (int) Math.max(1, maxMs / 60000);
            List<Integer> minsList = new ArrayList<>();
            for (int m : CHOICES_MIN) if (m < maxMin && minsList.size() < 3) minsList.add(m);
            minsList.add(maxMin);
            LinearLayout chips = Ui.row(this);
            final EditText whyBox = why;
            for (int i = 0; i < minsList.size(); i++) {
                int m = minsList.get(i);
                TextView chip = openChip(m + " min");
                chip.setOnClickListener(v -> {
                    if (limits.hardBlock(cat) != null || limits.left(cat) == 0) {
                        goBack();
                        return;
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 30)
                        Ui.buzz(v, android.view.HapticFeedbackConstants.CONFIRM);
                    limits.saveWhy(cat, whyBox == null ? "" : whyBox.getText().toString().trim());
                    limits.open(cat, m == maxMin ? maxMs : m * 60_000L);
                    if (GuardService.instance != null) GuardService.instance.showOpensLeft(cat);
                    finish();
                });
                gated.add(chip);
                LinearLayout.LayoutParams lp = Ui.grow();
                if (i > 0) lp.leftMargin = Ui.dp(this, 8);
                chips.addView(chip, lp);
            }
            panel.addView(chips, Ui.wide());
        }

        root.addView(flex());
        root.addView(Ui.space(this, 16));
        Button back = primary("I don't want to open " + label);
        back.setOnClickListener(v -> goBack());
        root.addView(back, Ui.wide());

        // Siblings slide when the panel appears and the orb shrinks, instead of jumping.
        LayoutTransition lt = new LayoutTransition();
        lt.disableTransitionType(LayoutTransition.APPEARING);
        lt.disableTransitionType(LayoutTransition.DISAPPEARING);
        lt.enableTransitionType(LayoutTransition.CHANGING);
        lt.setDuration(420);
        lt.setInterpolator(LayoutTransition.CHANGING, Ui.EASE);
        lt.setInterpolator(LayoutTransition.CHANGE_APPEARING, Ui.EASE);
        lt.setInterpolator(LayoutTransition.CHANGE_DISAPPEARING, Ui.EASE);
        root.setLayoutTransition(lt);

        refresh();
        show(root);
        Ui.enter(root);

        long total = seconds * 1000L;
        waitHint.setText(optionsIn(seconds));
        timer = new CountDownTimer(total, 50) {
            @Override
            public void onTick(long ms) {
                int s = (int) ((ms + 999) / 1000);
                guide.wait((total - ms) / (float) Math.max(1, total), String.valueOf(s));
                waitHint.setText(optionsIn(s));
            }

            @Override
            public void onFinish() {
                guide.wait(1, "✓");
                counted = true;
                refresh();
                reveal(root, waitHint, panel, guide, size);
            }
        }.start();
    }

    private String optionsIn(int s) {
        return L.t(this, "Choices in " + s + " s");
    }

    /** Wait over: the hint goes, the orb steps back, the choices rise in. */
    private void reveal(LinearLayout root, TextView waitHint, LinearLayout panel, Guide guide, int size) {
        if (android.os.Build.VERSION.SDK_INT >= 30)
            Ui.buzz(root, android.view.HapticFeedbackConstants.CONFIRM);
        waitHint.setVisibility(View.GONE);
        panel.setAlpha(0f);
        panel.setTranslationY(Ui.dp(this, 24));
        panel.setVisibility(View.VISIBLE);
        panel.animate().alpha(1f).translationY(0).setStartDelay(120).setDuration(520)
                .setInterpolator(Ui.EASE).start();
        int small = (int) (size * 0.7f);
        ValueAnimator shrink = ValueAnimator.ofInt(size, small);
        shrink.setDuration(520);
        shrink.setInterpolator(Ui.EASE);
        shrink.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = guide.getLayoutParams();
            lp.width = lp.height = (int) a.getAnimatedValue();
            guide.setLayoutParams(lp);
        });
        shrink.start();
    }

    private EditText whyField() {
        EditText why = new EditText(this);
        why.setHint(L.t(this, "Why are you opening it?"));
        why.setHintTextColor(Color.parseColor(Ui.DIM));
        why.setTextColor(Color.parseColor(Ui.WHITE));
        why.setTextSize(16);
        why.setSingleLine(true);
        why.setBackground(Ui.outlined(this, Ui.SURF1, 16));
        int p = Ui.dp(this, 16);
        why.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 14));
        why.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                whyOk = s.toString().trim().length() >= 3;
                refresh();
            }
        });
        return why;
    }

    /** One tap instead of typing: the common reasons. */
    private View quickWhy(EditText box) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        LinearLayout quick = Ui.row(this);
        for (String q : Limits.QUICK_WHY) {
            TextView c = Ui.text(this, q, 13, Ui.MUTED);
            c.setBackground(Ui.outlined(this, Ui.SURF1, 16));
            c.setPadding(Ui.dp(this, 13), Ui.dp(this, 7), Ui.dp(this, 13), Ui.dp(this, 7));
            c.setOnClickListener(v -> {
                box.setText(q);
                box.setSelection(q.length());
                Ui.buzz(v, android.view.HapticFeedbackConstants.CLOCK_TICK);
            });
            Ui.press(c);
            LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(-2, -2);
            if (quick.getChildCount() > 0) qp.leftMargin = Ui.dp(this, 6);
            quick.addView(c, qp);
        }
        hs.addView(quick);
        return hs;
    }

    // ---------------------------------------------------------------- closed (nothing to open)

    private void closed(LinearLayout root, Limits limits, String label, String blocked, int cooldown) {
        Config cfg = limits.config(cat);
        long until = 0;
        String reason;
        if (limits.inFocus()) {
            reason = "You're in a focus session. Stay with it.";
            until = limits.focusUntil();
        } else if (blocked != null && blocked.startsWith("blocked by")) {
            reason = "Blocked by your schedule right now.";
        } else if (blocked != null) {
            reason = "Today's time for it is used up.";
            until = midnight();
        } else if (cooldown > 0) {
            reason = "A short break after your last open.";
            until = Math.max(limits.lastOpen(cat), limits.unlockedUntil(cat)) + cfg.cooldownMin * 60_000L;
        } else {
            reason = "You've used all " + limits.total(cat) + " opens today.";
            until = midnight();
        }

        root.addView(header(label, "closed"), wrap());
        root.addView(flex());

        // A slow, resting orb with a lock in the middle.
        int size = Math.min(Ui.dp(this, 240), (int) (getResources().getDisplayMetrics().heightPixels * 0.3f));
        FrameLayout stage = new FrameLayout(this);
        Guide g = new Guide(this, Guide.rest());
        g.ring(false);
        g.wait(0, "");
        guide = g;
        stage.addView(g, new FrameLayout.LayoutParams(size, size));
        ImageView lock = new ImageView(this);
        lock.setImageResource(R.drawable.ic_lock);
        lock.setColorFilter(Color.WHITE);
        lock.setAlpha(0.95f);
        stage.addView(lock, new FrameLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40), Gravity.CENTER));
        root.addView(stage, new LinearLayout.LayoutParams(size, size));
        g.start();

        root.addView(Ui.space(this, 28));
        TextView h = Ui.text(this, label + " is closed", 30, Ui.WHITE);
        h.setTypeface(Ui.weight(300));
        h.setLetterSpacing(-0.01f);
        root.addView(h, Ui.wide());
        root.addView(Ui.space(this, 8));
        root.addView(Ui.text(this, reason, 15, Ui.MUTED), Ui.wide());

        if (until > limits.now()) {
            root.addView(Ui.space(this, 28));
            TextView over = Ui.overline(this, "Opens again in", Ui.DIM);
            over.setGravity(Gravity.CENTER);
            root.addView(over, Ui.wide());
            root.addView(Ui.space(this, 4));
            TextView count = Ui.text(this, "", 44, Ui.WHITE);
            count.setTypeface(Ui.weight(200));
            count.setFontFeatureSettings("tnum");
            count.setIncludeFontPadding(false);
            root.addView(count, Ui.wide());
            final long end = until;
            Runnable tick = new Runnable() {
                @Override
                public void run() {
                    long ms = end - limits.now();
                    if (ms <= 0) {
                        recreate();
                        return;
                    }
                    count.setText(hms(ms));
                    clock.postDelayed(this, 1000 - (ms % 1000) + 5);
                }
            };
            tick.run();
        }

        root.addView(flex());
        root.addView(Ui.space(this, 16));
        int wins = Stats.weekSkips(this);
        int used = limits.used(cat), mins = (limits.secondsToday(cat) + 59) / 60;
        LinearLayout tiles = Ui.row(this);
        tiles.addView(tile(String.valueOf(wins), "Went back this week"), Ui.grow());
        LinearLayout.LayoutParams tp = Ui.grow();
        tp.leftMargin = Ui.dp(this, 10);
        tiles.addView(tile(used + " · " + mins + "m", "Opens · time today"), tp);
        root.addView(tiles, Ui.wide());
        addSwap(root);
        root.addView(Ui.space(this, 16));
        Button back = primary("Go back");
        back.setOnClickListener(v -> goBack());
        root.addView(back, Ui.wide());
        show(root);
        Ui.enter(root);
        if (android.os.Build.VERSION.SDK_INT >= 30)
            root.post(() -> Ui.buzz(root, android.view.HapticFeedbackConstants.REJECT));
    }

    private long midnight() {
        Calendar c = Clock.calendar(this);
        c.add(Calendar.DAY_OF_YEAR, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String hms(long ms) {
        long s = (ms + 999) / 1000, h = s / 3600, m = s / 60 % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s % 60)
                : String.format(Locale.US, "%d:%02d", m, s % 60);
    }

    private LinearLayout tile(String value, String caption) {
        LinearLayout t = Ui.column(this);
        t.setBackground(Ui.outlined(this, Ui.SURF1, 18));
        int p = Ui.dp(this, 14);
        t.setPadding(p, p, p, p);
        TextView v = Ui.left(this, value, 22, Ui.WHITE);
        v.setTypeface(Ui.weight(300));
        v.setFontFeatureSettings("tnum");
        t.addView(v);
        t.addView(Ui.space(this, 2));
        t.addView(Ui.left(this, caption, 12, Ui.MUTED));
        return t;
    }

    // ---------------------------------------------------------------- shared

    /** Frosted pill at the top: app icon, name, where you stand. */
    private View header(String label, String state) {
        LinearLayout head = Ui.row(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackground(Ui.outlined(this, Ui.SURF1, 20));
        head.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        Drawable icon = icon();
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            head.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
            head.addView(Ui.space(this, 0), new LinearLayout.LayoutParams(Ui.dp(this, 9), 1));
        }
        TextView t = Ui.text(this, label, 15, Ui.WHITE);
        t.setTypeface(Ui.weight(600));
        head.addView(t);
        TextView s = Ui.text(this, "  ·  " + L.t(this, state), 13, Ui.MUTED);
        s.setFontFeatureSettings("tnum");
        head.addView(s);
        return head;
    }

    /** Main action: solid white pill with dark text, like a system sheet button. */
    private Button primary(String s) {
        Button b = Ui.button(this, s, "#FFFFFF", INK);
        b.setTypeface(Ui.weight(600));
        return b;
    }

    /** Flexible gap that pushes the actions to the bottom on tall screens. */
    private View flex() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1f));
        v.setMinimumHeight(Ui.dp(this, 20));
        return v;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /** Frosted open choice. */
    private TextView openChip(String s) {
        TextView t = Ui.text(this, s, 15, Ui.WHITE);
        t.setTypeface(Ui.weight(500));
        t.setFontFeatureSettings("tnum");
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        t.setBackground(Ui.outlined(this, Ui.SURF1, 16));
        Ui.press(t);
        return t;
    }

    private void refresh() {
        boolean on = counted && whyOk;
        for (View v : gated) {
            v.setEnabled(on);
            v.animate().alpha(on ? 1f : 0.4f).setDuration(200).start();
        }
        if (hint != null) hint.setText(L.t(this, !whyOk ? "Write why first" : "Or open it for").toUpperCase());
    }

    private void addSwap(LinearLayout root) {
        String swap = swap();
        if (swap == null) return;
        root.addView(Ui.space(this, 16));
        LinearLayout box = Ui.card(this);
        box.setBackground(Ui.outlined(this, Ui.GREEN_BOX, 18));
        box.addView(Ui.overline(this, "Instead, you could", Ui.GREEN));
        box.addView(Ui.space(this, 4));
        box.addView(Ui.left(this, swap, 17, Ui.WHITE));
        root.addView(box, Ui.wide());
    }

    /** Icon of the app being paused (null for browsers we can't name, or if it is gone). */
    private Drawable icon() {
        if (pkg == null) return null;
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private void show(LinearLayout root) {
        ScrollView sv = new ScrollView(this);
        scroll = sv;
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.TRANSPARENT);
        sv.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.MATCH_PARENT));
        Ui.fitInsets(sv);
        setContentView(sv);
    }

    private String question() {
        android.content.SharedPreferences p = getSharedPreferences("limits", MODE_PRIVATE);
        int i = p.getInt("question", 0);
        p.edit().putInt("question", (i + 1) % QUESTIONS.length).apply();
        return QUESTIONS[i % QUESTIONS.length];
    }

    /** One of the user's own alternatives, or null if none set. */
    private String swap() {
        List<String> s = Config.swaps(this);
        return s.isEmpty() ? null : s.get(new Random().nextInt(s.size()));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (breath != null && breath.isRunning()) breath.pause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (breath != null && breath.isPaused()) breath.resume();
    }

    @Override
    protected void onDestroy() {
        clock.removeCallbacksAndMessages(null);
        if (timer != null) timer.cancel();
        if (breath != null) breath.cancel();
        if (guide != null) guide.stop();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    /** Close the pause and move the app off the target screen; Home only if the service is gone. */
    private void goBack() {
        if (cat != null) new Limits(this).addSkip(cat);
        if (GuardService.instance != null && pkg != null) {
            GuardService.instance.leave(pkg, null);
        } else {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }
}
