// Sends an immediate test notification to one stored subscription, so the
// user can confirm closed-app delivery without waiting for the scheduler.
// Deliberately ignores quiet hours and the daily cap — it is user-initiated.

import webpush from 'web-push';
import { pushStore, endpointKey, json } from './lib/shared.mjs';

export default async (req) => {
  const { VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT } = process.env;
  if (!VAPID_PUBLIC_KEY || !VAPID_PRIVATE_KEY) {
    return json({ error: 'VAPID keys are not configured' }, 500);
  }
  webpush.setVapidDetails(VAPID_SUBJECT || 'mailto:admin@example.com', VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY);

  let body;
  try {
    body = await req.json();
  } catch {
    return json({ error: 'Invalid JSON' }, 400);
  }
  const endpoint = body?.endpoint;
  if (typeof endpoint !== 'string' || !endpoint.startsWith('https://')) {
    return json({ error: 'Missing endpoint' }, 400);
  }

  const store = pushStore();
  const record = await store.get(endpointKey(endpoint), { type: 'json' });
  if (!record) return json({ error: 'No stored subscription — enable reminders first' }, 404);

  const payload = JSON.stringify({
    title: 'Test notification',
    body: 'Push is working. Reminders will arrive like this, even with the app closed.',
    itemId: null,
    test: true,
  });

  try {
    await webpush.sendNotification(record.subscription, payload, { TTL: 300 });
    return json({ ok: true });
  } catch (err) {
    if (err.statusCode === 404 || err.statusCode === 410) {
      await store.delete(endpointKey(endpoint));
      return json({ error: 'Subscription expired — re-enable reminders' }, 410);
    }
    return json({ error: `Push failed (${err.statusCode || 'unknown'})` }, 502);
  }
};

export const config = {
  path: '/api/test-push',
  method: 'POST',
};
