# Google Play release checklist (non-root app)

Status 2026-11-12: code is Play-ready on branch `nonroot` (targetSdk 36, consent screen,
`FG_PLAY=1` build). Publishing needs the owner's decisions at the end of this file.

## Build

```sh
cd android && FG_PLAY=1 FG_KEYSTORE=/path/upload.keystore FG_KS_PASS=... ./build.sh
```

- `FG_PLAY=1` → `Flavor.PLAY = true`: no settings guard (Play forbids using accessibility to
  stop the user from turning an app off). Everything else is the same as the GitHub build.
- Move `android/app/assets/keywords.local.json` out before building (personal entries).
- Play wants an **AAB**, build.sh makes an APK. Needs `bundletool` (not installed here):
  `aapt2 link --proto-format` → zip base module → `bundletool build-bundle`. To do once.
- Use **Play App Signing**; the upload key is ours. Keep it out of the repo.

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

- Device admin: optional, no policies, used only to make uninstall harder in strict mode.
  Without it uninstall can't be blocked: Android 14+ hides the uninstall dialog from accessibility.
  Play may question it — be ready to drop it from the Play build (`Flavor.PLAY`).
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: allowed use (the service must stay alive); explain in
  the form if asked.
- Privacy policy URL: publish `docs/privacy-policy.md` (e.g. GitHub Pages).
- Target audience: 18+ (adult blocking mentions adult content); not for children.

## Assets to make

- App icon 512×512 PNG (from `res/drawable/ic_fg.xml`).
- Feature graphic 1024×500.
- 4–8 phone screenshots: Home, pause screen, edit target, Stats, widget, consent.

## New personal developer account

Closed test with **at least 12 testers for 14 days in a row** before production access.

## Owner decisions

1. Final app name and package (now `com.alrasin.focusguard`; can't change after publishing).
2. LICENSE for the repo.
3. Play developer account ($25) and 12 testers.
4. Upload key + Play App Signing.
5. Privacy policy URL.
6. Keep device admin in the Play build or not.
