package com.alrasin.focusguard;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Settings to and from a JSON file the user picks (Storage Access Framework, so no storage
 * permission). Format, shared with the Mac extension's options page:
 * {"focusguard":1, "targets":{cat: config...}, "apps":[packages], "swaps":"one idea per line"}.
 * Import goes through Strict.save, so under strict mode looser settings still wait out the
 * delay. Adult blocking is not a setting, so a file can never turn it off.
 */
final class Backup {
    static final int EXPORT = 41, IMPORT = 42;

    private Backup() {
    }

    static void addButtons(MainActivity m, LinearLayout root) {
        root.addView(m.section("Backup"));
        root.addView(Ui.left(m, "Targets, their settings and your ideas. Also works with the Mac extension.",
                14, Ui.MUTED));
        root.addView(Ui.space(m, 8));
        Button out = Ui.small(m, "Save settings to a file");
        out.setOnClickListener(v -> m.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "focus-guard-settings.json"), EXPORT));
        root.addView(out, m.card());
        Button in = Ui.small(m, "Load settings from a file");
        in.setOnClickListener(v -> m.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT));
        root.addView(in, m.card());
    }

    /** Called from MainActivity.onActivityResult. Returns true if it handled the request. */
    static boolean onResult(Activity a, int req, int res, Intent data) {
        if (req != EXPORT && req != IMPORT) return false;
        if (res != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        String msg = req == EXPORT ? export(a, data.getData()) : load(a, data.getData());
        Toast.makeText(a, L.t(a, msg), Toast.LENGTH_LONG).show();
        return true;
    }

    static JSONObject dump(Activity a) throws Exception {
        JSONObject targets = new JSONObject();
        for (String cat : Targets.all(a)) targets.put(cat, Config.load(a, cat).toJson());
        JSONArray apps = new JSONArray();
        for (String pkg : Targets.apps(a)) apps.put(pkg);
        return new JSONObject().put("focusguard", 1).put("targets", targets).put("apps", apps)
                .put("swaps", Config.swapsText(a));
    }

    private static String export(Activity a, Uri uri) {
        try (OutputStream os = a.getContentResolver().openOutputStream(uri, "wt")) {
            os.write(dump(a).toString(2).getBytes(StandardCharsets.UTF_8));
            return "Settings saved to the file";
        } catch (Exception e) {
            return "Could not write the file";
        }
    }

    private static String load(Activity a, Uri uri) {
        JSONObject j;
        try (InputStream is = a.getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0 && buf.size() < 1_000_000) buf.write(b, 0, n);
            j = new JSONObject(buf.toString("UTF-8"));
        } catch (Exception e) {
            return "Could not read the file";
        }
        if (j.optInt("focusguard", 0) != 1 || j.optJSONObject("targets") == null)
            return "Not a Focus Guard settings file";

        JSONArray apps = j.optJSONArray("apps");
        if (apps != null)
            for (int i = 0; i < apps.length(); i++) {
                String pkg = apps.optString(i, "").trim();
                if (pkg.matches("[A-Za-z0-9_.]+") && !pkg.equals(a.getPackageName())) Targets.addApp(a, pkg);
            }

        JSONObject targets = j.optJSONObject("targets");
        int done = 0;
        boolean waiting = false;
        for (Iterator<String> it = targets.keys(); it.hasNext(); ) {
            String cat = it.next();
            boolean known = Arrays.asList(Limits.BUILTIN).contains(cat)
                    || (cat.startsWith(Limits.APP_PREFIX)
                    && Targets.apps(a).contains(cat.substring(Limits.APP_PREFIX.length())));
            JSONObject cfg = targets.optJSONObject(cat);
            if (!known || cfg == null) continue;
            Config c = Config.load(a, cat);
            try {
                c.fromJson(cfg);
            } catch (Exception e) {
                continue;
            }
            if (!"Saved".equals(Strict.save(a, c))) waiting = true;
            done++;
        }
        String swaps = j.optString("swaps", "");
        if (!swaps.trim().isEmpty()) Config.saveSwaps(a, swaps);
        return (waiting ? "Loaded %s targets. Looser ones wait for strict mode." : "Loaded %s targets")
                .replace("%s", String.valueOf(done));
    }
}
