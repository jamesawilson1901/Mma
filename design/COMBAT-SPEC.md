# Combat Spec — Ember Hollow (Fire slice)

Design rules: young kids, so **forgiving and readable**. Every attack gets a clear ~1-second
telegraph; Kael gets brief invincibility (i-frames) after taking a hit; death = respawn at the
room checkpoint with full hearts (never sent far back). No timers, no permadeath. **Pip calls out
telegraphs aloud** as built-in coaching (lines below feed the Web Speech system).

Kael's tools in this slice: **Knight** (sword, tap-to-attack), **Dark Wolf** (Blood Moon — a
cooldown ultimate that calls a blood-red moon crashing down; plus "see in the dark"). Fire Wolf is
NOT available until the boss is beaten — the fight must be winnable with Knight + Dark Wolf.

Tuning defaults (agent can adjust): contact/attacks cost **1 heart**; Kael starts with **5**;
i-frames ~1s after a hit.

---

## Enemies

| Enemy | Look | Behavior | Health | Telegraph | Where |
|---|---|---|---|---|---|
| **Shade** (grunt) | Small dark wisp/blob with faint ember flecks | Drifts slowly toward Kael; contact damage | 1-2 sword hits | None needed — it's slow | Common, all rooms; teaches basic melee |
| **Ember Moth** | Shadow moth with glowing wings | Bobs/hovers, then **dives** at Kael in a line | 1-2 hits | Pauses + glows brighter ~0.8s before diving | Near lava/ceilings; teaches dodging |
| **Shadow Hound** (elite) | Corrupted shadow-wolf — a dark mirror of Kael | Stalks, then **crouches and charges** in a straight line; recovers slowly after | 3 hits | Crouches low + a shadow streak shows the charge lane ~1s | Guards the pup behind the boss door; mini-gatekeeper |

Defeated enemies puff into harmless smoke (not gory). Shades may appear in small groups (2-3),
never swarms. **Blood Moon** one-shots grunts in its radius — a satisfying "clear the room" moment
worth saving for when Shades cluster.

**Hazards (not enemies):** lava tiles (1 heart on contact), and optional **ember geysers** that
erupt on a 2s on/off telegraphed cycle — stand clear, cross when dormant.

---

## Boss — The Shadowgrip

**Concept:** a mass of shadow in the chamber's center, its tendrils wrapped around a caged ember
of warm light — that's **Cinder**, the trapped fire spirit. You can't hurt the core while the
tendrils hold it; you have to sever the grip, then strike the exposed core. Free Cinder = win.

**Arena:** a round volcanic chamber. Safe stone floor with a lava rim (don't get knocked... there's
no knockback — lava is just the edge boundary). Two pillars give cover from dives. Cinder glows
weakly at center, held aloft by tendrils.

### Phase 1 — Sever the tendrils
- Three **shadow tendrils** rise and **slam down** at telegraphed spots (a dark circle marks the
  ground ~1s before impact). Walk out of the circles.
- After a slam, the tendril **sticks in the floor for ~2s** — vulnerable. Sword it. Sever all 3.
- Pip: *"It's holding the spirit! Hit the tendrils when they get stuck — quick!"*
- Pip on a telegraph: *"Look out — move off the dark circle!"*

### Phase 2 — Strike the core + adds
- Tendrils severed → the core (a shadowy eye over Cinder) is exposed. Attack it.
- The Shadowgrip recoils, summons **2 Shades**, and sends a **slow shadow wave** rotating across
  the floor (telegraphed sweep — walk around it; there's always a safe arc).
- **Blood Moon** deals big damage to the core here — encourage it. Pip: *"Your Blood Moon is ready —
  use it on the core!"*
- Core takes ~6-8 sword hits or ~2 Blood Moons (plus a few hits) across Phases 2-3. Agent tunes.

### Phase 3 — The dark grip (Dark Wolf payoff)
- The Shadowgrip tightens and the **room goes dark**. As the **Knight** you can barely see; switch
  to **Dark Wolf** ("see in the dark") to light the chamber and read the boss's tells. This forces
  a satisfying use of the ability the kids learned earlier.
- Tendril slams come a bit faster (still telegraphed); the core opens in brief bursts — land the
  final hits.
- Pip: *"It's too dark! Become the Dark Wolf — you can see in the shadows!"*

### Defeat → free Cinder
- The Shadowgrip dissolves with a long howl; the tendrils release; warm light floods the room.
- Cinder (freed): *"You broke the shadow's hold, kind knight. I am Cinder. Take the heart of the
  Fire Wolf."*
- Kael gains the **Fire Wolf** form (ground-slam + burn obstacles). Pip: *"You can be the Fire Wolf
  now! Hold the screen to change. Try the ground-slam!"*

### Forgiveness specifics
- Death in the fight = respawn at the boss-room checkpoint, full hearts, boss reset cleanly
  (anti-soft-lock). 
- All telegraphs ~1s; Pip pre-warns the first time each attack appears.
- No phase has more than 2 Shades on screen; the wave always leaves a safe path.
