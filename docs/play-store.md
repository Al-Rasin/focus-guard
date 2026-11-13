# Google Play release checklist (non-root app)

Status 2026-11-13: Play build done and emulator-tested (API 36): AAB builds and verifies,
consent text, setup steps, Strict page without "Protection", Shorts pause in Chrome.
Publishing needs the owner's decisions at the end of this file.

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

Use **Play App Signing**; we only hold the upload key.

## Store listing (draft)

**Name**: Focus Guard: Shorts & Reels Limit

**Short description (80)**: Pause before Shorts & Reels. Daily limits. Blocks adult sites. No internet.

**Full description**:

> Focus Guard puts a short pause between you and endless videos.
>
> • A breathing pause before YouTube Shorts, Facebook Reels, Instagram Reels, TikTok or any app you pick
> • Choose how long to open: 1, 5, 10 or 15 minutes, with a timer on screen
> • Opens per day, daily time cap, cooldown, block hours — per app
> • Works in browsers too (Chrome, Brave, Firefox, Edge, Samsung Internet…)
> • Adult websites blocked, always
> • Strict mode: loosening a setting waits up to 72 h; optional partner PIN
> • Focus now: close everything for 30 min, 1 or 2 hours — no way out early
> • Stats, streaks, weekly report, home-screen widget and Quick Settings tile
> • English and বাংলা
>
> Privacy first: the app has **no internet permission**. Nothing is collected, nothing is sent.
> No ads, no account, no subscription.

**Category**: Productivity. **Contains ads**: No. **In-app purchases**: No.

## Edge over competitors

| | Focus Guard | Typical rivals (ScreenZen, one sec, Opal, BlockerX, Digital Wellbeing) |
|---|---|---|
| Internet permission | None — provably offline | Most need it (accounts, sync, analytics) |
| Price | Free, everything | Many lock limits/strict mode behind a subscription |
| Shorts/Reels only (not whole app) | Yes, apps and browsers | Often whole-app only, or app-only |
| Adult blocking | Built in, always on | Separate app or paid tier |
| Pause | Breathing circle, rotating question, choose open length | Fixed pause |
| Strict mode | Delayed loosening, PIN, weekly emergency open | Simple lock or none |
| Glance | Widget + QS tile with health status | Rare |
| Focus session | One tap closes all targets 30 min–2 h, can't end early | Often paid (Opal "deep focus") |
| Language | English + Bangla | Rarely Bangla |

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

1. Final app name and package (now `com.alrasin.focusguard`; can't change after publishing).
2. LICENSE for the repo.
3. Play developer account ($25) and 12 testers.
4. Upload key + Play App Signing.
5. Accessibility video + screenshots.
