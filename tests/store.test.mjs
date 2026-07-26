// Tests for the client data layer: metrics and the export → wipe → import
// round trip. localStorage is stubbed so the browser module runs under Node.

import test from 'node:test';
import assert from 'node:assert/strict';

const mem = new Map();
globalThis.localStorage = {
  getItem: (k) => (mem.has(k) ? mem.get(k) : null),
  setItem: (k, v) => mem.set(k, String(v)),
  removeItem: (k) => mem.delete(k),
};

const db = await import('../public/js/store.js');

function freshItem(overrides = {}) {
  mem.clear();
  db.wipe();
  return db.createItem({
    mode: 'build', title: 'Walk', ifCue: 'After coffee', thenAction: 'walk',
    anchor: 'coffee', goalTarget: 15, goalUnit: 'minutes', goalFrequency: 'day',
    why: 'energy', ...overrides,
  });
}

test('consistency counts done days and protects rest days', () => {
  const item = freshItem();
  // Simulate: created 9 days ago, 6 done, 1 rest, 2 missed.
  item.createdAt = db.shiftDate(db.todayStr(), -9);
  for (const off of [0, -1, -2, -4, -5, -7]) db.logDay(item.id, db.shiftDate(db.todayStr(), off), true);
  db.logDay(item.id, db.shiftDate(db.todayStr(), -3), 'rest');
  const c = db.consistency(db.findItem(item.id));
  assert.equal(c.window, 10);
  assert.equal(c.done, 6);
  assert.equal(c.rest, 1);
  assert.equal(c.pct, Math.round((6 / 9) * 100));
});

test('current run passes through rest days and stops at a miss', () => {
  const item = freshItem();
  item.createdAt = db.shiftDate(db.todayStr(), -10);
  db.logDay(item.id, db.todayStr(), true);
  db.logDay(item.id, db.shiftDate(db.todayStr(), -1), true);
  db.logDay(item.id, db.shiftDate(db.todayStr(), -2), 'rest');
  db.logDay(item.id, db.shiftDate(db.todayStr(), -3), true);
  // -4 missed
  db.logDay(item.id, db.shiftDate(db.todayStr(), -5), true);
  assert.equal(db.currentRun(db.findItem(item.id)), 3);
});

test('a single missed yesterday flags a lapse, not a broken world', () => {
  const item = freshItem();
  item.createdAt = db.shiftDate(db.todayStr(), -5);
  db.logDay(item.id, db.shiftDate(db.todayStr(), -2), true);
  assert.equal(db.inLapse(db.findItem(item.id)), true);
  db.logDay(item.id, db.todayStr(), true);
  assert.equal(db.inLapse(db.findItem(item.id)), false);
});

test('missesInLast7 ignores today and days before creation', () => {
  const item = freshItem();
  item.createdAt = db.shiftDate(db.todayStr(), -3);
  db.save();
  // yesterday, -2, -3 unlogged → 3 misses; -4.. are pre-creation
  assert.equal(db.missesInLast7(db.findItem(item.id)), 3);
});

test('export → wipe → import round-trips perfectly', () => {
  const item = freshItem();
  db.logDay(item.id, db.todayStr(), true, 'felt good');
  db.load().settings.badgesOptIn = true;
  db.save();
  const exported = db.exportJSON();

  db.wipe();
  assert.equal(db.load().items.length, 0);

  db.importJSON(exported);
  const restored = db.load();
  assert.equal(restored.items.length, 1);
  assert.equal(restored.items[0].title, 'Walk');
  assert.equal(restored.items[0].logs[0].note, 'felt good');
  assert.equal(restored.settings.badgesOptIn, true);
  assert.equal(JSON.parse(db.exportJSON()).items.length, 1);
});

test('import rejects malformed payloads with helpful errors', () => {
  freshItem();
  assert.throws(() => db.importJSON('not json'), /valid JSON/);
  assert.throws(() => db.importJSON('{"schemaVersion":99,"items":[]}'), /newer schema/);
  assert.throws(() => db.importJSON('{"schemaVersion":1}'), /no items/);
  assert.throws(() => db.importJSON('{"schemaVersion":1,"items":[{"id":1}]}'), /malformed/);
});
