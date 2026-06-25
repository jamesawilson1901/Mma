# Narration Script — Ember Hollow (Fire slice)

Consolidated, ordered, and TTS-ready. Lines are short (good for speech + captions) and at a young
reading level. Store these as DATA (id, voice, text, trigger) so the engine fires them on events
and so later regions reuse the structure. Captions show each line on screen (default on).

## Voices (Web Speech API — differentiate by rate/pitch; pick distinct system voices if available)
- **Pip** (warm fox guide) — rate ~1.0, pitch ~1.3. Does most of the talking.
- **Cinder** (wise, ancient fire spirit) — rate ~0.85, pitch ~0.8. Slow, deep, kind.
- **Grimm** (the Shadow King) — rate ~0.85, pitch ~0.6. Low and menacing, but never gory/scary.
- **Luna** (fading guardian, in dreams) — rate ~0.9, pitch ~1.1. Soft and gentle.
- **Kael** — silent (player avatar). No spoken lines by default.

---

## Part A — Story beats (fire in order along the critical path)

- **intro_arrival** *(new game, R1 start)* [Pip]: "This is Ember Hollow, Kael. The shadows crept in here… I feel it. Stay close."
- **first_enemy** *(first Shade appears)* [Pip]: "Careful — a shadow! Tap to swing your sword."
- **dark_nook** *(near the dark nook in R1)* [Pip]: "It's too dark to see in there. Hold the screen and become the Dark Wolf — you can see in the shadows."
- **obstacle_first** *(first burnable obstacle seen)* [Pip]: "Burnt vines block the way. We'll need fire for these. Let's remember this spot."
- **r2_enter** *(entering R2)* [Pip]: "Watch your step — lava ahead. Stay on the stone."
- **moth_intro** *(first Ember Moth)* [Pip]: "Shadow moths! Wait for them to dive, then move."
- **geyser_intro** *(at the geyser crossing)* [Pip]: "Fire geysers! Cross when they rest. Watch the timing."
- **hound_branch** *(near the Shadow Hound branch — optional)* [Pip]: "A shadow hound guards that way. Beat it for a pup — or skip it if you like."
- **boss_door** *(approaching the boss room)* [Pip]: "The spirit is near… but something's wrong. Be ready, Kael."
- **boss_intro** *(boss reveal)* [Pip]: "There! The fire spirit — a shadow has it in its grip! Free it!"
- **boss_p1** *(phase 1 start)* [Pip]: "It's holding the spirit! When a tendril gets stuck, hit it — quick!"
- **boss_p1_telegraph** *(first tendril slam telegraph)* [Pip]: "Look out — move off the dark circle!"
- **boss_p2** *(phase 2, core exposed)* [Pip]: "The core is open — strike it!"
- **boss_bloodmoon** *(Blood Moon ready during the fight)* [Pip]: "Your Blood Moon is ready — crash it down on the core!"
- **boss_p3** *(phase 3, room darkens)* [Pip]: "It's too dark! Become the Dark Wolf — you can see in the shadows!"
- **boss_defeat** *(boss beaten)* [Cinder]: "You broke the shadow's hold, kind knight. I am Cinder, keeper of the flame."
- **firewolf_grant** *(immediately after)* [Cinder]: "Take this gift — the heart of the Fire Wolf."
- **firewolf_howto** *(form unlocked)* [Pip]: "You can be the Fire Wolf now! Hold the screen to change. Try the ground-slam!"
- **burn_prompt** *(back at a burnable obstacle, Fire Wolf available)* [Pip]: "Now we can clear those burnt vines! Be the Fire Wolf and slam them."
- **all_pups** *(3rd pup collected)* [Pip]: "You found them all! The pups are safe now. Your heart grows stronger."
- **region_complete** *(slice end / exit)* [Pip]: "Ember Hollow is free, Kael. The light is coming back. Let's go on."

## Part B — Contextual lines (can fire any time their condition is met)

- **checkpoint** *(at any checkpoint)* [Pip]: "We can rest here. You're safe."
- **pup_found** *(any pup collected; reused for pups 1–2, and 3 also triggers all_pups)* [Pip]: "A lost wolf pup! You found one. Good eyes, Kael."
- **enemy_group** *(2–3 Shades clustered)* [Pip]: "Lots of shadows! Your Blood Moon can clear them — use it!"
- **low_hearts** *(hearts ≤ 2)* [Pip]: "Careful, Kael… let's find somewhere safe."
- **respawn** *(after losing all hearts — gentle, no "Game Over")* [Pip]: "It's okay. Let's try again — together."
- **form_locked** *(player opens the locked Fire Wolf slot pre-boss)* [Pip]: "We can't be the Fire Wolf yet. First we free the fire spirit."
- **stuck_hint** *(idle near a gate ~20s — re-hint)* [Pip]: replay the matching teach line (dark_nook / geyser_intro / burn_prompt).

## Part C — Bridge to the next region (the slice's outro; template for every region)

- **grimm_taunt_1** *(after region_complete, before the dream)* [Grimm]: "So… the little knight saved one spark. You cannot save them all. The shadow always returns."
- **luna_dream_1** *(dream interlude, then → Earth / Stoneroot Caverns)* [Luna]: "You did well, Kael. One light returned… six to go. Follow the path to the stone caves. I am with you, always."

---

## Notes for the build
- Fire each line ONCE per save unless it's a Part B contextual line (those can repeat).
- Duck music volume slightly while a line speaks; resume after.
- Respect the captions toggle and the narration on/off toggle independently (a kid may want text but
  no voice, or voice but no text).
- Keep a single `lines` data table keyed by id; trigger from game events. Later regions add their own
  ids with the same shape (e.g., earth_intro, petra_grant, grimm_taunt_2, luna_dream_2…).
