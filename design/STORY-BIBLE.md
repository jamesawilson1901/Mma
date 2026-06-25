# Story Bible — Wolf Knight: Ember Hollow (vertical slice)

Locked from the design Q&A. This is the story/content companion to the GDD. The build prompt
references it; the narration lines below feed the Web Speech (TTS) system directly.

## Logline
A kind knight named **Kael** enters **Ember Hollow**, a volcano region where Grimm's shadows
have crept in, and frees the wise fire spirit **Cinder** from a shadow's grip — earning the
**Fire Wolf** form.

## Tone
**Mysterious & magical.** Warm but with a sense of ancient depth — not cutesy, not scary.
Calm exploration punctuated by moments of wonder (the spirit) and gentle tension (the shadows).

## Characters
- **Kael** — the hero the kids play. A **kind, grown knight**: steady, gentle, brave. Strong and
  reassuring rather than flashy. He can transform between forms via the radial picker.
- **Kael's starting forms (playable from minute one):**
  - **Knight** — base form, sword melee (tap-to-attack).
  - **Dark Wolf** — Luna's gift, available from the start. Signature **Blood Moon**: Kael howls,
    the moon turns blood-red and comes crashing down onto an enemy — a devastating strike. Make it
    a powerful move on a cooldown / limited charge so it stays a "wow" moment, not a spam button.
    Ability: **see in the dark** (night-vision that lights up shadowed areas — fitting, since the
    Dark Wolf sees through Grimm's shadow; good for dark, corrupted rooms).
  - **Fire Wolf** — first *elemental* form, earned from Cinder at the Ember Hollow climax.
- **Pip** — a fox kit and Kael's **talking guide**. Warm, encouraging, kid-friendly. Delivers the
  story *in the moment* (no cutscenes) — points things out, cheers, gives gentle hints. Sparkles
  near hidden things.
- **Cinder** — the **wise, ancient** fire spirit. Speaks rarely, with weight and kindness. Freed
  at the climax; grants the Fire Wolf form.
- **The Shadowgrip** *(working name — rename freely)* — the mini-boss: a shadow-creature that has
  seized Cinder. Defeating it frees the spirit. A manifestation of Grimm's corruption.
- **Grimm the Shadow King** — overarching villain, **not fought in this slice**. Felt only through
  the shadow-creatures and corruption.

## The slice arc (told in-game via Pip — no cutscene)
1. Kael and Pip arrive in shadow-touched Ember Hollow. Pip senses the corruption.
2. Explore 3 connected volcano rooms: dodge **lava hazards**, fight **shadow-creatures**, find
   **scorched obstacles** blocking some paths (can't clear them yet — seeds the return trip).
3. Hidden **lost wolf pups** are tucked around the region; Pip sparkles when one is near.
4. Reach Cinder — held in **the Shadowgrip's** grasp. **Mini-boss battle.**
5. Win → Cinder is freed → grants the **Fire Wolf** form: **fire ground-slam** attack + the power
   to **burn obstacles**.
6. Payoff loop: use Fire Wolf to burn the scorched obstacles and reach the remaining pups.
   Finding **all 3 pups** grants a **permanent extra heart**.

## Gameplay rules (tied to story)
- **Health:** start with **5 hearts**. Checkpoints; respawn with **full hearts**. Forgiving.
- **Extra heart:** collecting all 3 lost pups → permanent +1 heart.
- **Fire Wolf combat:** **ground-slam** — a fire shockwave around Kael.
- **Fire Wolf traversal/puzzle:** **burn obstacles** to open blocked paths.
- **Anti-soft-lock:** puzzle objects reset to solvable on room re-entry. Never unwinnable.
- **Controls:** virtual joystick (move) + tap-to-attack + hold for radial form-picker.

## Narration / dialogue lines (simple reading level; for Web Speech TTS)
Keep sentences short. Pip = warm guide voice; Cinder = slow, wise voice.

**Pip — arrival (intro):** "This is Ember Hollow, Kael. The shadows crept in here… I feel it. Stay close."
**Pip — first enemy:** "Careful — a shadow! Tap to swing your sword."
**Pip — first scorched obstacle (pre-Fire Wolf):** "Burnt vines block the path. We'll need fire for these. Let's keep looking."
**Pip — nearing the spirit:** "There! The fire spirit… but a shadow has it! Help it, Kael!"
**Cinder — freed (after boss):** "You broke the shadow's hold, kind knight. I am Cinder. Take the heart of the Fire Wolf."
**Pip — Fire Wolf unlocked:** "You can be the Fire Wolf now! Hold the screen to change. Try the ground-slam!"
**Pip — burnable obstacle, now as Fire Wolf:** "Now we can clear those! Slam the burnt vines!"
**Pip — found a pup:** "A lost pup! You found one. Good eyes!"
**Pip — all pups found:** "You found them all! They're safe now. Your heart grows stronger."
**Pip — low hearts:** "Careful, Kael… let's find somewhere safe."
**Pip — checkpoint:** "We can rest here. You're safe."

## Open / rename if you like
- Mini-boss name ("the Shadowgrip"), exact number of rooms, and pup hiding spots are flexible.
- Dark Wolf is **playable from the start** of the slice (not a stub): Knight + Dark Wolf available
  from minute one, Fire Wolf earned at the climax.

---

# FULL GAME — all 7 regions

Kael starts with two playable forms: his **Knight** (sword) and the **Dark Wolf** (Luna's gift),
whose signature **Blood Moon** calls a blood-red moon crashing down on a foe and whose ability is
**see in the dark**. He then journeys
through seven regions, freeing one elemental spirit in each and earning that Wolf form. The
regions **chain directly** into one another (no hub world). Progression is **mostly linear with
some choice**. **Grimm taunts** Kael at the transitions between regions. **Luna guides Kael in
dreams** at rest points between regions (story + gentle hints — this is the "home base" feeling
without a hub). The journey ends by **restoring light to the world**.

## Region table (play order)

| # | Region (setting) | Spirit (personality) | Wolf form — combat special | Traversal / puzzle mechanic |
|---|---|---|---|---|
| 1 | **Fire** — Ember Hollow | **Cinder** — wise & ancient | Fire ground-slam (shockwave) | Burn obstacles |
| 2 | **Earth** — Stoneroot Caverns | **Petra** — stern guardian | Rising rock spikes | Push boulders |
| 3 | **Electric** — Stormspire | **Volt** — quick & excitable | Electric dash | Power up switches |
| 4 | **Water** — Tidecall Grotto | **Marina** — playful & splashy | Tidal wave sweep | Ride currents |
| 5 | **Ice** — Frostveil Glacier | **Glacia** — shy & quiet | Frost nova | Freeze water to cross |
| 6 | **Wind** — Galewing Cliffs | **Zephyr** — free & breezy | Tornado spin | Glide across gaps |
| 7 | **Light** — Dawnspire Temple | **Lumina** | Blinding flash | Reveal hidden paths |

## Per-region template (same shape as Ember Hollow)
Each region repeats the proven loop, so only the *content* changes:
- A shadow mini-boss clutches the spirit (the "Shadowgrip" pattern); beating it frees the spirit
  and **grants that Wolf form** (its combat special + traversal verb).
- The new traversal verb then opens that region's late secrets (where some hidden pups hide).
- **Lost wolf pups** are scattered each region; collecting a region's full set grants a
  **permanent +1 heart** (start: 5 hearts).
- Story is delivered in-game by **Pip** (talking guide) + the freed spirit's lines; **Grimm
  taunts** on the way out; a **Luna dream** bridges to the next region.

## Finale (Light / Dawnspire Temple)
With all spirits freed, Kael confronts Grimm in a **final wolf-form battle** — the player swaps
between earned forms to counter Grimm's attacks. Victory **restores light to the world**.

## Still open (easy to fill later, not blocking)
- Names for each region's mini-boss (currently the shared "Shadowgrip" pattern).
- The exact "some choice" branch points in the mostly-linear path.
- Whether each form keeps a small role outside its home region (recommended: yes — reused for
  puzzles in later regions, classic Zelda-style).
