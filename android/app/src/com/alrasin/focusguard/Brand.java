package com.alrasin.focusguard;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Drawn brand marks for the built-in targets (used when their app isn't installed). */
final class Brand extends Drawable {
    private final String cat;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Typeface BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    private final RectF b = new RectF(), sq = new RectF(), in = new RectF();
    private final Path tri = new Path();
    private Shader grad;
    private float gradS = -1;

    private Brand(String cat) {
        this.cat = cat;
    }

    /** Brand mark for a built-in target, or null. */
    static Drawable of(String cat) {
        switch (cat) {
            case Limits.SHORTS:
            case Limits.REELS:
            case Limits.FACEBOOK:
            case Limits.INSTAGRAM:
            case Limits.TIKTOK:
                return new Brand(cat);
            default:
                return null;
        }
    }

    @Override
    public void draw(Canvas c) {
        b.set(getBounds());
        float s = Math.min(b.width(), b.height());
        float cx = b.centerX(), cy = b.centerY();
        sq.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
        float r = s * 0.24f;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        switch (cat) {
            case Limits.SHORTS: {
                p.setColor(0xFFFF0033);
                c.drawRoundRect(sq, r, r, p);
                tri.rewind();
                tri.moveTo(cx - s * 0.14f, cy - s * 0.2f);
                tri.lineTo(cx + s * 0.22f, cy);
                tri.lineTo(cx - s * 0.14f, cy + s * 0.2f);
                tri.close();
                p.setColor(Color.WHITE);
                c.drawPath(tri, p);
                break;
            }
            case Limits.REELS:
            case Limits.FACEBOOK: {
                p.setColor(0xFF1877F2);
                c.drawCircle(cx, cy, s / 2, p);
                p.setColor(Color.WHITE);
                p.setTypeface(BOLD);
                p.setTextSize(s * 0.78f);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText("f", cx + s * 0.04f, cy + s * 0.3f, p);
                break;
            }
            case Limits.INSTAGRAM: {
                if (grad == null || gradS != sq.left + sq.top * 7 + s * 13) {
                    gradS = sq.left + sq.top * 7 + s * 13;
                    grad = new LinearGradient(sq.left, sq.bottom, sq.right, sq.top,
                            new int[]{0xFFF58529, 0xFFDD2A7B, 0xFF8134AF}, null, Shader.TileMode.CLAMP);
                }
                p.setShader(grad);
                c.drawRoundRect(sq, r, r, p);
                p.setShader(null);
                p.setColor(Color.WHITE);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.08f);
                float i = s * 0.24f;
                in.set(sq.left + i, sq.top + i, sq.right - i, sq.bottom - i);
                c.drawRoundRect(in, s * 0.14f, s * 0.14f, p);
                c.drawCircle(cx, cy, s * 0.12f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(in.right - s * 0.1f, in.top + s * 0.1f, s * 0.035f, p);
                break;
            }
            case Limits.TIKTOK: {
                p.setColor(0xFF111111);
                c.drawRoundRect(sq, r, r, p);
                p.setTypeface(BOLD);
                p.setTextSize(s * 0.62f);
                p.setTextAlign(Paint.Align.CENTER);
                float y = cy + s * 0.22f, o = s * 0.035f;
                p.setColor(0xFF25F4EE);
                c.drawText("♪", cx - o, y - o, p);
                p.setColor(0xFFFE2C55);
                c.drawText("♪", cx + o, y + o, p);
                p.setColor(Color.WHITE);
                c.drawText("♪", cx, y, p);
                break;
            }
        }
    }

    @Override
    public void setAlpha(int a) {
        p.setAlpha(a);
    }

    @Override
    public void setColorFilter(ColorFilter f) {
        p.setColorFilter(f);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
