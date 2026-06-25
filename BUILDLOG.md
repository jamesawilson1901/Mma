# Build Log — Wolf Knight: Ember Hollow

Running record of decisions, one entry per phase. Newest at the bottom.

## Phase 0 — Deployable skeleton (done)

**Goal:** a deployable static skeleton — `index.html`, vendored Phaser, manifest,
service worker, `/js`, `/assets` — rendering one "Ember Hollow" scene with a
placeholder floor + Kael (no movement). Live + offline-capable.

**Decisions:**
- **Repo placement.** This game shares the `jamesawilson1901/Mma` repo with an
  unrelated `mma-model/` Python project. Game files live at the **repo root** so
  GitHub Pages can serve `index.html` directly. `mma-model/` is untouched.
- **Phaser version.** Vendored **Phaser 3.80.1** (`vendor/phaser.min.js`) — the CDN
  (`cdn.jsdelivr.net`) is blocked by the build environment's egress policy, so it was
  pulled from the npm registry tarball (allowed) and committed. Fully offline.
- **Joystick plugin.** Vendored **rexVirtualJoystick** (`vendor/rexvirtualjoystickplugin.min.js`)
  now so Phase 1 movement can use it; not wired up yet.
- **Placeholder art (important).** The CC0 art hosts (`kenney.nl`, `opengameart.org`)
  are also blocked by egress, so the slice currently **generates placeholder textures in
  code** (`js/assets.js`) instead of loading PNGs. They're keyed by texture name
  (`floor`, `wall`, `player`), so dropping in real Kenney CC0 art later is a one-line
  change in `BootScene.preload()` per key — no gameplay code references pixels. Narration
  will use the Web Speech API (no files), so it's unaffected.
- **Data-driven from day one.** `js/config.js` holds the `FORMS` table (knight / dark_wolf
  / fire_wolf with tints, specials, traversal verbs, unlock flags) and the `REGIONS` table
  (Ember Hollow shape: spirit, mini-boss, pups, hearts). Later regions/forms are added as
  data here, per the "architect for reuse" constraint.
- **Scaling.** `Phaser.Scale.FIT` at a 960×540 (16:9) design resolution, centered and
  letterboxed. Landscape enforced via a CSS `@media (orientation: portrait)` rotate notice.
- **PWA.** `manifest.json` (fullscreen, landscape, SVG icon) + `sw.js` precaches the full
  app shell (including Phaser) for offline play.
- **`.nojekyll`** added so GitHub Pages serves the `js/` and `vendor/` trees verbatim.

**Verify:** served locally; the Ember Hollow scene renders the floor, the "Ember Hollow"
title, Kael centered, and the Phase 0 caption. Service worker registers without error.
