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

    // Design tokens (dark, sky accent). Set by theme(): "glass" (default) = translucent frosted surfaces
    // with white hairlines over a soft colour backdrop; "classic" = solid surfaces stepping up in lightness.
    static String BG, SURF1, SURF2, SURF3, CARD, LINE, LINE2, NAV, INSET, HERO, HERO_LINE,
            SKY, ON_SKY, SKY_BOX, SKY_SOFT, GREEN, GREEN_BOX, AMBER, AMBER_BOX, RED, RED_BOX, SEP, SWITCH_OFF,
            WHITE, MUTED, DIM, ORB1, ORB2, ORB3;
    static boolean glass;
    /** Glass look settings (prefs "ui"): blur 0..100, see-through 0..100, background palette index. */
    static int blur = 55, clear = 50, palette;
    static final String[] PALETTES = {"Ocean", "Sunset", "Forest", "Mono"};
    private static String loaded;

    static {
        tokens(true);
    }

    /** Theme name saved in settings: "glass" (default) or "classic". */
    static String themeName(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("theme", "glass");
    }

    static void setTheme(Context c, String name) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("theme", name).apply();
        loaded = null;
        theme(c);
    }

    /** Save one glass look setting ("blur", "clear", "palette") and reload the tokens. */
    static void setLook(Context c, String key, int v) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putInt(key, v).apply();
        loaded = null;
        theme(c);
    }

    /** Reload tokens after prefs "ui" were written elsewhere (backup import). */
    static void resetLoaded(Context c) {
        loaded = null;
        theme(c);
    }

    static void resetLook(Context c) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().remove("blur").remove("clear")
                .remove("palette").apply();
        loaded = null;
        theme(c);
    }

    /** Load the saved theme's tokens. Call in every activity's onCreate before building views. */
    static void theme(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("ui", Context.MODE_PRIVATE);
        String name = themeName(c);
        blur = Math.max(0, Math.min(100, p.getInt("blur", 55)));
        clear = Math.max(0, Math.min(100, p.getInt("clear", 50)));
        palette = Math.max(0, Math.min(PALETTES.length - 1, p.getInt("palette", 0)));
        String key = name + "/" + clear;
        if (key.equals(loaded)) return;
        loaded = key;
        tokens(!"classic".equals(name));
    }

    private static String a(int alpha, String rgb) {
        return String.format("#%02X%s", Math.max(0, Math.min(255, alpha)), rgb);
    }

    private static void tokens(boolean g) {
        glass = g;
        if (g) {
            // f = how solid the glass is (see-through setting reversed).
            float f = 1f - clear / 100f;
            int s = Math.round(6 + f * 40);
            // iOS dark system colours over glass.
            BG = "#0A0E1F"; SURF1 = a(s, "FFFFFF"); SURF2 = a(s + 10, "FFFFFF"); SURF3 = a(s + 28, "FFFFFF");
            LINE = "#24FFFFFF"; LINE2 = "#40FFFFFF"; SEP = "#2EFFFFFF"; NAV = a(Math.round(0x60 + f * 0x80), "16182A");
            INSET = "#40000000";
            SKY = "#0A84FF"; ON_SKY = "#FFFFFF"; SKY_SOFT = "#64D2FF";
            GREEN = "#30D158"; AMBER = "#FF9F0A"; RED = "#FF453A"; SWITCH_OFF = "#4D78788C";
            HERO = a(Math.round(0x22 + f * 0x2A), "0A84FF"); HERO_LINE = "#550A84FF";
            SKY_BOX = "#330A84FF"; GREEN_BOX = "#2E30D158"; AMBER_BOX = "#2EFF9F0A"; RED_BOX = "#33FF453A";
            WHITE = "#FFFFFF"; MUTED = "#99EBEBF5"; DIM = "#6BEBEBF5";
            ORB1 = "#1A0A84FF"; ORB2 = "#260A84FF"; ORB3 = "#400A84FF";
        } else {
            BG = "#0E1116"; SURF1 = "#151A21"; SURF2 = "#1C232C"; SURF3 = "#252D38";
            LINE = "#2E3744"; LINE2 = "#3D4757"; SEP = LINE; NAV = SURF2; INSET = BG;
            SKY = "#7DD3FC"; ON_SKY = "#062234"; SKY_SOFT = "#BAE6FD";
            GREEN = "#4ADE80"; AMBER = "#FBBF24"; RED = "#F87171"; SWITCH_OFF = "#39404C";
            HERO = "#13324A"; HERO_LINE = "#1F3B52";
            SKY_BOX = "#0F3347"; GREEN_BOX = "#0F2E1C"; AMBER_BOX = "#33270A"; RED_BOX = "#3A1616";
            WHITE = "#E8ECF1"; MUTED = "#AEB6C2"; DIM = "#7C8594";
            ORB1 = "#0B2433"; ORB2 = "#0F3347"; ORB3 = "#13415B";
        }
        CARD = SURF1;
    }

    /** Page background: soft colour orbs (glass) or the flat BG (classic). Never scrolls. */
    static void backdrop(Activity a) {
        android.view.Window w = a.getWindow();
        w.setBackgroundDrawable(glass ? new Backdrop(palette)
                : new android.graphics.drawable.ColorDrawable(Color.parseColor(BG)));
        // Clear bars: the window background spans behind them, so the backdrop runs edge to edge.
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        // Frosted surfaces sample the backdrop at their window position: redraw them when anything scrolls.
        View decor = w.getDecorView();
        if (HOOKED.put(decor, Boolean.TRUE) == null) {
            decor.getViewTreeObserver().addOnScrollChangedListener(() -> {
                if (glass && blur > 0) refrost(decor);
            });
        }
    }

    private static final java.util.Map<View, Boolean> HOOKED = new java.util.WeakHashMap<>();

    /** Redraw every frosted surface under {@code v} (after scrolling, moving or a blur change). */
    static void refrost(View v) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v.getBackground() instanceof Frost) {
            // Only frosted views that actually moved need a redraw; their frosted children
            // draw plain tint (nested), so the walk stops here.
            if (((Frost) v.getBackground()).moved(v)) v.invalidate();
            return;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) refrost(g.getChildAt(i));
        }
    }

    /** Redraws every frosted view, moved or not (after the backdrop itself changed). */
    static void refrostAll(View v) {
        if (v.getBackground() instanceof Frost) v.invalidate();
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) refrostAll(g.getChildAt(i));
        }
    }

    // Palettes: 3 background stops, then orbs as {x, y, r (fractions of width/height), ARGB}.
    private static final int[][] BG_STOPS = {
            {0xFF0B1230, 0xFF0A0E1F, 0xFF120A26},
            {0xFF22091F, 0xFF170A16, 0xFF2A120C},
            {0xFF061C16, 0xFF07130F, 0xFF081A20},
            {0xFF17191E, 0xFF0E1013, 0xFF1B1D22}};
    private static final int[][] ORBS = {
            {0x7DD3FC, 0xA78BFA, 0x2DD4BF, 0xF472B6},
            {0xF97316, 0xF472B6, 0xA855F7, 0xFBBF24},
            {0x34D399, 0x2DD4BF, 0xA3E635, 0x38BDF8},
            {0xCBD5E1, 0x94A3B8, 0xE2E8F0, 0x64748B}};
    private static final float[][] ORB_POS = {
            {0.08f, 0.12f, 0.62f}, {0.98f, 0.40f, 0.62f}, {0.12f, 0.76f, 0.55f}, {0.86f, 0.98f, 0.50f}};
    private static final int[] ORB_ALPHA = {0x8C, 0x70, 0x60, 0x60};

    /** Glass backdrop: vertical dark gradient plus four soft-edged colour orbs. */
    static final class Backdrop extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int pal;
        private android.graphics.Shader[] cached;
        private float cw = -1, ch = -1;

        Backdrop(int palette) {
            pal = palette;
        }

        @Override
        public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            float w = b.width(), h = b.height();
            if (cached == null || w != cw || h != ch) {
                // Shaders built once per size, not every frame the window background redraws.
                cached = new android.graphics.Shader[5];
                cached[0] = new android.graphics.LinearGradient(0, 0, 0, h, BG_STOPS[pal], null,
                        android.graphics.Shader.TileMode.CLAMP);
                int alphaScale = pal == 3 ? 2 : 1; // grey orbs need less light
                for (int i = 0; i < 4; i++) {
                    float[] o = ORB_POS[i];
                    int col = ((ORB_ALPHA[i] / alphaScale) << 24) | ORBS[pal][i];
                    // Defined orb: even colour to 55 %, then a soft fade, so blur behind glass reads as blur.
                    int mid = ((Color.alpha(col) * 3 / 4) << 24) | (col & 0xFFFFFF);
                    cached[i + 1] = new android.graphics.RadialGradient(w * o[0], h * o[1], w * o[2],
                            new int[]{col, mid, col & 0x00FFFFFF}, new float[]{0f, 0.55f, 1f},
                            android.graphics.Shader.TileMode.CLAMP);
                }
                cw = w;
                ch = h;
            }
            p.setShader(cached[0]);
            c.drawRect(b, p);
            for (int i = 0; i < 4; i++) {
                float[] o = ORB_POS[i];
                p.setShader(cached[i + 1]);
                c.drawCircle(w * o[0], h * o[1], w * o[2], p);
            }
            p.setShader(null);
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    private static android.graphics.Bitmap frostBmp;
    private static String frostKey;

    /** Backdrop rendered small and box-blurred (3 passes ≈ gaussian), cached per size/palette/blur. */
    private static android.graphics.Bitmap blurred(int w, int h) {
        String key = w + "x" + h + "/" + palette + "/" + blur;
        if (key.equals(frostKey)) return frostBmp;
        int bw = Math.max(1, w / 4), bh = Math.max(1, h / 4);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888);
        Backdrop d = new Backdrop(palette);
        d.setBounds(0, 0, bw, bh);
        d.draw(new android.graphics.Canvas(b));
        int r = Math.round(blur / 100f * 16);
        if (r > 0) {
            int[] px = new int[bw * bh], tmp = new int[bw * bh];
            b.getPixels(px, 0, bw, 0, 0, bw, bh);
            for (int i = 0; i < 3; i++) {
                pass(px, tmp, bw, bh, r, true);
                pass(tmp, px, bw, bh, r, false);
            }
            b.setPixels(px, 0, bw, 0, 0, bw, bh);
        }
        frostBmp = b;
        frostKey = key;
        return b;
    }

    /** One box-blur pass along rows (horiz) or columns, edges clamped. */
    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horiz) {
        int lines = horiz ? h : w, len = horiz ? w : h, step = horiz ? 1 : w;
        int div = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int base = horiz ? l * w : l;
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[base + Math.max(0, Math.min(len - 1, i)) * step];
                sa += c >>> 24; sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255;
            }
            for (int i = 0; i < len; i++) {
                out[base + i * step] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = in[base + Math.min(len - 1, i + r + 1) * step], rem = in[base + Math.max(0, i - r) * step];
                sa += (add >>> 24) - (rem >>> 24);
                sr += ((add >> 16) & 255) - ((rem >> 16) & 255);
                sg += ((add >> 8) & 255) - ((rem >> 8) & 255);
                sb += (add & 255) - (rem & 255);
            }
        }
    }

    /**
     * Frosted glass surface: the blurred backdrop at this view's window position (no real blur on
     * API 30, but nothing but the backdrop sits behind cards), then the tint/outline on top.
     * Nested surfaces skip the blur layer and only add their tint.
     */
    static final class Frost extends android.graphics.drawable.Drawable {
        private final GradientDrawable tint;
        private final float radius;
        private final android.graphics.Paint p = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.Matrix m = new android.graphics.Matrix();
        private final android.graphics.RectF box = new android.graphics.RectF();
        private final int[] loc = new int[2];
        private android.graphics.Bitmap shaderFor;
        private final int[] seen = {Integer.MIN_VALUE, Integer.MIN_VALUE};

        /** True when the view's window position changed since the last check. */
        boolean moved(View v) {
            v.getLocationInWindow(loc);
            if (loc[0] == seen[0] && loc[1] == seen[1]) return false;
            seen[0] = loc[0];
            seen[1] = loc[1];
            return true;
        }

        Frost(GradientDrawable tint, float radius) {
            this.tint = tint;
            this.radius = radius;
        }

        @Override
        public void draw(android.graphics.Canvas c) {
            Callback cb = getCallback();
            if (blur > 0 && cb instanceof View && !nested((View) cb)) {
                View v = (View) cb, root = v.getRootView();
                if (root.getWidth() > 0) {
                    android.graphics.Bitmap b = blurred(root.getWidth(), root.getHeight());
                    if (b != shaderFor) {
                        p.setShader(new android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP,
                                android.graphics.Shader.TileMode.CLAMP));
                        shaderFor = b;
                    }
                    v.getLocationInWindow(loc);
                    m.setScale(root.getWidth() / (float) b.getWidth(), root.getHeight() / (float) b.getHeight());
                    m.postTranslate(-loc[0], -loc[1]);
                    p.getShader().setLocalMatrix(m);
                    box.set(getBounds());
                    c.drawRoundRect(box, radius, radius, p);
                }
            }
            tint.setBounds(getBounds());
            tint.draw(c);
        }

        private static boolean nested(View v) {
            for (android.view.ViewParent q = v.getParent(); q instanceof View; q = q.getParent())
                if (((View) q).getBackground() instanceof Frost) return true;
            return false;
        }

        @Override
        public void setAlpha(int a) {
            tint.setAlpha(a);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }

        @Override
        public boolean getPadding(android.graphics.Rect padding) {
            return tint.getPadding(padding);
        }
    }

    /** Wrap a translucent glass fill in a Frost; classic/opaque fills stay as they are. */
    static android.graphics.drawable.Drawable frost(Context c, GradientDrawable g, int radiusDp) {
        return glass ? new Frost(g, dp(c, radiusDp)) : g;
    }

    /** Hero card fill: sky-tinted gradient into the surface colour, with a sky hairline. */
    static android.graphics.drawable.Drawable hero(Context c, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor(HERO), Color.parseColor(SURF1)});
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 1), Color.parseColor(HERO_LINE));
        return frost(c, g, radiusDp);
    }

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

    /** iOS segmented control: equal segments in a track, a raised thumb slides to the selected one. */
    static android.widget.FrameLayout segmented(Context c, String[] labels, int selected, IntSink sink) {
        android.widget.FrameLayout box = new android.widget.FrameLayout(c);
        box.setBackground(glass ? round(c, "#1FFFFFFF", 10) : round(c, SURF1, 10));
        int p = dp(c, 2);
        box.setPadding(p, p, p, p);
        View thumb = new View(c);
        GradientDrawable tg = round(c, glass ? "#5CFFFFFF" : SURF3, 8);
        thumb.setBackground(tg);
        box.addView(thumb, new android.widget.FrameLayout.LayoutParams(0, dp(c, 32)));
        LinearLayout segs = row(c);
        box.addView(segs, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, dp(c, 32)));
        TextView[] ts = new TextView[labels.length];
        final int[] cur = {selected};
        Runnable paint = () -> {
            for (int i = 0; i < ts.length; i++) {
                ts[i].setTextColor(Color.parseColor(i == cur[0] ? WHITE : MUTED));
                ts[i].setTypeface(weight(i == cur[0] ? 600 : 500));
            }
        };
        Runnable place = () -> {
            int w = segs.getWidth() / labels.length;
            if (w <= 0) return;
            android.view.ViewGroup.LayoutParams lp = thumb.getLayoutParams();
            if (lp.width != w) {
                lp.width = w;
                thumb.setLayoutParams(lp);
                thumb.setTranslationX(w * cur[0]);
            }
        };
        box.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> place.run());
        for (int i = 0; i < labels.length; i++) {
            TextView t = text(c, labels[i], 13, MUTED);
            t.setSingleLine(true);
            final int idx = i;
            t.setOnClickListener(v -> {
                if (cur[0] == idx) return;
                cur[0] = idx;
                buzz(v, android.view.HapticFeedbackConstants.CLOCK_TICK);
                paint.run();
                thumb.animate().translationX(thumb.getWidth() * idx).setDuration(280)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f)).start();
                sink.set(idx);
            });
            ts[i] = t;
            segs.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }
        paint.run();
        return box;
    }

    /** iOS switch: 51x31 dp capsule, green when on, white knob that slides. */
    static final class Switch extends View {
        interface Sink {
            void set(boolean on);
        }

        private final android.graphics.Paint track = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG),
                knob = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();
        private final int on = Color.parseColor(GREEN), off = Color.parseColor(SWITCH_OFF);
        private float pos;
        private boolean checked;
        private Sink sink;

        Switch(Context c, boolean checked, Sink sink) {
            super(c);
            this.checked = checked;
            this.sink = sink;
            pos = checked ? 1f : 0f;
            knob.setColor(Color.WHITE);
            knob.setShadowLayer(dp(c, 2), 0, dp(c, 1), 0x40000000);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            setLayoutParams(new LinearLayout.LayoutParams(dp(c, 51), dp(c, 31)));
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setClickable(true);
            setOnClickListener(v -> toggle());
        }

        boolean isChecked() {
            return checked;
        }

        void toggle() {
            checked = !checked;
            buzz(this, android.view.HapticFeedbackConstants.CLOCK_TICK);
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(pos, checked ? 1f : 0f);
            a.setDuration(260);
            a.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
            a.addUpdateListener(x -> {
                pos = (float) x.getAnimatedValue();
                invalidate();
            });
            a.start();
            if (sink != null) sink.set(checked);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo i) {
            super.onInitializeAccessibilityNodeInfo(i);
            i.setClassName("android.widget.Switch");
            i.setCheckable(true);
            i.setChecked(checked);
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight(), p = Math.max(0f, Math.min(1f, pos));
            track.setColor((Integer) ARGB.evaluate(p, off, on));
            r.set(0, 0, w, h);
            c.drawRoundRect(r, h / 2, h / 2, track);
            float m = dp(getContext(), 2), kr = h / 2 - m;
            float cx = m + kr + pos * (w - 2 * m - 2 * kr);
            c.drawCircle(cx, h / 2, kr, knob);
        }
    }

    /**
     * iOS action sheet: rounded box of choices sliding up from the bottom, Cancel in its own box.
     * {@code red} marks destructive choices (bit i = item i). {@code pick} gets the chosen index.
     */
    private static final android.animation.ArgbEvaluator ARGB = new android.animation.ArgbEvaluator();

    static void sheet(Activity a, String title, String msg, String[] items, int red, IntSink pick) {
        android.app.Dialog d = new android.app.Dialog(a);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout root = column(a);
        int m = dp(a, 8);
        root.setPadding(m, 0, m, m);
        LinearLayout box = column(a);
        String sheetBg = "#EB2C2C2E", hair = "#33EBEBF5";
        box.setBackground(round(a, sheetBg, 14));
        box.setClipToOutline(true);
        if (title != null || msg != null) {
            LinearLayout head = column(a);
            head.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14));
            if (title != null) {
                TextView t = text(a, title, 13, "#99EBEBF5");
                t.setTypeface(weight(600));
                head.addView(t, wide());
            }
            if (msg != null) {
                TextView t = text(a, msg, 13, "#99EBEBF5");
                t.setPadding(0, dp(a, 4), 0, 0);
                head.addView(t, wide());
            }
            box.addView(head, wide());
        }
        for (int i = 0; i < items.length; i++) {
            if (i > 0 || title != null || msg != null) {
                View line = new View(a);
                line.setBackgroundColor(Color.parseColor(hair));
                box.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }
            TextView t = text(a, items[i], 20, (red >> i & 1) != 0 ? "#FF453A" : "#0A84FF");
            t.setMinHeight(dp(a, 57));
            t.setBackground(pressTint(a));
            final int idx = i;
            t.setOnClickListener(v -> {
                d.dismiss();
                pick.set(idx);
            });
            box.addView(t, wide());
        }
        root.addView(box, wide());
        TextView cancel = text(a, "Cancel", 20, "#0A84FF");
        cancel.setTypeface(weight(600));
        cancel.setMinHeight(dp(a, 57));
        android.graphics.drawable.LayerDrawable cb = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{round(a, "#FF2C2C2E", 14), pressTint(a)});
        cancel.setBackground(cb);
        cancel.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams lp = wide();
        lp.topMargin = m;
        root.addView(cancel, lp);
        d.setContentView(root);
        android.view.Window w = d.getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.BOTTOM);
        w.setDimAmount(0.4f);
        w.getDecorView().setPadding(0, 0, 0, 0);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.getDecorView().setOnApplyWindowInsetsListener((v, in) -> {
                root.setPadding(m, 0, m, m + in.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom);
                return in;
            });
        }
        root.setTranslationY(dp(a, 400));
        d.setOnShowListener(x -> root.animate().translationY(0).setDuration(320).setInterpolator(EASE).start());
        d.show();
    }

    /** Handle to an {@link #alert}: the dialog and its action button (to enable it later). */
    static final class Alert {
        android.app.Dialog dialog;
        TextView action;

        void enable(boolean on) {
            action.setEnabled(on);
            action.setAlpha(on ? 1f : 0.35f);
        }
    }

    /**
     * iOS alert: centred rounded box, bold title, message, optional view (a PIN field, a countdown),
     * then Cancel | Action side by side. {@code ok} runs after the box closes.
     */
    static Alert alert(Activity a, String title, String msg, View extra, String action, Runnable ok) {
        Alert al = new Alert();
        android.app.Dialog d = new android.app.Dialog(a);
        al.dialog = d;
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        String hair = "#33EBEBF5";
        LinearLayout box = column(a);
        box.setBackground(round(a, "#F22C2C2E", 14));
        box.setClipToOutline(true);
        LinearLayout head = column(a);
        head.setPadding(dp(a, 16), dp(a, 20), dp(a, 16), dp(a, 18));
        TextView t = text(a, title, 17, WHITE);
        t.setTypeface(weight(600));
        head.addView(t, wide());
        if (msg != null) {
            TextView m = text(a, msg, 13, "#CCEBEBF5");
            m.setPadding(0, dp(a, 4), 0, 0);
            m.setLineSpacing(0, 1.15f);
            head.addView(m, wide());
        }
        if (extra != null) {
            LinearLayout.LayoutParams lp = wide();
            lp.topMargin = dp(a, 14);
            head.addView(extra, lp);
        }
        box.addView(head, wide());
        View line = new View(a);
        line.setBackgroundColor(Color.parseColor(hair));
        box.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        LinearLayout row = row(a);
        TextView cancel = text(a, "Cancel", 17, "#0A84FF");
        cancel.setMinHeight(dp(a, 46));
        cancel.setBackground(pressTint(a));
        cancel.setOnClickListener(v -> d.dismiss());
        TextView act = text(a, action, 17, "#0A84FF");
        act.setTypeface(weight(600));
        act.setMinHeight(dp(a, 46));
        act.setBackground(pressTint(a));
        act.setOnClickListener(v -> {
            d.dismiss();
            ok.run();
        });
        al.action = act;
        row.addView(cancel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        View sep = new View(a);
        sep.setBackgroundColor(Color.parseColor(hair));
        row.addView(sep, new LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT));
        row.addView(act, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row, wide());
        d.setContentView(box);
        android.view.Window w = d.getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        w.setLayout(dp(a, 290), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setDimAmount(0.45f);
        box.setScaleX(1.12f);
        box.setScaleY(1.12f);
        box.setAlpha(0f);
        d.setOnShowListener(x -> box.animate().scaleX(1).scaleY(1).alpha(1).setDuration(220).setInterpolator(EASE).start());
        d.show();
        return al;
    }

    /** Text field styled for {@link #alert}. */
    static android.widget.EditText alertField(Context c, String hint) {
        android.widget.EditText e = new android.widget.EditText(c);
        e.setHint(hint);
        e.setHintTextColor(Color.parseColor("#66EBEBF5"));
        e.setTextColor(Color.parseColor(WHITE));
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setBackground(round(c, "#1C1C1E", 8));
        int p = dp(c, 10);
        e.setPadding(p, p, p, p);
        return e;
    }

    /** iOS confirm: sheet with one action. */
    static void confirm(Activity a, String title, String msg, String action, boolean destructive, Runnable ok) {
        sheet(a, title, msg, new String[]{action}, destructive ? 1 : 0, i -> ok.run());
    }

    /** Highlight shown while a sheet row is held. */
    private static android.graphics.drawable.Drawable pressTint(Context c) {
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},
                new android.graphics.drawable.ColorDrawable(Color.parseColor("#26FFFFFF")));
        s.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return s;
    }

    /** iOS large title: 34 sp bold, tight tracking. */
    static TextView largeTitle(Context c, String s) {
        TextView t = left(c, s, 34, WHITE);
        t.setTypeface(weight(700));
        t.setLetterSpacing(-0.02f);
        return t;
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

    static android.graphics.drawable.Drawable outlined(Context c, String color, int radiusDp) {
        GradientDrawable g = round(c, color, radiusDp);
        int base = Color.parseColor(color);
        if (glass && Color.alpha(base) < 255) {
            // Frosted sheen: a little brighter at the top-left edge, like light on glass.
            int a = Color.alpha(base), top = Math.min(255, a + 0x14);
            g.setOrientation(GradientDrawable.Orientation.TL_BR);
            g.setColors(new int[]{(top << 24) | (base & 0xFFFFFF), base});
        }
        g.setStroke(dp(c, 1), Color.parseColor(LINE));
        return glass && Color.alpha(base) < 255 ? frost(c, g, radiusDp) : g;
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

    /** Press feedback: shrink + dim while held, spring back on release. */
    static void press(View v) {
        v.setOnTouchListener((view, e) -> {
            int a = e.getActionMasked();
            if (a == android.view.MotionEvent.ACTION_DOWN)
                view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).setInterpolator(EASE).start();
            else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setDuration(380)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start();
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
                    .setInterpolator(EASE_IN).withEndAction(() -> refrost(v)).start();
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
    static void fitInsets(android.view.View v, android.view.View... hideOnIme) {
        v.setOnApplyWindowInsetsListener((view, in) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = in.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout()
                        | android.view.WindowInsets.Type.ime());
                view.setPadding(i.left, i.top, i.right, i.bottom);
                // Keyboard up: drop the bottom bar (it would ride on top of the keyboard and cover
                // the field) and bring the focused field back into view.
                boolean ime = in.isVisible(android.view.WindowInsets.Type.ime()) || imeUp(view);
                setGone(ime, hideOnIme);
                if (ime) view.post(() -> {
                    android.view.View f = view.findFocus();
                    if (f != null) {
                        android.graphics.Rect r = new android.graphics.Rect(0, 0, f.getWidth(), f.getHeight());
                        r.bottom += dp(f.getContext(), 64); // room for the Add button / next row
                        f.requestRectangleOnScreen(r, false);
                    }
                });
            } else {
                view.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                        in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
            }
            return in;
        });
        // Some API 30 phones (MIUI) resize the window for the keyboard without reporting ime
        // insets, so also watch the visible frame.
        if (hideOnIme.length > 0)
            v.getViewTreeObserver().addOnGlobalLayoutListener(() -> setGone(imeUp(v), hideOnIme));
    }

    /** Keyboard showing: the visible window frame is >15 % shorter than the screen. */
    private static boolean imeUp(android.view.View v) {
        android.graphics.Rect r = new android.graphics.Rect();
        v.getWindowVisibleDisplayFrame(r);
        int h = v.getRootView().getHeight();
        return h > 0 && h - r.bottom > h * 0.15f;
    }

    private static void setGone(boolean gone, android.view.View... vs) {
        int want = gone ? android.view.View.GONE : android.view.View.VISIBLE;
        for (android.view.View h : vs) if (h.getVisibility() != want) h.setVisibility(want);
    }
}
