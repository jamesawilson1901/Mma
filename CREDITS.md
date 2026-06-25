# Credits

Wolf Knight: Ember Hollow — a personal family game. Built to use **CC0** (public-domain)
assets so there's no attribution or share-alike burden; this file is kept as good practice.

## Engine & libraries
- **Phaser 3** (MIT) — https://phaser.io — vendored at `vendor/phaser.min.js` (v3.80.1).
- **rexVirtualJoystick plugin** (MIT) — Rex Rainbow,
  https://rexrainbow.github.io/phaser3-rex-notes/docs/site/virtualjoystick/ —
  vendored at `vendor/rexvirtualjoystickplugin.min.js`.

## Narration
- **Browser Web Speech API** (`window.speechSynthesis`) — no asset files, computer-generated TTS.

## Art & audio — current status
The art and audio plan (design/ASSETS.md, design/HUD-MENU-SAVE.md) targets these CC0 packs:
- Kenney — **Tiny Dungeon** (CC0) — https://kenney.nl/assets/tiny-dungeon
- **Tiny Creatures** (CC0) — https://opengameart.org/content/tiny-creatures
- Kenney — **UI Pack** (CC0) — https://kenney.nl/assets/ui-pack
- Kenney — **RPG Audio / Impact Sounds / UI Audio** (CC0) — https://kenney.nl/assets
- Music: OpenGameArt CC0 / Kenney audio category.

**Substitution note:** the build environment's network policy blocks `kenney.nl` and
`opengameart.org`, so the game currently ships **procedurally-generated placeholder
textures** (`js/assets.js`) in place of the Kenney art, and will use Web Speech for
narration. When the real CC0 packs are added, list the exact files used here and load
them under the existing texture keys (see `js/scenes/BootScene.js`). All listed packs are
CC0, so no attribution is strictly required — these entries are courtesy credits.

- App icon (`assets/icons/icon.svg`) — original, made for this project (CC0).
