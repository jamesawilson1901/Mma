// The signature visual: the consistency ring, plus the 30-day heatmap.
// Both render as inline SVG/HTML strings; no dependencies.

import { consistency, getLog, shiftDate, todayStr, daysBetween } from './store.js';

// A ring with a gap at the top (echoing the app icon), rounded caps, and the
// consistency percentage as the headline figure in the centre.
export function ringSVG(pct, { size = 132, label = 'consistency' } = {}) {
  const stroke = Math.round(size * 0.082);
  const r = (size - stroke) / 2 - 1;
  const c = size / 2;
  // The ring spans 300° with a 60° gap centred at the top.
  const spanDeg = 300;
  const circumference = 2 * Math.PI * r;
  const arcLen = (spanDeg / 360) * circumference;
  const value = pct == null ? 0 : Math.max(0, Math.min(100, pct));
  const filled = (value / 100) * arcLen;
  // Rotate so the gap straddles 12 o'clock: arc starts at 120° (down-left).
  const rotation = 120;
  const display = pct == null ? '—' : value + '%';
  return `
  <svg class="ring" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}"
       role="img" aria-label="${display} ${label} over the last 30 days">
    <g transform="rotate(${rotation} ${c} ${c})">
      <circle class="ring-track" cx="${c}" cy="${c}" r="${r}"
        fill="none" stroke-width="${stroke}" stroke-linecap="round"
        stroke-dasharray="${arcLen} ${circumference}" />
      <circle class="ring-value" cx="${c}" cy="${c}" r="${r}"
        fill="none" stroke-width="${stroke}" stroke-linecap="round"
        stroke-dasharray="${filled} ${circumference}"
        style="--arc:${filled}" />
    </g>
    <text class="ring-num" x="${c}" y="${c - size * 0.02}" text-anchor="middle"
      dominant-baseline="central">${display}</text>
    <text class="ring-cap" x="${c}" y="${c + size * 0.16}" text-anchor="middle"
      dominant-baseline="central">30 days</text>
  </svg>`;
}

export function miniRingSVG(item, size = 56) {
  const { pct } = consistency(item);
  return ringSVG(pct, { size, label: 'consistency' }).replace('class="ring"', 'class="ring ring-mini"');
}

// 30-day heatmap, oldest first, ending today. No red anywhere: done days fill
// with brass, rest days show a hollow ring, misses are simply quiet.
export function heatmapHTML(item) {
  const today = todayStr();
  const cells = [];
  for (let i = 29; i >= 0; i--) {
    const date = shiftDate(today, -i);
    const before = daysBetween(item.createdAt, date) < 0;
    const log = before ? null : getLog(item, date);
    let cls = 'hm-cell';
    let title = date;
    if (before) { cls += ' hm-pre'; title += ' — before this item began'; }
    else if (log?.done === true) { cls += ' hm-done'; title += ' — done'; }
    else if (log?.done === 'rest') { cls += ' hm-rest'; title += ' — rest day'; }
    else if (date === today) { cls += ' hm-today'; title += ' — today, still open'; }
    else { cls += ' hm-quiet'; title += ' — not logged'; }
    if (item.freshStartAt === date) { cls += ' hm-fresh'; title += ' · fresh start'; }
    cells.push(`<span class="${cls}" title="${title}"></span>`);
  }
  return `<div class="heatmap" role="img" aria-label="Last 30 days of activity for ${escapeAttr(item.title)}">${cells.join('')}</div>`;
}

function escapeAttr(s) {
  return String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
}
