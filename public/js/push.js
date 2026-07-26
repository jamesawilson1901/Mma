// Push subscription management. The server only ever receives:
// the subscription, the timezone, reminder times/labels, and notification
// settings. Logs, whys, pledges and everything else stay on this device.

import { load } from './store.js';

function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  return Uint8Array.from([...raw].map((c) => c.charCodeAt(0)));
}

export function pushSupported() {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

async function registration() {
  return navigator.serviceWorker.ready;
}

export async function getSubscription() {
  if (!pushSupported()) return null;
  const reg = await registration();
  return reg.pushManager.getSubscription();
}

export async function enablePush() {
  if (!pushSupported()) throw new Error('This browser does not support push notifications.');
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') throw new Error('Notification permission was not granted.');

  const res = await fetch('/api/vapid-public-key');
  if (!res.ok) throw new Error('Could not fetch the server key — check VAPID env vars.');
  const { key } = await res.json();

  const reg = await registration();
  let sub = await reg.pushManager.getSubscription();
  if (!sub) {
    sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(key),
    });
  }
  await syncSubscription(sub);
  return sub;
}

// Builds the full snapshot and upserts it. Called on opt-in and again on any
// reminder or settings change (idempotent on the server).
export async function syncSubscription(existingSub) {
  const data = load();
  if (!data.settings.notificationsOptIn) return;
  const sub = existingSub || (await getSubscription());
  if (!sub) return;

  const reminders = data.items
    .filter((i) => i.reminderTime)
    .map((i) => ({ itemId: i.id, label: i.title, time: i.reminderTime }));

  const res = await fetch('/api/subscribe', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      subscription: sub.toJSON(),
      timezone: data.settings.timezone,
      reminders,
      settings: {
        quietStart: data.settings.quietHours.start,
        quietEnd: data.settings.quietHours.end,
        muted: data.settings.muted,
        generic: data.settings.genericPayloads,
      },
    }),
  });
  if (!res.ok) throw new Error('Saving the reminder schedule failed.');
}

export async function disablePush() {
  const sub = await getSubscription();
  if (sub) {
    try {
      await fetch('/api/unsubscribe', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ endpoint: sub.endpoint }),
      });
    } finally {
      await sub.unsubscribe();
    }
  }
}

export async function sendTestPush() {
  const sub = await getSubscription();
  if (!sub) throw new Error('Turn reminders on first.');
  const res = await fetch('/api/test-push', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ endpoint: sub.endpoint }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error || 'Test push failed.');
}
