package com.alrasin.focusguard;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adult keyword matcher for on-screen Telegram text (assets/keywords.json + keywords.local.json). */
final class Keywords {
    private final List<String> strong = new ArrayList<>();
    private final List<String> weak = new ArrayList<>();
    private final List<String> safe = new ArrayList<>();

    /** keywords.json is the shared list; keywords.local.json (optional, not committed) adds personal entries. */
    Keywords(Context ctx) {
        for (String file : new String[]{"keywords.json", "keywords.local.json"}) {
            try (InputStream in = ctx.getAssets().open(file)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                JSONObject json = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
                load(json.optJSONArray("strong"), strong);
                load(json.optJSONArray("weak"), weak);
                load(json.optJSONArray("safe_exceptions"), safe);
            } catch (Exception ignored) {
            }
        }
    }

    private static void load(JSONArray arr, List<String> into) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            String s = normalize(arr.optString(i));
            if (!s.isEmpty()) into.add(s);
        }
    }

    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[\\u200b-\\u200f\\u2060\\ufeff]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Latin letters and digits only, so "p o r n" and "p.o.r.n" become "porn". */
    private static String compact(String s) {
        return s.replaceAll("[^a-z0-9]", "");
    }

    /** Returns the matched term, or null if the text looks clean. */
    String match(String rawText) {
        String text = normalize(rawText);
        for (String s : safe) text = text.replace(s, " ");
        String packed = compact(text);

        for (String k : strong) {
            if (text.contains(k)) return k;
            String ck = compact(k);
            if (ck.length() >= 5 && ck.equals(k.replace(" ", "")) && packed.contains(ck)) return k;
        }
        String first = null;
        for (String k : weak) {
            if (text.contains(k)) {
                if (first == null) first = k;
                else if (!first.contains(k) && !k.contains(first)) return first + " + " + k;
            }
        }
        return null;
    }
}
