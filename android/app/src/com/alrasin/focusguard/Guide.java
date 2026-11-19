package com.alrasin.focusguard;

import android.animation.TimeAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Pause-screen guide: an orb whose motion matches the word under it. Breathe in = grows,
 * Hold = still with a soft glow, Breathe out = shrinks, Look far away = rings ripple outward
 * while the orb dims, Blink = the orb closes like an eye, Drop your shoulders = it sinks and settles.
 * A thin ring around it fills as the wait runs out; the seconds left sit in the middle.
 */
final class Guide extends View {
    static final int IN = 0, HOLD = 1, OUT = 2, LOOK = 3, BLINK = 4, RELAX = 5;
    /** Settings: style 0 breathe, 1 look away, 2 mix; pace 0 calm 4-4, 1 box 4-4-4, 2 deep 4-7-8. */
    static final String[] STYLES = {"Breathe", "Look away", "Mix"}, PACES = {"Calm", "Box", "Deep"};
    static final String[] PACE_INFO = {"In 4 s, out 4 s", "In 4, hold 4, out 4, hold 4", "In 4, hold 7, out 8"};

    static final class Phase {
        final int kind, ms;
        final String word, sub;

        Phase(int kind, int s, String word, String sub) {
            this.kind = kind;
            this.ms = s * 1000;
            this.word = word;
            this.sub = sub;
        }
    }

    interface Listener {
        void phase(Phase p, int index);
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE);
    }

    static int style(Context c) {
        return prefs(c).getInt("guide", 0);
    }

    static int pace(Context c) {
        return prefs(c).getInt("pace", 0);
    }

    static boolean question(Context c) {
        return prefs(c).getBoolean("guide_q", true);
    }

    static boolean haptic(Context c) {
        return prefs(c).getBoolean("guide_buzz", true);
    }

    static void set(Context c, String key, int v) {
        prefs(c).edit().putInt(key, v).apply();
    }

    static void set(Context c, String key, boolean v) {
        prefs(c).edit().putBoolean(key, v).apply();
    }

    /** The phase loop for the saved style and pace. */
    static List<Phase> sequence(Context c) {
        List<Phase> breath = new ArrayList<>();
        int pace = pace(c);
        breath.add(new Phase(IN, 4, "Breathe in", "slowly, through your nose"));
        if (pace == 1) breath.add(new Phase(HOLD, 4, "Hold", "stay still"));
        if (pace == 2) breath.add(new Phase(HOLD, 7, "Hold", "stay still"));
        breath.add(new Phase(OUT, pace == 2 ? 8 : 4, "Breathe out", "let it all go"));
        if (pace == 1) breath.add(new Phase(HOLD, 4, "Hold", "empty and calm"));
        Phase look = new Phase(LOOK, 6, "Look outside", "find something far away"),
                blink = new Phase(BLINK, 3, "Blink slowly", "rest your eyes"),
                relax = new Phase(RELAX, 4, "Drop your shoulders", "unclench your jaw");
        List<Phase> out = new ArrayList<>();
        int style = style(c);
        if (style == 1) {
            out.add(look);
            out.add(blink);
        } else if (style == 2) {
            out.addAll(breath);
            out.add(look);
            out.addAll(breath);
            out.add(relax);
        } else {
            out.addAll(breath);
        }
        return out;
    }

    /** Calm loop for the closed screen: slow breathing, no words needed. */
    static List<Phase> rest() {
        List<Phase> l = new ArrayList<>();
        l.add(new Phase(IN, 5, "", ""));
        l.add(new Phase(OUT, 6, "", ""));
        return l;
    }

    // Unit shaders (centre 0,0, radius 1) built once and placed with a matrix each frame,
    // so the 60 fps loop allocates nothing.
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG), base = new Paint(Paint.ANTI_ALIAS_FLAG),
            gloss = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG),
            ripple = new Paint(Paint.ANTI_ALIAS_FLAG), track = new Paint(Paint.ANTI_ALIAS_FLAG),
            arc = new Paint(Paint.ANTI_ALIAS_FLAG), head = new Paint(Paint.ANTI_ALIAS_FLAG),
            headGlow = new Paint(Paint.ANTI_ALIAS_FLAG), num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] blobs = new Paint[3];
    private final Shader[] blobShaders = new Shader[3];
    private final Shader glowShader, glossShader, headShader;
    private final android.graphics.Matrix m = new android.graphics.Matrix();
    private final android.graphics.Path clip = new android.graphics.Path();
    private final RectF box = new RectF();
    private final int sky, skySoft;
    private final List<Phase> seq;
    private TimeAnimator clock;
    private Listener listener;
    private int index = -1;
    private long phaseStart, time;
    /** Orb size 0..1 carried across phases so nothing jumps. */
    private float b, bFrom;
    private float progress;
    private String count = "";
    private boolean showRing = true;

    Guide(Context c, List<Phase> seq) {
        super(c);
        this.seq = seq;
        float d = c.getResources().getDisplayMetrics().density;
        sky = Color.parseColor("#0A84FF");
        skySoft = Color.parseColor("#64D2FF");
        int[] blobColors = {sky, Color.parseColor("#5E5CE6"), Color.parseColor("#40C8E0")};
        for (int i = 0; i < 3; i++) {
            int col = blobColors[i];
            blobShaders[i] = new RadialGradient(0, 0, 1, new int[]{col, col & 0x99FFFFFF, col & 0x00FFFFFF},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            blobs[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            blobs[i].setShader(blobShaders[i]);
        }
        glowShader = new RadialGradient(0, 0, 1, new int[]{0x8C2F7BFF, 0x332F7BFF, 0x002F7BFF},
                new float[]{0.45f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        glow.setShader(glowShader);
        glossShader = new RadialGradient(0, 0, 1, new int[]{0x8CFFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP);
        gloss.setShader(glossShader);
        headShader = new RadialGradient(0, 0, 1, new int[]{0xCC64D2FF, 0x0064D2FF}, null, Shader.TileMode.CLAMP);
        headGlow.setShader(headShader);
        base.setColor(Color.parseColor("#0B1440"));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(d);
        rim.setColor(Color.WHITE);
        ripple.setStyle(Paint.Style.STROKE);
        ripple.setStrokeWidth(d * 1.2f);
        ripple.setColor(skySoft);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(d * 1.5f);
        track.setColor(0x1FFFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(d * 3f);
        arc.setStrokeCap(Paint.Cap.ROUND);
        head.setColor(Color.WHITE);
        num.setTextAlign(Paint.Align.CENTER);
        num.setTypeface(Ui.weight(200));
        num.setFontFeatureSettings("tnum");
        num.setColor(Color.WHITE);
        num.setLetterSpacing(-0.02f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Show or hide the wait ring (the closed screen has none). */
    void ring(boolean on) {
        showRing = on;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // Sky to violet and back round the ring; rotated so the seam sits under the 12 o'clock start.
        android.graphics.SweepGradient sg = new android.graphics.SweepGradient(w / 2f, h / 2f,
                new int[]{skySoft, sky, Color.parseColor("#5E5CE6"), Color.parseColor("#BF5AF2"), skySoft},
                new float[]{0f, 0.3f, 0.6f, 0.85f, 1f});
        android.graphics.Matrix r = new android.graphics.Matrix();
        r.setRotate(-90, w / 2f, h / 2f);
        sg.setLocalMatrix(r);
        arc.setShader(sg);
    }

    void listen(Listener l) {
        listener = l;
    }

    /** Wait progress 0..1 and the centre text (seconds left, or ✓). */
    void wait(float f, String text) {
        progress = f;
        count = text;
    }

    void start() {
        if (clock != null || seq.isEmpty()) return;
        clock = new TimeAnimator();
        clock.setTimeListener((a, total, delta) -> tick(total));
        clock.start();
    }

    void stop() {
        if (clock != null) clock.cancel();
        clock = null;
    }

    private static float ease(float t) {
        return (float) (0.5 - 0.5 * Math.cos(Math.PI * Math.max(0, Math.min(1, t))));
    }

    private float t, eye = 1, dim = 1, drop, shimmer;

    private void tick(long now) {
        time = now;
        if (index < 0 || now - phaseStart >= seq.get(index).ms) {
            phaseStart = index < 0 ? now : phaseStart + seq.get(index).ms;
            if (now - phaseStart > 2000) phaseStart = now; // paused for a while: don't race through phases
            index = (index + 1) % seq.size();
            bFrom = b;
            if (listener != null) listener.phase(seq.get(index), index);
        }
        Phase p = seq.get(index);
        t = (now - phaseStart) / (float) p.ms;
        float e = ease(t);
        float settle = ease(Math.min(1, t * 3)); // quick return to normal at a phase start
        switch (p.kind) {
            case IN:
                b = bFrom + (1 - bFrom) * e;
                break;
            case OUT:
                b = bFrom * (1 - e);
                break;
            case LOOK:
            case BLINK:
            case RELAX:
                b = bFrom + (0.35f - bFrom) * settle;
                break;
            default: // HOLD keeps the size
                break;
        }
        shimmer = p.kind == HOLD ? (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * t * Math.max(1, p.ms / 2000))) : 0;
        dim += ((p.kind == LOOK ? 0.45f : 1f) - dim) * 0.06f;
        // Two slow blinks: the orb closes top-to-bottom like an eyelid.
        if (p.kind == BLINK) {
            float k = (t * 2) % 1f;
            eye = k < 0.5f ? 1 - 0.92f * ease(k * 2) : 0.08f + 0.92f * ease((k - 0.5f) * 2);
        } else eye += (1 - eye) * 0.2f;
        // Shoulders drop: the orb sinks, then settles back as the phase ends.
        drop = p.kind == RELAX ? (t < 0.6f ? ease(t / 0.6f) : 1 - ease((t - 0.6f) / 0.4f)) : drop * 0.85f;
        invalidate();
    }

    /** 0..1 orb size right now (for the background wash). */
    float breath() {
        return b;
    }

    private void place(Shader sh, float x, float y, float r) {
        m.setScale(r, r);
        m.postTranslate(x, y);
        sh.setLocalMatrix(m);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float d = getResources().getDisplayMetrics().density;
        float R = s / 2f - d * 6;
        float maxOrb = R * (showRing ? 0.74f : 0.86f), r = maxOrb * (0.55f + 0.45f * b);
        float oy = cy + drop * R * 0.12f;
        float sec = time / 1000f;

        // Wait ring: hairline track, gradient arc with a glowing head.
        if (showRing) {
            box.set(cx - R, cy - R, cx + R, cy + R);
            c.drawArc(box, 0, 360, false, track);
            if (progress > 0.001f) {
                c.drawArc(box, -90, Math.max(0.5f, 360 * progress), false, arc);
                double a = Math.toRadians(-90 + 360 * progress);
                float hx = cx + R * (float) Math.cos(a), hy = cy + R * (float) Math.sin(a);
                place(headShader, hx, hy, d * 12);
                c.drawCircle(hx, hy, d * 12, headGlow);
                c.drawCircle(hx, hy, d * 2.6f, head);
            }
        }

        // Look far away: rings drift out toward the edge and fade, like a horizon opening up.
        Phase p = index >= 0 ? seq.get(index) : null;
        if (p != null && p.kind == LOOK) {
            for (int i = 0; i < 3; i++) {
                float k = (t * 2f + i / 3f) % 1f;
                float rr = r + (R * 0.98f - r) * k;
                ripple.setAlpha((int) (150 * (1 - k) * Math.min(1, t * 4)));
                c.drawCircle(cx, oy, rr, ripple);
            }
        }

        // Halo, brighter while holding.
        float halo = r * (1.45f + 0.1f * shimmer);
        place(glowShader, cx, oy, halo);
        glow.setAlpha((int) (255 * dim * (0.7f + 0.3f * shimmer)));
        c.drawCircle(cx, oy, halo, glow);

        c.save();
        c.scale(1f, eye, cx, oy);
        clip.reset();
        clip.addCircle(cx, oy, r, android.graphics.Path.Direction.CW);
        c.clipPath(clip);
        // Body: deep navy with three colour blobs slowly orbiting inside, like light in liquid glass.
        base.setAlpha((int) (255 * Math.max(0.55f, dim)));
        c.drawCircle(cx, oy, r, base);
        for (int i = 0; i < 3; i++) {
            double ang = sec * (0.35 + 0.12 * i) * (i == 1 ? -1 : 1) + i * 2.1;
            float off = r * (0.32f + 0.08f * (float) Math.sin(sec * 0.7 + i));
            float bx = cx + off * (float) Math.cos(ang), by = oy + off * (float) Math.sin(ang);
            float br = r * (0.95f + 0.1f * (float) Math.sin(sec * 0.9 + i * 1.7));
            place(blobShaders[i], bx, by, br);
            blobs[i].setAlpha((int) (235 * dim));
            c.drawCircle(bx, by, br, blobs[i]);
        }
        // Glossy highlight top-left, then a hairline rim.
        float gx = cx - r * 0.32f, gy = oy - r * 0.42f;
        place(glossShader, gx, gy, r * 0.75f);
        gloss.setAlpha((int) (255 * dim));
        c.drawCircle(gx, gy, r * 0.75f, gloss);
        c.restore();
        c.save();
        c.scale(1f, eye, cx, oy);
        rim.setAlpha((int) (70 * dim));
        c.drawCircle(cx, oy, r - d * 0.5f, rim);
        c.restore();

        if (!count.isEmpty()) {
            num.setTextSize(s * 0.2f);
            Paint.FontMetrics fm = num.getFontMetrics();
            num.setShadowLayer(d * 8, 0, d, 0x66000000);
            c.drawText(count, cx, oy - (fm.ascent + fm.descent) / 2, num);
        }
    }

    private boolean wanted;

    /** Pause the frame clock while the window is hidden (screen off, app in background). */
    @Override
    protected void onWindowVisibilityChanged(int vis) {
        super.onWindowVisibilityChanged(vis);
        if (vis != VISIBLE) {
            wanted = wanted || clock != null;
            stop();
        } else if (wanted) {
            wanted = false;
            start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
