// Deletes a device's stored subscription and schedule.

import { pushStore, endpointKey, json } from './lib/shared.mjs';

export default async (req) => {
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
  await pushStore().delete(endpointKey(endpoint));
  return json({ ok: true });
};

export const config = {
  path: '/api/unsubscribe',
  method: 'POST',
};
