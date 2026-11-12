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
        Backup.addButtons(m, root);

        root.addView(m.section("Language"));
        LinearLayout row = Ui.row(a);
        String cur = L.setting(a);
        String[][] opts = {{"", "Phone"}, {"en", "English"}, {"bn", "বাংলা"}};
        for (String[] o : opts) {
            Button b = Ui.small(a, o[1]);
            if (o[0].equals(cur)) b.setBackground(Ui.round(a, Ui.SKY, 14));
            if (o[0].equals(cur)) b.setTextColor(android.graphics.Color.parseColor(Ui.BG));
            b.setOnClickListener(v -> {
                L.set(a, o[0]);
                m.refresh();
            });
            LinearLayout.LayoutParams lp = Ui.grow();
            lp.rightMargin = Ui.dp(a, 6);
            row.addView(b, lp);
        }
        root.addView(row);
    }
}
