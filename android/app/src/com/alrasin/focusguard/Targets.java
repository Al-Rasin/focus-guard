package com.alrasin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Which targets exist: the built-in ones plus whole apps the user picked. */
final class Targets {
    private static final String KEY = "apps";

    static final Set<String> YOUTUBE = new HashSet<>(Arrays.asList("com.google.android.youtube"));
    static final Set<String> FACEBOOK = new HashSet<>(Arrays.asList("com.facebook.katana", "com.facebook.lite"));
    static final Set<String> INSTAGRAM = new HashSet<>(Arrays.asList("com.instagram.android", "com.instagram.lite"));
    static final Set<String> TIKTOK = new HashSet<>(Arrays.asList(
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.aweme"));

    static List<String> all(Context ctx) {
        List<String> out = new ArrayList<>(Arrays.asList(Limits.BUILTIN));
        for (String pkg : new TreeSet<>(prefs(ctx).getStringSet(KEY, new HashSet<>())))
            out.add(Limits.APP_PREFIX + pkg);
        return out;
    }

    static void addApp(Context ctx, String pkg) {
        Set<String> s = new HashSet<>(prefs(ctx).getStringSet(KEY, new HashSet<>()));
        s.add(pkg);
        prefs(ctx).edit().putStringSet(KEY, s).apply();
    }

    static void removeApp(Context ctx, String pkg) {
        Set<String> s = new HashSet<>(prefs(ctx).getStringSet(KEY, new HashSet<>()));
        s.remove(pkg);
        prefs(ctx).edit().putStringSet(KEY, s).remove(Limits.APP_PREFIX + pkg).apply();
    }

    static Set<String> apps(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(KEY, new HashSet<>()));
    }

    /** Packages of the app behind a target, for "whole app" mode. Facebook-site has none. */
    static Set<String> packages(String cat) {
        switch (cat) {
            case Limits.SHORTS: return YOUTUBE;
            case Limits.REELS: return FACEBOOK;
            case Limits.INSTAGRAM: return INSTAGRAM;
            case Limits.TIKTOK: return TIKTOK;
            default:
                if (cat.startsWith(Limits.APP_PREFIX))
                    return new HashSet<>(Arrays.asList(cat.substring(Limits.APP_PREFIX.length())));
                return new HashSet<>();
        }
    }

    /** True for targets that only exist as a whole app (TikTok, picked apps). */
    static boolean wholeOnly(String cat) {
        return Limits.TIKTOK.equals(cat) || cat.startsWith(Limits.APP_PREFIX);
    }

    /** Facebook-site lives in browsers only, so "whole app" makes no sense there. */
    static boolean hasMode(String cat) {
        return !Limits.FACEBOOK.equals(cat) && !wholeOnly(cat);
    }

    private static SharedPreferences prefs(Context ctx) {
        return Config.prefs(ctx);
    }
}
