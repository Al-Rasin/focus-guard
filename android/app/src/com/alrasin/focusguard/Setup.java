package com.alrasin.focusguard;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

/**
 * Setup checklist at the top of the home screen. Without root nothing restarts the service for
 * us, so each step that keeps it alive (accessibility, battery, OEM autostart, notifications)
 * gets a live tick. Collapses to one line once everything is done. On rooted phones the module's
 * watchdog keeps the service on, so only the disclosure and notifications are asked for.
 */
final class Setup {
    static final String SERVICE = "com.alrasin.focusguard/com.alrasin.focusguard.GuardService";

    private Setup() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("setup", Context.MODE_PRIVATE);
    }

    /** The Magisk module's watchdog touches files/rooted on every loop. */
    static boolean rooted(Context c) {
        File f = new File(c.getFilesDir(), "rooted");
        return f.exists() && System.currentTimeMillis() - f.lastModified() < 10 * 60_000L;
    }

    static boolean accessibilityOn(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        for (String part : s.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (cn != null && cn.getPackageName().equals(c.getPackageName())) return true;
        }
        return false;
    }

    static boolean batteryOk(Context c) {
        return c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName());
    }

    static boolean notificationsOk(Context c) {
        return c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    /** Phone makers that kill background apps unless the user allows "autostart". */
    static boolean needsAutostart() {
        String m = Build.MANUFACTURER.toLowerCase(Locale.US);
        return m.matches(".*(xiaomi|redmi|poco|oppo|realme|vivo|iqoo|huawei|honor|oneplus|letv|asus|meizu).*");
    }

    static boolean done(Context c) {
        if (!prefs(c).getBoolean("disclosed", false) || !notificationsOk(c)) return false;
        if (rooted(c)) return true;
        return accessibilityOn(c) && GuardService.instance != null && batteryOk(c)
                && (!needsAutostart() || prefs(c).getBoolean("autostart", false));
    }

    static void addStatus(Activity a, LinearLayout root) {
        KeepAlive.schedule(a);
        SharedPreferences p = prefs(a);
        if (!p.getBoolean("disclosed", false)) {
            disclosure(a, root);
            return;
        }
        if (done(a)) {
            TextView ok = Ui.left(a, "✓ Protection is on" + (rooted(a) ? " (root watchdog)" : ""), 15, Ui.GREEN);
            ok.setPadding(0, 0, 0, Ui.dp(a, 4));
            root.addView(ok);
            privateDns(a, root);
            return;
        }
        LinearLayout box = box(a);
        TextView head = Ui.left(a, "Finish setup", 17, Ui.WHITE);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(head);
        box.addView(Ui.left(a, "Android stops apps it thinks are idle. Each step keeps Focus Guard running.",
                14, Ui.MUTED));
        boolean viaRoot = rooted(a);

        if (!viaRoot) {
            boolean on = accessibilityOn(a);
            boolean running = GuardService.instance != null;
            String accLabel = !on ? "Turn on Focus Guard in Accessibility"
                    : running ? "Accessibility service on"
                    : "Enabled but not running: turn it off and on again";
            step(a, box, accLabel, on && running, "Open", () ->
                    open(a, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            if (!on && Build.VERSION.SDK_INT >= 33) {
                TextView hint = Ui.left(a, "Greyed out (\"Restricted setting\")? App info → ⋮ menu → "
                        + "Allow restricted settings, then try again.", 13, Ui.MUTED);
                hint.setPadding(Ui.dp(a, 28), 0, 0, Ui.dp(a, 4));
                box.addView(hint);
                Button info = Ui.small(a, "App info");
                info.setOnClickListener(v -> appInfo(a));
                box.addView(info);
            }
            step(a, box, "Battery: unrestricted", batteryOk(a), "Allow", () -> {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + a.getPackageName()));
                if (!open(a, i)) open(a, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            });
            if (needsAutostart()) {
                boolean auto = p.getBoolean("autostart", false);
                step(a, box, "Autostart allowed (" + Build.MANUFACTURER + ")", auto, auto ? null : "Open", () -> {
                    autostart(a);
                    // Android can't tell us whether it worked; trust the user once they've been there.
                    p.edit().putBoolean("autostart", true).apply();
                });
            }
        }
        step(a, box, "Notifications allowed", notificationsOk(a), "Allow", () -> {
            // Android 13+: ask once with the system dialog; after a "Don't allow" only settings work.
            if (Build.VERSION.SDK_INT >= 33 && !prefs(a).getBoolean("notif_asked", false)) {
                prefs(a).edit().putBoolean("notif_asked", true).apply();
                a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            } else {
                open(a, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
            }
        });
        LinearLayout.LayoutParams lp = Ui.wide();
        lp.bottomMargin = Ui.dp(a, 8);
        root.addView(box, lp);
    }

    static final String DNS_HOST = "adult-filter-dns.cleanbrowsing.org";

    /**
     * Optional extra: a filtering Private DNS blocks adult sites in every app, not only in
     * browsers. Apps can't set it without root, so we copy the host and open the settings.
     * Skipped on rooted phones (the module decides) and once the user says no.
     */
    private static void privateDns(Activity a, LinearLayout root) {
        SharedPreferences p = prefs(a);
        if (rooted(a) || p.getBoolean("dns_skip", false)) return;
        if ("hostname".equals(Settings.Global.getString(a.getContentResolver(), "private_dns_mode"))) return;
        LinearLayout box = box(a);
        TextView head = Ui.left(a, "Optional: filter adult sites in all apps", 16, Ui.WHITE);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(head);
        box.addView(Ui.space(a, 4));
        box.addView(Ui.left(a, "Settings → Private DNS → \"Private DNS provider hostname\" → paste "
                + DNS_HOST + ". If some sites stop loading on your network, set it back to Automatic.",
                13, Ui.MUTED));
        box.addView(Ui.space(a, 8));
        LinearLayout row = Ui.row(a);
        Button set = Ui.small(a, "Copy host & open settings");
        set.setOnClickListener(v -> {
            android.content.ClipboardManager cb = a.getSystemService(android.content.ClipboardManager.class);
            cb.setPrimaryClip(android.content.ClipData.newPlainText("Private DNS", DNS_HOST));
            android.widget.Toast.makeText(a, L.t(a, "Copied " + DNS_HOST), android.widget.Toast.LENGTH_SHORT).show();
            if (!open(a, new Intent("android.settings.PRIVATE_DNS_SETTINGS")))
                open(a, new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        });
        row.addView(set);
        row.addView(Ui.space(a, 1), new LinearLayout.LayoutParams(Ui.dp(a, 8), 1));
        Button no = Ui.small(a, "No thanks");
        no.setOnClickListener(v -> {
            p.edit().putBoolean("dns_skip", true).apply();
            a.recreate();
        });
        row.addView(no);
        box.addView(row);
        LinearLayout.LayoutParams lp = Ui.wide();
        lp.bottomMargin = Ui.dp(a, 8);
        root.addView(box, lp);
    }

    private static LinearLayout box(Activity a) {
        LinearLayout box = Ui.column(a);
        box.setBackground(Ui.round(a, Ui.CARD, 14));
        int pad = Ui.dp(a, 14);
        box.setPadding(pad, pad, pad, pad);
        return box;
    }

    private static void step(Activity a, LinearLayout box, String label, boolean ok, String action, Runnable run) {
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        row.addView(Ui.left(a, (ok ? "✓  " : "○  ") + L.t(a, label), 15, ok ? Ui.GREEN : Ui.WHITE), Ui.grow());
        if (!ok && action != null) {
            Button b = Ui.small(a, action);
            b.setBackground(Ui.round(a, Ui.SKY, 14));
            b.setTextColor(android.graphics.Color.parseColor(Ui.BG));
            b.setOnClickListener(v -> run.run());
            row.addView(b);
        }
        box.addView(row);
    }

    /** Required before asking for accessibility: what it reads and that nothing leaves the phone. */
    private static void disclosure(Activity a, LinearLayout root) {
        LinearLayout box = box(a);
        TextView head = Ui.left(a, "Before you start", 17, Ui.WHITE);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(head);
        box.addView(Ui.space(a, 6));
        box.addView(Ui.left(a, "Focus Guard uses Android's Accessibility service to see which screen is open: "
                + "the app, the browser address, and on-screen text in Telegram chats. It uses this only to "
                + "show the pause screen, count opens and block adult content.\n\n"
                + "Everything stays on this phone. The app has no internet permission, no ads and no analytics.",
                14, Ui.MUTED));
        box.addView(Ui.space(a, 12));
        Button ok = Ui.button(a, "I understand", Ui.SKY, Ui.BG);
        ok.setOnClickListener(v -> {
            prefs(a).edit().putBoolean("disclosed", true).apply();
            a.recreate();
        });
        box.addView(ok, Ui.wide());
        LinearLayout.LayoutParams lp = Ui.wide();
        lp.bottomMargin = Ui.dp(a, 8);
        root.addView(box, lp);
    }

    static void appInfo(Context c) {
        open(c, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + c.getPackageName())));
    }

    private static void autostart(Context c) {
        String[][] screens = {
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"},
                {"com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"},
                {"com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"},
                {"com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC"},
        };
        for (String[] s : screens) {
            if (open(c, new Intent().setComponent(new ComponentName(s[0], s[1])))) return;
        }
        appInfo(c);
    }

    static boolean open(Context c, Intent i) {
        Strict.grace();
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
