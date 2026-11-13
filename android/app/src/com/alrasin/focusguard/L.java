package com.alrasin.focusguard;

import android.content.Context;

import java.util.regex.Pattern;

/**
 * UI text pass. The app is English only; Ui.text/left/button run every string through {@link #t},
 * which fixes plurals in built strings ("1 times" → "1 time").
 */
final class L {
    private L() {
    }

    static String t(Context c, String en) {
        return en == null || en.isEmpty() ? en : singular(en);
    }

    private static final Pattern ONE = Pattern.compile("(?<![\\d.,])1 (time|open|video|minute|hour|day)s\\b");

    /** English plural fix for built strings: "went back 1 times" → "went back 1 time". */
    static String singular(String s) {
        return s.indexOf('1') < 0 ? s : ONE.matcher(s).replaceAll("1 $1");
    }
}
