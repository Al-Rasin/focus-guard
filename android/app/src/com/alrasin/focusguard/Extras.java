package com.alrasin.focusguard;

import android.app.Activity;
import android.widget.LinearLayout;

/** Settings-tab sections: protection, backup. */
final class Extras {
    private Extras() {
    }

    static void addHomeButtons(Activity a, LinearLayout root) {
        if (!(a instanceof MainActivity)) return;
        MainActivity m = (MainActivity) a;
        root.addView(m.section("Protection"));
        LinearLayout g = m.group(root);
        g.addView(m.navRow("Strict mode", "Makes it hard to turn protection off",
                Strict.on(a) ? "On · " + Strict.delayH(a) + " h" : "Off", m::strict));
        Backup.addButtons(m, root);
    }
}
