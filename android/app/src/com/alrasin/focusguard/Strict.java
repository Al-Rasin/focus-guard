package com.alrasin.focusguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Strict mode: anything that loosens a rule (looser settings, removing an app target, a
 * shorter delay, turning strict mode off) waits {@code delayH} hours before it takes effect.
 * Tightening always applies at once. A partner PIN can apply a pending change right away.
 * Pending changes live in prefs "strict" as {@code p_<cat>} = {"at": ms, "cfg": {...}},
 * {@code rm_<pkg>} = ms, {@code delay_at}/{@code delay_to} and {@code off_at}. Times are on
 * the tamper-proof {@link Clock}.
 */
final class Strict {
    static final int[] DELAYS = {1, 6, 24, 72};
    private static final long WEEK = 7 * 24 * 3600_000L;
    static final int EMERGENCY_WAIT_S = 60;

    /** Shown on the "activate device admin" page; marks the one settings page strict mode lets through. */
    private static final String ADMIN_NOTE = "Stops Focus Guard from being uninstalled on a whim.";

    private Strict() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("strict", Context.MODE_PRIVATE);
    }

    static boolean on(Context c) {
        return prefs(c).getBoolean("on", false);
    }

    static int delayH(Context c) {
        return prefs(c).getInt("delay", 24);
    }

    private static long now(Context c) {
        return Clock.now(c);
    }

    private static long at(Context c) {
        return now(c) + delayH(c) * 3600_000L;
    }

    static String when(long ms) {
        return new SimpleDateFormat("EEE HH:mm", Locale.US).format(new Date(ms));
    }

    // ---- target settings ----------------------------------------------------------------

    static String save(Context ctx, Config c) {
        applyDue(ctx);
        if (!on(ctx) || c.stricterOrEqual(Config.load(ctx, c.cat))) {
            c.save(ctx);
            prefs(ctx).edit().remove("p_" + c.cat).apply();
            return "Saved";
        }
        long at = at(ctx);
        try {
            JSONObject j = new JSONObject().put("at", at).put("cfg", c.toJson());
            prefs(ctx).edit().putString("p_" + c.cat, j.toString()).apply();
        } catch (Exception e) {
            return "Not saved";
        }
        return "Strict mode: takes effect " + when(at);
    }

    static String removeApp(Context ctx, String pkg) {
        if (!on(ctx)) {
            Targets.removeApp(ctx, pkg);
            return "Removed";
        }
        long at = at(ctx);
        prefs(ctx).edit().putLong("rm_" + pkg, at).apply();
        return "Strict mode: removed " + when(at);
    }

    /** Apply every pending change whose time has come. Cheap; called often. */
    static void applyDue(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (p.getAll().isEmpty()) return;
        long now = now(ctx);
        SharedPreferences.Editor e = p.edit();
        boolean changed = false;
        for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
            String k = en.getKey();
            if (k.startsWith("p_")) {
                try {
                    JSONObject j = new JSONObject((String) en.getValue());
                    if (j.getLong("at") > now) continue;
                    applyConfig(ctx, k.substring(2), j.getJSONObject("cfg"));
                } catch (Exception ignored) {
                }
                e.remove(k);
                changed = true;
            } else if (k.startsWith("rm_") && (Long) en.getValue() <= now) {
                Targets.removeApp(ctx, k.substring(3));
                e.remove(k);
                changed = true;
            }
        }
        if (p.contains("delay_at") && p.getLong("delay_at", 0) <= now) {
            e.putInt("delay", p.getInt("delay_to", 24)).remove("delay_at").remove("delay_to");
            changed = true;
        }
        if (p.contains("off_at") && p.getLong("off_at", 0) <= now) {
            e.putBoolean("on", false).remove("off_at");
            changed = true;
        }
        if (changed) e.apply();
    }

    private static void applyConfig(Context ctx, String cat, JSONObject cfg) throws Exception {
        Config c = Config.load(ctx, cat);
        c.fromJson(cfg);
        c.save(ctx);
    }

    /** Apply everything pending now (PIN entered). */
    private static void applyAll(Context ctx) {
        SharedPreferences p = prefs(ctx);
        SharedPreferences.Editor e = p.edit();
        for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
            String k = en.getKey();
            if (k.startsWith("p_")) e.putString(k, setAt((String) en.getValue()));
            else if (k.startsWith("rm_") || k.equals("delay_at") || k.equals("off_at")) e.putLong(k, 0);
        }
        e.commit();
        applyDue(ctx);
    }

    private static String setAt(String raw) {
        try {
            return new JSONObject(raw).put("at", 0).toString();
        } catch (Exception e) {
            return raw;
        }
    }

    static void cancel(Context ctx, String cat) {
        SharedPreferences.Editor e = prefs(ctx).edit().remove("p_" + cat);
        if (cat.startsWith(Limits.APP_PREFIX)) e.remove("rm_" + cat.substring(Limits.APP_PREFIX.length()));
        e.apply();
    }

    static long pendingAt(Context ctx, String cat) {
        SharedPreferences p = prefs(ctx);
        long at = 0;
        String raw = p.getString("p_" + cat, null);
        if (raw != null) {
            try {
                at = new JSONObject(raw).getLong("at");
            } catch (Exception ignored) {
            }
        }
        if (cat.startsWith(Limits.APP_PREFIX)) {
            long rm = p.getLong("rm_" + cat.substring(Limits.APP_PREFIX.length()), 0);
            if (rm > 0) at = at == 0 ? rm : Math.min(at, rm);
        }
        return at;
    }

    static void addPendingNote(Activity a, LinearLayout root, String cat) {
        applyDue(a);
        long at = pendingAt(a, cat);
        if (at == 0) return;
        boolean rm = cat.startsWith(Limits.APP_PREFIX)
                && prefs(a).contains("rm_" + cat.substring(Limits.APP_PREFIX.length()));
        root.addView(note(a, (rm ? "Removal" : "Looser settings") + " waiting: takes effect " + when(at)
                + ". The settings below are the ones in force now.",
                () -> {
                    cancel(a, cat);
                    restart(a);
                }));
    }

    /** Card with a pending-change message, Cancel and "Apply now with PIN". */
    private static LinearLayout note(Activity a, String msg, Runnable cancel) {
        LinearLayout box = Ui.column(a);
        box.setBackground(Ui.round(a, Ui.CARD, 12));
        int p = Ui.dp(a, 14);
        box.setPadding(p, p, p, p);
        box.addView(Ui.left(a, msg, 14, Ui.SKY));
        box.addView(Ui.space(a, 8));
        LinearLayout row = Ui.row(a);
        Button c = Ui.small(a, "Cancel change");
        c.setOnClickListener(v -> cancel.run());
        row.addView(c);
        if (hasPin(a)) {
            row.addView(Ui.space(a, 0), new LinearLayout.LayoutParams(Ui.dp(a, 8), 1));
            Button now = Ui.small(a, "Apply now (PIN)");
            now.setOnClickListener(v -> askPin(a, "Partner PIN", () -> {
                applyAll(a);
                Toast.makeText(a, L.t(a, "Applied"), Toast.LENGTH_SHORT).show();
                restart(a);
            }));
            row.addView(now);
        }
        box.addView(row);
        LinearLayout.LayoutParams lp = Ui.wide();
        lp.bottomMargin = Ui.dp(a, 12);
        box.setLayoutParams(lp);
        return box;
    }

    // ---- partner PIN --------------------------------------------------------------------

    static boolean hasPin(Context c) {
        return prefs(c).contains("pin");
    }

    private static String hash(String salt, String pin) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((salt + ":" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static boolean checkPin(Context c, String pin) {
        SharedPreferences p = prefs(c);
        return hasPin(c) && hash(p.getString("salt", ""), pin).equals(p.getString("pin", ""));
    }

    private static void setPin(Context c, String pin) {
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        StringBuilder salt = new StringBuilder();
        for (byte b : s) salt.append(String.format("%02x", b));
        prefs(c).edit().putString("salt", salt.toString()).putString("pin", hash(salt.toString(), pin)).apply();
    }

    private static void askPin(Activity a, String title, Runnable ok) {
        EditText e = pinField(a);
        new AlertDialog.Builder(a).setTitle(title).setView(e)
                .setPositiveButton("OK", (d, w) -> {
                    if (checkPin(a, e.getText().toString())) ok.run();
                    else Toast.makeText(a, L.t(a, "Wrong PIN"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private static EditText pinField(Activity a) {
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setHint("PIN (4+ digits)");
        return e;
    }

    // ---- emergency open -----------------------------------------------------------------

    static long emergencyNext(Context c) {
        long last = prefs(c).getLong("emergency", 0);
        return last == 0 ? 0 : last + WEEK;
    }

    /** One open of any target, past every limit, once a week, after a 60 s wait. */
    private static void emergency(Activity a) {
        long next = emergencyNext(a);
        if (next > now(a)) {
            Toast.makeText(a, L.t(a, "Next emergency open: " + when(next)), Toast.LENGTH_LONG).show();
            return;
        }
        java.util.List<String> cats = new java.util.ArrayList<>();
        for (String cat : Targets.all(a)) if (Config.load(a, cat).enabled) cats.add(cat);
        if (cats.isEmpty()) return;
        String[] names = new String[cats.size()];
        for (int i = 0; i < names.length; i++) names[i] = Limits.label(a, cats.get(i));
        new AlertDialog.Builder(a).setTitle("Emergency open: which one?")
                .setItems(names, (d, w) -> emergencyWait(a, cats.get(w)))
                .setNegativeButton("Cancel", null).show();
    }

    private static void emergencyWait(Activity a, String cat) {
        TextView t = Ui.text(a, "", 18, Ui.WHITE);
        int p = Ui.dp(a, 20);
        t.setPadding(p, p, p, p);
        AlertDialog d = new AlertDialog.Builder(a).setTitle("Wait " + EMERGENCY_WAIT_S + " seconds")
                .setView(t).setCancelable(false)
                .setPositiveButton("Open", null)
                .setNegativeButton("Never mind", null).create();
        d.show();
        Button open = d.getButton(AlertDialog.BUTTON_POSITIVE);
        open.setEnabled(false);
        long end = SystemClock.elapsedRealtime() + EMERGENCY_WAIT_S * 1000L;
        Handler h = new Handler(Looper.getMainLooper());
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!d.isShowing()) return;
                long left = (end - SystemClock.elapsedRealtime() + 999) / 1000;
                if (left > 0) {
                    t.setText(L.t(a, "Is this really an emergency?") + "\n\n" + L.t(a, left + " s"));
                    h.postDelayed(this, 250);
                } else {
                    t.setText("Uses your emergency open for this week.");
                    open.setEnabled(true);
                }
            }
        };
        tick.run();
        open.setOnClickListener(v -> {
            prefs(a).edit().putLong("emergency", now(a)).apply();
            new Limits(a).open(cat);
            d.dismiss();
            Toast.makeText(a, L.t(a, Limits.label(a, cat) + " open"), Toast.LENGTH_LONG).show();
            restart(a);
        });
    }

    // ---- settings guard (used by GuardService) ------------------------------------------

    static final Set<String> SETTINGS = new HashSet<>(java.util.Arrays.asList(
            "com.android.settings", "com.miui.securitycenter", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller", "com.google.android.permissioncontroller"));

    /** Words that mean "this screen can turn Focus Guard off", next to our name. */
    private static final String[] DANGER = {
            "force stop", "uninstall", "deactivate", "use focus guard", "stop focus guard",
            "turn off focus guard", "clear storage", "clear data", "disable",
            // Deactivate-admin page: its buttons are hidden from accessibility (API 34+).
            "admin app is active"};

    /** Should a settings screen with this text be left? Only in strict mode. */
    static boolean blocks(Context c, String text) {
        if (!on(c)) return false;
        // No time-based grace: any window of "blocking off" is a way to reach the off switch.
        if (text.contains(ADMIN_NOTE) && !adminOn(c)) return false;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("private dns") && t.contains(Setup.DNS_HOST.toLowerCase(Locale.ROOT))) return true;
        if (!t.contains("focus guard")) return false;
        for (String w : DANGER) if (t.contains(w)) return true;
        return false;
    }

    // ---- strict mode screen -------------------------------------------------------------

    static ComponentName admin(Context c) {
        return new ComponentName(c, Admin.class);
    }

    static boolean adminOn(Context c) {
        return c.getSystemService(DevicePolicyManager.class).isAdminActive(admin(c));
    }

    /** The accessibility volume-key shortcut can switch the service off from any screen. */
    static boolean shortcutRisk(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "accessibility_shortcut_target_service");
        return s != null && s.contains(c.getPackageName());
    }

    private static void restart(Activity a) {
        if (a instanceof MainActivity) ((MainActivity) a).refresh();
        else a.recreate();
    }

    static void screen(MainActivity a) {
        applyDue(a);
        SharedPreferences p = prefs(a);
        LinearLayout root = a.page("Strict mode");
        root.addView(Ui.left(a, "When on, anything that loosens your rules waits " + delayH(a)
                + " h before it takes effect. Making rules stricter always works at once. "
                + "Phones without root can't stop everything (safe mode, factory reset), "
                + "but the delay gives your calmer self time to say no.", 14, Ui.MUTED));
        root.addView(Ui.space(a, 12));

        if (p.contains("off_at")) {
            root.addView(note(a, "Turning off: takes effect " + when(p.getLong("off_at", 0)),
                    () -> {
                        p.edit().remove("off_at").apply();
                        a.refresh();
                    }));
        }
        root.addView(a.toggle("Strict mode", on(a), b -> {
            if (b) {
                p.edit().putBoolean("on", true).remove("off_at").apply();
                Toast.makeText(a, L.t(a, "Strict mode on"), Toast.LENGTH_SHORT).show();
            } else if (on(a) && !p.contains("off_at")) {
                long at = at(a);
                p.edit().putLong("off_at", at).apply();
                Toast.makeText(a, L.t(a, "Strict mode ends " + when(at)), Toast.LENGTH_LONG).show();
            }
            a.refresh();
        }));

        root.addView(a.section("Delay"));
        if (p.contains("delay_at")) {
            root.addView(note(a, "Delay " + p.getInt("delay_to", 24) + " h from " + when(p.getLong("delay_at", 0)),
                    () -> {
                        p.edit().remove("delay_at").remove("delay_to").apply();
                        a.refresh();
                    }));
        }
        LinearLayout row = Ui.row(a);
        for (int h : DELAYS) {
            Button b = Ui.small(a, h + " h");
            boolean cur = h == delayH(a);
            b.setBackground(Ui.round(a, cur ? Ui.SKY : Ui.CARD, 14));
            b.setTextColor(android.graphics.Color.parseColor(cur ? Ui.BG : Ui.WHITE));
            b.setOnClickListener(v -> {
                if (h >= delayH(a) || !on(a)) {
                    p.edit().putInt("delay", h).remove("delay_at").remove("delay_to").apply();
                } else {
                    long at = at(a);
                    p.edit().putLong("delay_at", at).putInt("delay_to", h).apply();
                    Toast.makeText(a, L.t(a, "Shorter delay from " + when(at)), Toast.LENGTH_LONG).show();
                }
                a.refresh();
            });
            row.addView(b);
            row.addView(Ui.space(a, 0), new LinearLayout.LayoutParams(Ui.dp(a, 6), 1));
        }
        root.addView(row);

        root.addView(a.section("Partner PIN"));
        root.addView(Ui.left(a, hasPin(a)
                ? "Set. The PIN applies a waiting change right away. Only your partner should know it."
                : "Let a friend type a PIN here. With it they can apply a waiting change at once.", 14, Ui.MUTED));
        root.addView(Ui.space(a, 8));
        Button pin = Ui.small(a, hasPin(a) ? "Change PIN" : "Set PIN");
        pin.setOnClickListener(v -> {
            Runnable set = () -> {
                EditText e = pinField(a);
                new AlertDialog.Builder(a).setTitle("New PIN").setView(e)
                        .setPositiveButton("Save", (d, w) -> {
                            String s = e.getText().toString();
                            if (s.length() < 4) {
                                Toast.makeText(a, L.t(a, "At least 4 digits"), Toast.LENGTH_SHORT).show();
                                return;
                            }
                            setPin(a, s);
                            Toast.makeText(a, L.t(a, "PIN saved"), Toast.LENGTH_SHORT).show();
                            a.refresh();
                        })
                        .setNegativeButton("Cancel", null).show();
            };
            if (hasPin(a)) askPin(a, "Current PIN", set);
            else set.run();
        });
        root.addView(pin);

        root.addView(a.section("Emergency open"));
        long next = emergencyNext(a);
        root.addView(Ui.left(a, "One open of any target past every limit, once a week, after a "
                + EMERGENCY_WAIT_S + " s wait." + (next > now(a) ? " Next: " + when(next) + "." : " Available."),
                14, Ui.MUTED));
        root.addView(Ui.space(a, 8));
        Button em = Ui.small(a, "Emergency open");
        em.setEnabled(next <= now(a));
        em.setOnClickListener(v -> emergency(a));
        root.addView(em);

        // Play build: no device admin, no settings guard (Play policy), so nothing to show here.
        if (Flavor.PLAY) {
            a.show(root);
            return;
        }
        root.addView(a.section("Protection"));
        boolean admin = adminOn(a);
        root.addView(Ui.left(a, admin
                ? "Uninstall blocked: Focus Guard is a device admin. Strict mode keeps you off that page."
                : "Make Focus Guard a device admin so it can't be uninstalled without first going "
                + "through a page strict mode keeps you away from.", 14, Ui.MUTED));
        if (!admin) {
            root.addView(Ui.space(a, 8));
            Button b = Ui.small(a, "Block uninstall");
            b.setOnClickListener(v -> {
                // Not via Setup.open: Android refuses ADD_DEVICE_ADMIN started as a new task.
                try {
                    a.startActivity(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(a))
                            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ADMIN_NOTE));
                } catch (Exception ignored) {
                }
            });
            root.addView(b);
        }
        if (shortcutRisk(a)) {
            root.addView(Ui.space(a, 12));
            TextView w = Ui.left(a, "The volume-key accessibility shortcut can turn Focus Guard off from "
                    + "any screen. Remove it: Settings > Accessibility > Focus Guard > shortcut off.", 14, Ui.RED);
            w.setTypeface(Typeface.DEFAULT_BOLD);
            root.addView(w);
        }
        root.addView(Ui.space(a, 12));
        root.addView(Ui.left(a, "In strict mode the screens that switch Focus Guard off (its accessibility "
                + "page, App info, device admin, Private DNS) close as soon as they open.", 13, "#6B7280"));
        a.show(root);
    }
}
