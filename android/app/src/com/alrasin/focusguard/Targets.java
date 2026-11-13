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

    static final String GROUP_PREFIX = "grp:";
    private static final String GROUPS = "groups";

    static List<String> all(Context ctx) {
        List<String> out = new ArrayList<>(Arrays.asList(Limits.BUILTIN));
        for (String id : new TreeSet<>(prefs(ctx).getStringSet(GROUPS, new HashSet<>())))
            out.add(GROUP_PREFIX + id);
        for (String pkg : new TreeSet<>(prefs(ctx).getStringSet(KEY, new HashSet<>())))
            out.add(Limits.APP_PREFIX + pkg);
        return out;
    }

    // ---- groups: a named set of apps sharing one limit ("grp:<id>") ----------------------

    static Set<String> groups(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(GROUPS, new HashSet<>()));
    }

    /** Creates a group and returns its target id. */
    static String addGroup(Context ctx, String name, Set<String> apps) {
        Set<String> g = groups(ctx);
        int n = 1;
        while (g.contains(String.valueOf(n))) n++;
        String id = String.valueOf(n);
        g.add(id);
        prefs(ctx).edit().putStringSet(GROUPS, g).putString("gname_" + id, name)
                .putStringSet("gapps_" + id, new HashSet<>(apps)).apply();
        return GROUP_PREFIX + id;
    }

    /** Recreates a group under a known id (backup restore). */
    static void putGroup(Context ctx, String id, String name, Set<String> apps) {
        Set<String> g = groups(ctx);
        g.add(id);
        prefs(ctx).edit().putStringSet(GROUPS, g).putString("gname_" + id, name)
                .putStringSet("gapps_" + id, new HashSet<>(apps)).apply();
    }

    static void removeGroup(Context ctx, String cat) {
        String id = cat.substring(GROUP_PREFIX.length());
        Set<String> g = groups(ctx);
        g.remove(id);
        prefs(ctx).edit().putStringSet(GROUPS, g).remove("gname_" + id).remove("gapps_" + id).remove(cat).apply();
    }

    static String groupName(Context ctx, String cat) {
        return prefs(ctx).getString("gname_" + cat.substring(GROUP_PREFIX.length()), "Group");
    }

    static void renameGroup(Context ctx, String cat, String name) {
        prefs(ctx).edit().putString("gname_" + cat.substring(GROUP_PREFIX.length()), name).apply();
    }

    static Set<String> groupApps(Context ctx, String cat) {
        return new HashSet<>(prefs(ctx).getStringSet("gapps_" + cat.substring(GROUP_PREFIX.length()),
                new HashSet<>()));
    }

    static void setGroupApps(Context ctx, String cat, Set<String> apps) {
        prefs(ctx).edit().putStringSet("gapps_" + cat.substring(GROUP_PREFIX.length()), new HashSet<>(apps))
                .apply();
    }

    static boolean isGroup(String cat) {
        return cat.startsWith(GROUP_PREFIX);
    }

    /** Package -> target that already owns it as a whole app (single app or group), for the chooser. */
    static java.util.Map<String, String> owners(Context ctx) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        for (String pkg : apps(ctx)) m.put(pkg, Limits.APP_PREFIX + pkg);
        for (String id : groups(ctx)) {
            String cat = GROUP_PREFIX + id;
            for (String pkg : groupApps(ctx, cat)) m.put(pkg, cat);
        }
        return m;
    }

    /** Like {@link #packages(String)}, but also knows groups. */
    static Set<String> packages(Context ctx, String cat) {
        if (isGroup(cat)) return groupApps(ctx, cat);
        return packages(cat);
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

    /** True for targets that only exist as a whole app (TikTok, picked apps, groups). */
    static boolean wholeOnly(String cat) {
        return Limits.TIKTOK.equals(cat) || cat.startsWith(Limits.APP_PREFIX) || isGroup(cat);
    }

    /** Facebook-site lives in browsers only, so "whole app" makes no sense there. */
    static boolean hasMode(String cat) {
        return !Limits.FACEBOOK.equals(cat) && !wholeOnly(cat);
    }

    private static SharedPreferences prefs(Context ctx) {
        return Config.prefs(ctx);
    }
}
