# BUILD PROMPT — paste this into Claude Code (running in ~/wolf-knight)

Copy everything inside the code fence below into Claude Code. (If you have the GDD file,
attach it too and add: "The attached GDD is the locked source of truth; this prompt
summarizes it.")

---

```
You are building a vertical slice of a personal web game for my kids. Build locally,
TEST IN A BROWSER after every phase, and commit after every phase so progress is never lost.

FIRST: create a CLAUDE.md in the repo capturing the LOCKED CONSTRAINTS below so future
sessions stay on track. Then give me a short build plan + file structure before writing code.

=== PROJECT ===
A 3/4 top-down action-adventure for young children, Pokemon/Zelda style. This slice builds
ONLY "Ember Hollow" (a volcano region) and the FIRE WOLF form. Do NOT build other regions.

Tone: MYSTERIOUS & MAGICAL — warm, calm exploration with a sense of ancient wonder; not cutesy,
not scary. Hero: KAEL, a kind, grown knight (steady, gentle, brave). Story is told IN-GAME via
Pip (NO cutscenes). A talking fox-kit guide, PIP, points things out and gives gentle hints.

Kael's forms in this slice: KNIGHT (sword melee) and DARK WOLF are BOTH playable from the start
(Luna's gift). Dark Wolf's signature BLOOD MOON: Kael howls, the moon turns blood-red and crashes
down on a foe — a devastating hit; make it a strong move on a cooldown / limited charge so it
stays special. Dark Wolf ability: SEE IN THE DARK (lights up shadowed areas). FIRE WOLF is the
first elemental form, locked until Cinder is freed.

Slice arc: Grimm's shadows have crept into Ember Hollow. Kael + Pip explore 3 volcano rooms,
dodging lava and fighting shadow-creatures. The wise, ancient fire spirit CINDER is held by a
shadow mini-boss ("the Shadowgrip"). Kael beats it → frees Cinder → Cinder grants the FIRE WOLF
form (fire ground-slam attack + ability to burn obstacles). Hidden LOST WOLF PUPS are scattered
about; some sit behind burnable obstacles only reachable once you have the Fire Wolf. Finding all
3 pups grants a permanent extra heart. (Grimm himself is NOT in this slice.)

Full story + the exact TTS narration lines are in the attached STORY-BIBLE.md — use those lines.
Enemy + boss design (the Shadowgrip fight, Shades/Ember Moths/Shadow Hound, Pip's coaching lines)
is in the attached COMBAT-SPEC.md — follow it for Phases 3 and 5. The room layout, pup locations,
checkpoints, dark zones, burnable obstacles and hazards are in the attached LEVEL-MAP.md — follow
it for Phases 2, 4 and 6 (build the 3 rooms and the backtrack loop as drawn). The HUD, menus,
named-profile save system, and audio plan are in the attached HUD-MENU-SAVE.md — follow it for
Phases 7 and 8. All spoken lines (ordered, with voices and triggers) are in the attached
NARRATION-SCRIPT.md — it's the single source for narration in Phase 7.

=== LOCKED CONSTRAINTS (do not relitigate) ===
- Engine: Phaser 3, VENDORED locally (download phaser.min.js into the repo). Must work offline.
- NO bundler. Plain static files (index.html + /js + /assets) served directly. Zero-config Pages.
- Landscape orientation only.
- Deploy: GitHub Pages from main branch /(root). Live URL must work after Phase 0.
- PWA: installable + offline (manifest.json + a service worker that caches ALL assets incl. phaser).
- Controls: on-screen virtual joystick (move) + tap-to-attack + hold-anywhere radial menu (pick form).
- Child-friendly (FIRM): hearts health; checkpoints; respawn with FULL hearts; large simple text;
  forgiving difficulty. ANTI-SOFT-LOCK: any puzzle object resets to a solvable state when the
  player re-enters its room/area, so the game can never become unwinnable.
- ARCHITECT FOR REUSE: this is region 1 of 7 (Fire/Earth/Electric/Water/Ice/Wind/Light). Build
  the systems DATA-DRIVEN so later regions are added as config/data, not rewrites. Specifically:
  define a wolf-form config (id, tint, combat-special, traversal-verb) and a region config
  (tileset, rooms, enemies, spirit, mini-boss, pups, narration lines). Implement only the FIRE
  form + Ember Hollow now, but structure it so dropping in Earth/Volt/etc. later is trivial.

=== ASSETS (all CC0; verify real filenames in the extracted folders, never invent paths) ===
- Kenney "Tiny Dungeon" (CC0): tiles/items — https://kenney.nl/assets/tiny-dungeon
- "Tiny Creatures" (CC0, 3/4 top-down): wolf=Kael's form, fox=Pip, small canines=wolf pups
  — https://opengameart.org/content/tiny-creatures
- Kenney "UI Pack" (CC0): hearts/buttons — https://kenney.nl/assets/ui-pack
- Elemental recolors: do NOT make multiple wolf files. Use ONE base wolf sprite and apply a
  Phaser tint per form. Fire Wolf tint = 0xff5a2b.
- Controls: rexVirtualJoystick plugin (free) for the joystick.
- Narration: browser Web Speech API (window.speechSynthesis) with on/off toggle AND on-screen
  captions (default ON, toggleable). PLUS CC0 audio: SFX from Kenney RPG Audio / Impact Sounds /
  UI Audio; music (calm Ember Hollow loop, tense boss loop, victory sting) from OpenGameArt CC0 or
  Kenney's audio category. Vendor audio locally + cache in the service worker (offline). Music &
  SFX volume sliders in Settings. (See HUD-MENU-SAVE.md.)
- Write CREDITS.md with the real packs/licenses you actually used.
- If a needed sprite isn't in a pack, use the nearest match (or tint a similar one) and note it.

=== OPERATING RULES ===
- After EACH phase: run a local static server (e.g. `python3 -m http.server 8000` or `npx serve`),
  tell me the URL to open, and state exactly what I should see. Wait for nothing destructive.
- Commit + push after each phase with a clear message. Keep a BUILDLOG.md of decisions.
- Make reasonable decisions yourself; only ask me when something is genuinely ambiguous about
  child-experience or art choice.
- Prefer simple, readable code over cleverness — I'll be reading and extending this.

=== PHASES (each ends with: browser-verify, commit, push) ===
0. Scaffold: index.html, vendored phaser.min.js, manifest.json, service worker, /js, /assets.
   One "Ember Hollow" scene rendering a placeholder floor + a player sprite (no movement yet).
   Get it live on GitHub Pages and confirm the URL renders. Commit "Phase 0: deployable skeleton".
1. Movement: tilemap volcano room, camera follow, rexVirtualJoystick, 4-direction walk anims,
   wall collisions. Verify I can walk around. Commit.
2. Environment: volcano tileset, lava hazard tiles (touching costs a heart), 3 connected rooms
   with exits, and visually-marked "scorched/burnable obstacles" that are inert for now (cleared
   later by the Fire Wolf). Commit.
3. Combat + enemies + form menu: tap-to-attack with the knight's sword; simple shadow-creature
   enemies that chase/contact-damage; hold for a radial form picker. KNIGHT and DARK WOLF are both
   selectable from the start — Dark Wolf has the Blood Moon special (howl → blood-red moon crashes
   on a foe; cooldown-gated) and "see in the dark" (lights up shadowed rooms). Show a LOCKED Fire
   Wolf slot. Build the form system data-driven (see ARCHITECT FOR REUSE). Commit.
4. Hearts + checkpoints: heart HUD starting at 5 HEARTS; damage; death; respawn at last checkpoint
   with FULL hearts; anti-soft-lock reset on room re-entry. Commit.
5. Mini-boss + Fire Wolf unlock: "the Shadowgrip" guards Cinder; a forgiving mini-boss fight;
   on win, free Cinder and UNLOCK the Fire Wolf form — tinted wolf sprite (0xff5a2b), a FIRE
   GROUND-SLAM special (shockwave around Kael), and the ability to BURN the scorched obstacles
   from Phase 2. Commit.
6. Collectibles + payoff: Pip is a TALKING GUIDE (Web Speech, see Phase 7) and sparkles near
   hidden things; place 3 lost wolf pups, at least one behind a burnable obstacle (reachable only
   as Fire Wolf); HUD found-count; collecting all 3 grants a PERMANENT extra heart. Commit.
7. Narration + audio + captions: wire Web Speech (window.speechSynthesis) with an on/off toggle and
   on-screen CAPTIONS (default on, toggleable, independent of voice). Use the attached
   NARRATION-SCRIPT.md as the single source for all lines, voices (per-character rate/pitch), and
   triggers. Add CC0 music (Ember Hollow loop, boss loop, victory sting) + SFX (sword, hit, lava,
   form-switch, Blood Moon, pup chime, UI), with Music/SFX volume sliders; duck music while a line
   speaks. Vendor audio locally. Commit.
8. Title, profiles, settings, save: title screen with NAMED PROFILES (each kid their own save,
   typed name + icon); Continue / New Game / Settings; pause menu; gentle respawn (no harsh "Game
   Over"); region-complete screen. localStorage auto-save at checkpoints/unlocks/pups (schema in
   HUD-MENU-SAVE.md). Settings per profile. Commit.
9. Polish + offline: confirm the service worker caches EVERYTHING (incl. phaser + audio), the game
   installs as a PWA and plays fully offline, and it's landscape-locked. Final commit + push.

=== SUCCESS CRITERIA ===
Live GitHub Pages URL, landscape, installable offline PWA. Playable Ember Hollow: move via
joystick, attack shadow-creatures, take lava damage, lose/regain hearts (start 5), hit a checkpoint
and respawn full. Beat the Shadowgrip mini-boss → Cinder freed → Fire Wolf unlocked with the fire
ground-slam and burn-obstacle ability. Pip talks (Web Speech) and sparkles near pups; finding all
3 pups grants a permanent extra heart. Narration has captions; music + SFX play with volume
controls. Title screen with named per-kid profiles; progress auto-saves and Continue resumes at the
last checkpoint. BUILDLOG.md documents decisions; CREDITS.md lists real assets + licenses.

When you finish (or stop), summarize: the live URL, what works, what doesn't, and the next phase.
```

---

## How to drive it tomorrow
1. Let it create CLAUDE.md + show its plan. Skim it; correct anything off before it codes.
2. Work **phase by phase**. After each, open the URL it gives you and actually play-test with
   the kids' experience in mind (is text big enough? is it forgiving?).
3. If it drifts or a phase gets messy, the cleanest recovery is: `claude --continue`, point at
   BUILDLOG.md, and say "resume at Phase N; previous phases are done and committed."
4. Don't try to do all 8 phases in one sitting if it gets long — committing per phase means you
   can stop and resume anytime without losing work.
