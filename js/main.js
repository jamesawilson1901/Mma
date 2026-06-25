// =============================================================================
// main.js — boots the Phaser game.
//
// Landscape, responsive scaling (Scale.FIT keeps the whole scene visible and
// letterboxes as needed), arcade physics ready for movement in Phase 1.
// =============================================================================

const GAME_WIDTH = 960;   // 3/4 top-down, landscape design resolution
const GAME_HEIGHT = 540;  // 16:9

const gameConfig = {
  type: Phaser.AUTO,
  parent: 'game',
  backgroundColor: '#1a0f0f',
  pixelArt: true,          // crisp scaling for the pixel-style placeholder art
  scale: {
    mode: Phaser.Scale.FIT,
    autoCenter: Phaser.Scale.CENTER_BOTH,
    width: GAME_WIDTH,
    height: GAME_HEIGHT,
  },
  physics: {
    default: 'arcade',
    arcade: { debug: false },
  },
  scene: [BootScene, EmberHollowScene],
};

// eslint-disable-next-line no-unused-vars
const game = new Phaser.Game(gameConfig);

// Register the service worker for offline/PWA support. Safe to call every load.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').catch((err) => {
      console.warn('Service worker registration failed:', err);
    });
  });
}
