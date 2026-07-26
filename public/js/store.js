// Data layer. Everything lives in localStorage under a versioned schema;
// only push subscriptions and reminder schedules ever leave the device.

const STORAGE_KEY = 'anchor:data';

export const SCHEMA_VERSION = 1;

export function defaultData() {
  return {
    schemaVersion: SCHEMA_VERSION,
    items: [],
    settings: {
      theme: 'dark',
      notificationsOptIn: false,
      genericPayloads: false,
      muted: false,
      badgesOptIn: false,
      quietHours: { start: '21:00', end: '07:00' },
      timezone: detectTimezone(),
      freshStartDate: null,
    },
  };
}

export function detectTimezone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Australia/Brisbane';
  } catch {
    return 'Australia/Brisbane';
  }
}

let cache = null;

export function load() {
  if (cache) return cache;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    cache = raw ? migrate(JSON.parse(raw)) : defaultData();
  } catch {
    cache = defaultData();
  }
  return cache;
}

export function save() {
  if (cache) localStorage.setItem(STORAGE_KEY, JSON.stringify(cache));
}

export function wipe() {
  localStorage.removeItem(STORAGE_KEY);
  cache = null;
}

function migrate(data) {
  if (!data || typeof data !== 'object') return defaultData();
  // Future schema bumps get handled here, stepwise.
  const base = defaultData();
  return {
    ...base,
    ...data,
    schemaVersion: SCHEMA_VERSION,
    settings: { ...base.settings, ...(data.settings || {}),
      quietHours: { ...base.settings.quietHours, ...(data.settings?.quietHours || {}) } },
    items: Array.isArray(data.items) ? data.items : [],
  };
}

// ---------- items ----------

export function newId() {
  return 'i' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

export function createItem(fields) {
  const data = load();
  const item = {
    id: newId(),
    mode: fields.mode,
    title: (fields.title || '').trim(),
    ifCue: fields.ifCue || '',
    thenAction: fields.thenAction || '',
    anchor: fields.anchor || '',
    trigger: fields.trigger || '',
    substitute: fields.substitute || '',
    goalTarget: fields.goalTarget ?? null,
    goalUnit: fields.goalUnit || '',
    goalFrequency: fields.goalFrequency || 'day',
    subGoal: fields.subGoal || '',
    why: fields.why || '',
    treat: fields.treat || '',
    pledge: fields.pledge || '',
    reminderTime: fields.reminderTime || '',
    createdAt: todayStr(),
    freshStartAt: null,
    reflections: [],
    logs: [],
    freezesUsed: 0,
  };
  data.items.push(item);
  save();
  return item;
}

export function updateItem(id, fields) {
  const item = findItem(id);
  if (!item) return null;
  Object.assign(item, fields);
  save();
  return item;
}

export function deleteItem(id) {
  const data = load();
  data.items = data.items.filter((i) => i.id !== id);
  save();
}

export function findItem(id) {
  return load().items.find((i) => i.id === id) || null;
}

// ---------- dates ----------

export function todayStr(offsetDays = 0) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return localISO(d);
}

function localISO(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function daysBetween(a, b) {
  return Math.round((new Date(b + 'T12:00:00') - new Date(a + 'T12:00:00')) / 86400000);
}

// ---------- logging ----------

export function getLog(item, date) {
  return item.logs.find((l) => l.date === date) || null;
}

export function logDay(id, date, done, note) {
  const item = findItem(id);
  if (!item) return;
  const existing = getLog(item, date);
  if (existing) {
    existing.done = done;
    if (note !== undefined) existing.note = note;
  } else {
    item.logs.push({ date, done, note: note || '' });
    if (done === 'rest') item.freezesUsed = (item.freezesUsed || 0) + 1;
  }
  save();
}

export function unlogDay(id, date) {
  const item = findItem(id);
  if (!item) return;
  item.logs = item.logs.filter((l) => l.date !== date);
  save();
}

// ---------- metrics ----------

// Consistency over the last 30 days (or since creation if newer).
// Rest days are protected: they shrink the denominator, never count as misses.
export function consistency(item, windowDays = 30) {
  const today = todayStr();
  const age = daysBetween(item.createdAt, today) + 1;
  const window = Math.max(1, Math.min(windowDays, age));
  let done = 0, rest = 0;
  for (let i = 0; i < window; i++) {
    const date = shiftDate(today, -i);
    const log = getLog(item, date);
    if (log?.done === true) done++;
    else if (log?.done === 'rest') rest++;
  }
  const denom = window - rest;
  return {
    pct: denom > 0 ? Math.round((done / denom) * 100) : null,
    done, rest, window,
  };
}

export function shiftDate(dateStr, days) {
  const d = new Date(dateStr + 'T12:00:00');
  d.setDate(d.getDate() + days);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

// Current run: consecutive done days ending today (or yesterday if today is
// still open). Rest days pass through without breaking or extending the run.
// A fresh start resets the baseline the run is counted from.
export function currentRun(item) {
  const today = todayStr();
  let run = 0;
  let i = getLog(item, today)?.done === true ? 0 : 1;
  for (; ; i++) {
    const date = shiftDate(today, -i);
    if (daysBetween(item.createdAt, date) < 0) break;
    if (item.freshStartAt && daysBetween(item.freshStartAt, date) < 0) break;
    const log = getLog(item, date);
    if (log?.done === true) run++;
    else if (log?.done === 'rest') continue;
    else break;
  }
  return run;
}

export function totalDone(item) {
  return item.logs.filter((l) => l.done === true).length;
}

// Missed days in the last 7 (yesterday backwards — today is still open).
export function missesInLast7(item) {
  const today = todayStr();
  let misses = 0;
  for (let i = 1; i <= 7; i++) {
    const date = shiftDate(today, -i);
    if (daysBetween(item.createdAt, date) < 0) break;
    if (item.freshStartAt && daysBetween(item.freshStartAt, date) < 0) break;
    const log = getLog(item, date);
    if (!log) misses++;
  }
  return misses;
}

// A lapse = the most recent closed day was a miss, and today isn't done yet.
export function inLapse(item) {
  const today = todayStr();
  if (getLog(item, today)?.done === true) return false;
  const yesterday = shiftDate(today, -1);
  if (daysBetween(item.createdAt, yesterday) < 0) return false;
  if (item.freshStartAt && daysBetween(item.freshStartAt, yesterday) < 0) return false;
  const log = getLog(item, yesterday);
  return !log;
}

// ---------- export / import ----------

export function exportJSON() {
  return JSON.stringify(load(), null, 2);
}

export function importJSON(text) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error('That file is not valid JSON.');
  }
  if (!parsed || typeof parsed !== 'object') throw new Error('That file is not a valid export.');
  if (typeof parsed.schemaVersion !== 'number' || parsed.schemaVersion > SCHEMA_VERSION) {
    throw new Error('That export uses a newer schema than this app understands.');
  }
  if (!Array.isArray(parsed.items)) throw new Error('That export has no items list.');
  for (const item of parsed.items) {
    if (!item || typeof item.id !== 'string' || typeof item.title !== 'string' ||
        !['build', 'learn', 'break'].includes(item.mode) || !Array.isArray(item.logs)) {
      throw new Error('An item in that file is malformed; import cancelled.');
    }
  }
  cache = migrate(parsed);
  save();
  return cache;
}
