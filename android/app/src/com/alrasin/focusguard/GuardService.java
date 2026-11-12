package com.alrasin.focusguard;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Build;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class GuardService extends AccessibilityService {
    static GuardService instance;

    private static final String YOUTUBE = "com.google.android.youtube";
    private static final Set<String> FACEBOOK = new HashSet<>(Arrays.asList(
            "com.facebook.katana", "com.facebook.lite"));
    private static final Set<String> TELEGRAM = new HashSet<>(Arrays.asList(
            "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus",
            "org.thunderdog.challegram", "tw.nekomimi.nekogram", "xyz.nextalone.nagram",
            "com.exteragram.messenger", "org.telegram.mdgram"));
    private static final Set<String> BROWSERS = new HashSet<>(Arrays.asList(
            "com.android.chrome", "com.brave.browser", "com.chrome.beta", "com.kiwibrowser.browser",
            "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native",
            "com.sec.android.app.sbrowser", "com.mi.globalbrowser", "com.android.browser",
            "org.mozilla.firefox", "org.mozilla.focus", "com.duckduckgo.mobile.android",
            "com.vivaldi.browser", "com.UCMobile.intl", "com.uc.browser.en"));
    private static final String[] URL_BAR_IDS = {
            ":id/url_bar", ":id/mozac_browser_toolbar_url_view", ":id/url", ":id/omnibarTextInput",
            ":id/search_src_text", ":id/url_field"};
    private static final String[] SHORTS_IDS = {
            ":id/reel_watch_player", ":id/reel_watch_fragment_root", ":id/reel_recycler",
            ":id/reel_player_page_container"};

    /** Instagram Reels viewer. Not verified on a device yet: calibrate with uiautomator dump. */
    private static final String[] INSTA_REELS_IDS = {
            ":id/clips_viewer_view_pager", ":id/clips_viewer_container", ":id/clips_video_container",
            ":id/clips_root_layout"};

    private static final Pattern SHORTS_URL = Pattern.compile("youtube\\.com/shorts");
    private static final Pattern YOUTUBE_SITE = Pattern.compile("^(https?://)?([a-z0-9-]+\\.)*youtube\\.com(?![a-z0-9.-])|youtu\\.be/");
    private static final Pattern INSTA_REELS_URL = Pattern.compile("instagram\\.com/(reels?|stories)\\b");
    private static final Pattern INSTA_SITE = Pattern.compile("^(https?://)?([a-z0-9-]+\\.)*instagram\\.com(?![a-z0-9.-])");
    private static final Pattern TIKTOK_SITE = Pattern.compile("^(https?://)?([a-z0-9-]+\\.)*tiktok\\.com(?![a-z0-9.-])");
    private static final Pattern REELS_URL = Pattern.compile(
            "facebook\\.com/(reel|reels|watch/reels)\\b|fb\\.watch/|facebook\\.com/share/r/");
    // facebook.com in a browser is its own category (Limits.FACEBOOK), except Messages and what logging in/opening links needs.
    private static final Pattern FACEBOOK_SITE = Pattern.compile(
            "^(https?://)?([a-z0-9-]+\\.)*facebook\\.com(?![a-z0-9.-])");
    private static final Pattern FACEBOOK_OK = Pattern.compile(
            "^(https?://)?((l|lm)\\.facebook\\.com|([a-z0-9-]+\\.)*facebook\\.com/(messages|messenger_media|login"
            + "|checkpoint|recover|two_step_verification|two_factor|groupcall|videocall)(?![a-z0-9_-]))");

    private static final int MAX_NODES = 700;
    private static final String ADULT = "adult";
    private static final String SAFE_PAGE = "https://www.google.com";
    private static final int ESCAPE_STEPS = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Limits limits;
    private Keywords keywords;
    private SessionTimer timer;
    private static final long HEARTBEAT_MS = 5000, ESCAPE_STALE_MS = 15_000;

    private long lastPauseAt, lastKickAt, lastScan, escapeStartedAt;
    private boolean scanQueued, escaping;
    private final Runnable rescan = this::queueScan;
    private final Runnable heartbeat = this::heartbeat;
    private File alive;
    /** Packages worth scanning (targets + browsers + Telegram), refreshed on each heartbeat. */
    private Set<String> watched = new HashSet<>();
    private long lastVideoAt;
    private String lastTrace;

    /** Decision log (`adb logcat -s FocusGuard`), only when it changes. */
    private void trace(String msg) {
        if (msg.equals(lastTrace)) return;
        lastTrace = msg;
        Log.i("FocusGuard", msg);
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        Health.changed(this);
        limits = new Limits(this);
        keywords = new Keywords(this);
        AdultDomains.load(this);
        timer = new SessionTimer(this, handler, limits);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                "status", "Opens left", NotificationManager.IMPORTANCE_LOW));
        nm.cancel(1); // older builds kept a permanent notification
        alive = new File(getFilesDir(), "heartbeat");
        KeepAlive.cancel(this);
        KeepAlive.schedule(this);
        refreshWatched();
        handler.removeCallbacks(heartbeat);
        heartbeat();
    }

    /**
     * Self-healing tick. Re-checks the screen without waiting for events (a playing Short sends
     * none, and missed events must not leave a Short open), frees a stuck escape, and touches
     * files/heartbeat: the module's watchdog restarts the app when that file goes stale while
     * the screen is on. Seen on MIUI: frozen process, or bound but blind (no windows at all,
     * every scan sees nothing); a blind service stops beating so it gets restarted too.
     */
    private void heartbeat() {
        if (escaping && SystemClock.elapsedRealtime() - escapeStartedAt > ESCAPE_STALE_MS) {
            trace("escape stuck, reset");
            escaping = false;
        }
        boolean on = getSystemService(PowerManager.class).isInteractive();
        if (on && blind()) {
            trace("blind: no windows");
        } else {
            try {
                if (!alive.setLastModified(System.currentTimeMillis())) alive.createNewFile();
            } catch (java.io.IOException ignored) {
            }
        }
        Strict.applyDue(this);
        refreshWatched();
        if (on) queueScan();
        handler.postDelayed(heartbeat, HEARTBEAT_MS);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        if (timer != null) timer.hide();
        Health.changed(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();
        if (pkg.equals(getPackageName())) return;
        if (EXEMPT.contains(pkg)) {
            stepAside(pkg);
            return;
        }
        if (watched.contains(pkg)) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) countVideo(pkg);
            queueScan();
        }
    }

    private void refreshWatched() {
        Set<String> w = new HashSet<>(TELEGRAM);
        w.addAll(BROWSERS);
        w.add(YOUTUBE);
        w.addAll(FACEBOOK);
        for (String cat : Targets.all(this)) w.addAll(Targets.packages(cat));
        if (guardSettings()) w.addAll(Strict.SETTINGS);
        watched = w;
    }

    /**
     * Video limit inside an open: a scroll in a short-video feed = next video (1 s debounce,
     * a swipe sends a burst). At the limit the open ends and the pause comes back.
     */
    private void countVideo(String pkg) {
        String cat = timer == null ? null : timer.cat();
        if (cat == null || !timer.running() || !Targets.packages(cat).contains(pkg)) return;
        Config c = limits.config(cat);
        if (c.maxVideos <= 0 || wholeMode(cat)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastVideoAt < 1000) return;
        lastVideoAt = now;
        limits.addVideo(cat);
        if (limits.videosNow(cat) >= c.maxVideos) {
            trace("video limit " + cat);
            limits.endOpen(cat);
            Toast.makeText(this, L.t(this, c.maxVideos + " videos watched, open ended"), Toast.LENGTH_SHORT).show();
            queueScan();
        }
    }

    private boolean wholeMode(String cat) {
        return Targets.wholeOnly(cat) || "whole".equals(limits.config(cat).mode);
    }

    /**
     * Apps that refuse to run while a non-store accessibility service is on (bKash shows
     * "security risk ... turn off Accessibility" and closes). While one is in front the service
     * turns itself off; files/exempt tells the module's watchdog, which turns it back on within
     * ~2 s once that app is no longer the focused app. Keep in sync with EXEMPT in service.sh.
     */
    private static final Set<String> EXEMPT = new HashSet<>(Arrays.asList("com.bKash.customerapp"));

    private boolean steppedAside;

    private void stepAside(String pkg) {
        if (steppedAside) return;
        steppedAside = true;
        trace("exempt app " + pkg + ", turning off");
        try {
            new File(getFilesDir(), "exempt").createNewFile();
        } catch (java.io.IOException ignored) {
        }
        if (timer != null) timer.hide();
        disableSelf();
    }

    // Events arrive in bursts while scrolling; check the screen at most every 400 ms.
    private void queueScan() {
        if (scanQueued) return;
        scanQueued = true;
        long wait = Math.max(0, 400 - (SystemClock.elapsedRealtime() - lastScan));
        handler.postDelayed(() -> {
            scanQueued = false;
            lastScan = SystemClock.elapsedRealtime();
            scan();
        }, wait);
    }

    void recheck() {
        queueScan();
    }

    private void scan() {
        if (escaping) {
            trace("skip: escaping");
            return;
        }
        String pkg = currentPkg();
        if (EXEMPT.contains(pkg)) {
            stepAside(pkg);
            return;
        }
        if (TELEGRAM.contains(pkg)) {
            timer.hide();
            scanTelegram();
            return;
        }
        if (Strict.SETTINGS.contains(pkg)) {
            if (guardSettings()) scanSettings();
            return;
        }
        String what = detect(pkg);
        trace("scan " + pkg + " -> " + what);
        if (what == null || what.equals(ADULT)) timer.hide();
        if (what == null) return;
        if (what.equals(ADULT)) leave(pkg, "Adult site blocked");
        else gate(what, pkg);
    }

    /** Strict mode without root: keep the user off the screens that switch us off. */
    private boolean guardSettings() {
        return !Flavor.PLAY && Strict.on(this) && !Setup.rooted(this);
    }

    private void scanSettings() {
        AccessibilityNodeInfo root = appRoot();
        if (root == null) return;
        StringBuilder sb = new StringBuilder();
        allText(root, sb, 0);
        root.recycle();
        if (!Strict.blocks(this, sb.toString())) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastKickAt < 1500) return;
        lastKickAt = now;
        trace("strict: settings screen closed");
        performGlobalAction(GLOBAL_ACTION_HOME);
        handler.post(() -> android.widget.Toast.makeText(this,
                L.t(this, "Strict mode is on. Changes to Focus Guard wait " + Strict.delayH(this) + " h."),
                android.widget.Toast.LENGTH_LONG).show());
    }

    private void allText(AccessibilityNodeInfo n, StringBuilder out, int depth) {
        if (n == null || depth > 40 || out.length() > 20000) return;
        if (n.getText() != null) out.append(n.getText()).append('\n');
        if (n.getContentDescription() != null) out.append(n.getContentDescription()).append('\n');
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c == null) continue;
            allText(c, out, depth + 1);
            c.recycle();
        }
    }

    private boolean blind() {
        if (!getWindows().isEmpty()) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return true;
        root.recycle();
        return false;
    }

    String currentPkg() {
        AccessibilityNodeInfo probe = appRoot();
        if (probe == null) return "";
        String pkg = probe.getPackageName() == null ? "" : probe.getPackageName().toString();
        probe.recycle();
        return pkg;
    }

    /** What on screen needs blocking: a target category (only if enabled), ADULT, or null. */
    private String detect(String pkg) {
        String what = detectRaw(pkg);
        if (what == null || what.equals(ADULT)) return what;
        return limits.config(what).enabled ? what : null;
    }

    private String detectRaw(String pkg) {
        // Whole-app targets first: any screen of the app counts.
        for (String cat : Targets.all(this)) {
            if (Targets.packages(cat).contains(pkg) && wholeMode(cat) && limits.config(cat).enabled) return cat;
        }
        if (pkg.equals(YOUTUBE)) {
            if (hasVisibleId(pkg, SHORTS_IDS)) return Limits.SHORTS;
        } else if (FACEBOOK.contains(pkg)) {
            if (facebookReelsVisible()) return Limits.REELS;
        } else if (Targets.INSTAGRAM.contains(pkg)) {
            if (hasVisibleId(pkg, INSTA_REELS_IDS)) return Limits.INSTAGRAM;
        } else if (BROWSERS.contains(pkg)) {
            AccessibilityNodeInfo bar = urlBar(pkg);
            if (bar != null) {
                String url = bar.getText().toString().toLowerCase();
                boolean typing = bar.isFocused(); // "facebook.com/mess…" half typed must not kick
                bar.recycle();
                if (SHORTS_URL.matcher(url).find()) return Limits.SHORTS;
                if (REELS_URL.matcher(url).find()) return Limits.REELS;
                if (INSTA_REELS_URL.matcher(url).find()) return Limits.INSTAGRAM;
                if (!typing && TIKTOK_SITE.matcher(url).find()) return Limits.TIKTOK;
                if (!typing && YOUTUBE_SITE.matcher(url).find() && "whole".equals(limits.config(Limits.SHORTS).mode))
                    return Limits.SHORTS;
                if (!typing && INSTA_SITE.matcher(url).find() && "whole".equals(limits.config(Limits.INSTAGRAM).mode))
                    return Limits.INSTAGRAM;
                if (!typing && FACEBOOK_SITE.matcher(url).find() && !FACEBOOK_OK.matcher(url).find()) {
                    return Limits.FACEBOOK;
                }
                if (!typing && AdultDomains.blocked(url)) return ADULT;
                if (keywords.match(url.replaceAll("[./_-]", " ")) != null) return ADULT;
            }
        }
        return null;
    }

    /** Shorts/Reels/Facebook is on screen: pause, allow, or strict block. */
    private void gate(String cat, String pkg) {
        if (limits.isUnlocked(cat)) {
            trace("gate " + cat + ": unlocked");
            timer.show(cat, pkg);
            return;
        }
        timer.hide();
        long now = SystemClock.elapsedRealtime();
        String label = Limits.label(this, cat);
        String blocked = limits.hardBlock(cat);
        if (blocked != null) {
            leave(pkg, label + ": " + blocked);
            return;
        }
        int cooldown = limits.cooldownLeftMin(cat);
        if (cooldown > 0) {
            leave(pkg, label + ": next open in " + cooldown + " min");
            return;
        }
        if (limits.left(cat) == 0 && !limits.canExtend(cat)) {
            leave(pkg, label + ": daily limit reached");
            return;
        }
        long wait = 3000 - (now - lastPauseAt);
        if (wait > 0) {
            // A playing Short sends no more events, so check again once the cooldown ends.
            handler.removeCallbacks(rescan);
            handler.postDelayed(rescan, wait);
            trace("gate " + cat + ": cooldown " + wait);
            return;
        }
        lastPauseAt = now;
        trace("gate " + cat + ": pause");
        Intent i = new Intent(this, PauseActivity.class)
                .putExtra("cat", cat)
                .putExtra("pkg", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
    }

    /**
     * Move off the blocked screen but stay in the app, so reopening it doesn't land on the
     * same Short/page again. YouTube: tap its Home tab. Browser: load a safe page in the same
     * tab. Otherwise Back. Re-checks after each step; Home only if nothing else worked.
     */
    void leave(String pkg, String message) {
        long now = SystemClock.elapsedRealtime();
        if (escaping || now - lastKickAt < 1500) return;
        lastKickAt = now;
        escaping = true;
        escapeStartedAt = now;
        trace("leave " + pkg);
        if (message != null) Toast.makeText(this, L.t(this, message), Toast.LENGTH_SHORT).show();
        // Give a closing pause screen time to reveal the app underneath.
        handler.postDelayed(() -> escapeStep(pkg, 0, 0), 300);
    }

    private void escapeStep(String pkg, int step, int waits) {
        // Still typing the safe page (slow phone): a Back now would close the URL field.
        if (typingSafe && waits < 20) {
            handler.postDelayed(() -> escapeStep(pkg, step, waits + 1), 200);
            return;
        }
        String cur = currentPkg();
        // The pause screen can still be on top; giving up here would let the Short play and pause again.
        if (cur.equals(getPackageName()) && waits < 10) {
            handler.postDelayed(() -> escapeStep(pkg, step, waits + 1), 200);
            return;
        }
        // Closing the pause screen can surface our own main screen instead of the app the user
        // was in (it has its own task now). Bring that app back once and try again.
        if (cur.equals(getPackageName()) && waits == 10) {
            Intent back = getPackageManager().getLaunchIntentForPackage(pkg);
            if (back != null) {
                try {
                    startActivity(back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    handler.postDelayed(() -> escapeStep(pkg, step, 11), 600);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        String what = pkg.equals(cur) ? detect(pkg) : null;
        if (what == null) {
            escaping = false;
            return;
        }
        // A whole-app target has no safe screen inside the app: go Home (browsers still get a safe page).
        boolean wholeApp = !what.equals(ADULT) && wholeMode(what) && !BROWSERS.contains(pkg);
        if (step >= ESCAPE_STEPS || wholeApp) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            escaping = false;
            return;
        }
        boolean done = false;
        if (step == 0 && pkg.equals(YOUTUBE)) done = clickYoutubeHome(pkg);
        else if (step < 2 && BROWSERS.contains(pkg)) done = openSafePage(pkg);
        if (!done) performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(() -> escapeStep(pkg, step + 1, 0), 900);
    }

    private boolean clickYoutubeHome(String pkg) {
        AccessibilityNodeInfo root = appRoot();
        if (root == null) return false;
        boolean clicked = false;
        for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText("Home")) {
            CharSequence d = n.getContentDescription(), t = n.getText();
            boolean home = (d != null && "Home".contentEquals(d)) || (t != null && "Home".contentEquals(t));
            if (!clicked && home && n.isVisibleToUser()) clicked = clickSelfOrParent(n);
            n.recycle();
        }
        root.recycle();
        return clicked;
    }

    private boolean clickSelfOrParent(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = AccessibilityNodeInfo.obtain(n);
        for (int i = 0; i < 4 && cur != null; i++) {
            if (cur.isClickable()) {
                boolean ok = cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                cur.recycle();
                return ok;
            }
            AccessibilityNodeInfo p = cur.getParent();
            cur.recycle();
            cur = p;
        }
        if (cur != null) cur.recycle();
        return false;
    }

    /** Type a safe address into the URL bar of the current tab and press Enter. */
    private boolean typingSafe;

    private boolean openSafePage(String pkg) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false; // needs ACTION_IME_ENTER
        AccessibilityNodeInfo bar = urlBar(pkg);
        if (bar == null) return false;
        bar.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        bar.recycle();
        typingSafe = true;
        handler.postDelayed(() -> typeSafePage(0), 250);
        return true;
    }

    /** Waits (slow phones) for the URL field to take focus, then loads the safe page. */
    private void typeSafePage(int tries) {
        AccessibilityNodeInfo root = appRoot();
        AccessibilityNodeInfo field = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (root != null) root.recycle();
        if (field == null || !field.isEditable()) {
            if (field != null) field.recycle();
            if (tries < 10) handler.postDelayed(() -> typeSafePage(tries + 1), 250);
            else typingSafe = false;
            return;
        }
        typingSafe = false;
        android.os.Bundle args = new android.os.Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, SAFE_PAGE);
        field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
        field.recycle();
    }

    // ---- Telegram: judge the open chat ----

    private void scanTelegram() {
        AccessibilityNodeInfo root = appRoot();
        if (root == null) return;
        CharSequence pkg = root.getPackageName();
        if (pkg == null || !TELEGRAM.contains(pkg.toString())) {
            root.recycle();
            return;
        }
        StringBuilder text = new StringBuilder();
        boolean[] inChat = {false};
        collect(root, text, inChat, new int[]{0});
        root.recycle();
        // Only judge inside a chat, channel, profile or search, never the plain chat list.
        if (!inChat[0]) return;
        String hit = keywords.match(text.toString());
        long now = SystemClock.elapsedRealtime();
        if (hit != null && now - lastKickAt > 1500) {
            lastKickAt = now; // the next scan may still see the chat; don't back out of Telegram
            performGlobalAction(GLOBAL_ACTION_BACK);
            handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 350);
            Toast.makeText(this, L.t(this, "Adult channel blocked"), Toast.LENGTH_SHORT).show();
        }
    }

    private void collect(AccessibilityNodeInfo node, StringBuilder out, boolean[] inChat, int[] count) {
        if (node == null || count[0]++ > MAX_NODES) return;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        if (t != null) out.append(t).append('\n');
        if (d != null) {
            out.append(d).append('\n');
            if ("Go back".contentEquals(d)) inChat[0] = true;
        }
        // Newer Telegram search has no "Go back". A focused search box (recent chats) or a typed
        // query (results) means search is open; the plain chat list has no editable field in focus.
        if (node.isEditable() && (node.isFocused()
                || (t != null && t.length() > 0 && !node.isShowingHintText()))) inChat[0] = true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null) {
                collect(c, out, inChat, count);
                c.recycle();
            }
        }
    }

    /**
     * Root of the app the user is looking at. getRootInActiveWindow() alone breaks when a
     * floating overlay (e.g. Messenger chat heads) holds focus.
     */
    private AccessibilityNodeInfo appRoot() {
        AccessibilityNodeInfo fallback = null;
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo r = w.getRoot();
            if (r == null) continue;
            if (w.isActive()) {
                if (fallback != null) fallback.recycle();
                return r;
            }
            if (fallback == null) fallback = r; // windows are ordered top-most first
            else r.recycle();
        }
        return fallback != null ? fallback : getRootInActiveWindow();
    }

    // ---- Detection helpers ----

    private boolean hasVisibleId(String pkg, String[] ids) {
        AccessibilityNodeInfo root = appRoot();
        if (root == null) return false;
        try {
            for (String id : ids) {
                List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByViewId(pkg + id);
                boolean visible = false;
                for (AccessibilityNodeInfo n : found) {
                    visible |= n.isVisibleToUser();
                    n.recycle();
                }
                if (visible) return true;
            }
            return false;
        } finally {
            root.recycle();
        }
    }


    /** The browser's address bar showing text, or null. Caller recycles. */
    private AccessibilityNodeInfo urlBar(String pkg) {
        AccessibilityNodeInfo root = appRoot();
        if (root == null) return null;
        try {
            for (String id : URL_BAR_IDS) {
                AccessibilityNodeInfo bar = null;
                for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByViewId(pkg + id)) {
                    if (bar == null && n.getText() != null) bar = n;
                    else n.recycle();
                }
                if (bar != null) return bar;
            }
            return null;
        } finally {
            root.recycle();
        }
    }

    /** Facebook hides view ids, so look for the Reels viewer by its labels. */
    private boolean facebookReelsVisible() {
        AccessibilityNodeInfo root = appRoot();
        if (root == null) return false;
        boolean[] found = {false};
        findReels(root, found, new int[]{0});
        root.recycle();
        return found[0];
    }

    private void findReels(AccessibilityNodeInfo node, boolean[] found, int[] count) {
        if (node == null || found[0] || count[0]++ > MAX_NODES) return;
        String d = node.getContentDescription() == null ? "" : node.getContentDescription().toString().toLowerCase();
        String t = node.getText() == null ? "" : node.getText().toString().toLowerCase();
        String id = node.getViewIdResourceName() == null ? "" : node.getViewIdResourceName().toLowerCase();
        if (id.contains("reel")
                || ((d.equals("reels") || t.equals("reels")) && node.isSelected())
                || d.startsWith("reel by") || d.contains("reels, tab 1 of") && node.isSelected()
                || d.contains("reel video") || d.contains("reels viewer")) {
            found[0] = node.isVisibleToUser();
            if (found[0]) return;
        }
        for (int i = 0; i < node.getChildCount() && !found[0]; i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null) {
                findReels(c, found, count);
                c.recycle();
            }
        }
    }

    // ---- Opens-left notification, shown briefly after each open ----

    void showOpensLeft(String cat) {
        Notification n = new Notification.Builder(this, "status")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle(Limits.label(this, cat) + ": " + limits.left(cat) + " of "
                        + limits.total(cat) + " opens left today")
                .setContentText(limits.summary())
                .setTimeoutAfter(10_000)
                .setShowWhen(false)
                .build();
        getSystemService(NotificationManager.class).notify(1, n);
    }
}
