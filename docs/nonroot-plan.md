# Non-root app plan

Goal: a plain APK anyone can install (no root, no Magisk) that does more than ScreenZen.
One APK for both setups: if the Magisk module is present, the watchdog keeps doing
keep-alive; without it, the app does it itself. No Gradle, no libraries, UI built in code.

## Edge over ScreenZen

| Area | Edge |
|---|---|
| Stays in the app | Blocking lands on YouTube home / google.com in the same tab, not out of the app |
| Browsers too | Shorts, Reels, facebook.com gated in Chrome, Brave, Firefox, not only the apps |
| Adult blocking built in | URL check + Telegram + keyword lists (EN, Bangla, Banglish, Hindi, Urdu, Roman Urdu) |
| Hard to cheat | Strict-mode delay, partner lock, uninstall lock, settings guard, clock-change proof |
| Self-healing | Heartbeat + restart prompts, OEM-aware (MIUI etc.) |
| Smarter friction | Video counter, cooldown, growing pause, intention reminder, swap prompt |
| Privacy | No internet permission, no account, open source |
| Tiny | APK < 200 KB, no background drain |
| Focus session | "Focus now" 30 min / 1 h / 2 h closes every target, can't be ended early |
| Glanceable home | Status card: protection on, minutes today, went back, streak, kind line |

## Phase 1 — Settings instead of fixed rules
- `Config.java` (new): one JSON block per target in prefs `config`. Fields: `pause_s`,
  `pause_step_s`, `opens`, `open_min`, `daily_cap_min`, `cooldown_min`, `max_videos`,
  `extend` (opens / max / cutoff hour), `ask_why`, `mode` (part / whole app), `schedule[]`, `timer`.
- `Limits.java` reads `Config` instead of constants. Defaults = current rules.
  Keep existing pref keys (`yyyyMMdd_<cat>`, `until_<cat>`) so root installs keep their counts.
- `MainActivity` (new launcher; manifest has none today): targets list with opens left;
  tap → edit screen.
- `PauseActivity`: configurable pause, growing pause, "Why are you opening?" box.

## Phase 2 — Onboarding + keep-alive without root
- Steps, each with a live ✓ check:
  - Accessibility: check `AccessibilityManager`, open `ACTION_ACCESSIBILITY_SETTINGS`.
  - Android 13+: sideloaded apps must first get "Allow restricted settings" (App info → ⋮).
  - Battery: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
  - OEM autostart (MIUI, Samsung, Oppo/Realme, Vivo, Huawei): open vendor screen, fall back to App info.
  - Notifications (Android 13+): `POST_NOTIFICATIONS`.
- Keep-alive: `JobScheduler` periodic job (15 min) + `BOOT_COMPLETED` receiver. Service
  enabled but `files/heartbeat` stale → "Focus Guard stopped — tap to fix" notification.
  (Non-root can't rebind, only prompt.)
- Skip root-handled steps when `/data/adb/modules/focusguard` exists.

## Phase 3 — Targets + smarter friction
- New targets: Instagram Reels (calibrate ids with `uiautomator dump`), TikTok and any app
  (whole-app, by package; app picker from launcher apps).
- Schedule: strict block hours (e.g. 22:00–07:00), per target.
- Daily time cap: sum time spent in opens.
- Video counter: count Shorts/Reels swipes in one open, nudge or end the open after N.
- Cooldown: minimum gap between opens.
- Intention reminder: the "why" answer shown on the timer tab.
- Swap prompt: pause screen shows the user's own alternatives ("Read 5 pages", "Walk").

## Phase 4 — Strict mode + protection
- Strict mode: loosening changes wait in `pending` with `apply_at` = now + delay (default 24 h);
  tightening applies at once. Pending changes visible and cancellable.
- Partner lock: a friend sets a PIN; loosening needs the PIN or the delay.
- Emergency unlock: 1 per week, behind a long pause (e.g. 60 s).
- Device Admin (`DeviceAdminReceiver`): blocks uninstall and "Clear storage" while active;
  deactivating = loosening change.
- Settings guard (strict mode only): our accessibility page, App info, Device Admin page,
  Private DNS dialog → `leave()` (Back).
- Accessibility shortcut (volume keys) can turn the service off: warn in onboarding if our
  service is the shortcut target; treat a stop as a missed heartbeat (Phase 2 prompt).
- Clock cheats: unlock windows/cooldowns on `SystemClock.elapsedRealtime()` + wall time;
  on `ACTION_TIME_CHANGED` / `TIMEZONE_CHANGED` never grant extra opens (keep the higher used count).
- Known limits (document, can't fix without root): safe-mode boot, factory reset.

## Phase 5 — Adult blocking without root
- URL-bar check: bundle `adult_hosts` as a domain set (incl. parent-domain match), browser
  URL bar → `leave()` to google.com.
- Private DNS: read `private_dns_mode` / `private_dns_specifier`, guided setup, warn if
  turned off; settings guard blocks changes in strict mode. Optional (user's choice).
- Telegram keywords: already work without root.
- Always on; no setting to turn it off.
- Later, if needed: local `VpnService` DNS filter.

## Phase 6 — Stats + motivation
- Per day: opens, "Go back" taps, minutes, videos per target (new keys `skip`, `min`, `vid`).
- 7-day list in `MainActivity`, streaks (days within limits), weekly time saved vs last week.
- Prune data older than 60 days.

## Phase 7 — Release prep
- Package rename (+ watchdog `SVC`/`PKG`), new app name.
- `targetSdk` 30 → 34 (Android 14+ warns on old targets; check foreground/notification rules).
- Public signing key, README, LICENSE.
- Distribution: GitHub releases first, F-Droid next. Google Play is hard: accessibility use
  must pass its policy (prominent disclosure screen + declaration form) — add the disclosure
  screen in Phase 2 anyway.

## Phase 8 — Extras
- Bangla UI (strings in code → small string table).
- Settings export/import as a file (no cloud).
- Mac parity: extension options page with the same `Config` fields; import the Android export.

## Testing
- Non-root: emulator (always `adb -s`). Root: phone, module + app together.
- Per phase: Shorts pause / Go back / Open / strict block, browser Shorts, adult URL,
  Telegram adult vs normal chat, force-stop → prompt (non-root) or rebind (root).
- Performance: scan time and battery over a day, no worse than today.

## Order
1 → 2 → 5 first (settings, staying alive, adult blocking), then 3, 4, 6, 7, 8.

## Progress
- Done: all phases 1–8 (no rename). Emulator-tested 2026-11-12 (API 36, Chrome + YouTube):
  install, main screen, accessibility bound, Chrome Shorts → pause, Go back → same tab,
  adult URL → google.com same tab (fixed: wait for the URL field), YouTube app Shorts → pause,
  Open → Short plays + timer tab + opens 5/6, edit screen save/reload, stats, Bangla UI
  (one card fixed). Service off → "Focus Guard is off" notification OK.
- Limit found: on API 36 a force-stop clears `enabled_accessibility_services` and cancels jobs,
  so no "stopped" prompt is possible after it (documented in README).
- Not tested yet: strict mode pending changes, PIN, emergency open, device admin, backup
  export/import, Telegram, Instagram/TikTok, real phone (MIUI).
- Phase 3: Instagram Reels ids need calibration with `uiautomator dump` on a device.
- Phase 4 as built: `Strict.java` (prefs `strict`: pending `p_<cat>`, `rm_<pkg>`, `delay_at`,
  `off_at`; delay 1/6/24/72 h; salted SHA-256 PIN; emergency open once per 7 days after 60 s),
  `Admin.java` (device admin, no policies), settings guard in `GuardService.scanSettings()`
  (Home + toast; 90 s grace when the app itself opens a settings screen). No time-change
  receiver needed: `Clock` already ignores wall-clock jumps when auto time is off.
- Phase 6 as built: `Stats.java` on the home screen — today tiles (opens, went back, minutes,
  streak), last 7 days line with change vs the week before, per-day list. Streak = days in a row
  without an extension. Day keys older than 60 days pruned once a day (`pruned` key).
- Phase 7 as built: targetSdk 34, `<queries>` for launcher apps (app picker), POST_NOTIFICATIONS
  asked once at runtime (then settings). Package rename skipped (no new name yet). Release
  signing via `FG_KEYSTORE` / `FG_KS_PASS`. README covers the non-root app; no LICENSE yet.
- Phase 8 as built: `Backup.java` — save/load settings as a JSON file through the system file
  picker (no storage permission). Format `{"focusguard":1,"targets":{cat:config},"apps":[...],
  "swaps":"..."}`; load goes through `Strict.save`, so looser settings still wait. `L.java` — UI
  text in English or Bangla (phone language, or picked under "Language"); exact strings plus
  `%s` templates. Mac extension v1.5: options page (`options.html/js`) with the same fields
  (enabled, pause + growing pause, opens, minutes per open, cooldown, extend rules, block hours,
  timer tab, ideas) and the same backup file (only shorts/reels/facebook used). Looser changes
  on Mac always wait 24 h; daily cap, max videos, "why" prompt and whole-app mode are kept in
  the file but not enforced there.
- UI/UX rework (2026-11-12): bottom tabs (Home / Stats / Settings), target cards with app icon,
  opens-left pill and usage bar, presets light/medium/strict on the edit screen, app picker with
  icons. Pause screen: app icon, "opened N times today", breathing circle (4 s in/out) with the
  countdown inside, open-length chips (1/5/10/15/30/60 min below the max, plus the max; open spends
  one open either way), "I don't want to open" as the main button. Stats: 7-bar minutes chart.
  Emulator-tested in Bangla: all tabs, edit, pause (chips enabled after the countdown), Go back.
- Play readiness + competitor edge (2026-11-12): targetSdk 36 with edge-to-edge insets
  (`Ui.fitInsets`), adaptive icon, consent screen before accessibility, `FG_PLAY=1` build without
  the settings guard. New: Quick Settings tile (`Tile.java`) and home widget (`Widget.java`) with
  health status (`Health.java`: accessibility on + service alive or heartbeat < 3 min), weekly
  report notification (`Stats.weekly`, sent once per week from `KeepAlive`), rotating questions on
  the pause screen. Builds OK in both flavors; installed on the emulator, service binds, widget
  provider registered, no crashes. Tile/widget not checked on screen (emulator shared with
  another project). Listing, data safety, owner decisions: `docs/play-store.md`.
- Focus session + home card (2026-11-13): `Limits.focus_until` (pref) — `hardBlock` says
  "focus session until HH:MM", `isUnlocked` is false during it (a running open ends). Start only
  extends (max). Home hero card (`MainActivity.hero`, from `Setup.addStatus` when setup is done):
  protection status, today's minutes / went back / streak, encouraging line, Focus now buttons with
  a confirm dialog. Edit screen: matching preset highlighted (`PRESETS`, `presetMatch`), live
  summary line. Bangla strings added.
- Bangla digits (2026-11-13): `L.t` turns pure-number strings/groups into Bangla digits; steppers,
  focus dialog time and emergency countdown go through it. Emulator-tested (API 36, Bangla): hero,
  focus confirm "০৯:১১", steppers "৫ সেকেন্ড"/"১৫ মিনিট"/"১৯:০০", Strict preset highlight + live
  summary, highlight drops when a value changes.
- Remaining emulator tests (2026-11-13, API 36): backup save/load ✅; strict on (1 h, PIN) ✅,
  App info + accessibility pages kicked ✅, stricter change instant / looser waits ✅, PIN wrong
  rejected / right applies / cancel ✅, looser import waits ✅; emergency open (60 s, next in a
  week) ✅; device admin on → "Uninstall blocked" ✅, deactivate-admin page kicked in strict ✅,
  with strict off the admin's warning dialog shows and OK removes it ✅; QS tile "Protected" +
  tap opens app ✅; widget shows status, counts, today ✅. Not tested (apps not installed):
  Telegram, Instagram, TikTok, Facebook. Real phone not tested.
  Fixes from these tests: Back key via `Ui.onBack` (targetSdk 36 ignores `onBackPressed`);
  ADD_DEVICE_ADMIN started without NEW_TASK (else result lost); strict "grace" after setup
  removed (was a bypass); settings scan walks all app windows (`scanSettings`); strict screen
  refreshes on resume. OS limit: API 34+ marks the uninstall dialog and the deactivate-admin
  buttons `accessibilityDataSensitive` — the service can't see them. So uninstall is only blocked
  with device admin; the deactivate page is matched by its header "admin app is active"
  (English only). Cosmetic fixes after: widget picker preview (`previewLayout`, API 31+) and English singular ("1 time") via `L.singular`.
