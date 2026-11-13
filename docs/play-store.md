# Google Play release checklist (non-root app)

Status 2026-11-13: Play build done and emulator-tested (API 36): AAB builds and verifies,
consent text, setup steps, Strict page without "Protection", Shorts pause in Chrome.
Publishing needs the owner's decisions at the end of this file.

Real phone 2026-11-13 (Xiaomi MIUI, Android 11): YouTube Shorts pause / Go back / 1-min open + timer /
expiry, Brave Shorts + adult URL → google.com, Telegram adult search kicked + normal chat kept, Stats.
Found and fixed: MIUI blocks activity starts from the background unless "Pop-ups in background" is on
(new setup step; if the pause still can't open, the Short is left instead of playing); YouTube Premium
went picture-in-picture behind the pause (`FLAG_ACTIVITY_NO_USER_ACTION`). Force-stop removes the
service from the enabled list → "Unswipe is off" notification, user turns it back on.

## Build

One-time upload key (keep it out of the repo, back it up — losing it means a key reset request):

```sh
keytool -genkeypair -keystore ~/upload.keystore -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Build:

```sh
cd android && FG_PLAY=1 FG_KEYSTORE=~/upload.keystore FG_KS_PASS=... FG_KS_ALIAS=upload ./build.sh
```

→ `android/FocusGuard-play.aab` (upload this) + `android/FocusGuard-play.apk` (same build, for a quick
`adb install` test). Needs `bundletool` at `~/.cache/bundletool/bundletool.jar` (or `BUNDLETOOL=`).
Bump `versionCode` in the manifest for every upload.

Play build differences (`FG_PLAY=1` → `Flavor.PLAY`):

| | GitHub build | Play build |
|---|---|---|
| Settings guard (strict mode blocks our accessibility/app-info pages) | yes | no — Play forbids using accessibility to stop users turning an app off |
| Device admin (harder uninstall) | optional | removed (receiver dropped from the manifest) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | yes (direct dialog) | removed; setup opens the battery settings list instead |
| Consent text | mentions the settings guard | without it |
| `keywords.local.json` | included if present | always left out |
| Name / package | Focus Guard, `com.alrasin.focusguard` | Unswipe, `com.alrasin.unswipe` (build.sh renames the copy; Java classes stay in `com.alrasin.focusguard`) |

Use **Play App Signing**; we only hold the upload key.

## Store listing (ASO draft)

Name chosen 2026-11-13 after a name check: **Unswipe** (package `com.alrasin.unswipe`). Taken or
crowded: FocusGuard, Focus Shield, ScrollPause, Pausely, BreathGate, Scroll Brake, NoScroll,
StopScroll, Unreel, ShortStop, Focsy. "Unswipe" = the habit it breaks, short, easy to spell,
no rival app found with it. Check Play search once more right before creating the app.

ASO rules used: main keywords in the title (strongest signal), the rest in the short description
and the first lines of the full description; natural sentences, no keyword lists, no rival brand
names (Play policy), no "#1"/"best".

**Title (30)**: `Unswipe: Block Shorts & Reels` (29 chars)

**Short description (80)**: `Pause before YouTube Shorts & Reels. Daily limits, porn blocker. No internet.` (77 chars)

**Full description**:

> Stop doomscrolling without deleting your apps. Unswipe adds a short breathing pause before
> YouTube Shorts, Instagram Reels, Facebook Reels and TikTok, so you choose instead of swiping
> on autopilot. The rest of YouTube, Instagram and Facebook keeps working.
>
> HOW IT WORKS
> • A breathing pause and a question before short videos open
> • Choose how long to watch: 1, 5, 10 or 15 minutes, with a timer on screen
> • Daily limits per app: opens per day, time cap, cooldown, block hours
> • Works in browsers too (Chrome, Brave, Firefox, Edge, Samsung Internet)
> • Add any app you want to limit, not only short video
>
> STAY ON TRACK
> • Focus now: block every distraction for 30 min, 1 or 2 hours
> • Strict mode: loosening a limit waits up to 72 h; optional partner PIN
> • Screen time stats, streaks and a weekly report
> • Home-screen widget and Quick Settings tile
>
> ADULT CONTENT BLOCKER
> • Porn and adult websites are blocked in browsers, always on
>
> PRIVATE BY DESIGN
> • No internet permission: nothing is collected, nothing is sent
> • No ads, no account, no subscription — every feature is free
> • English and বাংলা
>
> Unswipe uses Android's Accessibility service only to see when Shorts, Reels or a limited app
> is on screen and to leave it. Nothing is stored or shared.

**Category**: Productivity. **Tags** (Console, pick up to 5): Digital wellbeing, Productivity,
Focus, Habit tracker, Self-improvement — use what the Console offers. **Contains ads**: No.
**In-app purchases**: No.

**Screenshot captions** (big text on top of each screenshot, keywords again):
1. Pause before Shorts & Reels
2. Choose how long you watch
3. Daily limits per app
4. Block porn sites, always
5. Focus now: no distractions
6. No internet. No ads. Free.

## Edge over competitors

| | Unswipe | Typical rivals (ScreenZen, one sec, Opal, BlockerX, Digital Wellbeing) |
|---|---|---|
| Internet permission | None — provably offline | Most need it (accounts, sync, analytics) |
| Price | Free, everything | Many lock limits/strict mode behind a subscription |
| Shorts/Reels only (not whole app) | Yes, apps and browsers | Often whole-app only, or app-only |
| Adult blocking | Built in, always on | Separate app or paid tier |
| Pause | Breathing circle, rotating question, choose open length | Fixed pause |
| Strict mode | Delayed loosening, PIN, weekly emergency open | Simple lock or none |
| Glance | Widget + QS tile with health status | Rare |
| App groups | Ready-made groups (shows which of your apps each covers) + custom groups, one shared limit | Fixed categories or per-app only |
| Focus session | One tap closes all targets 30 min–2 h, can't end early | Often paid (Opal "deep focus") |

Check rival claims again before using any of them in the listing (Play forbids false comparisons).

## Data safety form

- Data collected: **none**. Data shared: **none**.
- Encrypted in transit: not applicable (no network). Deletion: uninstall removes everything.

## Accessibility declaration (Play Console → App content)

- Not an accessibility tool (`isAccessibilityTool` not set). Core purpose: digital wellbeing —
  detect short-form video and adult pages and add a pause/block the user asked for.
- Prominent disclosure: `Setup.disclosure()` — shown before any accessibility step,
  needs "Agree"; "No thanks" leaves the app inert.
- **Video** (required): record on a phone, ~1 min: open app → consent screen → Agree →
  enable service → open YouTube Shorts → pause screen → Go back → Stats tab.

## Other declarations

- No device admin and no battery-exemption permission in the Play build (see table above).
- Privacy policy URL: https://github.com/Al-Rasin/focus-guard/blob/main/docs/privacy-policy.md
- Target audience: 18+ (adult blocking mentions adult content); not for children.

## Assets

- `docs/play/icon-512.png` — app icon 512×512 (from `res/drawable/ic_fg.xml`).
- `docs/play/feature-graphic.png` — 1024×500.
- To do: 4–8 phone screenshots (ratio 2:1 or less; a 1080×2160 screen is fine): Home, pause
  screen, edit target, Stats, widget, consent. Take them on the phone used for the video.

## New personal developer account

Closed test with **at least 12 testers for 14 days in a row** before production access.

## Owner decisions

1. ~~App name and package~~ — done: Unswipe, `com.alrasin.unswipe` (can't change after publishing).
2. LICENSE for the repo.
3. Play developer account ($25) and 12 testers.
4. Upload key + Play App Signing.
5. Accessibility video + screenshots.
