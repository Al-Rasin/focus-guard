package com.alrasin.focusguard;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Adult domain list (the module's adult_hosts, bundled as an asset) for phones without root,
 * where the hosts file can't be changed: the browser's address bar is checked against it,
 * including parent domains (m.example.com matches example.com). Loaded once, off the main thread.
 */
final class AdultDomains {
    private static volatile Set<String> set;

    private AdultDomains() {
    }

    static void load(Context ctx) {
        if (set != null) return;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            Set<String> s = new HashSet<>(100_000);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    app.getAssets().open("adult_hosts")))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.startsWith("0.0.0.0 ")) continue;
                    String d = line.substring(8).trim().toLowerCase(Locale.US);
                    if (d.startsWith("www.")) d = d.substring(4);
                    if (!d.isEmpty() && !d.equals("0.0.0.0")) s.add(d);
                }
            } catch (Exception ignored) {
            }
            set = s;
        }, "adult-domains").start();
    }

    /** Host part of what a browser shows in its address bar ("https://m.x.com/a" → "m.x.com"). */
    static String host(String url) {
        String u = url.trim().toLowerCase(Locale.US);
        int scheme = u.indexOf("://");
        if (scheme >= 0) u = u.substring(scheme + 3);
        int end = u.length();
        for (char c : new char[]{'/', '?', '#', ':', ' '}) {
            int i = u.indexOf(c);
            if (i >= 0 && i < end) end = i;
        }
        u = u.substring(0, end);
        int at = u.lastIndexOf('@');
        if (at >= 0) u = u.substring(at + 1);
        return u.indexOf('.') > 0 ? u : null;
    }

    static boolean blocked(String url) {
        Set<String> s = set;
        String h = host(url);
        if (s == null || h == null) return false;
        if (h.startsWith("www.")) h = h.substring(4);
        while (true) {
            if (s.contains(h)) return true;
            int dot = h.indexOf('.');
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false; // stop at "example.com"
            h = h.substring(dot + 1);
        }
    }
}
