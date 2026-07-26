// Unit tests for the dispatcher's timezone, window, quiet-hours and
// validation logic. Run with: npm test

import test from 'node:test';
import assert from 'node:assert/strict';
import {
  parseHM, localNow, inQuietHours, dueInWindow,
  validSubscription, sanitiseReminders, sanitiseSettings,
} from '../netlify/functions/lib/shared.mjs';

test('parseHM parses valid times and rejects junk', () => {
  assert.equal(parseHM('07:30'), 450);
  assert.equal(parseHM('7:30'), 450);
  assert.equal(parseHM('00:00'), 0);
  assert.equal(parseHM('23:59'), 1439);
  assert.equal(parseHM('24:00'), null);
  assert.equal(parseHM('7:5'), null);
  assert.equal(parseHM(''), null);
  assert.equal(parseHM(null), null);
});

test('localNow converts UTC to Brisbane (UTC+10, no DST)', () => {
  // 2026-07-26T22:30Z → 2026-07-27 08:30 in Brisbane
  const local = localNow(new Date('2026-07-26T22:30:00Z'), 'Australia/Brisbane');
  assert.equal(local.date, '2026-07-27');
  assert.equal(local.minutes, 8 * 60 + 30);
});

test('localNow midnight edge does not produce hour 24', () => {
  // 14:00Z = 00:00 Brisbane
  const local = localNow(new Date('2026-07-26T14:00:00Z'), 'Australia/Brisbane');
  assert.equal(local.date, '2026-07-27');
  assert.equal(local.minutes, 0);
});

test('localNow falls back to Brisbane on a bad timezone', () => {
  const local = localNow(new Date('2026-07-26T02:00:00Z'), 'Not/AZone');
  assert.equal(local.minutes, 12 * 60);
});

test('quiet hours wrap midnight (default 21:00–07:00)', () => {
  const quiet = { start: '21:00', end: '07:00' };
  assert.equal(inQuietHours(parseHM('22:00'), quiet), true);
  assert.equal(inQuietHours(parseHM('02:00'), quiet), true);
  assert.equal(inQuietHours(parseHM('06:59'), quiet), true);
  assert.equal(inQuietHours(parseHM('07:00'), quiet), false);
  assert.equal(inQuietHours(parseHM('12:00'), quiet), false);
  assert.equal(inQuietHours(parseHM('20:59'), quiet), false);
  assert.equal(inQuietHours(parseHM('21:00'), quiet), true);
});

test('quiet hours within a single day', () => {
  const quiet = { start: '13:00', end: '14:00' };
  assert.equal(inQuietHours(parseHM('13:30'), quiet), true);
  assert.equal(inQuietHours(parseHM('12:59'), quiet), false);
  assert.equal(inQuietHours(parseHM('14:00'), quiet), false);
});

test('zero-length quiet window means quiet hours off', () => {
  assert.equal(inQuietHours(600, { start: '09:00', end: '09:00' }), false);
});

test('malformed quiet values fall back to defaults', () => {
  assert.equal(inQuietHours(parseHM('23:00'), { start: 'junk', end: null }), true);
  assert.equal(inQuietHours(parseHM('12:00'), {}), false);
});

test('dueInWindow catches the elapsed window, including midnight wrap', () => {
  // reminder 08:00, now 08:10, window 20 → due
  assert.equal(dueInWindow(480, 490, 20), true);
  // now 08:25 → outside
  assert.equal(dueInWindow(480, 505, 20), false);
  // reminder 23:55, now 00:05 → wraps, delta 10 → due
  assert.equal(dueInWindow(1435, 5, 20), true);
  // future reminder is not due
  assert.equal(dueInWindow(500, 490, 20), false);
});

test('validSubscription requires https endpoint and keys', () => {
  assert.equal(validSubscription({
    endpoint: 'https://fcm.googleapis.com/x', keys: { p256dh: 'a', auth: 'b' },
  }), true);
  assert.equal(validSubscription({ endpoint: 'http://x', keys: { p256dh: 'a', auth: 'b' } }), false);
  assert.equal(validSubscription({ endpoint: 'https://x' }), false);
  assert.equal(validSubscription(null), false);
});

test('sanitiseReminders drops malformed entries and clamps fields', () => {
  const out = sanitiseReminders([
    { itemId: 'a', label: 'Walk', time: '08:00' },
    { itemId: 'b', label: 'Bad', time: '25:00' },
    { label: 'no id', time: '09:00' },
    'junk',
  ]);
  assert.equal(out.length, 1);
  assert.equal(out[0].itemId, 'a');
});

test('sanitiseSettings applies safe defaults', () => {
  const s = sanitiseSettings({ quietStart: 'nope', muted: 1, generic: false });
  assert.equal(s.quietStart, '21:00');
  assert.equal(s.quietEnd, '07:00');
  assert.equal(s.muted, true);
  assert.equal(s.generic, false);
});
