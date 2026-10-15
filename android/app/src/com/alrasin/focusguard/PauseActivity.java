package com.alrasin.focusguard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen pause shown before Shorts/Reels: wait, then open (spends one) or go home. */
public class PauseActivity extends Activity {
    static boolean showing;

    private String cat, pkg;
    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cat = getIntent().getStringExtra("cat");
        pkg = getIntent().getStringExtra("pkg");
        Limits limits = new Limits(this);
        int left = limits.left(cat);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#0E1116"));
        int pad = dp(32);
        root.setPadding(pad, pad, pad, pad);

        TextView title = text(Limits.label(cat), 26, "#FFFFFF");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView breathe = text("Take a breath. Do you really need this now?", 17, "#AEB6C2");
        TextView count = text(Limits.PAUSE_SECONDS + "", 64, "#7DD3FC");
        boolean extending = left == 0;
        TextView info = text(extending
                ? "Daily limit reached. Extensions left today: " + (Limits.MAX_EXTENDS - limits.extends_(cat))
                : left + " of " + limits.total(cat) + " opens left today", 16, "#AEB6C2");

        Button open = button(extending ? "Extend +" + Limits.EXTEND_OPENS + " opens" : "Open (15 min)",
                "#1F2937", "#FFFFFF");
        open.setEnabled(false);
        open.setAlpha(0.35f);
        Button back = button("Go back", "#7DD3FC", "#0E1116");

        open.setOnClickListener(v -> {
            if (extending) {
                // Extending may have closed (19:00) while this screen was up: leave instead.
                if (!limits.canExtend(cat)) {
                    goBack();
                    return;
                }
                // New opens still go through a fresh pause.
                limits.extend(cat);
                recreate();
                return;
            }
            limits.open(cat);
            if (GuardService.instance != null) GuardService.instance.showOpensLeft(cat);
            finish();
        });
        back.setOnClickListener(v -> goBack());

        root.addView(title);
        root.addView(space(12));
        root.addView(breathe);
        root.addView(space(40));
        root.addView(count);
        root.addView(space(40));
        root.addView(info);
        root.addView(space(28));
        root.addView(back, wide());
        root.addView(space(12));
        root.addView(open, wide());
        setContentView(root);

        timer = new CountDownTimer(Limits.PAUSE_SECONDS * 1000L, 250) {
            @Override
            public void onTick(long ms) {
                count.setText(String.valueOf((ms + 999) / 1000));
            }

            @Override
            public void onFinish() {
                count.setText("✓");
                open.setEnabled(true);
                open.setAlpha(1f);
            }
        }.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showing = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        showing = false;
    }

    @Override
    protected void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    /** Close the pause and move the app off Shorts/Reels; Home only if the service is gone. */
    private void goBack() {
        if (GuardService.instance != null && pkg != null) {
            GuardService.instance.leave(pkg, null);
        } else {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }

    private TextView text(String s, int sp, String color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button button(String s, String bg, String fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(Color.parseColor(fg));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.parseColor(bg));
        shape.setCornerRadius(dp(14));
        b.setBackground(shape);
        b.setPadding(0, dp(14), 0, dp(14));
        return b;
    }

    private LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
