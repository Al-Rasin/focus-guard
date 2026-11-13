package com.alrasin.focusguard;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for UI built in code (no layouts). Pause-screen colours. */
final class Ui {
    /**
     * Back key handler. With targetSdk 36, Android 16 no longer calls onBackPressed(),
     * so register a callback there; older versions call onBackPressed(), which should run the same code.
     */
    static void onBack(Activity a, Runnable r) {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, r::run);
    }

    // Design tokens (dark, sky accent). Surfaces step up in lightness; 1 dp outlines instead of shadows.
    static final String BG = "#0E1116", SURF1 = "#151A21", SURF2 = "#1C232C", SURF3 = "#252D38",
            CARD = SURF1, LINE = "#2E3744", LINE2 = "#3D4757",
            SKY = "#7DD3FC", ON_SKY = "#062234", SKY_BOX = "#0F3347", SKY_SOFT = "#BAE6FD",
            GREEN = "#4ADE80", GREEN_BOX = "#0F2E1C", AMBER = "#FBBF24", AMBER_BOX = "#33270A",
            RED = "#F87171", RED_BOX = "#3A1616",
            WHITE = "#E8ECF1", MUTED = "#AEB6C2", DIM = "#7C8594";

    static final android.view.animation.Interpolator EASE =
            new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f),
            EASE_IN = new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    private Ui() {
    }

    static TextView text(Context c, String s, int sp, String color) {
        TextView t = new TextView(c);
        t.setText(L.t(c, s));
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    static TextView left(Context c, String s, int sp, String color) {
        TextView t = text(c, s, sp, color);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return t;
    }

    static Button button(Context c, String s, String bg, String fg) {
        Button b = new Button(c);
        b.setText(L.t(c, s));
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(round(c, bg, 28));
        b.setPadding(dp(c, 20), dp(c, 15), dp(c, 20), dp(c, 15));
        b.setMinHeight(dp(c, 52));
        b.setStateListAnimator(null);
        b.setTypeface(weight(500));
        press(b);
        return b;
    }

    /** Main call-to-action: full-width sky pill with bold dark text. */
    static Button pill(Context c, String s) {
        Button b = button(c, s, SKY, ON_SKY);
        b.setTypeface(weight(600));
        return b;
    }

    /** Rounded chip; selected = sky fill, else outlined. */
    static TextView chip(Context c, String s, boolean on) {
        TextView t = text(c, s, 14, on ? SKY_SOFT : WHITE);
        GradientDrawable g = round(c, on ? SKY_BOX : SURF1, 12);
        g.setStroke(dp(c, 1), Color.parseColor(on ? SKY : LINE));
        t.setBackground(g);
        t.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        t.setTypeface(weight(on ? 600 : 400));
        return t;
    }

    interface IntSink {
        void set(int i);
    }

    /** iOS-style segmented control: equal segments in a dark track, selected one in sky. */
    static LinearLayout segmented(Context c, String[] labels, int selected, IntSink sink) {
        LinearLayout track = row(c);
        track.setBackground(outlined(c, SURF1, 14));
        int p = dp(c, 4);
        track.setPadding(p, p, p, p);
        TextView[] segs = new TextView[labels.length];
        final int[] cur = {selected};
        Runnable paint = () -> {
            for (int i = 0; i < segs.length; i++) {
                boolean on = i == cur[0];
                segs[i].setBackground(on ? round(c, SURF3, 11) : null);
                segs[i].setTextColor(Color.parseColor(on ? WHITE : DIM));
                segs[i].setTypeface(weight(on ? 600 : 400));
            }
        };
        for (int i = 0; i < labels.length; i++) {
            TextView t = text(c, labels[i], 14, MUTED);
            t.setPadding(dp(c, 6), dp(c, 9), dp(c, 6), dp(c, 9));
            final int idx = i;
            t.setOnClickListener(v -> {
                cur[0] = idx;
                buzz(v, android.view.HapticFeedbackConstants.CLOCK_TICK);
                paint.run();
                sink.set(idx);
            });
            segs[i] = t;
            track.addView(t, grow());
        }
        paint.run();
        return track;
    }

    /** Secondary action: outlined pill. */
    static Button ghost(Context c, String s) {
        Button b = button(c, s, BG, WHITE);
        GradientDrawable g = round(c, SURF1, 28);
        g.setStroke(dp(c, 1), Color.parseColor(LINE2));
        b.setBackground(g);
        return b;
    }

    /** Standard surface card: surface fill, 1 dp outline, radius 20, 16 dp padding. */
    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(outlined(c, CARD, 20));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    static Button small(Context c, String s) {
        Button b = button(c, s, CARD, WHITE);
        b.setTextSize(15);
        b.setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    static GradientDrawable round(Context c, String color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.parseColor(color));
        shape.setCornerRadius(dp(c, radiusDp));
        return shape;
    }

    static GradientDrawable outlined(Context c, String color, int radiusDp) {
        GradientDrawable g = round(c, color, radiusDp);
        g.setStroke(dp(c, 1), Color.parseColor(LINE));
        return g;
    }

    /** Typeface of a given weight (API 28+; else bold for 600+). */
    static android.graphics.Typeface weight(int w) {
        if (android.os.Build.VERSION.SDK_INT >= 28)
            return android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, w, false);
        return w >= 600 ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT;
    }

    /** Big number: display size, tabular digits. */
    static TextView number(Context c, String s, int sp, String color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(weight(700));
        t.setFontFeatureSettings("tnum");
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Title text with weight 600, left aligned. */
    static TextView title(Context c, String s, int sp) {
        TextView t = left(c, s, sp, WHITE);
        t.setTypeface(weight(600));
        return t;
    }

    /** Small caps label above a section. */
    static TextView overline(Context c, String s, String color) {
        TextView t = left(c, L.t(c, s).toUpperCase(), 11, color);
        t.setTypeface(weight(600));
        t.setLetterSpacing(0.08f);
        return t;
    }

    /** Soft tinted badge (e.g. status). */
    static TextView badge(Context c, String s, String fg, String bg) {
        TextView t = text(c, s, 12, fg);
        t.setTypeface(weight(600));
        t.setBackground(round(c, bg, 10));
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        return t;
    }

    /** Press feedback: scale to 0.97 while held. */
    static void press(View v) {
        v.setOnTouchListener((view, e) -> {
            int a = e.getActionMasked();
            if (a == android.view.MotionEvent.ACTION_DOWN)
                view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).setInterpolator(EASE).start();
            else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(EASE).start();
            return false;
        });
    }

    /** Haptic tick (HapticFeedbackConstants value). */
    static void buzz(View v, int kind) {
        v.performHapticFeedback(kind);
    }

    /** Fade + rise in the children of a container, 40 ms apart. */
    static void enter(android.view.ViewGroup g) {
        int n = Math.min(g.getChildCount(), 12);
        float rise = dp(g.getContext(), 16);
        for (int i = 0; i < n; i++) {
            View v = g.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(rise);
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 40L).setDuration(350)
                    .setInterpolator(EASE_IN).start();
        }
    }

    static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams grow() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    static View space(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /**
     * Pad a screen's root by the system bars, cutout and keyboard. targetSdk 35+ draws apps
     * edge-to-edge, and adjustResize no longer shrinks the window for the keyboard.
     */
    static void fitInsets(android.view.View v) {
        v.setOnApplyWindowInsetsListener((view, in) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = in.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout()
                        | android.view.WindowInsets.Type.ime());
                view.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                view.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                        in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
            }
            return in;
        });
    }
}
