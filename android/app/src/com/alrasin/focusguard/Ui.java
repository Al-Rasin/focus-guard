package com.alrasin.focusguard;

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
    static final String BG = "#0E1116", SKY = "#7DD3FC", MUTED = "#AEB6C2", CARD = "#1F2937",
            WHITE = "#FFFFFF", RED = "#F87171", GREEN = "#86EFAC";

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
        b.setBackground(round(c, bg, 14));
        b.setPadding(dp(c, 12), dp(c, 14), dp(c, 12), dp(c, 14));
        return b;
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
