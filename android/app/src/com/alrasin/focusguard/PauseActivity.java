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
        int pad = Ui.dp(this, 24);
        root.setPadding(pad, Ui.dp(this, 32), pad, Ui.dp(this, 24));

        // Header: app icon + name, then where you stand today.
        LinearLayout head = Ui.row(this);
        head.setGravity(Gravity.CENTER);
        Drawable icon = icon();
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            head.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
            head.addView(Ui.space(this, 0), new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        }
        TextView title = Ui.text(this, label, 18, Ui.WHITE);
        title.setTypeface(Ui.weight(600));
        head.addView(title);
        root.addView(head);
        root.addView(Ui.space(this, 6));
        int used = limits.used(cat), mins = (limits.secondsToday(cat) + 59) / 60;
        root.addView(Ui.text(this, used == 0 ? "First time today"
                : "Opened " + used + (used == 1 ? " time" : " times") + " today · " + mins + " min", 13, Ui.DIM));
        int wins = Stats.weekSkips(this);

        if (blocked != null || cooldown > 0 || (left == 0 && !limits.canExtend(cat))) {
            // Nothing to open: say why, only "Go back".
            String why = blocked != null ? "Closed: " + blocked
                    : cooldown > 0 ? "Next open in " + cooldown + " min"
                    : "You've used all your opens";
            root.addView(flex());
            TextView lock = Ui.text(this, "🔒", 40, Ui.WHITE);
            GradientDrawable lg = new GradientDrawable();
            lg.setShape(GradientDrawable.OVAL);
            lg.setColor(Color.parseColor(Ui.SURF2));
            lg.setStroke(Ui.dp(this, 1), Color.parseColor(Ui.LINE2));
            lock.setBackground(lg);
            root.addView(lock, new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112)));
            root.addView(Ui.space(this, 24));
            TextView h = Ui.text(this, why, 24, Ui.WHITE);
            h.setTypeface(Ui.weight(600));
            root.addView(h);
            root.addView(Ui.space(this, 8));
            root.addView(Ui.text(this, blocked == null && cooldown == 0
                    ? "Back tomorrow. Good moment to do something else." : "Good moment to do something else.", 15, Ui.MUTED));
            if (wins > 0) {
                root.addView(Ui.space(this, 20));
                root.addView(Ui.badge(this, "✓  Went back " + wins + "× this week", Ui.GREEN, Ui.GREEN_BOX), wrap());
            }
            addSwap(root);
            root.addView(flex());
            Button back = Ui.pill(this, "Go back");
            back.setOnClickListener(v -> goBack());
            root.addView(back, Ui.wide());
            show(root);
            if (android.os.Build.VERSION.SDK_INT >= 30)
                root.post(() -> Ui.buzz(root, android.view.HapticFeedbackConstants.REJECT));
            return;
        }

        root.addView(flex());
        TextView q = Ui.text(this, question(), 20, Ui.WHITE);
        q.setTypeface(Ui.weight(500));
        q.setLineSpacing(0, 1.15f);
        root.addView(q);
        root.addView(Ui.space(this, 28));

        // Breathing orb: three soft rings that swell and settle, countdown in the middle.
        int seconds = limits.pauseSeconds(cat);
        int size = Ui.dp(this, 232);
        FrameLayout orb = new FrameLayout(this);
        View r1 = disc("#0B2433", null), r2 = disc("#0F3347", null), r3 = disc("#13415B", Ui.SKY);
        orb.addView(r1, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        orb.addView(r2, new FrameLayout.LayoutParams(size * 3 / 4, size * 3 / 4, Gravity.CENTER));
        orb.addView(r3, new FrameLayout.LayoutParams(size / 2, size / 2, Gravity.CENTER));
        TextView count = Ui.number(this, seconds + "", 48, Ui.SKY_SOFT);
        orb.addView(count, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        root.addView(orb, new LinearLayout.LayoutParams(size, size));
        root.addView(Ui.space(this, 16));
        TextView breathe = Ui.text(this, "Breathe in", 15, Ui.SKY);
        breathe.setLetterSpacing(0.04f);
        root.addView(breathe);
        int bgFrom = Color.parseColor(Ui.BG), bgTo = Color.parseColor("#0C1A24");
        android.animation.ArgbEvaluator argb = new android.animation.ArgbEvaluator();
        breath = ValueAnimator.ofFloat(0f, 1f);
        breath.setDuration(4000);
        breath.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        breath.setRepeatMode(ValueAnimator.REVERSE);
        breath.setRepeatCount(ValueAnimator.INFINITE);
        breath.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            scale(r1, 0.62f + 0.38f * f);
            scale(r2, 0.72f + 0.28f * f);
            scale(r3, 0.86f + 0.14f * f);
            r1.setAlpha(0.5f + 0.5f * f);
            if (scroll != null) scroll.setBackgroundColor((int) argb.evaluate(f, bgFrom, bgTo));
        });
        final boolean[] in = {true};
        breath.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator an) {
                in[0] = !in[0];
                breathe.setText(L.t(PauseActivity.this, in[0] ? "Breathe in" : "Breathe out"));
            }
        });
        breath.start();

        if (wins > 0) {
            root.addView(Ui.space(this, 24));
            root.addView(Ui.badge(this, "✓  You went back " + wins + "× this week", Ui.GREEN, Ui.GREEN_BOX), wrap());
        }
        addSwap(root);
        root.addView(flex());

        boolean extending = left == 0;
        root.addView(Ui.text(this, extending
                ? "Daily limit reached · " + (cfg.extMax - limits.extends_(cat)) + " extensions left"
                : left + " of " + limits.total(cat) + " opens left today", 13, Ui.DIM));
        root.addView(Ui.space(this, 12));

        EditText why = null;
        whyOk = extending || !cfg.askWhy;
        if (!whyOk) {
            why = new EditText(this);
            why.setHint(L.t(this, "Why are you opening it?"));
            why.setHintTextColor(Color.parseColor(Ui.DIM));
            why.setTextColor(Color.parseColor(Ui.WHITE));
            why.setSingleLine(true);
            why.setBackground(Ui.outlined(this, Ui.SURF1, 14));
            int p = Ui.dp(this, 14);
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
            root.addView(Ui.space(this, 12));
        }

        Button back = Ui.pill(this, "I don't want to open " + label);
        back.setOnClickListener(v -> goBack());
        root.addView(back, Ui.wide());
        root.addView(Ui.space(this, 16));

        hint = Ui.text(this, "", 12, Ui.DIM);
        root.addView(hint);
        root.addView(Ui.space(this, 8));

        if (extending) {
            TextView ext = openChip("Extend +" + cfg.extOpens + " opens");
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
            root.addView(chips, Ui.wide());
        }
        refresh();
        show(root);
        Ui.enter(root);

        long total = seconds * 1000L;
        timer = new CountDownTimer(total, 50) {
            @Override
            public void onTick(long ms) {
                count.setText(String.valueOf((ms + 999) / 1000));
                int level = (int) (10000 * (total - ms) / Math.max(1, total));
                for (View v : gated) fill(v, level);
            }

            @Override
            public void onFinish() {
                count.setText("✓");
                for (View v : gated) fill(v, 10000);
                counted = true;
                refresh();
            }
        }.start();
    }

    private ScrollView scroll;

    private static void scale(View v, float f) {
        v.setScaleX(f);
        v.setScaleY(f);
    }

    private View disc(String fill, String stroke) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.parseColor(fill));
        if (stroke != null) g.setStroke(Ui.dp(this, 2), Color.parseColor(stroke));
        v.setBackground(g);
        return v;
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

    /** Outlined open choice; a soft sky fill grows behind it while the countdown runs. */
    private TextView openChip(String s) {
        TextView t = Ui.text(this, s, 15, Ui.WHITE);
        t.setTypeface(Ui.weight(500));
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(
                Ui.round(this, Ui.SKY_BOX, 14), Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        t.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{
                Ui.round(this, Ui.SURF1, 14), clip, stroke()}));
        Ui.press(t);
        return t;
    }

    private Drawable stroke() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.dp(this, 14));
        g.setStroke(Ui.dp(this, 1), Color.parseColor(Ui.LINE2));
        return g;
    }

    private static void fill(View v, int level) {
        if (v.getBackground() instanceof android.graphics.drawable.LayerDrawable)
            ((android.graphics.drawable.LayerDrawable) v.getBackground()).getDrawable(1).setLevel(level);
    }

    private void refresh() {
        boolean on = counted && whyOk;
        for (View v : gated) {
            v.setEnabled(on);
            v.setAlpha(on ? 1f : 0.55f);
            if (v instanceof TextView) ((TextView) v).setTextColor(Color.parseColor(on ? Ui.SKY_SOFT : Ui.DIM));
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
        LinearLayout box = Ui.card(this);
        box.setBackground(Ui.round(this, Ui.GREEN_BOX, 16));
        box.addView(Ui.overline(this, "Instead, you could", Ui.GREEN));
        box.addView(Ui.space(this, 4));
        box.addView(Ui.left(this, swap, 16, Ui.WHITE));
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
