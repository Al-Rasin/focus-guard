package com.alrasin.focusguard;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * "Time left" tab on the screen edge over Shorts/Reels while an open (unlock window) is running.
 * Pause-screen colours; turns red and pulses in the last minute. Drag it to move it: it snaps to
 * the nearer side and the position is remembered. An accessibility overlay: no extra permission.
 */
final class SessionTimer {
    private static final int BG = 0xF20E1116, ACCENT = 0xFF7DD3FC, MUTED = 0xFFAEB6C2;
    private static final int WARN = 0xFFF87171, WARN_TEXT = 0xFF0E1116;
    private static final long WARN_MS = 60_000;

    private final GuardService service;
    private final Handler handler;
    private final Limits limits;
    private final SharedPreferences pos;
    private WindowManager.LayoutParams lp;
    private LinearLayout view;
    private TextView time, label;
    private GradientDrawable bg;
    private ObjectAnimator pulse;
    private boolean warned, running;
    private String cat, pkg, why = "";
    private final Runnable tick = this::tick;

    SessionTimer(GuardService service, Handler handler, Limits limits) {
        this.service = service;
        this.handler = handler;
        this.limits = limits;
        pos = service.getSharedPreferences("timer", Context.MODE_PRIVATE);
    }

    String cat() {
        return cat;
    }

    void show(String cat, String pkg) {
        this.cat = cat;
        this.pkg = pkg;
        Config c = limits.config(cat);
        why = c.askWhy ? limits.why(cat) : "";
        // With the tab turned off, still tick (daily time cap, expiry re-check), just invisibly.
        if (c.timer && view == null) create();
        if (!c.timer && view != null) removeView();
        if (view != null) label.setText(normalLabel());
        running = true;
        handler.removeCallbacks(tick);
        tick();
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean create() {
        int dp = Math.round(service.getResources().getDisplayMetrics().density);
        time = new TextView(service);
        time.setTextSize(22);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setGravity(Gravity.CENTER);
        label = new TextView(service);
        label.setText(normalLabel());
        label.setMaxWidth(110 * dp);
        label.setTextSize(11);
        label.setGravity(Gravity.CENTER);

        view = new LinearLayout(service);
        view.setOrientation(LinearLayout.VERTICAL);
        view.addView(time);
        view.addView(label);
        bg = new GradientDrawable();
        view.setBackground(bg);
        warned = true; // forces the normal colours on the first tick
        style(false);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        int screenH = service.getResources().getDisplayMetrics().heightPixels;
        lp.y = Math.round(pos.getFloat("y", 0.45f) * screenH);
        dock(pos.getBoolean("right", false));
        view.setOnTouchListener(new Drag());
        try {
            service.getSystemService(WindowManager.class).addView(view, lp);
            return true;
        } catch (RuntimeException e) {
            view = null;
            return false;
        }
    }

    /** Stick to the left or right edge; the rounded corners face the screen. */
    private void dock(boolean right) {
        int dp = Math.round(service.getResources().getDisplayMetrics().density);
        float r = 16 * dp;
        lp.gravity = Gravity.TOP | (right ? Gravity.RIGHT : Gravity.LEFT);
        lp.x = 0;
        bg.setCornerRadii(right ? new float[]{r, r, 0, 0, 0, 0, r, r} : new float[]{0, 0, r, r, r, r, 0, 0});
        view.setPadding((right ? 14 : 12) * dp, 8 * dp, (right ? 12 : 14) * dp, 8 * dp);
    }

    /** Follows the finger; on release snaps to the nearer side and saves the spot. */
    private final class Drag implements View.OnTouchListener {
        private float downX, downY;
        private int startY;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            WindowManager wm = service.getSystemService(WindowManager.class);
            int screenW = service.getResources().getDisplayMetrics().widthPixels;
            int screenH = service.getResources().getDisplayMetrics().heightPixels;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startY = lp.y;
                    lp.gravity = Gravity.TOP | Gravity.LEFT;
                    lp.x = Math.round(downX - e.getX());
                    wm.updateViewLayout(view, lp);
                    downX = lp.x - downX; // offset from finger to the tab's left edge
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x = Math.round(e.getRawX() + downX);
                    lp.y = clamp(startY + Math.round(e.getRawY() - downY), 0, screenH - view.getHeight());
                    wm.updateViewLayout(view, lp);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    boolean right = e.getRawX() > screenW / 2f;
                    dock(right);
                    wm.updateViewLayout(view, lp);
                    pos.edit().putBoolean("right", right).putFloat("y", lp.y / (float) screenH).apply();
                    return true;
            }
            return false;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    boolean running() {
        return running;
    }

    void hide() {
        running = false;
        handler.removeCallbacks(tick);
        removeView();
    }

    private void removeView() {
        if (pulse != null) pulse.cancel();
        pulse = null;
        if (view == null) return;
        try {
            service.getSystemService(WindowManager.class).removeView(view);
        } catch (RuntimeException ignored) {
        }
        view = null;
    }

    private void tick() {
        if (!running) return;
        long left = limits.unlockedUntil(cat) - limits.now();
        if (left <= 0) {
            hide();
            service.recheck(); // window ran out on screen: pause again
            return;
        }
        if (!pkg.equals(service.currentPkg())) { // left the app (Home, recents, another app)
            hide();
            return;
        }
        limits.addSeconds(cat, 1);
        handler.postDelayed(tick, 1000);
        if (view == null) return;
        long s = (left + 999) / 1000;
        time.setText(String.format(Locale.US, "%d:%02d", s / 60, s % 60));
        style(left <= WARN_MS);
    }

    /** "left", or the user's own reason for this open (intention reminder). */
    private String normalLabel() {
        if (why.isEmpty()) return "left";
        return why.length() > 14 ? why.substring(0, 13) + "…" : why;
    }

    /** Normal: dark tab, sky-blue border and time. Last minute: red tab, pulsing. */
    private void style(boolean warn) {
        if (warn == warned) return;
        warned = warn;
        bg.setColor(warn ? WARN : BG);
        bg.setStroke(Math.round(2 * service.getResources().getDisplayMetrics().density), warn ? WARN : ACCENT);
        time.setTextColor(warn ? WARN_TEXT : ACCENT);
        label.setTextColor(warn ? WARN_TEXT : MUTED);
        label.setText(warn ? "last min" : normalLabel());
        if (warn) {
            pulse = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.55f);
            pulse.setDuration(600);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.start();
        } else if (pulse != null) {
            pulse.cancel();
            pulse = null;
            view.setAlpha(1f);
        }
    }
}
