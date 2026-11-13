# Focus Guard

A self-control tool for **Android** (any phone; extra protection with root/Magisk) and **macOS (Chrome / Brave)**.
It adds friction to short-form video and blocks adult content. It is hard to switch off on a weak moment.

## What it does

- **YouTube Shorts / Facebook Reels** (apps and browsers): a 5 s pause screen with "Go back" or "Open (15 min)".
  6 opens per day per category, then a strict block until midnight. You stay in the app, only the Short is closed.
- **facebook.com in the browser**: its own category with the same pause, opens, extend and timer, counted
  separately from Reels. Messages (`facebook.com/messages`) and login stay free. The Facebook app itself only gets
  the Reels pause.
- **Extend**: once a category is used up you can add +3 opens, at most twice a day and only before 19:00.
- **Adult sites**: blocked system-wide with a hosts list ([StevenBlack porn-only](https://github.com/StevenBlack/hosts)) and a family-filter DNS ([CleanBrowsing](https://cleanbrowsing.org/filters/)).
- **Adult Telegram chats** (Android): on-screen text is matched against a keyword list
  (English, Bangla, Hindi, Urdu and their romanized forms) and the chat is closed.
- **Banking apps that reject accessibility services** (Android, currently bKash): Focus Guard
  turns itself off while the app is open and the watchdog turns it back on ~2 s after you leave.

These are the defaults. On Android every target has its own settings in the app (see below);
on the Mac they are still fixed in `mac/extension/background.js`.

## Android

Needs Android 8+. One APK works with or without root.

```sh
cd android && ./build.sh        # needs Android SDK build-tools 35 + platform 36, no Gradle
```

### Without root

Install `android/FocusGuard.apk` and open it. A setup checklist walks through accessibility
(Android 13+: App info → ⋮ → "Allow restricted settings" first), battery, autostart (MIUI, Samsung, Oppo,
Vivo, Huawei) and notifications. If Android stops the service, a "tap to fix" notification appears.

- **Targets**: YouTube Shorts, Facebook Reels, facebook.com, Instagram Reels, TikTok, or any app (whole app).
- **Per-target settings**: pause length (and growing pause), opens per day, minutes per open, daily time cap,
  cooldown between opens, max videos per open, extend rules, block hours, "why are you opening?" prompt,
  your own "do this instead" list on the pause screen.
- **Adult sites**: browser URLs are checked against the bundled domain list. Optional filtering
  Private DNS (guided setup). Always on, no switch.
- **Strict mode**: loosening a setting (or turning strict mode off) waits 1–72 h, tightening applies at once.
  Optional partner PIN, one emergency open per week behind a 60 s wait, device admin against uninstall,
  and a settings guard that closes our accessibility / app info / device admin pages.
  Can't stop: safe-mode boot, factory reset, the accessibility volume-key shortcut (the app warns about it), and "Force stop" in App info
  (on newer Android it also turns accessibility off and cancels the keep-alive job, so the app can't
  even prompt — the settings guard closes App info to make this harder).
- **Stats**: today's opens, times you went back, minutes, streak; last 7 days vs the week before.
  Stored on the phone only, kept 60 days.
- **Backup**: save settings to a JSON file and load them back (also on another phone, or in the Mac extension).
- **Language**: English or Bangla (follows the phone, or pick one).
- **Focus now**: one tap closes every target for 30 min, 1 h or 2 h. It can't be ended early
  (starting again only makes it longer).
- **Screens**: Home (status card with today's minutes, times you went back, streak and a kind line;
  targets with app icons, opens left, usage bar), Stats, Settings tabs. Presets
  (gentle / balanced / strict) on each target; the matching preset is highlighted and a one-line
  summary ("10 s pause · 6 opens/day · 10 min each") updates as you change numbers. The pause screen shows a breathing circle with the countdown
  inside, how often you opened it today, and lets you pick how long to open it (e.g. 1 / 5 / 10 / 15 min).
  Stats include a minutes-per-day chart for the last 7 days.
- **At a glance**: Quick Settings tile and home-screen widget show whether protection is running
  ("Stopped – tap to fix" if not), opens left and today's minutes. A weekly report notification
  compares last week with the week before. The pause screen asks a different question each time.
- **Consent**: before any accessibility step the app explains what it reads and asks you to agree.

### With root (Magisk)

Works with AdAway's systemless hosts module if present.

Flash `android/FocusGuard-module.zip` in Magisk and reboot. The module installs the app and a watchdog that,
every 30 s, keeps the accessibility service on and bound, reinstalls the app if removed, keeps the hosts block
and Private DNS in place, and keeps today's counts after a data wipe. The module's **Action** button in Magisk
shows the status of each part.

Removing or disabling the module in Magisk doesn't work: a guardian script outside the module
(`/data/adb/service.d/focusguard.sh`, backup in `/data/adb/focusguard/`) undoes the remove/disable flag,
puts the module back if it is deleted and restarts the watchdog; the watchdog in turn puts the guardian back.

To remove: `su -c sh /data/adb/focusguard/remove.sh` schedules removal 24 h later (`cancel` withdraws it).
Then everything it set is undone.

## macOS

```sh
sudo ./mac/install.sh
```

Then in Chrome and Brave: extensions page → Developer mode → Load unpacked → pick
`/Library/Application Support/FocusGuard/extension`, then again for `.../keeper`.
Then give `/Library/Application Support/FocusGuard/focusguard-runner` Full Disk Access (System Settings →
Privacy & Security → Full Disk Access → +). Without it the daemon can't see that the extensions are loaded
and hard-blocks Shorts and facebook.com. Building it needs Xcode or the Command Line Tools.
A root daemon enforces browser policies (no guest mode, SafeSearch, filtered DoH; no private windows in Brave) and the hosts block.
The two extensions re-enable each other if one is turned off.

Settings: right-click the Focus Guard icon → Options. Per target (Shorts, Reels, facebook.com): pause length,
opens per day, minutes per open, cooldown, extend rules, block hours, time-left tab, "do this instead" ideas.
Stricter changes apply at once, looser ones after 24 h. The same backup file as the Android app can be loaded here.

To remove: `sudo ./mac/uninstall.sh` schedules removal 24 h later (`cancel` withdraws it).

## Personal keywords

Put your own entries in `android/app/assets/keywords.local.json` (same format as `keywords.json`, ignored by git).
Both Android and Mac builds pick it up.

## Privacy

No network access, no analytics, no accounts. Everything stays on the device.

## Building your own

Signing uses a local `focusguard.keystore` that `build.sh` generates on first run. It is not in this repo.
For a release key set `FG_KEYSTORE=/path/to.keystore FG_KS_PASS=...` (alias `fg`, or set `FG_KS_ALIAS`). Keep the key:
Android won't update an app signed with a different one.

`FG_PLAY=1 ./build.sh` makes the Google Play build, `FocusGuard-play.aab`, named **Unswipe**
(package `com.alrasin.unswipe`, so it can sit next to the root build) (needs `bundletool`): no settings
guard, device admin or battery-exemption permission, which Play doesn't allow or questions.
See `docs/play-store.md` and `docs/privacy-policy.md`.

## License

None yet: all rights reserved by the author until a license is added.
