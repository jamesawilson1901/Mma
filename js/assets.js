// =============================================================================
// assets.js — placeholder art, generated in code.
//
// The egress policy in the build environment blocks the CC0 art hosts
// (kenney.nl / opengameart.org), so for now we DRAW simple placeholder textures
// at boot instead of loading PNG files. Everything is keyed by a texture name
// (e.g. 'floor', 'wolf') so swapping in real art later is a one-line change:
// load the PNG under that same key in the Boot scene and delete the matching
// generator here. No gameplay code references pixels — only texture keys.
//
// Style: clean, readable, warm "Ember Hollow" palette. 32px tiles/sprites.
// =============================================================================

const TILE = 32; // logical tile / sprite size in pixels

// Build every placeholder texture into the given scene's texture manager.
function generatePlaceholderTextures(scene) {
  makeFloorTile(scene);
  makeWallTile(scene);
  makePlayer(scene);
}

// A warm volcanic stone floor tile with subtle speckles.
function makeFloorTile(scene) {
  const g = scene.make.graphics({ x: 0, y: 0, add: false });
  g.fillStyle(0x3a2a2a, 1);              // dark warm stone
  g.fillRect(0, 0, TILE, TILE);
  g.fillStyle(0x47332f, 1);              // lighter inset
  g.fillRect(1, 1, TILE - 2, TILE - 2);
  // a couple of faint ember flecks so the floor reads as volcanic
  g.fillStyle(0x6b3b2a, 1);
  g.fillRect(7, 9, 3, 3);
  g.fillRect(22, 20, 2, 2);
  g.generateTexture('floor', TILE, TILE);
  g.destroy();
}

// A rocky wall tile, clearly darker/blockier than the floor.
function makeWallTile(scene) {
  const g = scene.make.graphics({ x: 0, y: 0, add: false });
  g.fillStyle(0x241a1a, 1);
  g.fillRect(0, 0, TILE, TILE);
  g.fillStyle(0x2e2220, 1);
  g.fillRect(2, 2, TILE - 4, TILE - 4);
  g.lineStyle(2, 0x171010, 1);           // mortar lines
  g.strokeRect(2, 2, TILE - 4, TILE - 4);
  g.generateTexture('wall', TILE, TILE);
  g.destroy();
}

// Kael, the base sprite. Drawn as a simple knight/wolf-ish figure in neutral
// tones so a per-form Phaser tint reads correctly. White-ish base = tint shows.
// Centered for the 3/4 top-down view.
function makePlayer(scene) {
  const g = scene.make.graphics({ x: 0, y: 0, add: false });
  // body
  g.fillStyle(0xd9d2c4, 1);
  g.fillRoundedRect(8, 10, 16, 18, 4);
  // head
  g.fillStyle(0xe8e2d6, 1);
  g.fillCircle(16, 9, 6);
  // simple visor / eyes so there's a clear "front"
  g.fillStyle(0x2b2b3a, 1);
  g.fillRect(12, 8, 3, 2);
  g.fillRect(17, 8, 3, 2);
  // feet
  g.fillStyle(0x9a8f7c, 1);
  g.fillRect(10, 27, 4, 4);
  g.fillRect(18, 27, 4, 4);
  g.generateTexture('player', TILE, TILE);
  g.destroy();
}
