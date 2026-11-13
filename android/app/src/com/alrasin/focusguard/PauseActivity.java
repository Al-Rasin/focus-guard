package com.alrasin.focusguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Full-screen pause shown before a target: breathe while the countdown runs, then pick how long
 * to open it (spends one open) or go back.
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

    private String cat, pkg;
    private CountDownTimer timer;
    private ValueAnimator breath;
    private boolean counted, whyOk;
    /** Open / extend controls: dimmed and disabled until the countdown (and "why") is done. */
    private final List<View> gated = new ArrayList<>();
    private TextView hint;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        createdAt = SystemClock.elapsedRealtime();
        Ui.onBack(this, this::goBack);
        cat = getIntent().getStringExtra("cat");
        pkg = getIntent().getStringExtra("pkg");
        if (cat == null) {
            finish();
            return;
        }
        Limits limits = new Limits(this);
        Config cfg = limits.config(cat);
        int left = limits.left(cat);
        String blocked = limits.hardBlock(cat);
        int cooldown = limits.cooldownLeftMin(cat);
        String label = Limits.label(this, cat);

        LinearLayout root = Ui.column(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 28);
        root.setPadding(pad, Ui.dp(this, 40), pad, pad);

        Drawable icon = icon();
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            root.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
            root.addView(Ui.space(this, 10));
        }
        TextView title = Ui.text(this, label, 26, Ui.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        root.addView(Ui.space(this, 6));
        root.addView(Ui.text(this, "Opened " + limits.used(cat) + " times today · "
                + (limits.secondsToday(cat) + 59) / 60 + " min", 14, "#6B7280"));

        if (blocked != null || cooldown > 0 || (left == 0 && !limits.canExtend(cat))) {
            // Nothing to open: say why, only "Go back".
            String why = blocked != null ? "Closed: " + blocked
                    : cooldown > 0 ? "Next open in " + cooldown + " min"
                    : "Daily limit reached";
            root.addView(Ui.space(this, 48));
            TextView lock = Ui.text(this, "🔒", 56, Ui.WHITE);
            root.addView(lock);
            root.addView(Ui.space(this, 16));
            root.addView(Ui.text(this, why, 19, Ui.WHITE));
            root.addView(Ui.space(this, 8));
            root.addView(Ui.text(this, "Good moment to do something else.", 15, Ui.MUTED));
            addSwap(root);
            root.addView(Ui.space(this, 40));
            Button back = Ui.button(this, "Go back", Ui.SKY, Ui.BG);
            back.setOnClickListener(v -> goBack());
            root.addView(back, Ui.wide());
            show(root);
            return;
        }

        root.addView(Ui.space(this, 20));
        root.addView(Ui.text(this, question(), 17, Ui.MUTED));
        root.addView(Ui.space(this, 20));

        // Breathing circle with the countdown inside.
        int seconds = limits.pauseSeconds(cat);
        int size = Ui.dp(this, 200);
        FrameLayout circle = new FrameLayout(this);
        View ring = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.parseColor("#1A7DD3FC"));
        g.setStroke(Ui.dp(this, 2), Color.parseColor(Ui.SKY));
        ring.setBackground(g);
        circle.addView(ring, new FrameLayout.LayoutParams(size, size));
        TextView count = Ui.text(this, seconds + "", 56, Ui.SKY);
        count.setTypeface(Typeface.DEFAULT_BOLD);
        circle.addView(count, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        root.addView(circle, new LinearLayout.LayoutParams(size, size));
        root.addView(Ui.space(this, 10));
        TextView breathe = Ui.text(this, "Breathe in", 16, Ui.SKY);
        root.addView(breathe);
        breath = ValueAnimator.ofFloat(0.6f, 1f);
        breath.setDuration(4000);
        breath.setRepeatMode(ValueAnimator.REVERSE);
        breath.setRepeatCount(ValueAnimator.INFINITE);
        breath.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            ring.setScaleX(f);
            ring.setScaleY(f);
        });
        final boolean[] in = {true};
        breath.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator a) {
                in[0] = !in[0];
                breathe.setText(L.t(PauseActivity.this, in[0] ? "Breathe in" : "Breathe out"));
            }
        });
        breath.start();

        addSwap(root);
        root.addView(Ui.space(this, 20));

        boolean extending = left == 0;
        root.addView(Ui.text(this, extending
                ? "Daily limit reached. Extensions left today: " + (cfg.extMax - limits.extends_(cat))
                : left + " of " + limits.total(cat) + " opens left today", 15, Ui.MUTED));

        EditText why = null;
        whyOk = extending || !cfg.askWhy;
        if (!whyOk) {
            root.addView(Ui.space(this, 16));
            why = new EditText(this);
            why.setHint(L.t(this, "Why are you opening it?"));
            why.setHintTextColor(Color.parseColor("#6B7280"));
            why.setTextColor(Color.WHITE);
            why.setSingleLine(true);
            why.setBackground(Ui.round(this, Ui.CARD, 10));
            int p = Ui.dp(this, 12);
            why.setPadding(p, p, p, p);
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
            root.addView(why, Ui.wide());
        }
        root.addView(Ui.space(this, 24));

        Button back = Ui.button(this, "I don't want to open " + label, Ui.SKY, Ui.BG);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setOnClickListener(v -> goBack());
        root.addView(back, Ui.wide());
        root.addView(Ui.space(this, 18));

        hint = Ui.text(this, "", 13, "#6B7280");
        root.addView(hint);
        root.addView(Ui.space(this, 8));

        if (extending) {
            Button ext = Ui.button(this, "Extend +" + cfg.extOpens + " opens", Ui.CARD, Ui.WHITE);
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
            root.addView(ext, Ui.wide());
        } else {
            // Session length: a few short choices plus the full length.
            long maxMs = limits.openMs(cat);
            int maxMin = (int) Math.max(1, maxMs / 60000);
            List<Integer> mins = new ArrayList<>();
            for (int m : CHOICES_MIN) if (m < maxMin && mins.size() < 3) mins.add(m);
            mins.add(maxMin);
            LinearLayout chips = Ui.row(this);
            final EditText whyBox = why;
            for (int i = 0; i < mins.size(); i++) {
                int m = mins.get(i);
                Button chip = Ui.small(this, m + " min");
                chip.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
                chip.setOnClickListener(v -> {
                    if (limits.hardBlock(cat) != null || limits.left(cat) == 0) {
                        goBack();
                        return;
                    }
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
            root.addView(chips, Ui.wide());
        }
        refresh();
        show(root);

        timer = new CountDownTimer(seconds * 1000L, 250) {
            @Override
            public void onTick(long ms) {
                count.setText(String.valueOf((ms + 999) / 1000));
            }

            @Override
            public void onFinish() {
                count.setText("✓");
                counted = true;
                refresh();
            }
        }.start();
    }

    private void refresh() {
        boolean on = counted && whyOk;
        for (View v : gated) {
            v.setEnabled(on);
            v.setAlpha(on ? 1f : 0.35f);
        }
        if (hint != null) {
            hint.setText(L.t(this, !counted ? "Wait for the timer to open it"
                    : !whyOk ? "Write why first" : gated.size() == 1 ? "Or" : "Or open it for"));
        }
    }

    private void addSwap(LinearLayout root) {
        String swap = swap();
        if (swap == null) return;
        root.addView(Ui.space(this, 16));
        TextView t = Ui.text(this, "Or instead: " + swap, 16, Ui.GREEN);
        t.setBackground(Ui.round(this, "#14301F", 12));
        int p = Ui.dp(this, 12);
        t.setPadding(p, p, p, p);
        root.addView(t, Ui.wide());
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
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.parseColor(Ui.BG));
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
    protected void onDestroy() {
        if (timer != null) timer.cancel();
        if (breath != null) breath.cancel();
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
