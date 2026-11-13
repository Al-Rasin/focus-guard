# Focus Guard – Privacy Policy

Last updated: 2026-11-13

Focus Guard is a self-control app. It adds a pause before short-form video (YouTube Shorts,
Facebook Reels, Instagram Reels, TikTok and apps you pick) and blocks adult websites.

## What the app collects

Nothing leaves your phone. The app has **no internet permission**, so it cannot send data
anywhere. There are no ads, no analytics, no crash reporting and no accounts.

## Accessibility service

Focus Guard uses Android's Accessibility service, and only after you agree on an in-app
consent screen. It uses it to:

- see which app and screen is in front, so it can tell when Shorts, Reels or a target app opens;
- read the browser's address bar, to catch Shorts/Reels pages and adult websites;
- press Back or Home, or type `google.com` into the address bar, to leave a blocked page.

Screen content is checked on the phone at that moment and then dropped. It is never saved,
sent or shared.

## What is stored on the phone

- Your settings (targets, limits, strict mode, optional PIN as a salted hash).
- Daily counts: opens, times you went back, minutes watched. Kept 60 days, then deleted.

This data stays in the app's private storage. Android backup is turned off for the app.
"Save settings" writes a file only where you choose, and only settings (no usage data).

## Permissions

| Permission | Why |
|---|---|
| Accessibility service | Detect Shorts/Reels/adult pages and leave them (see above) |
| Notifications | "Opens left", "Focus Guard is off – tap to fix", weekly report |
| Run at startup | Restart the protection after a reboot |
| Ignore battery optimisation | Keep the service from being killed |
| Device admin (optional) | Makes uninstalling harder while strict mode is on. No admin policies are used |

The Google Play version does not ask for the last two (battery optimisation, device admin).

## Children

The app is not aimed at children and collects no data from anyone.

## Changes and contact

Changes to this policy are published in this file in the app's public repository.
Questions: open an issue at https://github.com/Al-Rasin/focus-guard/issues
