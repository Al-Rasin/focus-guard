package com.alrasin.focusguard;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * First-run pages: intro → pick what to limit → consent → turn on. Swipe or tap Next; the
 * consent page only moves on with an explicit Agree. Also opened from the Home banner.
 */
final class Welcome {
    static final int INTRO = 0, PICK = 1, CONSENT = 2, TURN_ON = 3, COUNT = 4;

    /** Pick page state, kept while the user swipes back and forth. */
    private static boolean[] picked;
    private static int preset = 1;

    private Welcome() {
    }

    /** Draw page {@code page}; {@code dir} = +1 slid in from the right, -1 from the left, 0 no slide. */
    static void show(MainActivity a, int page, int dir) {
        if (picked == null) {
            picked = new boolean[Limits.BUILTIN.length];
            Limits limits = new Limits(a);
            for (int i = 0; i < picked.length; i++) picked[i] = limits.config(Limits.BUILTIN[i]).enabled;
        }
        LinearLayout frame = Ui.column(a);
        frame.setBackgroundColor(Color.TRANSPARENT);

        // Top: dots + Skip.
        LinearLayout top = Ui.row(a);
        int hp = Ui.dp(a, 20);
        top.setPadding(hp, Ui.dp(a, 14), hp, Ui.dp(a, 6));
        LinearLayout dots = Ui.row(a);
        for (int i = 0; i < COUNT; i++) {
            View d = new View(a);
            d.setBackground(Ui.round(a, i == page ? Ui.SKY : i < page ? Ui.SKY_BOX : Ui.SURF3, 4));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Ui.dp(a, i == page ? 22 : 8), Ui.dp(a, 8));
            l.rightMargin = Ui.dp(a, 6);
            dots.addView(d, l);
        }
        top.addView(dots, Ui.grow());
        if (page == INTRO || page == TURN_ON) {
            TextView skip = Ui.text(a, page == INTRO ? "Skip" : "Later", 15, Ui.MUTED);
            skip.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), 0, Ui.dp(a, 8));
            skip.setOnClickListener(v -> {
                if (page == INTRO) a.welcome(CONSENT);
                else finish(a);
            });
            top.addView(skip);
        }
        frame.addView(top, Ui.wide());

        LinearLayout body = Ui.column(a);
        body.setPadding(hp, Ui.dp(a, 8), hp, Ui.dp(a, 16));
        LinearLayout foot = Ui.column(a);
        foot.setPadding(hp, Ui.dp(a, 8), hp, Ui.dp(a, 16));
        switch (page) {
            case INTRO: intro(a, body, foot); break;
            case PICK: pick(a, body, foot); break;
            case CONSENT: consent(a, body, foot); break;
            default: turnOn(a, body, foot); break;
        }

        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        sv.addView(body);
        frame.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        frame.addView(foot, Ui.wide());

        // Swipe: back always; forward only past the pages that need no answer.
        GestureDetector g = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || Math.abs(vx) < Math.abs(vy) * 1.5f || Math.abs(vx) < 600) return false;
                if (vx > 0 && page > INTRO && page != TURN_ON) a.welcome(page - 1, -1);
                else if (vx < 0 && page == INTRO) a.welcome(PICK, 1);
                else return false;
                return true;
            }
        });
        sv.setOnTouchListener((v, e) -> {
            g.onTouchEvent(e);
            return false;
        });

        Ui.fitInsets(frame);
        a.setContentView(frame);
        if (dir != 0) {
            float w = a.getResources().getDisplayMetrics().widthPixels * 0.35f;
            body.setTranslationX(dir * w);
            body.setAlpha(0f);
            body.animate().translationX(0f).alpha(1f).setDuration(320).setInterpolator(Ui.EASE).start();
        } else Ui.enter(body);
    }

    private static void finish(MainActivity a) {
        Setup.setWelcomed(a);
        picked = null;
        a.leaveWelcome();
    }

    // ---- pages ----------------------------------------------------------------------------

    private static void intro(MainActivity a, LinearLayout body, LinearLayout foot) {
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(Ui.space(a, 28));
        FrameLayout logo = new FrameLayout(a);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(a.getDrawable(R.drawable.ic_logo));
        logo.addView(iv, new FrameLayout.LayoutParams(Ui.dp(a, 150), Ui.dp(a, 150), Gravity.CENTER));
        body.addView(logo, new LinearLayout.LayoutParams(Ui.dp(a, 150), Ui.dp(a, 150)));
        // Slow breathing, like the pause screen.
        logo.setScaleX(0.94f);
        logo.setScaleY(0.94f);
        android.animation.ObjectAnimator br = android.animation.ObjectAnimator.ofPropertyValuesHolder(logo,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.94f, 1.04f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.94f, 1.04f));
        br.setDuration(2400);
        br.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        br.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        br.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        br.start();

        body.addView(Ui.space(a, 24));
        TextView h = Ui.text(a, "Scroll less.\nLive more.", 32, Ui.WHITE);
        h.setTypeface(Ui.weight(700));
        h.setLineSpacing(0, 1.05f);
        body.addView(h);
        body.addView(Ui.space(a, 10));
        body.addView(Ui.text(a, "A short pause before Shorts and Reels gives you the moment to choose.",
                15, Ui.MUTED));
        body.addView(Ui.space(a, 28));
        feature(a, body, R.drawable.ic_pause, null, "A pause, not a wall", "Breathe for a few seconds, then decide.", Ui.SKY, Ui.SKY_BOX);
        feature(a, body, 0, "6", "A few opens a day", "Each open is a short timed session.", Ui.GREEN, Ui.GREEN_BOX);
        feature(a, body, R.drawable.ic_lock, null, "Private by design", "No internet access. Nothing leaves your phone.", Ui.AMBER, Ui.AMBER_BOX);

        Button go = Ui.pill(a, "Get started");
        go.setOnClickListener(v -> a.welcome(PICK, 1));
        foot.addView(go, Ui.wide());
    }

    private static void feature(MainActivity a, LinearLayout body, int res, String icon, String head, String sub,
                                String fg, String bg) {
        LinearLayout r = Ui.row(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Ui.outlined(a, Ui.SURF1, 18));
        int p = Ui.dp(a, 14);
        r.setPadding(p, p, p, p);
        View i;
        if (res != 0) {
            ImageView iv = new ImageView(a);
            iv.setImageResource(res);
            iv.setColorFilter(Color.parseColor(fg));
            int ip = Ui.dp(a, 11);
            iv.setPadding(ip, ip, ip, ip);
            i = iv;
        } else {
            TextView t = Ui.text(a, icon, 18, fg);
            t.setTypeface(Ui.weight(700));
            i = t;
        }
        i.setBackground(Ui.round(a, bg, 12));
        r.addView(i, new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42)));
        LinearLayout w = Ui.column(a);
        w.setPadding(Ui.dp(a, 14), 0, 0, 0);
        w.addView(Ui.title(a, head, 16));
        w.addView(Ui.left(a, sub, 13, Ui.MUTED));
        r.addView(w, Ui.grow());
        LinearLayout.LayoutParams lp = Ui.wide();
        lp.bottomMargin = Ui.dp(a, 10);
        body.addView(r, lp);
    }

    private static void heading(MainActivity a, LinearLayout body, String over, String head, String sub) {
        body.addView(Ui.space(a, 12));
        body.addView(Ui.overline(a, over, Ui.SKY));
        body.addView(Ui.space(a, 8));
        TextView h = Ui.title(a, head, 26);
        h.setTypeface(Ui.weight(700));
        body.addView(h);
        body.addView(Ui.space(a, 6));
        body.addView(Ui.left(a, sub, 15, Ui.MUTED));
        body.addView(Ui.space(a, 20));
    }

    private static void pick(MainActivity a, LinearLayout body, LinearLayout foot) {
        heading(a, body, "Step 1 of 3", "What pulls you in?", "Pick what to put a pause in front of. "
                + "You can add any other app later.");
        Button next = Ui.pill(a, "");
        Runnable label = () -> {
            int n = 0;
            for (boolean b : picked) if (b) n++;
            next.setText(L.t(a, n == 0 ? "Skip for now" : "Continue with " + n));
        };
        for (int i = 0; i < Limits.BUILTIN.length; i++) {
            final int idx = i;
            String cat = Limits.BUILTIN[i];
            LinearLayout r = Ui.row(a);
            int p = Ui.dp(a, 14);
            r.setPadding(p, Ui.dp(a, 12), p, Ui.dp(a, 12));
            ImageView ic = new ImageView(a);
            ic.setImageDrawable(Brand.of(cat));
            r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 38), Ui.dp(a, 38)));
            TextView name = Ui.title(a, Limits.label(a, cat), 16);
            name.setPadding(Ui.dp(a, 14), 0, 0, 0);
            r.addView(name, Ui.grow());
            ImageView tick = new ImageView(a);
            tick.setImageDrawable(a.getDrawable(R.drawable.ic_check));
            int tp = Ui.dp(a, 4);
            tick.setPadding(tp, tp, tp, tp);
            r.addView(tick, new LinearLayout.LayoutParams(Ui.dp(a, 26), Ui.dp(a, 26)));
            Runnable paint = () -> {
                boolean on = picked[idx];
                GradientDrawable g = Ui.round(a, on ? Ui.SKY_BOX : Ui.SURF1, 18);
                g.setStroke(Ui.dp(a, on ? 2 : 1), Color.parseColor(on ? Ui.SKY : Ui.LINE));
                r.setBackground(g);
                GradientDrawable t = Ui.round(a, on ? Ui.SKY : Ui.SURF3, 13);
                if (!on) t.setStroke(Ui.dp(a, 2), Color.parseColor(Ui.LINE2));
                tick.setBackground(t);
                tick.setImageAlpha(on ? 255 : 0);
                tick.setColorFilter(Color.parseColor(Ui.ON_SKY));
                r.setContentDescription(Limits.label(a, cat) + (on ? ", on" : ", off"));
            };
            paint.run();
            r.setOnClickListener(v -> {
                picked[idx] = !picked[idx];
                Ui.buzz(v, HapticFeedbackConstants.CLOCK_TICK);
                paint.run();
                label.run();
            });
            Ui.press(r);
            LinearLayout.LayoutParams lp = Ui.wide();
            lp.bottomMargin = Ui.dp(a, 10);
            body.addView(r, lp);
        }

        body.addView(Ui.space(a, 14));
        body.addView(Ui.overline(a, "How strict?", Ui.MUTED));
        body.addView(Ui.space(a, 10));
        TextView desc = Ui.left(a, "", 13, Ui.MUTED);
        Runnable say = () -> {
            int[] p = MainActivity.PRESETS[preset];
            desc.setText(L.t(a, p[0] + " s pause · " + p[2] + " opens a day · " + p[3] + " min each"));
        };
        body.addView(Ui.segmented(a, new String[]{"Gentle", "Balanced", "Strict"}, preset, i -> {
            preset = i;
            say.run();
        }), Ui.wide());
        body.addView(Ui.space(a, 8));
        say.run();
        body.addView(desc);

        label.run();
        next.setOnClickListener(v -> {
            for (int i = 0; i < Limits.BUILTIN.length; i++) {
                Config c = Config.load(a, Limits.BUILTIN[i]);
                if (picked[i]) MainActivity.preset(c, preset);
                c.enabled = picked[i];
                Strict.save(a, c);
            }
            a.welcome(CONSENT, 1);
        });
        foot.addView(next, Ui.wide());
    }

    private static void consent(MainActivity a, LinearLayout body, LinearLayout foot) {
        heading(a, body, "Step 2 of 3", "How it works", "Please read this before you agree.");
        String[][] parts = Setup.consentParts();
        for (String[] part : parts) {
            LinearLayout c = Ui.card(a);
            if (!part[0].isEmpty()) {
                c.addView(Ui.title(a, part[0], 15));
                c.addView(Ui.space(a, 4));
            }
            TextView b = Ui.left(a, part[1], 14, part[0].isEmpty() ? Ui.WHITE : Ui.MUTED);
            b.setLineSpacing(0, 1.15f);
            c.addView(b);
            LinearLayout.LayoutParams lp = Ui.wide();
            lp.bottomMargin = Ui.dp(a, 10);
            body.addView(c, lp);
        }
        Button yes = Ui.pill(a, "Agree and continue");
        yes.setOnClickListener(v -> {
            Setup.agree(a, true);
            Ui.buzz(v, HapticFeedbackConstants.CONFIRM);
            a.welcome(TURN_ON, 1);
        });
        foot.addView(yes, Ui.wide());
        TextView no = Ui.text(a, "No thanks", 15, Ui.MUTED);
        no.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 4));
        no.setOnClickListener(v -> {
            Setup.agree(a, false);
            finish(a);
        });
        foot.addView(no, Ui.wide());
    }

    private static void turnOn(MainActivity a, LinearLayout body, LinearLayout foot) {
        int[] n = Setup.steps(a, null);
        boolean all = n[0] >= n[1];
        heading(a, body, "Step 3 of 3", all ? "You're all set" : "Turn it on",
                all ? "Protection is running. You can change limits any time."
                        : "A few switches so Android keeps it running. Come back here after each one.");
        LinearLayout prog = Ui.row(a);
        prog.addView(Ui.left(a, n[0] + " of " + n[1] + " done", 14, all ? Ui.GREEN : Ui.SKY), Ui.grow());
        body.addView(prog, Ui.wide());
        body.addView(Ui.space(a, 8));
        LinearLayout track = Ui.row(a);
        track.setBackground(Ui.round(a, Ui.SURF3, 3));
        View fill = new View(a);
        fill.setBackground(Ui.round(a, all ? Ui.GREEN : Ui.SKY, 3));
        float f = n[1] == 0 ? 1f : n[0] / (float) n[1];
        track.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, f));
        track.addView(new View(a), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - f));
        body.addView(track, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, 6)));
        body.addView(Ui.space(a, 16));
        LinearLayout box = Ui.card(a);
        Setup.steps(a, box);
        body.addView(box, Ui.wide());

        Button done = all ? Ui.pill(a, "Start using it") : Ui.ghost(a, "Do this later");
        done.setOnClickListener(v -> {
            if (all) Ui.buzz(v, HapticFeedbackConstants.CONFIRM);
            finish(a);
        });
        foot.addView(done, Ui.wide());
    }
}
