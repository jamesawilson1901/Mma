// =============================================================================
// BootScene — runs first. Builds placeholder textures, then starts the game.
//
// When real CC0 art is added later, load PNGs here in preload() under the same
// texture keys (e.g. this.load.image('floor', 'assets/...png')) and remove the
// matching generators in assets.js. Nothing else needs to change.
// =============================================================================

class BootScene extends Phaser.Scene {
  constructor() {
    super('Boot');
  }

  preload() {
    // (Real PNG/audio loads will go here in later phases.)
  }

  create() {
    generatePlaceholderTextures(this);
    this.scene.start('EmberHollow');
  }
}
