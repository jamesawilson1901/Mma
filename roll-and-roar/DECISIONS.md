# Roll & Roar — Decisions Log

Substitutions, fallbacks, and judgement calls, per the build spec.

## M0 — Scaffold & assets (2026-07-05)

- **Shipped:** Vite + Phaser 4 scaffold booting to a coloured test scene
  (480×270 logical, FIT-scaled, pixelArt) with touch-input and FPS checks;
  `tools/fetch-assets.sh` implementing the full §4.3 protocol; `CREDITS.md`
  drafted with in-game attribution obligations listed.
- **Phaser 4 confirmed:** `phaser@4.2.0` installed from npm — no fallback to
  3.87 needed.
- **Repo layout:** this repository already contained an unrelated project
  (`mma-model/`), so the game lives in the `roll-and-roar/` subdirectory
  rather than the repo root. All spec paths are relative to `roll-and-roar/`.
- **Asset download blocked in the remote build environment:** the Claude Code
  remote sandbox's network policy only allows GitHub and package registries —
  opengameart.org, itch.io, and kenney.nl are all denied at the proxy (403 on
  CONNECT). `fetch-assets.sh` therefore could not run to completion here.
  It is written to be run on the owner's machine (`./tools/fetch-assets.sh`,
  needs curl + unzip) or in this environment once those domains are added to
  the environment's network allowlist. Licences in CREDITS.md are marked
  PENDING until the script (or the owner) verifies them on-page.
- **`assets/raw/` is gitignored** (downloaded zips + extractions stay local);
  the curated/renamed copies under `assets/sprites|tiles|audio|ui` will be
  committed from M2 onward so the built game is self-contained.
- **Known issues:** none in the scaffold itself.
