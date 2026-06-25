# Level Map — Ember Hollow (Fire slice)

Three connected chambers, played left-to-right, with one backtrack loop after the Fire Wolf is
earned. The slice is small but teaches one tool per pup. **Mostly linear with one optional branch.**

## Critical path (one line)
Arrive (R1) → learn move + attack → *(Dark Wolf nook → Pup #1)* → CP1 → cross R2 (moths, geysers)
→ *(beat Shadow Hound → Pup #2)* → CP2 → boss door → **Shadowgrip** (R3) → free Cinder → **gain
Fire Wolf** → backtrack, burn obstacle → Pup #3 → all 3 pups = **+1 heart** → exit toward Earth
(Luna dream).

## Legend
`o` start · `★` checkpoint · `🐺` lost wolf pup · `▓` burnable obstacle (Fire Wolf) ·
`░` dark zone (Dark Wolf "see in dark") · `~` lava · `!` ember geyser (timed) · `S` Shade ·
`M` Ember Moth · `H` Shadow Hound (elite) · `◉` boss (Shadowgrip + caged Cinder) · `→` exit/door

## Connection diagram
```
  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐
  │  R1 Entrance │──────│ R2 Causeway  │──────│ R3 Heart of  │
  │   (intro)    │      │ (hazards)    │      │ the Hollow   │
  └──────┬───────┘      └──────────────┘      │   (boss)     │
         │   ◄── shortcut opens after boss ───┘──────────────┘
         └────────────── (backtrack loop) ──────────────────►
```
After the boss, a one-way shortcut opens from R3 back to R1 so backtracking for Pup #3 is quick.

---

## Room 1 — Hollow Entrance  *(teaches: move, attack, form-switch)*
Purpose: gentle onboarding. Kael and Pip arrive; the corruption is visible as shadow wisps.

```
                              → CP1 / to R2
                                   ▲
        +--------------------------|----------+
        |   ~~~lava~~~                   ★    |
        |                          S          |
        |   o  (Kael + Pip)                   |
        |   intro narration      ░░░ dark nook|
        |                        ░ 🐺#1 ░      |
        |   ▓▓ burnable ▓▓                     |
        |   (behind it: 🐺#3)                  |
        +-------------------------------------+
```
- Open with Pip's arrival line. Player walks (joystick) to proceed; first **Shade** appears →
  Pip prompts tap-to-attack.
- A small **dark nook** (`░`): too dark to see as the Knight. Pip prompts switching to **Dark
  Wolf** ("see in the dark"); inside is **Pup #1** — rewards trying the form early.
- A **burnable obstacle** (`▓`) blocks a side cubby holding **Pup #3** — can't pass yet (no Fire
  Wolf). Seeds the backtrack.
- Small lava patches introduce the hazard (walk around). **Checkpoint CP1** at the exit.

## Room 2 — Ember Causeway  *(teaches: dodging, telegraph-reading; the optional branch)*
Purpose: the "meat" — light hazard navigation + combat, with one optional reward path.

```
                                        → boss door
                                            ▲
     +--------------------------------------|-----+
     |  ~~~~lava channel~~~~          ★ CP2       |
     |   safe path  M        ! ! ! (geysers)      |
     |  =========(stone bridge)===========        |
     |                 \                          |
     |   [easy path]    \__ [branch] H  🐺#2      |
     |                        (Shadow Hound)      |
     +--------------------------------------------+
```
- **Ember Moths** (`M`) dive near the lava channel — teaches dodging.
- An **ember-geyser** crossing (`! ! !`): geysers erupt on a ~2s telegraphed on/off cycle; cross
  when dormant. A pure-skill gate, no enemy.
- **Branch (the "some choice"):** the main path is easy; a side path holds **Pup #2**, guarded by
  the elite **Shadow Hound** (`H`). Optional — forgiving players can skip it and still finish.
- **Checkpoint CP2** just before the boss door.

## Room 3 — Heart of the Hollow  *(the boss + the reward)*
Purpose: the climax. See COMBAT-SPEC.md for the full Shadowgrip fight.

```
        +-----------------------------+
        |   ~~~lava rim~~~             |
        |     ▌pillar        pillar▐   |
        |          ◉  (Shadowgrip      |
        |          gripping Cinder)    |
        |     ▌pillar        pillar▐   |
        |   ★ CP3 (arena entrance)     |
        +-----------------------------+
                    ▲
                 boss door
```
- **CP3** at the arena entrance → boss respawns are at full hearts, room reset cleanly.
- Win → Cinder freed → **Fire Wolf** granted (ground-slam + burn). Room fills with warm light.
- The **shortcut to R1** opens. Player backtracks, **burns the obstacle** in R1 → **Pup #3**.
- Collecting all 3 pups grants the **permanent +1 heart** (5 → 6). Pip celebrates.
- A glowing exit beyond R3 leads onward → the first **Luna dream**, then **Earth / Stoneroot**.

---

## At-a-glance tables

**Pups → each behind a different ability gate**
| Pup | Where | Gate / how to reach | Teaches |
|---|---|---|---|
| 🐺 #1 | R1 dark nook | switch to **Dark Wolf** (see in dark) | form-switching, early |
| 🐺 #2 | R2 side branch | beat the **Shadow Hound** | telegraph-reading / combat |
| 🐺 #3 | R1 cubby behind burnable | **Fire Wolf** burn (after boss) | using the new power, backtracking |

**Checkpoints (respawn = full hearts)**
| CP | Location |
|---|---|
| ★ CP1 | R1 exit |
| ★ CP2 | R2, before boss door |
| ★ CP3 | R3 arena entrance |

**Dark zones (Dark Wolf):** R1 nook (Pup #1) · boss Phase 3 darkness · *(optional)* a short R2 passage.
**Burnable obstacles (Fire Wolf):** R1 cubby (Pup #3) is the required one; add 1-2 optional ones for flavor.
