package com.alrasin.focusguard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Random;

/** Full-screen pause shown before a target: wait, then open (spends one) or go back. */
public class PauseActivity extends Activity {
    private String cat, pkg;
    private CountDownTimer timer;
    private boolean counted, whyOk;
    private Button open;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cat = getIntent().getStringExtra("cat");
        pkg = getIntent().getStringExtra("pkg");
        if (cat == null) {
            finish();
            return;
        }
        Limits limits = new Limits(this);
        Config cfg = limits.config(cat);
        int left = limits.left(cat);
        String blocked = limits.hardBlock(cat);
        int cooldown = limits.cooldownLeftMin(cat);

        LinearLayout root = Ui.column(this);
        root.setGravity(Gravity.CENTER);
        int pad = Ui.dp(this, 32);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, Limits.label(this, cat), 26, Ui.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        root.addView(Ui.space(this, 12));

        Button back = Ui.button(this, "Go back", Ui.SKY, Ui.BG);
        back.setOnClickListener(v -> goBack());

        if (blocked != null || cooldown > 0 || (left == 0 && !limits.canExtend(cat))) {
            // Nothing to open: say why, only "Go back".
            String why = blocked != null ? "Closed: " + blocked
                    : cooldown > 0 ? "Next open in " + cooldown + " min"
                    : "Daily limit reached";
            root.addView(Ui.text(this, why, 18, Ui.MUTED));
            root.addView(Ui.space(this, 40));
            root.addView(back, Ui.wide());
            show(root);
            return;
        }

        root.addView(Ui.text(this, "Take a breath. Do you really need this now?", 17, Ui.MUTED));
        String swap = swap();
        if (swap != null) {
            root.addView(Ui.space(this, 10));
            root.addView(Ui.text(this, "Or instead: " + swap, 16, Ui.GREEN));
        }
        root.addView(Ui.space(this, 32));
        int seconds = limits.pauseSeconds(cat);
        TextView count = Ui.text(this, seconds + "", 64, Ui.SKY);
        root.addView(count);
        root.addView(Ui.space(this, 32));

        boolean extending = left == 0;
        root.addView(Ui.text(this, extending
                ? "Daily limit reached. Extensions left today: " + (cfg.extMax - limits.extends_(cat))
                : left + " of " + limits.total(cat) + " opens left today", 16, Ui.MUTED));

        EditText why = null;
        whyOk = extending || !cfg.askWhy;
        if (!whyOk) {
            root.addView(Ui.space(this, 20));
            why = new EditText(this);
            why.setHint("Why are you opening it?");
            why.setHintTextColor(Color.parseColor("#6B7280"));
            why.setTextColor(Color.WHITE);
            why.setSingleLine(true);
            why.setBackground(Ui.round(this, Ui.CARD, 10));
            int p = Ui.dp(this, 12);
            why.setPadding(p, p, p, p);
            why.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void afterTextChanged(Editable s) {
                    whyOk = s.toString().trim().length() >= 3;
                    refresh();
                }
            });
            root.addView(why, Ui.wide());
        }
        root.addView(Ui.space(this, 24));

        String openText = extending ? "Extend +" + cfg.extOpens + " opens"
                : "Open (" + Math.max(1, limits.openMs(cat) / 60000) + " min)";
        open = Ui.button(this, openText, Ui.CARD, Ui.WHITE);
        open.setEnabled(false);
        open.setAlpha(0.35f);
        final EditText whyBox = why;
        open.setOnClickListener(v -> {
            if (extending) {
                // Extending may have closed (cutoff hour) while this screen was up: leave instead.
                if (!limits.canExtend(cat)) {
                    goBack();
                    return;
                }
                // New opens still go through a fresh pause.
                limits.extend(cat);
                recreate();
                return;
            }
            if (limits.hardBlock(cat) != null || limits.left(cat) == 0) {
                goBack();
                return;
            }
            limits.saveWhy(cat, whyBox == null ? "" : whyBox.getText().toString().trim());
            limits.open(cat);
            if (GuardService.instance != null) GuardService.instance.showOpensLeft(cat);
            finish();
        });

        root.addView(back, Ui.wide());
        root.addView(Ui.space(this, 12));
        root.addView(open, Ui.wide());
        show(root);

        timer = new CountDownTimer(seconds * 1000L, 250) {
            @Override
            public void onTick(long ms) {
                count.setText(String.valueOf((ms + 999) / 1000));
            }

            @Override
            public void onFinish() {
                count.setText("✓");
                counted = true;
                refresh();
            }
        }.start();
    }

    private void refresh() {
        if (open == null) return;
        boolean on = counted && whyOk;
        open.setEnabled(on);
        open.setAlpha(on ? 1f : 0.35f);
    }

    private void show(LinearLayout root) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.parseColor(Ui.BG));
        sv.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.MATCH_PARENT));
        setContentView(sv);
    }

    /** One of the user's own alternatives, or null if none set. */
    private String swap() {
        List<String> s = Config.swaps(this);
        return s.isEmpty() ? null : s.get(new Random().nextInt(s.size()));
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

    /** Close the pause and move the app off the target screen; Home only if the service is gone. */
    private void goBack() {
        if (cat != null) new Limits(this).addSkip(cat);
        if (GuardService.instance != null && pkg != null) {
            GuardService.instance.leave(pkg, null);
        } else {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }
}
