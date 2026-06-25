// =============================================================================
// config.js — DATA that drives the whole game.
//
// This is the "architect for reuse" layer. The game is region 1 of 7; later
// regions (Earth, Electric, Water, Ice, Wind, Light) get added here as DATA,
// not as new code. Same for wolf forms. Keep gameplay code generic and read
// everything it needs from these tables.
// =============================================================================

// One base wolf sprite is recolored per element with a Phaser tint — never make
// separate wolf art files. (See design/ASSETS.md.)
const ELEMENT_TINTS = {
  dark:     0x4a3b6b,
  fire:     0xff5a2b, // Ember Hollow / Fire Wolf
  earth:    0x8b6b3d,
  electric: 0xf2d54a,
  water:    0x3aa0ff,
  ice:      0x9be3ff,
  wind:     0xb6f0c4,
  light:    0xfff4c2,
};

// Kael's forms. `tint: null` = the Knight's true colors (no recolor).
// `unlockedAtStart` forms are playable from minute one (Luna's gift); the Fire
// Wolf unlocks after Cinder is freed at the boss.
const FORMS = {
  knight: {
    id: 'knight',
    label: 'Knight',
    tint: null,
    special: 'sword_combo',   // tap-to-attack melee
    traversal: null,
    unlockedAtStart: true,
  },
  dark_wolf: {
    id: 'dark_wolf',
    label: 'Dark Wolf',
    tint: ELEMENT_TINTS.dark,
    special: 'blood_moon',    // cooldown ultimate: blood-red moon crashes a foe
    traversal: 'see_in_dark', // lights up shadowed rooms
    unlockedAtStart: true,
  },
  fire_wolf: {
    id: 'fire_wolf',
    label: 'Fire Wolf',
    tint: ELEMENT_TINTS.fire,
    special: 'ground_slam',   // fire shockwave around Kael
    traversal: 'burn',        // burns scorched obstacles
    unlockedAtStart: false,   // earned from Cinder after the Shadowgrip
  },
};

// Region config. Only Ember Hollow (fire) is implemented now; the shape is the
// template every future region reuses (tileset, rooms, enemies, spirit, boss,
// pups, narration). Rooms/enemies/pups get fleshed out in later phases.
const REGIONS = {
  ember_hollow: {
    id: 'ember_hollow',
    label: 'Ember Hollow',
    element: 'fire',
    spirit: { id: 'cinder', label: 'Cinder', grantsForm: 'fire_wolf' },
    miniBoss: { id: 'shadowgrip', label: 'the Shadowgrip' },
    startHearts: 5,
    maxHeartsAfterPups: 6,
    pupsForExtraHeart: 3,
    // rooms / enemies / pups are added in later phases as data.
    rooms: [],
    pups: [],
  },
};

// The slice we are building right now.
const CURRENT_REGION = 'ember_hollow';
