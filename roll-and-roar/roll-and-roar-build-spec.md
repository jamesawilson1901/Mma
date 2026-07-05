# ROLL & ROAR — Build Specification
### A DKC-style 2D platformer for mobile browsers (PWA)

**Audience of this document:** Claude Code. Follow it as the single source of truth. Where it says MUST, do not deviate without asking. Where judgement is needed, prefer the simplest implementation that preserves game feel.

**How the owner will use this:** drop this file in an empty project folder and say:
> "Read roll-and-roar-build-spec.md and execute Milestone 0. Stop at each milestone's acceptance check so I can test on my phone."

---

## 1. What we're building

A 5-level, side-scrolling run-and-jump platformer with the *mechanical feel* of Donkey Kong Country (SNES) — momentum, roll-jumps, tag-team partners, rideable animal mounts, themed worlds, one unique boss per level — but with **original theme and characters**. It is NOT a Donkey Kong clone: no monkeys, no Nintendo assets, no jungle-ape skin.

- **Platform:** mobile browser first (Android/Chrome), installable as a PWA. Must also run on desktop with keyboard for dev testing.
- **Orientation:** landscape.
- **Art:** 16-bit-era pixel art from the free-licence packs in §4. Crisp nearest-neighbour scaling, no smoothing.
- **Working title:** Roll & Roar (owner may rename later; keep it in one constant).

### Non-negotiables
1. **NEVER download, reference, or reproduce ripped/commercial game sprites (Nintendo or otherwise).** Only the packs in §4 or licence-verified substitutes per §4.4.
2. Playable at a solid frame rate on a mid-range Android phone (target device: Oppo A5 Pro 5G, Chrome).
3. Touch controls that work with two thumbs, plus keyboard fallback.
4. 5 levels, 5 distinct biomes, 5 distinct boss fights.
5. Two difficulty modes: **Classic** and **Junior** (§7).
6. A `CREDITS.md` naming every asset pack, author, licence, and source URL. Attribution-required assets MUST be credited in-game on the credits screen too.

---

## 2. Tech stack

| Layer | Choice | Notes |
|---|---|---|
| Engine | **Phaser 4** (`npm i phaser@^4`) | v4 is the current stable line (4.0 shipped April 2026) with a rebuilt WebGL renderer and large mobile performance gains. If a v4-specific API blocks you, fall back to `phaser@3.87` — the core API for this project (Arcade Physics, sprites, tilemaps, input) is compatible. Record any fallback decision in `DECISIONS.md`. |
| Build | **Vite**, plain **JavaScript** (no TypeScript) | Owner reads/edits JS. Keep modules small and named clearly. |
| Physics | **Arcade Physics** | Tile platformer; do not use Matter. |
| Levels | **ASCII grid maps in JS files** (§8.1) | No Tiled editor files — hand-authorable and diff-able. |
| Persistence | `localStorage` | Progress, unlocks, difficulty, audio settings. |
| PWA | `manifest.webmanifest` + service worker | Cache-first for all assets after first load; installable; landscape display preference. |

**Internal resolution:** 480×270 logical pixels (16:9), integer-scaled up via `Phaser.Scale.FIT` + `pixelArt: true`, `roundPixels: true`. All sprite placement in logical pixels.

**Project layout**
```
/src
  main.js            // game config + scene registration
  scenes/            // Boot, Preload, Title, Level, Boss, Bonus, Results, Credits
  systems/           // controls.js, player.js, partner.js, mount.js, camera.js, save.js, audio.js
  levels/            // level1.js ... level5.js (ASCII maps + entity spawns + theme config)
  ui/                // hud.js, touchpad.js, menus.js
/assets
  raw/               // downloaded zips, untouched
  sprites/ tiles/ audio/ ui/   // extracted, organised, renamed
/tools
  fetch-assets.sh    // asset download script (§4.3)
CREDITS.md
DECISIONS.md
```

---

## 3. Art direction & cohesion rules

The packs come from different artists; without discipline it will look like a jumble. Enforce:

1. **One character family.** All heroes, enemies, and item pickups come from the Pixel Frog packs (Pixel Adventure 1/2, Treasure Hunters, Pirate Bomb, Kings and Pigs) so silhouettes, outlines, and palette feel related. The mount dinos (Arks) are the only exception — scale them ×2 so they read as large rideable animals next to 32px heroes.
2. **Environments may differ per biome** — that's period-authentic (DKC's biomes looked wildly different) — but within one level use ONE environment pack only.
3. **Integer scaling only.** Never scale a sprite by 1.5×. 1×, 2×, 3×.
4. **Per-level colour grade.** Add a single full-screen tint/ambient overlay per level (e.g. cool blue for snow, warm amber for desert) to pull mixed assets toward one palette. Keep it subtle (≤15% opacity).
5. **Parallax:** minimum 2 background layers per level, scrolling at 0.2× and 0.5× camera speed.
6. Honest limitation, acknowledged up front: DKC's literal pre-rendered-3D sprite look does not exist in the free-licence ecosystem. High-quality 16-bit pixel art is the ceiling here, and these packs are the best of it.

---

## 4. Assets — sources, licences, acquisition

### 4.1 Verified manifest

All OpenGameArt (OGA) pages host their zips directly (links under the "File(s):" heading on each page) and are fetchable with `curl`. **At download time, read the "License(s):" line on each page and record it verbatim in CREDITS.md** — do not trust this table blindly if the page disagrees.

| # | Pack | Author | Licence (as verified) | Source page | Used for |
|---|---|---|---|---|---|
| 1 | Pixel Adventure 1 | Pixel Frog | CC0 | https://opengameart.org/content/pixel-adventure-1 | 4 hero characters (full anim sets: idle/run/jump/fall/hit), terrain, fruit pickups, traps |
| 2 | Pixel Adventure 2 | Pixel Frog | verify on page | https://opengameart.org/content/pixel-adventure-2 | 20 enemies (use OGA — the itch listing is now paid; the OGA upload is the free-licensed copy) |
| 3 | Treasure Hunters (Demo) | Pixel Frog | verify on page | https://opengameart.org/content/treasure-hunters-demo | Pirate crew enemies + Captain (L2 boss), ship props |
| 4 | Pirate Bomb | Pixel Frog | verify on page | https://opengameart.org/content/pirate-bomb | Ship-deck tiles, barrels, bomb hazard, pirate enemies |
| 5 | Kings and Pigs | Pixel Frog | verify on page | https://opengameart.org/content/kings-and-pigs | King Pig (L5 boss base), castle-interior tiles for bonus rooms |
| 6 | SunnyLand - Forest | ansimuz | CC0 | https://opengameart.org/content/sunnyland-forest | L1 tileset (16×16), 2-layer parallax, 3 enemies |
| 7 | SunnyLand Tall Forest Environment | ansimuz | CC0 | https://opengameart.org/content/sunnyland-tall-forest-environment | L1 extra parallax/props (file: `tall_forest_files.zip`) |
| 8 | Underwater Diving Pack | ansimuz | verify on page | https://opengameart.org/content/underwater-diving-pack | L3 complete kit: tileset, parallax, diver player sprite, enemies, FX (file: `underwater-diving-files.zip`) |
| 9 | 2D Platformer Snow Pack | Tio Aimar | CC0 | https://opengameart.org/content/2d-platformer-snow-pack | L5 tileset (file: `2D Platformer Snow Pack (Tio Aimar).zip`) |
| 10 | Sand / Desert 16×16 Tileset | Niss36 | CC-BY 4.0 | https://opengameart.org/content/sand-desert-16x16-tileset | L4 terrain (basic + slope tiles) — **attribution required** |
| 11 | 16x16 Desert Tilesets | 16pixel | CC-BY-SA 4.0 | https://opengameart.org/content/16x16-desert-tilesets | L4 props: pyramid, cactus, vases — **attribution + share-alike** |
| 12 | Dino Character Sprites | Arks | Free, **attribution required** (credit "Arks / @ArksDigital") | https://arks.itch.io/dino-characters | Rideable mounts — 4 colour dinos, run anims (file: `DinoCharacters - Sprites 1_1.zip`) |
| 13 | 5 Chiptunes (Action) | Juhani Junkala (SubspaceAudio) | CC0 | https://opengameart.org/content/5-chiptunes-action | Title + level themes |
| 14 | 4 Chiptunes (Adventure) | Juhani Junkala | CC0 | https://opengameart.org/content/4-chiptunes-adventure | Additional level themes |
| 15 | Boss Battle Music | Juhani Junkala | CC0 | https://opengameart.org/content/boss-battle-music | All 5 boss fights (`Epic Boss Battle`, seamless loop) |
| 16 | 512 Sound Effects (8-bit style) | Juhani Junkala | CC0 | https://opengameart.org/content/512-sound-effects-8-bit-style | All SFX: jump, roll, stomp, pickup, hit, splash, roar, UI |
| 17 | JRPG Pack 1 (Exploration) | Juhani Junkala | CC0 | https://opengameart.org/content/jrpg-pack-1-exploration | Underwater level ambience (calmer track) |

### 4.2 Known gaps and approved fallbacks

- **Desert (L4) is the thinnest biome.** Packs 10+11 are terrain + props only, from different artists. If, after assembly, the level looks incoherent, the **approved swap** is a cave/grotto biome using ansimuz's "Warped: Super Grotto Escape Pack" (search OGA for the exact page; it appears in OGA's CC0/OGA-BY collections — verify licence on the page). Record the swap in `DECISIONS.md` and rename the level accordingly.
- **Boss sprites.** Default strategy (and the DKC1-authentic one — its bosses were literally giant versions of regular enemies): each boss = a **2–3× scaled, tinted "elite" version of that level's toughest enemy**, with unique behaviour (§10). Named exceptions where the packs provide a proper big character: the pirate **Captain** (pack 3) for L2, **King Pig** (pack 5) for L5. Optionally browse https://opengameart.org/users/ansimuz for his "Boss Fight" packs and use one if its licence checks out.
- **Roll animation.** Pixel Adventure heroes have no roll frames. Implement roll as the jump-tuck frame rotated programmatically (360°/roll) with a dust particle trail — a standard, good-looking trick.
- **Swim animation.** In L3, swap the hero sprite for the Underwater Diving Pack's diver sprite (palette-tint it toward the active hero's colours if feasible; otherwise use as-is and treat it as a "dive suit").

### 4.3 Acquisition protocol (`tools/fetch-assets.sh`)

1. For each OGA pack: `curl` the content page, extract the first `sites/default/files/*.zip` (or listed archive) href under "File(s):", download to `/assets/raw/`, unzip to a named folder. Also scrape the "License(s):" line into `CREDITS.md`.
2. Verify every archive: non-zero size, unzips cleanly, contains PNG/OGG/WAV as expected. Log results.
3. **Arks dino pack (itch.io):** itch download URLs are session-generated; automated download may fail. Attempt a polite fetch of the page for a direct file link; if none, PRINT a clear instruction and pause:
   > "Manual step: open https://arks.itch.io/dino-characters → Download Now → pay $0 → save `DinoCharacters - Sprites 1_1.zip` into assets/raw/ — then tell me to continue."
4. Never hotlink at runtime. Everything is served locally from `/assets`.

### 4.4 Substitution protocol
If any URL is dead or a licence fails verification: find a replacement of equivalent quality on OpenGameArt, Kenney.nl (all CC0), or itch.io free packs. Requirements: licence must be CC0, CC-BY, OGA-BY, or CC-BY-SA (record share-alike obligations); style must be 16-bit pixel art; log the substitution in `DECISIONS.md` and `CREDITS.md`. Never substitute with ripped commercial sprites or AI-scraped "sprite rip" sites.

---

## 5. Controls

### Touch (primary)
- **Left third of screen:** virtual left/right pad (two large invisible-ish zones with subtle arrow glyphs; active thumb zone highlights).
- **Right third:** two round buttons — **A (Jump)** lower-right, **B (Roll / Action)** left of it.
- Multitouch mandatory (run + jump simultaneously). Use pointer events; `touch-action: none`; prevent scroll/zoom/long-press menus.
- Buttons ≥ 64 logical px, 30% opacity idle / 60% pressed. HUD-safe margins for notches.

### Keyboard (dev/desktop)
Arrows/A-D = move, Space/Z = jump, Shift/X = roll/action, P = pause, M = mute.

### Context actions on B
Ground: roll. Holding a throwable (barrel/bomb): throw. Beside a mount crate: mount. While mounted: dismount is **down + B**.

---

## 6. Core mechanics — the DKC feel

Tuning values are starting points; expose them all in one `config/tuning.js` so the owner can tweak. Logical-pixel units.

| Parameter | Value |
|---|---|
| Gravity | 1400 px/s² |
| Run speed (max) | 150 px/s, accel 900, drag 1200 |
| Jump velocity | −420 (variable: releasing jump early caps rise at −180) |
| Coyote time | 100 ms · Jump buffer 120 ms |
| Roll speed | 260 px/s, duration 450 ms, cooldown 250 ms |
| Stomp bounce | −300 (−480 if jump held) |
| Hit i-frames | 1200 ms with sprite flicker |

### 6.1 Roll-into-jump momentum (signature mechanic — get this right)
- Rolling **continues off ledge edges** — the player does not immediately fall out of the roll state.
- For **400 ms after leaving the ground mid-roll**, pressing Jump grants a FULL jump with roll's horizontal speed preserved. This is the DKC roll-jump: roll off a cliff, jump in mid-air, clear huge gaps. It must feel generous.
- Rolling through an enemy defeats stompable enemies and **extends the roll** by 200 ms per kill (chain-rolling through enemy lines, DKC-style).
- Roll does NOT defeat armoured/spiked enemies — it bounces the player back with brief stun (no damage).

### 6.2 Tag-team partner (2-hit system)
- Two heroes from Pixel Adventure 1 (default pair: Ninja Frog + Pink Man; the other two are unlockable palette-swap skins). Active hero is controlled; partner follows ~24 px behind replaying a position buffer (ghost-follow), hops when you hop.
- **Take a hit with a partner:** partner yelps and despawns (runs off-screen), active hero gets i-frames. **Take a hit solo:** lose the attempt → respawn at last checkpoint.
- **Partner crates** (Pixel Adventure box sprite with a face decal) respawn the missing partner. Place 2–3 per level.
- Tap the partner's HUD portrait (or press Down+A) to swap which hero is active — cosmetic + who eats the next hit, keeps the mechanic present.

### 6.3 Rideable mounts
- **Dino crates** release a rideable dino (Arks sprite, ×2 scale). Mounting is automatic on touch.
- Mounted stats: run 190 px/s, jump −500, and the dino **defeats spiked/armoured enemies on contact** (the mount's power, like DKC animal buddies).
- Taking a hit while mounted: the dino bucks you off and flees (despawns); you keep your partner state. One free hit, effectively.
- Dismount (down+B) hops you off; the dino idles for 5 s then despawns.
- One dino colour per biome (use the 4 Arks colours; repeat one for L5 with a frost tint). No mounts in L3 (underwater).

### 6.4 Swimming (L3 only)
- Gravity ~120, water drag high; A = swim-stroke impulse toward held direction (8-way), continuous slow sink otherwise.
- No roll underwater; B = dash-stroke (short burst, 600 ms cooldown) which defeats soft enemies.
- No air meter in Junior; in Classic a 60 s bubble timer refreshed by air-bubble pickups (vents in the tileset), with audible warning at 10 s.

---

## 7. Difficulty modes

Chosen on the title screen, stored per save slot, switchable between levels.

| | **Classic** | **Junior** |
|---|---|---|
| Hits | Tag-team 2-hit (§6.2) | Same, PLUS solo hits knock back instead of kill; 3 solo hits to lose the attempt (heart HUD) |
| Pits/water death | Lose attempt → checkpoint | Respawn at ledge edge, lose 10 fruit |
| Lives | 5 per level attempt run; game-over → level restart | Infinite |
| Enemy speed | 1.0× | 0.7×, and projectile bosses telegraph 50% longer |
| Checkpoints | 1 mid-level flag | 3 flags per level |
| Air timer (L3) | On | Off |

Everything else (level layout, bosses, collectibles) is identical — Junior is the same game, softened.

---

## 8. Levels

### 8.1 Level data format
Each `levels/levelN.js` exports: `{ name, theme, tileSize: 16, map: [...ASCII rows...], entities: [...], music, ambientTint }`.

ASCII legend (extend as needed, document in the file header):
`#` solid · `=` one-way platform · `^` spikes · `~` water · `.` empty · `F` fruit · `R/O/A/R2` letter tokens · `C` partner crate · `D` dino crate · `K` checkpoint flag · `B` bonus-room door · `E1..E9` enemy spawns (per-level roster) · `S` player start · `X` end-of-level target · `!` crumbling tile · `>` `<` conveyor/current

Levels are ~200–280 tiles wide, 17–34 tiles tall (vertical sections allowed). Camera: horizontal follow with lookahead (40 px in facing direction), soft vertical follow, deadzone box.

### 8.2 The five levels

**L1 — Whispering Woods** (forest · packs 6+7 · music: Chiptunes-Action track 1)
Teaching level: run, jump, stomp, roll, roll-jump gap (a gap only clearable with roll-jump, with a safe pit first), partner crate, first dino. Gimmick: bouncy mushroom props (springs). Enemies: SunnyLand's three + 2 easy Pixel Adventure 2 walkers.
**Boss — "The Old Growth":** giant tinted elite of the level's toughest enemy. Pattern: charge across arena → hits wall, stunned 2 s (stomp window) → recovers faster each phase; phase 3 adds falling branches. 3 stomps to win.

**L2 — The Salt-Rot Galleon** (pirate ship · packs 3+4 · music: Action track 2)
Gimmick: **deck sway** — platform group offsets on a slow sine (±6 px, 4 s period; ±10 px in the rigging section) — plus rolling barrels to jump or roll through, and throwable bombs (B to throw) that break cracked planks hiding fruit caches.
**Boss — "Captain Brinebeard"** (Treasure Hunters Captain sprite, or scaled Bald Pirate from Pirate Bomb): lobs bombs in arcs; you throw bombs back or roll into him during his reload. Phase 2 adds two crew minions; phase 3 faster lobs + deck sway doubles. 3 hits.

**L3 — The Drowned Reef** (underwater · pack 8 · music: JRPG Exploration calm track; SFX muffled via lowpass)
Full swim level (§6.4). Gimmick: current jets (`>` `<`) that push, used as speed boosts and hazards; air-bubble vents in Classic.
**Boss — "The Lantern Below":** giant tinted elite of the pack's meanest sea enemy in a dark arena — only its glow + a light radius around the player. It telegraphs lunges with a brightening glow; dodge, then dash-stroke its tail. Phase 3: two fake glows. 3 hits.

**L4 — Sunscorch Ruins** (desert ruins · packs 10+11 · music: Adventure track 1)
Gimmick: **crumbling tiles** (`!` — shake 400 ms after touch, fall, respawn 4 s) and quicksand pools (slow sink; mash jump to escape; dinos walk on it). Ruin props (pyramid, columns, vases — vases smashable by roll, drop fruit).
**Boss — "The Tomb Warden":** giant armoured elite (roll bounces off). It burrows into sand and erupts under you (shadow telegraph); the only damage window is stomping it while it's stuck after erupting into a crumbling platform you've baited it under. Phase 3: sand geysers. 3 hits.
*(If this biome fails the cohesion check, execute the grotto swap in §4.2.)*

**L5 — Frostfang Peak** (snow · pack 9 · music: Action track 3)
Gimmick: **ice friction** (drag drops to 150 on ice tiles — momentum is king; roll-jumps go far), falling icicles (telegraphed by shimmer), wind-gust sections that push mid-air.
**Boss — "The Frost King"** (King Pig, ×2.5, ice-blue tint, fur collar decal): final boss, 3 full phases — (1) belly-slide charges across an ice arena, steerable-into wall stuns; (2) summons 2 pig minions + icicle rain; (3) arena floor half-crumbles, faster slides, stomp windows shorten. 4 hits. Victory → results + credits.

### 8.3 Collectibles & progression
- **Fruit** (Pixel Adventure fruit sprites): 100 = extra life in Classic, celebratory fanfare in Junior. HUD counter.
- **R-O-A-R letter tokens:** 4 per level, placed on risky detours (one behind each level's hardest optional roll-jump). Collecting all 4 = gold badge on level select; all 20 unlocks the two remaining hero skins.
- **Bonus room** (1 per level): hidden door (`B`) behind a breakable wall or fake wall; inside, a 30-second fruit-grab room (Kings and Pigs interior tiles), exit returns you to the door.
- **Level select map:** simple 5-node path screen; nodes show badges; levels unlock sequentially. Progress in `localStorage` (`save.js`, versioned schema).

---

## 9. HUD & UI
Minimal, top corners: fruit count, letter tokens (dim → lit), hero portraits (active highlighted; greyed if partner lost), hearts (Junior), air bubble (L3 Classic). Pause button top-right → resume / restart / level select / audio toggles / difficulty (between levels only). Use Pixel Adventure UI-ish frames or simple 2-px bordered panels matching the palette. Title screen: logo text (pixel font — bundle a CC0/OFL pixel font such as "Press Start 2P" from Google Fonts, licence-check and credit it), Start, Difficulty, Credits.

---

## 10. Boss framework (shared)
One `BossBase` class: phase state machine (phase = f(hits taken)), telegraph → attack → vulnerable-window loop, hit flash + knockback, health pips UI (3–4 pips), arena door locks on entry, boss music (pack 15) starts on entry and stops on win, defeat = slow-mo 500 ms + fanfare + exit door. Each boss subclass overrides the attack set only. Bosses are separate scenes fed by the level's theme config so tiles/parallax match.

---

## 11. Audio
- `audio.js`: music channel (looping, cross-fade 500 ms on scene change) + SFX pool. Persisted mute/volume.
- Map ~15 SFX from pack 16 (512 SFX): jump, land, roll, stomp, hurt, partner-lost, crate, mount, roar (dino), fruit, letter, checkpoint, splash, boss-hit, fanfare. Choose by ear; keep a mapping table in `audio.js` comments.
- Mobile constraint: unlock the AudioContext on first user gesture (standard Phaser pattern); no autoplaying before that.
- Convert WAV → OGG (and keep an M4A/AAC fallback if any target browser lacks OGG) to keep the PWA cache small. Total audio budget ≤ 12 MB.

---

## 12. PWA & performance
- Manifest: name, short_name "Roll&Roar", `display: fullscreen`, `orientation: landscape`, theme/background colours, 192/512 icons (compose from a hero sprite on a solid rounded square).
- Service worker: precache app shell + all assets after first successful load; cache-first with versioned cache name; bump on release.
- If the browser reports portrait, show a "rotate your phone" overlay rather than forcing a broken layout.
- Performance budget: steady frame rate on the target phone with ≥25 active physics bodies. Techniques: sprite pooling for enemies/particles, cull off-screen updates, cap particles (≤60 alive), single tilemap layer per collision pass, **no runtime filters/shaders**, keep texture count low (pack into atlases with `free-tex-packer-core` or load per-sheet if simpler — measure first, optimise only if the frame rate drops).
- Test hooks: FPS meter toggle (triple-tap top-left), and a `?level=N&mode=classic` query param to jump straight into any level.

---

## 13. Milestones — build in this order, stop at each check

**M0 — Scaffold & assets.** Vite+Phaser boots to a coloured scene on the phone; `fetch-assets.sh` has downloaded and verified every pack (or flagged the manual dino step); `CREDITS.md` drafted with verified licences.
*Check: owner opens dev URL on phone, sees the boot scene; reviews CREDITS.md.*

**M1 — Feel prototype.** L1 greybox (ASCII map rendering, collision), full movement per §6 incl. roll-jump, touch controls, camera. No art polish yet.
*Check: owner confirms on-phone that roll-jump feels DKC-generous. Do not proceed until the feel is signed off — everything else builds on it.*

**M2 — L1 complete.** Real SunnyLand art, parallax, enemies, stomp/roll kills, partner system, dino mount, fruit/letters/checkpoint/bonus room, HUD, L1 boss, results screen, save.
*Check: L1 beatable start-to-finish on the phone in both difficulties.*

**M3 — L2 + L3.** Ship sway + throwables; swim system + underwater boss.
**M4 — L4 + L5.** Crumble/quicksand; ice physics; final boss; credits screen; level-select map; skin unlocks.
**M5 — Ship it.** PWA install flow verified on the phone, offline replay works, performance pass, audio pass, `CREDITS.md` final and mirrored on the in-game credits screen.

After each milestone: commit, and write a 5-line status in `DECISIONS.md` (what shipped, any substitutions, known issues).

---

## 14. Definition of done
- Installs to an Android home screen and runs offline after first load.
- All 5 levels + 5 bosses beatable in Classic and Junior with touch only.
- Roll-jump, tag-team, and mounts all present and load-bearing in level design (at least one section per level requiring each).
- No non-licensed assets anywhere; CREDITS.md complete; attribution-required credits (Arks, Niss36, 16pixel, font) shown in-game.
- Frame rate holds on the target phone through the busiest section (L2 boss phase 3).
