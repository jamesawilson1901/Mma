// =============================================================================
// sw.js — service worker for offline / PWA support.
//
// Strategy: precache the full app shell on install (so the game works fully
// offline, including the vendored Phaser engine), then serve cache-first and
// fall back to the network. Bump CACHE_VERSION whenever assets change so old
// caches are cleaned up.
//
// As new files are added in later phases (sprites, audio, more JS), add them to
// PRECACHE_URLS so they're cached for offline too.
// =============================================================================

const CACHE_VERSION = 'wolfknight-v0';

const PRECACHE_URLS = [
  './',
  './index.html',
  './manifest.json',
  './vendor/phaser.min.js',
  './vendor/rexvirtualjoystickplugin.min.js',
  './js/config.js',
  './js/assets.js',
  './js/scenes/BootScene.js',
  './js/scenes/EmberHollowScene.js',
  './js/main.js',
  './assets/icons/icon.svg',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_VERSION)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys.filter((k) => k !== CACHE_VERSION).map((k) => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  // Only handle GET; let everything else hit the network normally.
  if (event.request.method !== 'GET') return;

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request)
        .then((response) => {
          // Cache same-origin successful responses for next time (offline).
          if (response.ok && new URL(event.request.url).origin === self.location.origin) {
            const copy = response.clone();
            caches.open(CACHE_VERSION).then((cache) => cache.put(event.request, copy));
          }
          return response;
        })
        .catch(() => cached); // offline + uncached: nothing we can do
    })
  );
});
