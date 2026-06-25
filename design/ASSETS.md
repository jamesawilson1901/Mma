# Asset Plan — Wolf Knight (Ember Hollow vertical slice)

All choices below are **CC0** (public domain, no attribution required, no share-alike).
That keeps a personal family project simple. We still keep a `CREDITS.md` as good practice.

> **Verify-before-trust rule for the build agent:** Asset packs change. Before writing
> load code, open the actual extracted folder and use the real filenames you find.
> Do not invent file paths. If a named sprite isn't present, use the nearest match and
> note the substitution in `CREDITS.md`.

---

## Keystone decision: use Kenney's "Tiny" series

One coherent 16×16 pixel style, all CC0, and **drawn for the 3/4 top-down view** the GDD
specifies (the Tiny Creatures pack notes its sprites are centered for a "3/4ths top down view").
Using one family keeps art consistent without any artist work.

| Pack | License | What it gives us | Source |
|---|---|---|---|
| **Kenney — Tiny Dungeon** (130+ sprites) | CC0 | Floor/wall tiles, items, weapons, a few characters, a Tiled sample map | https://kenney.nl/assets/tiny-dungeon |
| **Tiny Creatures** (180 sprites: 100+ monsters, 50+ animals) | CC0 | Wolf (Kael's base form), fox (Pip), small canines (wolf pups), enemies. Tiny-Dungeon-compatible | https://opengameart.org/content/tiny-creatures |
| **Kenney — Tiny Town** (optional) | CC0 | Outdoor/surface decor if Ember Hollow has above-ground areas | https://kenney.nl/assets/tiny-town |

**Fallback / comprehensive source (first-party Kenney):**
Kenney — Roguelike/RPG pack (1700 assets, CC0): https://kenney.nl/assets/roguelike-rpg-pack
Use this if a specific creature (e.g. a distinct fox) isn't in Tiny Creatures.

---

## The elemental wolf forms — solved with code, not art

The GDD says "recolor one base wolf sprite per element." **Don't make 7 files.** Load the
single Tiny Creatures wolf sprite once and apply a Phaser tint per form:

```js
const ELEMENT_TINTS = {
  dark:     0x4a3b6b,
  fire:     0xff5a2b,  // Ember Hollow / Fire Wolf
  earth:    0x8b6b3d,
  electric: 0xf2d54a,
  water:    0x3aa0ff,
  ice:      0x9be3ff,
  wind:     0xb6f0c4,
  light:    0xfff4c2,
};
wolfSprite.setTint(ELEMENT_TINTS[currentForm]); // 0 extra art files
```

This satisfies "one base sprite recolored per element" with zero asset overhead and makes
the remaining six forms free to add later.

---

## UI — hearts, buttons

- **Hearts / HUD / button frames:** Kenney — UI Pack (CC0): https://kenney.nl/assets/ui-pack
  (Tiny Dungeon also contains a heart item sprite; either works.)
- Keep hearts large and high-contrast for young kids.

## Controls — no art needed

- **Virtual joystick:** use the **rexVirtualJoystick** Phaser 3 plugin (free, code-only) —
  https://rexrainbow.github.io/phaser3-rex-notes/docs/site/virtualjoystick/
  (Or hand-roll a simple two-circle joystick; the plugin is faster and battle-tested.)
- **Tap-to-attack** and **hold-for-radial-form-picker:** built from Phaser pointer events;
  the radial menu is drawn with Graphics + the tinted wolf icons. No external assets.

## Audio / narration — CC0 (now in scope)

- **Narration:** browser **Web Speech API** (`window.speechSynthesis`). Computer-generated TTS,
  zero files, matches the GDD's "natural computer-generated TTS, recordable over later." Clear
  voice, slightly slow rate for kids. On/off toggle. **Captions on by default** (toggle).
- **SFX (Kenney, CC0):** RPG Audio (https://kenney.nl/assets/rpg-audio), Impact Sounds
  (https://kenney.nl/assets/impact-sounds), UI Audio (https://kenney.nl/assets/ui-audio).
- **Music (CC0):** OpenGameArt filtered to CC0 (fantasy/ambient loops), or Kenney's audio category
  (https://kenney.nl/assets/category:Audio). Need: calm Ember Hollow loop, tense boss loop, victory
  sting.
- **Vendor audio locally** and cache in the service worker so the PWA plays offline. List tracks in
  CREDITS.md. Full plan in HUD-MENU-SAVE.md.

---

## What to put in CREDITS.md (the build agent fills real entries)

```
# Credits
Kenney — Tiny Dungeon (CC0) — https://kenney.nl/assets/tiny-dungeon
Tiny Creatures (CC0) — https://opengameart.org/content/tiny-creatures
Kenney — UI Pack (CC0) — https://kenney.nl/assets/ui-pack
Phaser 3 (MIT) — https://phaser.io
rexVirtualJoystick plugin (MIT) — Rex Rainbow
Narration: browser Web Speech API (no asset)
```

## Things to deliberately AVOID (license friction)

- **LPC wolf / LPC anything on OpenGameArt** — these are usually **CC-BY-SA 3.0**, which
  requires attribution *and* share-alike (viral). Fine legally, but more hassle than CC0.
  Stay in the Kenney/CC0 lane unless you specifically want that art style.
