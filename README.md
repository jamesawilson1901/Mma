# Anchor

An evidence-based behaviour-change PWA: build a habit, learn a skill, or break a behaviour. Vanilla HTML/CSS/JS, no framework, no build step. Hosted on Netlify so push reminders arrive even with the app fully closed.

Every feature implements a peer-reviewed finding — implementation intentions, cue anchoring, self-monitoring, lapse-friendly design, honest 66-day timelines, fresh starts, COM-B barrier checks and more. Tap any ⓘ in the app to see the study behind a feature.

## Layout

```
public/                  the app shell (static, offline-first PWA)
netlify/functions/       subscribe, unsubscribe, test-push, vapid-public-key,
                         reminders-dispatch (scheduled, */15 * * * *)
scripts/generate-icons.mjs   regenerates the icon set (pure Node, no deps)
tests/                   node:test suite for dispatcher logic, store, functions
netlify.toml             publish dir + functions config
```

Data lives in localStorage on the device (versioned schema, JSON export/import). The server stores **only** push subscriptions, reminder times/labels, timezone and notification settings, in Netlify Blobs keyed by a hash of the subscription endpoint.

## Deploy from a fresh clone

Prerequisites: Node 18+, a free Netlify account.

```bash
npm install
npm install -g netlify-cli
netlify login
netlify init          # create a new site (or link an existing one)
```

Generate VAPID keys for Web Push (one-off):

```bash
npx web-push generate-vapid-keys
```

Set the three environment variables on the site (Site configuration → Environment variables, or CLI):

```bash
netlify env:set VAPID_PUBLIC_KEY  "<public key from above>"
netlify env:set VAPID_PRIVATE_KEY "<private key from above>"
netlify env:set VAPID_SUBJECT     "mailto:you@example.com"
```

Deploy:

```bash
netlify deploy --prod
```

The scheduled function `reminders-dispatch` registers itself from its in-code `config.schedule` on deploy — no extra setup. Netlify cron runs in UTC; the dispatcher converts to each device's stored IANA timezone (default `Australia/Brisbane`, UTC+10, no DST).

## Local development

```bash
netlify dev
```

This serves `public/`, runs the functions, and emulates Netlify Blobs locally. Push subscription needs HTTPS in real browsers; for full push testing use a deployed URL (Chrome treats `localhost` as secure, so opt-in also works under `netlify dev`). To fire the scheduled function by hand during development:

```bash
netlify functions:invoke reminders-dispatch
```

Run the test suite (dispatcher window/quiet-hours/cap logic, store metrics, export→wipe→import round trip, function integration against a local Blobs server):

```bash
npm test
```

## Trying it on your phone (Android Chrome)

1. Open the deployed URL in Chrome, menu → **Add to Home screen** (installs the PWA).
2. Settings → turn on **Push notifications** and grant permission when prompted.
3. Tap **Send a test notification**, then fully close the app — the test should still arrive.
4. Set a reminder time on an item; the dispatcher (every 15 minutes) sends it at that local time.

Server-enforced guarantees (in `reminders-dispatch`, not just the UI): max **1 push per item per day** (last-sent recorded per item in Blobs), **quiet hours** (default 21:00–07:00 local, adjustable in Settings), and a **global mute**. Notification text defaults to "Time for: {item title}"; a Settings toggle switches to fully generic text. Nothing else ever leaves the device.

> **iOS note:** iPhone/iPad need iOS 16.4+ **and** the app installed to the Home Screen (Share → Add to Home Screen) before web push works. Relevant if you share this with others later.

## More users / devices later

The design is already multi-device: each browser that opts in creates its own subscription record in Blobs (keyed by endpoint hash) carrying its own timezone, reminders and settings. Another person or another phone is just another subscription — no schema or code changes needed. Item data itself is per-device; move it between devices with Settings → Export/Import.

## Renaming the app

1. `public/js/config.js` — change `APP_NAME`.
2. `public/manifest.webmanifest` — change `name` and `short_name`.

That's it (the `<title>` in `index.html` is set from the constant at runtime; edit it too if you care about the pre-JavaScript flash). To restyle the icons, tweak the colours in `scripts/generate-icons.mjs` and run `npm run icons`.

## What was deliberately deferred

- **Sync of item data between devices** — out of scope by design; localStorage + export/import keeps the server surface (and privacy exposure) minimal. The subscription store is already multi-device, so cloud sync could be added later without refactoring.
- **Per-item multiple reminders / weekly schedules** — one reminder time per item keeps the over-notification risk low, which the evidence says matters more than coverage.
- **Automated end-to-end push delivery test** — real delivery depends on FCM/Mozilla push services and a physical device; the crypto/queueing path is covered by unit and integration tests, and the in-app "Send a test notification" button covers the last mile manually.
- **Icon design tooling** — icons are generated programmatically (pure Node PNG encoder) rather than authored assets, so they stay trivially editable without design software.

## Manual steps you must do yourself

1. `netlify login` and `netlify init` (account + site creation).
2. `npx web-push generate-vapid-keys` and setting the three env vars (`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT`).
3. `netlify deploy --prod`.
4. On the phone: install the PWA, grant notification permission in Settings, and send the test notification with the app closed to confirm delivery.
5. Optionally verify a scheduled reminder: set a reminder a few minutes ahead (outside quiet hours) and wait for the next 15-minute dispatch tick.
