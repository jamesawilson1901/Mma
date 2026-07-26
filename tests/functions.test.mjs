// Integration tests for the HTTP functions, run against a local Netlify
// Blobs server (filesystem-backed) so no cloud access is needed.

import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { BlobsServer } from '@netlify/blobs/server';

const PORT = 9971;
const token = 'test-token';
const server = new BlobsServer({ directory: mkdtempSync(join(tmpdir(), 'blobs-')), port: PORT, token });
await server.start();
process.env.NETLIFY_BLOBS_CONTEXT = Buffer.from(JSON.stringify({
  edgeURL: `http://localhost:${PORT}`,
  token,
  siteID: 'test-site',
})).toString('base64');

const { default: subscribe } = await import('../netlify/functions/subscribe.mjs');
const { default: unsubscribe } = await import('../netlify/functions/unsubscribe.mjs');
const { default: dispatch } = await import('../netlify/functions/reminders-dispatch.mjs');

const sub = {
  endpoint: 'https://fcm.googleapis.com/fcm/send/abc123',
  keys: { p256dh: 'BPubKey', auth: 'authKey' },
};

function post(body) {
  return new Request('http://localhost/api/subscribe', {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' },
  });
}

test('subscribe upserts and preserves the sent map across re-posts', async () => {
  const res = await subscribe(post({
    subscription: sub,
    timezone: 'Australia/Brisbane',
    reminders: [{ itemId: 'item1', label: 'Walk', time: '08:00' }],
    settings: { quietStart: '21:00', quietEnd: '07:00', muted: false, generic: false },
  }));
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.ok, true);
  assert.equal(body.reminders, 1);

  // Simulate the dispatcher having recorded a send, then re-post.
  const { getStore } = await import('@netlify/blobs');
  const { endpointKey, STORE_NAME } = await import('../netlify/functions/lib/shared.mjs');
  const store = getStore(STORE_NAME);
  const key = endpointKey(sub.endpoint);
  const rec = await store.get(key, { type: 'json' });
  rec.sent = { item1: '2026-07-26' };
  await store.setJSON(key, rec);

  const res2 = await subscribe(post({
    subscription: sub,
    timezone: 'Australia/Brisbane',
    reminders: [{ itemId: 'item1', label: 'Walk', time: '09:00' }],
    settings: {},
  }));
  assert.equal(res2.status, 200);
  const rec2 = await store.get(key, { type: 'json' });
  assert.equal(rec2.reminders[0].time, '09:00');
  assert.deepEqual(rec2.sent, { item1: '2026-07-26' }, 'daily-cap record must survive upserts');
});

test('subscribe rejects malformed payloads', async () => {
  assert.equal((await subscribe(post({ subscription: { endpoint: 'http://insecure' } }))).status, 400);
  assert.equal((await subscribe(new Request('http://x/api/subscribe', { method: 'POST', body: 'junk' }))).status, 400);
});

test('unsubscribe deletes the record', async () => {
  const res = await unsubscribe(new Request('http://x/api/unsubscribe', {
    method: 'POST', body: JSON.stringify({ endpoint: sub.endpoint }),
  }));
  assert.equal(res.status, 200);
  const { getStore } = await import('@netlify/blobs');
  const { endpointKey, STORE_NAME } = await import('../netlify/functions/lib/shared.mjs');
  assert.equal(await getStore(STORE_NAME).get(endpointKey(sub.endpoint)), null);
});

test('dispatcher refuses to run without VAPID keys', async () => {
  delete process.env.VAPID_PUBLIC_KEY;
  delete process.env.VAPID_PRIVATE_KEY;
  const res = await dispatch();
  assert.equal(res.status, 500);
});

test.after(async () => {
  await server.stop();
});
