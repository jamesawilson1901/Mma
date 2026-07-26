// Idempotent upsert of a device's push subscription + reminder schedule.
// The client re-POSTs the full snapshot whenever anything changes.

import {
  pushStore, endpointKey, json,
  validSubscription, sanitiseReminders, sanitiseSettings, DEFAULT_TZ,
} from './lib/shared.mjs';

export default async (req) => {
  let body;
  try {
    body = await req.json();
  } catch {
    return json({ error: 'Invalid JSON' }, 400);
  }

  const { subscription, timezone, reminders, settings } = body || {};
  if (!validSubscription(subscription)) {
    return json({ error: 'Missing or malformed push subscription' }, 400);
  }

  const store = pushStore();
  const key = endpointKey(subscription.endpoint);
  // Preserve the last-sent map across upserts so the daily cap survives
  // reminder edits and re-subscribes.
  const existing = await store.get(key, { type: 'json' });

  const record = {
    subscription,
    timezone: typeof timezone === 'string' && timezone ? timezone : DEFAULT_TZ,
    reminders: sanitiseReminders(reminders),
    settings: sanitiseSettings(settings),
    sent: existing?.sent || {},
    updatedAt: new Date().toISOString(),
  };
  await store.setJSON(key, record);

  return json({ ok: true, reminders: record.reminders.length });
};

export const config = {
  path: '/api/subscribe',
  method: 'POST',
};
