// =============================================================================
// EmberHollowScene — the gameplay scene for the Fire region.
//
// PHASE 0: render a placeholder volcanic floor and Kael standing on it. No
// movement yet — this just proves the engine, scene, textures and deploy work.
// Movement, rooms, combat, etc. arrive in later phases.
// =============================================================================

class EmberHollowScene extends Phaser.Scene {
  constructor() {
    super('EmberHollow');
  }

  create() {
    const region = REGIONS[CURRENT_REGION];

    // --- Placeholder floor: tile a warm volcanic floor across the view. ---
    // A TileSprite is the simplest way to fill the screen with the floor tile.
    this.floor = this.add.tileSprite(
      0, 0,
      this.scale.width, this.scale.height,
      'floor'
    ).setOrigin(0, 0);

    // --- Kael (player). Centered; no physics/movement in Phase 0. ---
    this.player = this.add.sprite(
      this.scale.width / 2,
      this.scale.height / 2,
      'player'
    );
    this.player.setScale(2); // 32px sprite -> readable at 64px for young kids

    // Start as the Knight (base form, no tint).
    this.currentForm = 'knight';
    applyFormTint(this.player, this.currentForm);

    // --- A little onboarding text so the deploy is obviously "live". ---
    this.add.text(this.scale.width / 2, 40, region.label, {
      fontFamily: 'sans-serif',
      fontSize: '34px',
      color: '#ffd9a0',
      stroke: '#000000',
      strokeThickness: 5,
    }).setOrigin(0.5, 0);

    this.add.text(this.scale.width / 2, this.scale.height - 36,
      'Phase 0 — Kael has arrived. (Movement comes next.)', {
        fontFamily: 'sans-serif',
        fontSize: '18px',
        color: '#ffffff',
        stroke: '#000000',
        strokeThickness: 4,
      }).setOrigin(0.5, 1);

    // Keep the floor + text sensible if the window resizes.
    this.scale.on('resize', this.onResize, this);
  }

  onResize(gameSize) {
    if (this.floor) this.floor.setSize(gameSize.width, gameSize.height);
  }
}

// Apply the tint for a given form id (null tint = the Knight's true colors).
// Shared helper so every place that switches forms looks identical.
function applyFormTint(sprite, formId) {
  const form = FORMS[formId];
  if (!form || form.tint == null) {
    sprite.clearTint();
  } else {
    sprite.setTint(form.tint);
  }
}
