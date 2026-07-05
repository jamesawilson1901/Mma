import Phaser from 'phaser';
import { GAME_TITLE, GAME_WIDTH, GAME_HEIGHT } from '../config/constants.js';

// Milestone 0 boot scene: proves Vite + Phaser render on the phone.
// A moving sprite confirms the render loop; a tap/press counter confirms input.
export default class Boot extends Phaser.Scene {
  constructor() {
    super('Boot');
  }

  create() {
    const cx = GAME_WIDTH / 2;

    // Backdrop bands so scaling/letterboxing problems are obvious at a glance
    this.add.rectangle(cx, GAME_HEIGHT / 2, GAME_WIDTH, GAME_HEIGHT, 0x1d2b53);
    this.add.rectangle(cx, GAME_HEIGHT - 24, GAME_WIDTH, 48, 0x21402c);

    this.add
      .text(cx, 70, GAME_TITLE.toUpperCase(), {
        fontFamily: 'monospace',
        fontSize: '32px',
        color: '#ffd23f',
        stroke: '#000000',
        strokeThickness: 4,
      })
      .setOrigin(0.5);

    this.add
      .text(cx, 100, 'Milestone 0 — boot scene', {
        fontFamily: 'monospace',
        fontSize: '10px',
        color: '#c2c3c7',
      })
      .setOrigin(0.5);

    // Bouncing square = render loop alive
    this.bouncer = this.add.rectangle(cx, 160, 16, 16, 0xff004d);
    this.bounceT = 0;

    this.tapText = this.add
      .text(cx, 220, 'Tap anywhere to test touch input', {
        fontFamily: 'monospace',
        fontSize: '10px',
        color: '#ffffff',
      })
      .setOrigin(0.5);

    this.taps = 0;
    this.input.on('pointerdown', (pointer) => {
      this.taps += 1;
      this.tapText.setText(`Touch OK — taps: ${this.taps}`);
      this.add
        .circle(pointer.worldX, pointer.worldY, 6, 0x29adff, 0.8)
        .setDepth(10);
    });

    this.fpsText = this.add.text(4, 4, '', {
      fontFamily: 'monospace',
      fontSize: '8px',
      color: '#5f574f',
    });
  }

  update(time, delta) {
    this.bounceT += delta / 1000;
    this.bouncer.y = 160 + Math.sin(this.bounceT * 3) * 24;
    this.bouncer.rotation += delta / 400;
    this.fpsText.setText(`${Math.round(this.game.loop.actualFps)} fps`);
  }
}
