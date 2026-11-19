package com.alrasin.focusguard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Round progress dial: track + round-cap arc that sweeps in, a big number and a label in the middle. */
final class Ring extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG),
            big = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float target, shown;
    private String num = "", label = "";

    Ring(Context c) {
        this(c, 12);
    }

    /** Ring with a given stroke width; with no label the number sits in the middle (small rings). */
    Ring(Context c, int strokeDp) {
        super(c);
        float w = Ui.dp(c, strokeDp);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(Color.parseColor(Ui.SURF3));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Ui.weight(700));
        big.setColor(Color.parseColor(Ui.WHITE));
        big.setFontFeatureSettings("tnum");
        small.setTextAlign(Paint.Align.CENTER);
        small.setColor(Color.parseColor(Ui.MUTED));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** Fill 0..1, arc colour, centre number and label. Animates the sweep from 0. */
    void set(float f, String color, String number, String text, boolean animate) {
        target = Math.max(0f, Math.min(1f, f));
        arc.setColor(Color.parseColor(color));
        num = number;
        label = L.t(getContext(), text);
        setContentDescription(number + " " + label);
        if (!animate) {
            shown = target;
            invalidate();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(0f, target);
        a.setDuration(900);
        a.setStartDelay(150);
        a.setInterpolator(Ui.EASE);
        a.addUpdateListener(v -> {
            shown = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    /** Colour of the centre number (default white). */
    void numColor(String color) {
        big.setColor(Color.parseColor(color));
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()), pad = track.getStrokeWidth() / 2 + 1;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        box.set(cx - s / 2 + pad, cy - s / 2 + pad, cx + s / 2 - pad, cy + s / 2 - pad);
        c.drawArc(box, 0, 360, false, track);
        if (shown > 0.001f) c.drawArc(box, -90, Math.max(1f, 360 * shown), false, arc);
        if (label.isEmpty()) {
            big.setTextSize(s * 0.36f);
            Paint.FontMetrics m = big.getFontMetrics();
            c.drawText(num, cx, cy - (m.ascent + m.descent) / 2, big);
            return;
        }
        big.setTextSize(s * 0.30f);
        small.setTextSize(s * 0.095f);
        c.drawText(num, cx, cy + s * 0.07f, big);
        c.drawText(label, cx, cy + s * 0.22f, small);
    }
}
