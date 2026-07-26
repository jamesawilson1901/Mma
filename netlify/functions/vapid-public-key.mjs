// Exposes the VAPID public key so the client never hardcodes it.

import { json } from './lib/shared.mjs';

export default async () => {
  const key = process.env.VAPID_PUBLIC_KEY;
  if (!key) return json({ error: 'VAPID_PUBLIC_KEY is not configured' }, 500);
  return json({ key });
};

export const config = {
  path: '/api/vapid-public-key',
  method: 'GET',
};
