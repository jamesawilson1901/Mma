# HUD, Menus, Save & Audio — Wolf Knight

Choices locked: **named profiles** (each kid their own save), **captions on by default** (toggle),
**music + SFX** (CC0). Design for landscape touch, young kids: big targets, icon-first, minimal
text, thumb-friendly zones.

## In-game HUD (overlay)
- **Hearts** — top-left, large, clear full/empty states. Max grows 5 → 6 after all pups.
- **Pup counter** — top-right: `🐺 x / 3` for the current region.
- **Form badge** — small active-form icon (Knight / Dark Wolf / Fire Wolf) with its tint, near the
  action buttons so kids see what they are.
- **Special cooldown** — a small radial/bar by the special button showing when Blood Moon (or a
  form special) is ready; greyed while cooling down.
- **Caption bar** — bottom-center: shows the current narration line as large high-contrast text.
  **On by default**, toggle in Settings; auto-hides a moment after the line ends.
- **Pause** — top-right corner button.

## Controls overlay (recap)
- Virtual joystick bottom-left; tap-to-attack bottom-right.
- **Hold** for the radial form picker (icons of unlocked forms; release on one to switch).
- Special button near attack (Blood Moon / form special), greyed during cooldown.

## Menus
**Title screen** — logo + warm volcano art. Shows **profiles**; pick one to play, or **New Player**.
Big icon-first buttons: Continue · New Game · Settings.
**Profile create** — typed name entry (on-screen-keyboard friendly) + pick a color/wolf icon. Each
kid gets their own save.
**Pause** — Resume · Settings · Title (back to profile select).
**Settings** — Captions on/off (default ON) · Narration voice on/off (+ slight rate control) ·
**Music volume** slider · **SFX volume** slider · (optional) Big-text toggle. Settings are
per-profile so each kid's prefs stick.
**Gentle respawn (no harsh "Game Over")** — at 0 hearts: soft fade, Pip reassures
("Let's try again, Kael"), respawn at the last checkpoint with full hearts. No lives, no score.
**Region complete** — celebratory screen: Fire Wolf earned, pups `x/3`, then "Onward!" → Luna dream
→ next region. (For the slice, an end-of-slice summary.)

## Save system (localStorage — auto-save, kids never manage it)
Save on: each **checkpoint**, **form unlock**, **pup collected**, **region complete**, and
**settings change**. Show a small "saved ✓" tick at checkpoints. Continue resumes at the last
checkpoint with saved forms / pups / max hearts.

**Keys & schema (v1)** — version the keys; parse in try/catch; if missing/corrupt, start fresh.
```
localStorage["wolfknight:profiles"] = [ {id, name, icon, updatedAt}, ... ]

localStorage["wolfknight:save:<id>"] = {
  profileId, name,
  region: "ember_hollow",
  checkpoint: "cp2",
  maxHearts: 5,
  formsUnlocked: ["knight","dark_wolf"],     // + "fire_wolf" after the boss
  pups: { "ember_hollow": ["pup1","pup2"] },  // collected ids per region
  settings: { captions:true, voice:true, musicVol:0.6, sfxVol:0.8, voiceRate:0.95 },
  updatedAt: 1719000000000
}
```
Forward-compatible: the schema already covers all 7 regions and 8 forms — later regions just add
ids, no rewrite. (This is real localStorage in a deployed site — not the in-artifact restriction.)

## Audio (all CC0) — sources + moments
**SFX (Kenney, CC0):** RPG Audio (https://kenney.nl/assets/rpg-audio), Impact Sounds
(https://kenney.nl/assets/impact-sounds), UI Audio (https://kenney.nl/assets/ui-audio).
**Music (CC0):** OpenGameArt filtered to CC0 (fantasy/ambient loops) or Kenney's audio category
(https://kenney.nl/assets/category:Audio). Pick: a calm mysterious **Ember Hollow** loop, a tense
**boss** loop, a warm **victory** sting.
**SFX moments:** sword swing/hit, enemy puff, lava sizzle (hurt), geyser, footstep, form-switch
whoosh, Blood Moon impact, pup-found chime, checkpoint tick, UI click/toggle.
- Wire volume to the Settings sliders; respect mute. **Vendor audio files locally** and cache them
  in the service worker so the PWA still plays offline. List tracks/SFX in CREDITS.md.
