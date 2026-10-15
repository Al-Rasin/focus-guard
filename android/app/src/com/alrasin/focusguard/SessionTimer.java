package com.alrasin.focusguard;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Locale;

/**
 * Small "time left" pill over Shorts/Reels while an open (unlock window) is running.
 * An accessibility overlay: needs no extra permission and never takes touches.
 */
final class SessionTimer {
    private final GuardService service;
    private final Handler handler;
    private final Limits limits;
    private TextView view;
    private String cat, pkg;
    private final Runnable tick = this::tick;

    SessionTimer(GuardService service, Handler handler, Limits limits) {
        this.service = service;
        this.handler = handler;
        this.limits = limits;
    }

    void show(String cat, String pkg) {
        this.cat = cat;
        this.pkg = pkg;
        if (view == null) {
            view = new TextView(service);
            view.setTextColor(Color.WHITE);
            view.setTextSize(13);
            view.setGravity(Gravity.CENTER);
            int dp = Math.round(service.getResources().getDisplayMetrics().density);
            view.setPadding(10 * dp, 4 * dp, 10 * dp, 4 * dp);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(14 * dp);
            bg.setColor(0xB3000000);
            view.setBackground(bg);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = statusBarHeight() + 6 * dp;
            try {
                service.getSystemService(WindowManager.class).addView(view, lp);
            } catch (RuntimeException e) {
                view = null;
                return;
            }
        }
        handler.removeCallbacks(tick);
        tick();
    }

    void hide() {
        handler.removeCallbacks(tick);
        if (view == null) return;
        try {
            service.getSystemService(WindowManager.class).removeView(view);
        } catch (RuntimeException ignored) {
        }
        view = null;
    }

    private void tick() {
        if (view == null) return;
        long left = limits.unlockedUntil(cat) - System.currentTimeMillis();
        if (left <= 0) {
            hide();
            service.recheck(); // window ran out on screen: pause again
            return;
        }
        if (!pkg.equals(service.currentPkg())) { // left the app (Home, recents, another app)
            hide();
            return;
        }
        long s = (left + 999) / 1000;
        view.setText(String.format(Locale.US, "⏱ %d:%02d left", s / 60, s % 60));
        view.setTextColor(s <= 60 ? 0xFFFF6B6B : Color.WHITE);
        handler.postDelayed(tick, 1000);
    }

    private int statusBarHeight() {
        int id = service.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? service.getResources().getDimensionPixelSize(id) : 0;
    }
}
