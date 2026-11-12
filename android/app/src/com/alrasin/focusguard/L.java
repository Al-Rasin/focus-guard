package com.alrasin.focusguard;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI language. English text is the key; {@link #t} returns the Bangla version when the app
 * language is Bangla and a translation exists, else the English text unchanged. Keys with
 * {@code %s} match strings built at runtime (numbers, names, times) and carry those parts over.
 * Ui.text/left/button run every string through here, so most screens need no changes.
 */
final class L {
    static final String PREF = "lang"; // in Config prefs: "" = phone language, "en", "bn"

    private static final Map<String, String> EXACT = new HashMap<>();
    private static final List<Pattern> PAT = new ArrayList<>();
    private static final List<String> PAT_BN = new ArrayList<>();
    private static Boolean bangla;

    private L() {
    }

    static String setting(Context c) {
        return Config.prefs(c).getString(PREF, "");
    }

    static void set(Context c, String lang) {
        Config.prefs(c).edit().putString(PREF, lang).apply();
        bangla = null;
    }

    static boolean bangla(Context c) {
        if (bangla == null) {
            String s = setting(c);
            bangla = s.isEmpty() ? "bn".equals(Locale.getDefault().getLanguage()) : "bn".equals(s);
        }
        return bangla;
    }

    static String t(Context c, String en) {
        if (en == null || en.isEmpty() || !bangla(c)) return en;
        String bn = EXACT.get(en);
        if (bn != null) return bn;
        for (int i = 0; i < PAT.size(); i++) {
            Matcher m = PAT.get(i).matcher(en);
            if (!m.matches()) continue;
            String out = PAT_BN.get(i);
            for (int g = 1; g <= m.groupCount(); g++)
                out = out.replaceFirst("%s", Matcher.quoteReplacement(t(c, m.group(g))));
            return out;
        }
        return en;
    }

    private static void add(String en, String bn) {
        if (!en.contains("%s")) {
            EXACT.put(en, bn);
            return;
        }
        StringBuilder re = new StringBuilder();
        String[] parts = en.split("%s", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) re.append("(.+?)");
            if (!parts[i].isEmpty()) re.append(Pattern.quote(parts[i]));
        }
        PAT.add(Pattern.compile(re.toString(), Pattern.DOTALL));
        PAT_BN.add(bn);
    }

    static {
        // Home and targets
        add("Targets", "টার্গেট");
        add("+ Add an app", "+ একটি অ্যাপ যোগ করুন");
        add("Add an app", "অ্যাপ যোগ করুন");
        add("The whole app gets the pause and opens limit.", "পুরো অ্যাপে বিরতি আর খোলার সীমা লাগবে।");
        add("Do this instead", "এর বদলে এটা করুন");
        add("Your own ideas, one per line. The pause screen shows one at random.",
                "আপনার নিজের আইডিয়া, প্রতি লাইনে একটি। বিরতির স্ক্রিনে এলোমেলোভাবে একটি দেখাবে।");
        add("Save ideas", "আইডিয়া সেভ করুন");
        add("Saved", "সেভ হয়েছে");
        add("Off", "বন্ধ");
        add("Open now · %s/%s left", "এখন খোলা · %s/%s বাকি");
        add("Closed: %s", "বন্ধ: %s");
        add("%s of %s opens left today · whole app", "আজ %s/%s বার খোলা বাকি · পুরো অ্যাপ");
        add("%s of %s opens left today", "আজ %s/%s বার খোলা বাকি");
        add("YouTube Shorts", "ইউটিউব শর্টস");
        add("Facebook Reels", "ফেসবুক রিলস");
        add("Facebook", "ফেসবুক");
        add("Instagram Reels", "ইনস্টাগ্রাম রিলস");
        add("TikTok", "টিকটক");
        add("Language", "ভাষা");
        add("Phone", "ফোনের");
        add("Backup", "ব্যাকআপ");
        add("Save settings to a file", "সেটিংস ফাইলে সেভ করুন");
        add("Load settings from a file", "ফাইল থেকে সেটিংস আনুন");
        add("Targets, their settings and your ideas. Also works with the Mac extension.",
                "টার্গেট, সেগুলোর সেটিংস আর আপনার আইডিয়া। ম্যাক এক্সটেনশনেও চলে।");
        add("Settings saved to the file", "সেটিংস ফাইলে সেভ হয়েছে");
        add("Not a Focus Guard settings file", "এটা Focus Guard সেটিংস ফাইল নয়");
        add("Could not read the file", "ফাইলটি পড়া গেল না");
        add("Could not write the file", "ফাইলে লেখা গেল না");
        add("Loaded %s targets", "%s টি টার্গেট আনা হয়েছে");
        add("Loaded %s targets. Looser ones wait for strict mode.",
                "%s টি টার্গেট আনা হয়েছে। ঢিলা সেটিংস স্ট্রিক্ট মোডের অপেক্ষায়।");

        // Edit screen
        add("On", "চালু");
        add("Block", "ব্লক");
        add("Only Shorts", "শুধু শর্টস");
        add("Only Reels", "শুধু রিলস");
        add("Whole app", "পুরো অ্যাপ");
        add("Pause", "বিরতি");
        add("Pause length", "বিরতির সময়");
        add("Longer each open by", "প্রতিবার খুললে বাড়বে");
        add("Ask why before opening", "খোলার আগে কারণ জিজ্ঞেস করুক");
        add("Opens", "খোলা");
        add("Opens per day", "দিনে কতবার খোলা");
        add("Minutes per open", "প্রতিবার কত মিনিট");
        add("Max minutes per day", "দিনে সর্বোচ্চ মিনিট");
        add("Wait between opens", "দুইবার খোলার মাঝে অপেক্ষা");
        add("End open after videos", "এতগুলো ভিডিওর পর শেষ");
        add("Time-left tab", "বাকি সময়ের ট্যাব");
        add("Extend (after the last open)", "বাড়ানো (শেষ খোলার পর)");
        add("Opens added", "কতবার যোগ হবে");
        add("Extends per day", "দিনে কতবার বাড়ানো যাবে");
        add("Only before", "শুধু এর আগে");
        add("Blocked hours", "ব্লকের সময়");
        add("No opens at all in these windows, e.g. 22:00-07:00. One per line.",
                "এই সময়গুলোতে একদমই খোলা যাবে না, যেমন 22:00-07:00। প্রতি লাইনে একটি।");
        add("Save", "সেভ");
        add("Remove this app", "এই অ্যাপ সরান");
        add("Removed", "সরানো হয়েছে");
        add("off", "বন্ধ");
        add("none", "নেই");
        add("Not a time window: %s", "সময়ের ঘর নয়: %s");

        // Pause screen
        add("Go back", "ফিরে যান");
        add("Take a breath. Do you really need this now?", "একটু শ্বাস নিন। এখন কি সত্যিই দরকার?");
        add("Or instead: %s", "বরং: %s");
        add("Daily limit reached", "আজকের সীমা শেষ");
        add("Daily limit reached. Extensions left today: %s", "আজকের সীমা শেষ। আজ আর বাড়ানো যাবে: %s বার");
        add("Next open in %s min", "পরের বার খোলা যাবে %s মিনিট পর");
        add("Why are you opening it?", "কেন খুলছেন?");
        add("Extend +%s opens", "আরও %s বার খুলুন");
        add("Open (%s min)", "খুলুন (%s মিনিট)");
        add("last min", "শেষ মিনিট");
        add("left", "বাকি");
        add("%s videos watched, open ended", "%s টি ভিডিও দেখা হয়েছে, শেষ");
        add("Adult site blocked", "অ্যাডাল্ট সাইট ব্লক করা হয়েছে");
        add("%s: next open in %s min", "%s: পরের বার খোলা যাবে %s মিনিট পর");
        add("%s: daily limit reached", "%s: আজকের সীমা শেষ");
        add("Strict mode is on. Changes to Focus Guard wait %s h.", "স্ট্রিক্ট মোড চালু। Focus Guard-এর পরিবর্তন %s ঘণ্টা অপেক্ষা করবে।");
        add("Not saved", "সেভ হয়নি");
        add("Adult channel blocked", "অ্যাডাল্ট চ্যানেল ব্লক করা হয়েছে");

        // Setup
        add("Before you start", "শুরু করার আগে");
        add("Focus Guard uses Android's Accessibility service to see which screen is open: "
                + "the app, the browser address, and on-screen text in Telegram chats. It uses this only to "
                + "show the pause screen, count opens and block adult content.\n\n"
                + "Everything stays on this phone. The app has no internet permission, no ads and no analytics.",
                "Focus Guard অ্যান্ড্রয়েডের Accessibility সার্ভিস দিয়ে দেখে কোন স্ক্রিন খোলা: অ্যাপ, "
                + "ব্রাউজারের ঠিকানা আর টেলিগ্রাম চ্যাটের লেখা। এটা শুধু বিরতির স্ক্রিন দেখাতে, খোলা গুনতে "
                + "আর অ্যাডাল্ট কনটেন্ট ব্লক করতে ব্যবহার হয়।\n\n"
                + "সবকিছু এই ফোনেই থাকে। অ্যাপের ইন্টারনেট পারমিশন নেই, বিজ্ঞাপন নেই, অ্যানালিটিক্স নেই।");
        add("I understand", "বুঝেছি");
        add("Finish setup", "সেটআপ শেষ করুন");
        add("Android stops apps it thinks are idle. Each step keeps Focus Guard running.",
                "অ্যান্ড্রয়েড অলস মনে হওয়া অ্যাপ বন্ধ করে দেয়। প্রতিটি ধাপ Focus Guard চালু রাখে।");
        add("Turn on Focus Guard in Accessibility", "Accessibility-তে Focus Guard চালু করুন");
        add("Accessibility service on", "Accessibility সার্ভিস চালু");
        add("Enabled but not running: turn it off and on again", "চালু কিন্তু চলছে না: বন্ধ করে আবার চালু করুন");
        add("Greyed out (\"Restricted setting\")? App info → ⋮ menu → Allow restricted settings, then try again.",
                "ধূসর (\"Restricted setting\")? App info → ⋮ মেনু → Allow restricted settings, তারপর আবার চেষ্টা করুন।");
        add("App info", "অ্যাপ ইনফো");
        add("Battery: unrestricted", "ব্যাটারি: আনরেস্ট্রিক্টেড");
        add("Notifications allowed", "নোটিফিকেশন চালু");
        add("Autostart allowed (%s)", "অটোস্টার্ট চালু (%s)");
        add("Open", "খুলুন");
        add("Allow", "অনুমতি দিন");
        add("✓ Protection is on", "✓ সুরক্ষা চালু");
        add("✓ Protection is on (root watchdog)", "✓ সুরক্ষা চালু (রুট ওয়াচডগ)");
        add("Optional: filter adult sites in all apps", "ঐচ্ছিক: সব অ্যাপে অ্যাডাল্ট সাইট ফিল্টার");
        add("Copy host & open settings", "হোস্ট কপি করে সেটিংস খুলুন");
        add("No thanks", "দরকার নেই");
        add("Copied %s", "কপি হয়েছে %s");

        // Strict mode
        add("Protection", "সুরক্ষা");
        add("Strict mode", "স্ট্রিক্ট মোড");
        add("Strict mode: off", "স্ট্রিক্ট মোড: বন্ধ");
        add("Strict mode: on (%s h delay)", "স্ট্রিক্ট মোড: চালু (%s ঘণ্টা দেরি)");
        add("Strict mode on", "স্ট্রিক্ট মোড চালু");
        add("Strict mode: takes effect %s", "স্ট্রিক্ট মোড: কার্যকর হবে %s");
        add("Strict mode: removed %s", "স্ট্রিক্ট মোড: সরানো হবে %s");
        add("Strict mode ends %s", "স্ট্রিক্ট মোড শেষ হবে %s");
        add("Delay", "দেরি");
        add("Partner PIN", "পার্টনার পিন");
        add("Set PIN", "পিন সেট করুন");
        add("Change PIN", "পিন বদলান");
        add("Current PIN", "বর্তমান পিন");
        add("PIN (4+ digits)", "পিন (৪+ সংখ্যা)");
        add("PIN saved", "পিন সেভ হয়েছে");
        add("Wrong PIN", "ভুল পিন");
        add("At least 4 digits", "অন্তত ৪টি সংখ্যা");
        add("Apply now (PIN)", "এখনই প্রয়োগ (পিন)");
        add("Applied", "প্রয়োগ হয়েছে");
        add("Cancel change", "পরিবর্তন বাতিল");
        add("Emergency open", "জরুরি খোলা");
        add("Uses your emergency open for this week.", "এই সপ্তাহের জরুরি খোলা খরচ হবে।");
        add("Next emergency open: %s", "পরের জরুরি খোলা: %s");
        add("%s open", "%s খোলা");
        add("Block uninstall", "আনইনস্টল ব্লক");
        add("Removal waiting: takes effect %s", "সরানো অপেক্ষায়: কার্যকর হবে %s");
        add("Looser settings waiting: takes effect %s", "ঢিলা সেটিংস অপেক্ষায়: কার্যকর হবে %s");
        add("Turning off: takes effect %s", "বন্ধ হচ্ছে: কার্যকর হবে %s");
        add("Shorter delay from %s", "কম দেরি শুরু %s");

        // Stats
        add("Today", "আজ");
        add("opens", "বার খোলা");
        add("went back", "ফিরে গেছেন");
        add("minutes", "মিনিট");
        add("day streak", "দিনের ধারা");
        add("Last 7 days", "গত ৭ দিন");

        // Keep-alive notice
        add("Focus Guard stopped", "Focus Guard থেমে গেছে");
        add("Focus Guard is off", "Focus Guard বন্ধ");
        add("Android stopped it. Tap to fix.", "অ্যান্ড্রয়েড এটা থামিয়েছে। ঠিক করতে ট্যাপ করুন।");
        add("Tap to turn it back on.", "আবার চালু করতে ট্যাপ করুন।");
    }
}
