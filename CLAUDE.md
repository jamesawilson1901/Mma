# Wolf Knight — Ember Hollow (vertical slice)

A 3/4 top-down action-adventure web game for young kids (Pokémon/Zelda style),
built as a vertical slice of a 7-region game. This slice builds ONLY the **Fire**
region ("Ember Hollow") and the **Fire Wolf** form.

The full design lives in `./design/` — `BUILD-PROMPT.md` is the master plan and
references `STORY-BIBLE`, `COMBAT-SPEC`, `LEVEL-MAP`, `HUD-MENU-SAVE`,
`NARRATION-SCRIPT`, and `ASSETS`. Those docs are the locked source of truth.

## LOCKED CONSTRAINTS (do not relitigate)

- **Engine:** Phaser 3, **vendored locally** (`vendor/phaser.min.js`). Must work offline.
- **No bundler.** Plain static files — `index.html` + `/js` + `/assets` + `/vendor`,
  served directly. Zero-config GitHub Pages.
- **Landscape only.**
- **Deploy:** GitHub Pages. Live URL must work. (See "Deployment" below for this repo's
  branch situation.)
- **PWA:** installable + offline (`manifest.json` + a service worker that caches ALL
  assets, including Phaser).
- **Controls:** on-screen virtual joystick (move) + tap-to-attack + hold-anywhere
  radial menu (pick form).
- **Child-friendly (FIRM):** hearts for health; checkpoints; respawn with FULL hearts;
  large simple text; forgiving difficulty. **Anti-soft-lock:** any puzzle object resets
  to a solvable state when the player re-enters its room, so the game can never become
  unwinnable.
- **Architect for reuse:** this is region 1 of 7 (Fire/Earth/Electric/Water/Ice/Wind/Light).
  Systems are **data-driven** so later regions are added as config/data, not rewrites:
  - a **wolf-form config** (id, tint, combat-special, traversal-verb), and
  - a **region config** (tileset, rooms, enemies, spirit, mini-boss, pups, narration lines).
  Implement only the FIRE form + Ember Hollow now; structure so dropping in Earth/Volt/etc.
  later is trivial.

## Story / content (summary — full text in ./design)

- Hero **KAEL**, a kind grown knight. Tone: **mysterious & magical** (not cutesy, not scary).
- Story told **in-game via Pip** (a talking fox-kit guide) — **no cutscenes**.
- Starting forms (both playable from minute one — Luna's gift): **Knight** (sword melee)
  and **Dark Wolf** (signature **Blood Moon** cooldown ultimate; ability **see in the dark**).
- **Fire Wolf** is the first elemental form, **locked** until **Cinder** (the fire spirit)
  is freed by beating the mini-boss **"the Shadowgrip"**. Fire Wolf = **fire ground-slam**
  special + **burn obstacles** traversal. Tint = `0xff5a2b`.
- 3 hidden **lost wolf pups**; collecting all 3 grants a **permanent extra heart** (5 → 6).
- Start with **5 hearts**.

## Forms (data-driven — see `js/config.js`)

| id          | tint       | special        | traversal      | unlocked at start |
|-------------|------------|----------------|----------------|-------------------|
| `knight`    | (no tint)  | sword combo    | —              | yes               |
| `dark_wolf` | `0x4a3b6b` | Blood Moon     | see-in-dark    | yes               |
| `fire_wolf` | `0xff5a2b` | ground-slam    | burn obstacles | after boss        |

## Assets

- Plan is Kenney "Tiny" CC0 packs (Tiny Dungeon, Tiny Creatures) + Kenney UI Pack + CC0 audio.
- **Network note:** the build environment's egress policy blocks `kenney.nl` and
  `opengameart.org`, so the slice currently ships **procedurally-generated placeholder
  textures** (and Web Speech for narration, which needs no files). Asset loading is routed
  through `js/config.js` / the Boot scene so real CC0 PNGs drop in later **without code
  rewrites** — just add files to `/assets` and point the config at them. This is tracked in
  `BUILDLOG.md` and `CREDITS.md`.
- **One base wolf sprite, tinted per form** — never multiple wolf files.

## Deployment (this repo)

This game lives in the `jamesawilson1901/Mma` repo alongside an unrelated `mma-model/`
Python project. Game files are at the **repo root** so GitHub Pages can serve `index.html`.

Development happens on branch **`claude/youthful-hopper-mjiv63`**. To go live, in the repo's
**Settings → Pages**, set Source = "Deploy from a branch" and pick that branch, folder `/(root)`
(or merge to `main` and deploy from `main`). URL: `https://<user>.github.io/Mma/`.

## Build phases (one at a time; browser-verify + commit after each)

0. Scaffold + deployable skeleton (this).  1. Movement + tilemap room.
2. Environment: lava, 3 rooms, burnable obstacles (inert).  3. Combat + enemies + form menu.
4. Hearts + checkpoints + respawn.  5. Mini-boss + Fire Wolf unlock.
6. Pups + permanent heart payoff.  7. Narration + audio + captions.
8. Title + named profiles + settings + save.  9. Polish + verified offline PWA.

## Operating rules

- After each phase: run a local static server, give the URL + what to look for, then
  **commit + push** and **wait for the user's OK** before the next phase.
- Keep **`BUILDLOG.md`** of decisions and **`CREDITS.md`** of real assets/licenses.
- Prefer **simple, readable code** over cleverness — the owner will read and extend it.
