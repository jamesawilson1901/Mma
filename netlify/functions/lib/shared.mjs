// Shared helpers for the push functions. Not a function itself — Netlify only
// picks up top-level files (or dir/dir.mjs) in the functions directory.

import { createHash } from 'node:crypto';
import { getStore } from '@netlify/blobs';

export const STORE_NAME = 'anchor-push';
export const DEFAULT_TZ = 'Australia/Brisbane';
export const DEFAULT_QUIET = { start: '21:00', end: '07:00' };

export function pushStore() {
  return getStore(STORE_NAME);
}

export function endpointKey(endpoint) {
  return 'subs/' + createHash('sha256').update(endpoint).digest('hex').slice(0, 32);
}

export function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

// "HH:MM" → minutes since local midnight, or null if malformed.
export function parseHM(s) {
  const m = /^([01]?\d|2[0-3]):([0-5]\d)$/.exec(String(s || '').trim());
  return m ? Number(m[1]) * 60 + Number(m[2]) : null;
}

// Local wall-clock for an instant in an IANA timezone.
export function localNow(date, timeZone) {
  let parts;
  try {
    parts = new Intl.DateTimeFormat('en-AU', {
      timeZone,
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', hour12: false,
    }).formatToParts(date);
  } catch {
    return localNow(date, DEFAULT_TZ);
  }
  const get = (t) => parts.find((p) => p.type === t)?.value;
  const hour = Number(get('hour')) % 24; // Intl can emit "24" at midnight
  return {
    date: `${get('year')}-${get('month')}-${get('day')}`,
    minutes: hour * 60 + Number(get('minute')),
  };
}

// True when `minutes` falls inside quiet hours (a window that may wrap midnight).
// A zero-length window means quiet hours are off.
export function inQuietHours(minutes, quiet) {
  const start = parseHM(quiet?.start) ?? parseHM(DEFAULT_QUIET.start);
  const end = parseHM(quiet?.end) ?? parseHM(DEFAULT_QUIET.end);
  if (start === end) return false;
  if (start < end) return minutes >= start && minutes < end;
  return minutes >= start || minutes < end;
}

// True when a reminder set for `remMinutes` came due within the last
// `windowMin` minutes relative to `nowMinutes` (handles midnight wrap).
export function dueInWindow(remMinutes, nowMinutes, windowMin) {
  const delta = (nowMinutes - remMinutes + 1440) % 1440;
  return delta <= windowMin;
}

export function validSubscription(sub) {
  return Boolean(
    sub &&
    typeof sub.endpoint === 'string' &&
    sub.endpoint.startsWith('https://') &&
    sub.keys && typeof sub.keys.p256dh === 'string' && typeof sub.keys.auth === 'string'
  );
}

export function sanitiseReminders(reminders) {
  if (!Array.isArray(reminders)) return [];
  return reminders
    .filter((r) => r && typeof r.itemId === 'string' && parseHM(r.time) !== null)
    .slice(0, 50)
    .map((r) => ({
      itemId: r.itemId.slice(0, 64),
      label: String(r.label || 'your item').slice(0, 120),
      time: r.time,
    }));
}

export function sanitiseSettings(s) {
  return {
    quietStart: parseHM(s?.quietStart) !== null ? s.quietStart : DEFAULT_QUIET.start,
    quietEnd: parseHM(s?.quietEnd) !== null ? s.quietEnd : DEFAULT_QUIET.end,
    muted: Boolean(s?.muted),
    generic: Boolean(s?.generic),
  };
}
