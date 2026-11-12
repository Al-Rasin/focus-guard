package com.alrasin.focusguard;

import android.app.Activity;
import android.widget.Button;
import android.widget.LinearLayout;

/** Extra home-screen buttons (strict mode, backup, ...). */
final class Extras {
    private Extras() {
    }

    static void addHomeButtons(Activity a, LinearLayout root) {
        if (!(a instanceof MainActivity)) return;
        MainActivity m = (MainActivity) a;
        root.addView(m.section("Protection"));
        Button strict = Ui.small(a, Strict.on(a) ? "Strict mode: on (" + Strict.delayH(a) + " h delay)" : "Strict mode: off");
        strict.setOnClickListener(v -> m.strict());
        root.addView(strict);
    }
}
