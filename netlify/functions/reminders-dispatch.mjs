// Scheduled dispatcher. Runs every 15 minutes (UTC cron), converts to each
// device's stored IANA timezone, and sends anything that came due in the
// elapsed window. All safety caps are enforced HERE, server-side:
//   - global mute per device
//   - quiet hours (default 21:00–07:00 local, user adjustable)
//   - hard cap of 1 push per item per day, recorded in Blobs so nothing
//     double-fires even with the overlapping grace window.

import webpush from 'web-push';
import {
  pushStore, json, parseHM, localNow, inQuietHours, dueInWindow,
} from './lib/shared.mjs';

// Slightly longer than the 15-minute cadence so a slow or skipped run never
// silently drops a reminder; the per-day cap deduplicates the overlap.
const WINDOW_MINUTES = 20;

export default async () => {
  const { VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT } = process.env;
  if (!VAPID_PUBLIC_KEY || !VAPID_PRIVATE_KEY) {
    console.error('VAPID keys not configured; skipping dispatch');
    return json({ ok: false, error: 'VAPID not configured' }, 500);
  }
  webpush.setVapidDetails(VAPID_SUBJECT || 'mailto:admin@example.com', VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY);

  const store = pushStore();
  const now = new Date();
  let sent = 0, skipped = 0, expired = 0;

  const { blobs } = await store.list({ prefix: 'subs/' });
  for (const { key } of blobs) {
    const record = await store.get(key, { type: 'json' });
    if (!record || !record.subscription) continue;
    if (record.settings?.muted) { skipped += record.reminders?.length || 0; continue; }

    const local = localNow(now, record.timezone);
    const quiet = { start: record.settings?.quietStart, end: record.settings?.quietEnd };
    let dirty = false;

    for (const reminder of record.reminders || []) {
      const remMin = parseHM(reminder.time);
      if (remMin === null) continue;
      if (!dueInWindow(remMin, local.minutes, WINDOW_MINUTES)) continue;
      if (record.sent?.[reminder.itemId] === local.date) { skipped++; continue; } // daily cap
      if (inQuietHours(local.minutes, quiet)) { skipped++; continue; }

      const payload = JSON.stringify({
        title: record.settings?.generic ? 'Reminder' : 'Time for: ' + reminder.label,
        body: record.settings?.generic
          ? 'Your reminder is due.'
          : 'Your plan is waiting — one small step counts.',
        itemId: reminder.itemId,
      });

      try {
        await webpush.sendNotification(record.subscription, payload, { TTL: 3600 });
        record.sent = record.sent || {};
        record.sent[reminder.itemId] = local.date;
        dirty = true;
        sent++;
      } catch (err) {
        if (err.statusCode === 404 || err.statusCode === 410) {
          await store.delete(key);
          expired++;
          dirty = false;
          break; // record is gone; stop processing its reminders
        }
        console.error('push failed', err.statusCode, err.message);
      }
    }

    if (dirty) {
      // Prune stale cap entries so the record stays small.
      for (const [itemId, date] of Object.entries(record.sent || {})) {
        if (date !== local.date) delete record.sent[itemId];
      }
      await store.setJSON(key, record);
    }
  }

  console.log(`dispatch: sent=${sent} skipped=${skipped} expired=${expired}`);
  return json({ ok: true, sent, skipped, expired });
};

export const config = {
  schedule: '*/15 * * * *',
};
