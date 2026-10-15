# Focus Guard

A self-control tool for **rooted Android phones (Magisk)** and **macOS (Chrome / Brave)**.
It adds friction to short-form video and blocks adult content. It is hard to switch off on a weak moment.

## What it does

- **YouTube Shorts / Facebook Reels** (apps and browsers): a 5 s pause screen with "Go back" or "Open (15 min)".
  6 opens per day per category, then a strict block until midnight. You stay in the app, only the Short is closed.
- **Extend**: once a category is used up you can add +3 opens, at most twice a day and only before 19:00.
- **Adult sites**: blocked system-wide with a hosts list ([StevenBlack porn-only](https://github.com/StevenBlack/hosts)) and a family-filter DNS ([CleanBrowsing](https://cleanbrowsing.org/filters/)).
- **Adult Telegram chats** (Android): on-screen text is matched against a keyword list
  (English, Bangla, Hindi, Urdu and their romanized forms) and the chat is closed.

The limits above are currently fixed in code (`Limits.java`, `mac/extension/background.js`).

## Android

Needs: a rooted phone with Magisk, Android 8+. Works with AdAway's systemless hosts module if present.

```sh
cd android && ./build.sh        # needs Android SDK build-tools 35 + platform 34, no Gradle
```

Flash `android/FocusGuard-module.zip` in Magisk and reboot. The module installs the app and a watchdog that,
every 30 s, keeps the accessibility service on and bound, reinstalls the app if removed, keeps the hosts block
and Private DNS in place, and keeps today's counts after a data wipe. The module's **Action** button in Magisk
shows the status of each part.

To remove: remove the module in Magisk and reboot. Everything it set is undone.

## macOS

```sh
sudo ./mac/install.sh
```

Then in Chrome and Brave: extensions page → Developer mode → Load unpacked → pick
`/Library/Application Support/FocusGuard/extension`, then again for `.../keeper`.
A root daemon enforces browser policies (no incognito / guest, SafeSearch, filtered DoH) and the hosts block.
The two extensions re-enable each other if one is turned off.

To remove: `sudo ./mac/uninstall.sh` schedules removal 24 h later (`cancel` withdraws it).

## Personal keywords

Put your own entries in `android/app/assets/keywords.local.json` (same format as `keywords.json`, ignored by git).
Both Android and Mac builds pick it up.

## Privacy

No network access, no analytics, no accounts. Everything stays on the device.

## Building your own

Signing uses a local `focusguard.keystore` that `build.sh` generates on first run. It is not in this repo.
