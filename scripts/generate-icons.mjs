// Generates the PWA icon set as PNGs with no image-library dependency.
// Design: the consistency ring — the app's signature visual — brass on deep green-black.
// Run: npm run icons  (writes into public/icons/)

import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'icons');
mkdirSync(OUT, { recursive: true });

const BG = [14, 20, 18];        // #0E1412
const RING = [201, 165, 87];    // #C9A557 brass
const DOT = [232, 230, 223];    // #E8E6DF warm white

const crc32Table = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = crc32Table[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const c = Buffer.alloc(4);
  c.writeUInt32BE(crc(body));
  return Buffer.concat([len, body, c]);
}

function encodePNG(width, height, rgba) {
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; ihdr[9] = 6; // 8-bit RGBA
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0; // filter: none
    rgba.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  }
  return Buffer.concat([
    sig,
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// Signed distance helpers with 3x3 supersampling for smooth edges.
function drawIcon(size, { maskable = false, transparentBg = false } = {}) {
  const px = Buffer.alloc(size * size * 4);
  const cx = size / 2, cy = size / 2;
  // Maskable icons must keep art inside the central 80% safe zone.
  const scale = maskable ? 0.62 : 0.78;
  const rOuter = (size / 2) * scale;
  const ringWidth = size * 0.085;
  const rMid = rOuter - ringWidth / 2;
  const dotR = size * 0.075;
  // Ring gap: 55 degrees centred at the top (12 o'clock), with round caps.
  const gapHalf = (55 / 2) * (Math.PI / 180);
  const capR = ringWidth / 2;
  const capAngleA = -Math.PI / 2 - gapHalf; // gap edge, left of top
  const capAngleB = -Math.PI / 2 + gapHalf; // gap edge, right of top
  const capA = [cx + rMid * Math.cos(capAngleA), cy + rMid * Math.sin(capAngleA)];
  const capB = [cx + rMid * Math.cos(capAngleB), cy + rMid * Math.sin(capAngleB)];

  const S = 3; // supersample grid
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let ringCov = 0, dotCov = 0;
      for (let sy = 0; sy < S; sy++) {
        for (let sx = 0; sx < S; sx++) {
          const fx = x + (sx + 0.5) / S;
          const fy = y + (sy + 0.5) / S;
          const dx = fx - cx, dy = fy - cy;
          const dist = Math.hypot(dx, dy);
          // ring band, excluding the top gap, plus round caps at the gap edges
          const inBand = Math.abs(dist - rMid) <= ringWidth / 2;
          let ang = Math.atan2(dy, dx); // -PI..PI, -PI/2 is top
          const inGap = ang > capAngleA && ang < capAngleB;
          let hit = inBand && !inGap;
          if (!hit) {
            if (Math.hypot(fx - capA[0], fy - capA[1]) <= capR) hit = true;
            if (Math.hypot(fx - capB[0], fy - capB[1]) <= capR) hit = true;
          }
          if (hit) ringCov++;
          if (dist <= dotR) dotCov++;
        }
      }
      ringCov /= S * S; dotCov /= S * S;
      const i = (y * size + x) * 4;
      let r = BG[0], g = BG[1], b = BG[2], a = 255;
      if (transparentBg) { a = 0; r = 0; g = 0; b = 0; }
      if (ringCov > 0) {
        r = Math.round(r * (1 - ringCov) + RING[0] * ringCov);
        g = Math.round(g * (1 - ringCov) + RING[1] * ringCov);
        b = Math.round(b * (1 - ringCov) + RING[2] * ringCov);
        if (transparentBg) a = Math.round(255 * ringCov);
      }
      if (dotCov > 0) {
        r = Math.round(r * (1 - dotCov) + DOT[0] * dotCov);
        g = Math.round(g * (1 - dotCov) + DOT[1] * dotCov);
        b = Math.round(b * (1 - dotCov) + DOT[2] * dotCov);
        if (transparentBg) a = Math.max(a, Math.round(255 * dotCov));
      }
      px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = a;
    }
  }
  return encodePNG(size, size, px);
}

const files = [
  ['icon-192.png', drawIcon(192)],
  ['icon-512.png', drawIcon(512)],
  ['maskable-512.png', drawIcon(512, { maskable: true })],
  ['badge-96.png', drawIcon(96, { maskable: false, transparentBg: true })],
];
for (const [name, buf] of files) {
  writeFileSync(join(OUT, name), buf);
  console.log('wrote', name, buf.length, 'bytes');
}
