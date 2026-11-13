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
        return en.matches("[\\d:.,/%+\\- ]+") ? digits(en) : en;
    }

    /** "01:29" → "০১:২৯": numbers filled into a pattern (times, counts) in Bangla digits. */
    private static String digits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('\u09E6' + ch - '0') : ch);
        return b.toString();
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
        add("What were you about to do before this?", "এর আগে আপনি কী করতে যাচ্ছিলেন?");
        add("How do you want to feel in 15 minutes?", "১৫ মিনিট পর কেমন অনুভব করতে চান?");
        add("Is this a choice, or a habit?", "এটা কি আপনার সিদ্ধান্ত, নাকি অভ্যাস?");
        add("Are you bored, tired or avoiding something?", "আপনি কি বিরক্ত, ক্লান্ত, নাকি কিছু এড়িয়ে যাচ্ছেন?");
        add("Will you remember this video tomorrow?", "কাল কি এই ভিডিওটা মনে থাকবে?");
        add("What would make the next hour good?", "পরের এক ঘণ্টা ভালো কাটাতে কী করবেন?");

        // Tile, widget, weekly report
        add("Protected", "সুরক্ষিত");
        add("Stopped – tap to fix", "বন্ধ – ঠিক করতে ট্যাপ করুন");
        add("Today: %s min · went back %s times", "আজ: %s মিনিট · %s বার ফিরে গেছেন");
        add("Weekly report", "সাপ্তাহিক রিপোর্ট");
        add("Last week: %s min, %s opens, went back %s times.",
                "গত সপ্তাহ: %s মিনিট, %s বার খোলা, %s বার ফিরে গেছেন।");
        add("%s min less than the week before.", "আগের সপ্তাহের চেয়ে %s মিনিট কম।");
        add("%s min more than the week before.", "আগের সপ্তাহের চেয়ে %s মিনিট বেশি।");
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
        add(Setup.CONSENT_TEXT,
                "Focus Guard অ্যান্ড্রয়েডের Accessibility সার্ভিস ব্যবহার করে। রাজি হওয়ার আগে এটা কী করে পড়ে নিন।\n\n"
                + "কী পড়ে: স্ক্রিনে কোন অ্যাপ খোলা; ইউটিউব, ফেসবুক, ইনস্টাগ্রাম ও টিকটকের স্ক্রিন-উপাদানের নাম "
                + "(শর্টস ও রিলস চিনতে); ব্রাউজারের ওয়েব ঠিকানা; টেলিগ্রাম চ্যাট ও সার্চের লেখা; আর স্ট্রিক্ট মোডে "
                + "Focus Guard-এর নিজের সেটিংস পেজ খোলা কি না।\n\n"
                + "কেন: ছোট ভিডিওর আগে বিরতির স্ক্রিন দেখাতে, খোলা ও মিনিট গুনতে, অ্যাডাল্ট ওয়েবসাইট ও অ্যাডাল্ট "
                + "টেলিগ্রাম চ্যাট ব্লক করতে, আর স্ট্রিক্ট মোডে হুট করে বন্ধ করা ঠেকাতে।\n\n"
                + "স্ক্রিনে কী করে: ব্লক করা পেজ থেকে বের হতে Back বা Home চাপতে পারে, বা ঠিকানার ঘরে google.com লিখতে পারে।\n\n"
                + "কখনো যা করে না: স্ক্রিনের লেখা সেভ, পাঠানো বা শেয়ার হয় না। শুধু গোনা আর সেটিংস এই ফোনে থাকে। "
                + "অ্যাপের ইন্টারনেট পারমিশন নেই, বিজ্ঞাপন নেই, অ্যানালিটিক্স নেই, অ্যাকাউন্ট নেই।");
        add("Accessibility service: your consent", "Accessibility সার্ভিস: আপনার সম্মতি");
        add("Agree", "রাজি");
        add("Focus Guard is not active", "Focus Guard চালু নেই");
        add("You chose not to allow the Accessibility service. Without it Focus Guard "
                + "can't see Shorts, Reels or adult sites, so it does nothing.",
                "আপনি Accessibility সার্ভিসের অনুমতি দেননি। এটা ছাড়া Focus Guard শর্টস, রিলস বা অ্যাডাল্ট সাইট "
                + "দেখতে পায় না, তাই কিছুই করে না।");
        add("Read again", "আবার পড়ুন");
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
        add("Settings → Private DNS → \"Private DNS provider hostname\" → paste %s. If some sites stop loading on your network, set it back to Automatic.",
                "Settings → Private DNS → \"Private DNS provider hostname\" → %s পেস্ট করুন। আপনার নেটওয়ার্কে কিছু সাইট না খুললে আবার Automatic করে দিন।");
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

        // Home hero + focus session
        add("Protection is on", "সুরক্ষা চালু");
        add("Protection is on (root watchdog)", "সুরক্ষা চালু (রুট ওয়াচডগ)");
        add("min today", "মিনিট আজ");
        add("No short videos today. Keep it up.", "আজ কোনো ছোট ভিডিও নয়। এভাবেই চালিয়ে যান।");
        add("You went back %s times today. Well done.", "আজ %s বার ফিরে গেছেন। দারুণ।");
        add("%s days in a row without extending.", "টানা %s দিন বাড়ানো ছাড়া।");
        add("Every pause is a choice. You are doing fine.", "প্রতিটি বিরতি একটি সিদ্ধান্ত। আপনি ভালো করছেন।");
        add("Focus session until %s", "ফোকাস সেশন %s পর্যন্ত");
        add("%s min left. Every target is closed.", "আর %s মিনিট। সব টার্গেট বন্ধ।");
        add("Focus now", "এখন ফোকাস");
        add("Close every target for a while. Can't be undone.", "কিছুক্ষণ সব টার্গেট বন্ধ রাখুন। ফেরানো যাবে না।");
        add("30 min", "৩০ মিনিট");
        add("1 hour", "১ ঘণ্টা");
        add("2 hours", "২ ঘণ্টা");
        add("Focus for %s?", "%s ফোকাস করবেন?");
        add("Shorts, Reels and every app you added stay closed until %s. You can't end it early.",
                "Shorts, Reels আর আপনার যোগ করা সব অ্যাপ %s পর্যন্ত বন্ধ থাকবে। আগে শেষ করা যাবে না।");
        add("Start", "শুরু");
        add("Cancel", "বাতিল");
        add("focus session until %s", "ফোকাস সেশন %s পর্যন্ত");
        add("%s s pause", "%s সেকেন্ড বিরতি");
        add("%s opens/day", "দিনে %s বার");
        add("%s min each", "প্রতিবার %s মিনিট");
        add("no opens", "খোলা যাবে না");

        // Stats
        add("Today", "আজ");
        add("opens", "বার খোলা");
        add("went back", "ফিরে গেছেন");
        add("minutes", "মিনিট");
        add("day streak", "দিনের ধারা");
        add("Last 7 days", "গত ৭ দিন");
        add("Last 7 days: %s opens, %s times you went back, %s min", "গত ৭ দিন: %s বার খোলা, %s বার ফিরে গেছেন, %s মিনিট");
        add(", %s videos", ", %sটি ভিডিও");
        add("%s min less than the week before.", "আগের সপ্তাহের চেয়ে %s মিনিট কম।");
        add("%s min more than the week before.", "আগের সপ্তাহের চেয়ে %s মিনিট বেশি।");
        add("Same as the week before.", "আগের সপ্তাহের মতোই।");

        add("Minutes per day", "দিনে কত মিনিট");

        // New UI
        add("Home", "হোম");
        add("Stats", "হিসাব");
        add("Settings", "সেটিংস");
        add("Presets", "তৈরি সেটিংস");
        add("Gentle", "হালকা");
        add("Balanced", "মাঝামাঝি");
        add("Strict", "কড়া");
        add("A preset fills in the numbers below. Nothing changes until you save.",
                "তৈরি সেটিংস নিচের সংখ্যাগুলো ভরে দেয়। সেভ না করা পর্যন্ত কিছু বদলায় না।");
        add("Open now", "এখন খোলা");
        add("Closed", "বন্ধ");
        add("%s left", "%s বাকি");
        add("Off", "বন্ধ");
        add("Not watched", "নজরে নেই");
        add("Opened %s of %s today", "আজ %s/%s বার খোলা হয়েছে");
        add("Opened %s of %s today · whole app", "আজ %s/%s বার খোলা হয়েছে · পুরো অ্যাপ");

        // Pause screen (new)
        add("Breathe in", "শ্বাস নিন");
        add("Breathe out", "শ্বাস ছাড়ুন");
        add("Opened %s times today · %s min", "আজ %s বার খোলা · %s মিনিট");
        add("%s min", "%s মিনিট");
        add("%s s", "%s সেকেন্ড");
        add("Is this really an emergency?", "সত্যিই কি জরুরি?");
        add("I don't want to open %s", "আমি %s খুলতে চাই না");
        add("Good moment to do something else.", "অন্য কিছু করার ভালো সময়।");
        add("Take a breath. Do you really need this now?", "একটু শ্বাস নিন। এখন কি সত্যিই দরকার?");
        add("Wait for the timer to open it", "খুলতে টাইমার শেষ হওয়া পর্যন্ত অপেক্ষা করুন");
        add("Write why first", "আগে কারণ লিখুন");
        add("Or", "অথবা");
        add("Or open it for", "অথবা খুলুন");

        // Keep-alive notice
        add("Focus Guard stopped", "Focus Guard থেমে গেছে");
        add("Focus Guard is off", "Focus Guard বন্ধ");
        add("Android stopped it. Tap to fix.", "অ্যান্ড্রয়েড এটা থামিয়েছে। ঠিক করতে ট্যাপ করুন।");
        add("Tap to turn it back on.", "আবার চালু করতে ট্যাপ করুন।");
    }
}
