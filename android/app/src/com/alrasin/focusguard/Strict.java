package com.alrasin.focusguard;

import android.app.Activity;
import android.content.Context;
import android.widget.LinearLayout;

/** Saving settings; strict mode delays loosening (phase 4). */
final class Strict {
    private Strict() {
    }

    static String save(Context ctx, Config c) {
        c.save(ctx);
        return "Saved";
    }

    static String removeApp(Context ctx, String pkg) {
        Targets.removeApp(ctx, pkg);
        return "Removed";
    }

    static void addPendingNote(Activity a, LinearLayout root, String cat) {
    }
}
