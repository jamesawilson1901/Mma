// Anchor — main application. Views are rendered from location.hash; all state
// lives in store.js (localStorage). No framework, no build step.

import * as db from './store.js';
import * as push from './push.js';
import { ringSVG, miniRingSVG, heatmapHTML } from './ring.js';
import {
  EVIDENCE, MODES, TEMPLATES, ANCHOR_SUGGESTIONS, NEGATIVE_WORDS,
  MI_PROMPTS_SETUP, MI_PROMPTS_LAPSE, LAPSE_COPY, RECOVERY_PLAN,
  COMB_TIPS, BADGES, FRESH_START_COPY, pick,
} from './copy.js';

const APP_NAME = self.APP_NAME || 'Anchor';
const main = document.getElementById('main');

// ---------- utilities ----------

function h(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function toast(msg, ms = 3200) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toast._t);
  toast._t = setTimeout(() => t.classList.remove('show'), ms);
}

function info(key) {
  return `<button type="button" class="info" data-evidence="${key}" aria-label="Why this feature? Show the evidence.">ⓘ</button>`;
}

function goal(item) {
  if (item.goalTarget == null || item.goalTarget === '') return '';
  return `${item.goalTarget} ${item.goalUnit} per ${item.goalFrequency}`;
}

// Cues often already start with "After/When/Once…" — don't prefix "If" twice.
function composePlan(cue, action, isBreak) {
  const c = (cue || '…').trim();
  const opening = /^(after|when|whenever|once|if|before|as soon as)\b/i.test(c)
    ? c.charAt(0).toUpperCase() + c.slice(1)
    : 'If ' + c.charAt(0).toLowerCase() + c.slice(1);
  return `${opening}, then ${isBreak ? 'instead ' : ''}I will ${action || '…'}.`;
}

function planSentence(item) {
  if (item.mode === 'break') return composePlan(item.trigger, item.substitute, true);
  return composePlan(item.ifCue, item.thenAction, false);
}

function syncPushQuietly() {
  push.syncSubscription().catch(() => {
    toast('Reminder schedule will sync when you are back online.');
  });
}

// ---------- routing ----------

window.addEventListener('hashchange', render);
document.getElementById('nav-settings').addEventListener('click', () => { location.hash = '#settings'; });
document.getElementById('brand-link').addEventListener('click', (e) => { e.preventDefault(); location.hash = ''; render(); });
document.getElementById('brand-name').textContent = APP_NAME;
document.title = APP_NAME;

function render() {
  const hash = location.hash.replace(/^#\/?/, '');
  const [route, arg] = hash.split('/');
  closeTooltip();
  if (route === 'settings') viewSettings();
  else if (route === 'add' && arg) viewWizard(arg);
  else if (route === 'add') viewModePicker();
  else if (route === 'edit' && arg) viewWizard(null, arg);
  else if (route === 'item' && arg) viewDetail(arg);
  else viewHome();
  main.focus({ preventScroll: true });
}

// ---------- home ----------

function freshStartDue() {
  const s = db.load().settings;
  const now = new Date();
  const today = db.todayStr();
  if (localStorage.getItem('anchor:freshDismiss') === today) return null;
  const items = db.load().items;
  if (!items.length) return null;
  const anyMisses = items.some((i) => db.missesInLast7(i) > 0);
  if (!anyMisses) return null;
  if (s.freshStartDate === today) return FRESH_START_COPY.custom;
  if (now.getDate() === 1) return FRESH_START_COPY.month;
  if (now.getDay() === 1) return FRESH_START_COPY.monday;
  return null;
}

function viewHome() {
  const data = db.load();
  const items = data.items;

  if (!items.length) {
    main.innerHTML = `
      <h1>Welcome.</h1>
      <p class="muted">${h(APP_NAME)} helps you change one behaviour at a time, using methods
      with real evidence behind them — not streak pressure. Pick a starting point;
      a template gets you going in under two minutes, and everything can be edited later.</p>
      ${modeCardsHTML()}
      <p class="small muted" style="margin-top:1rem">Your data stays on this device.
      Only reminder times ever reach the server, and only if you opt in.</p>`;
    wireModeCards();
    return;
  }

  const fresh = freshStartDue();
  const today = db.todayStr();

  main.innerHTML = `
    <h1>Today</h1>
    ${fresh ? `
      <div class="banner" role="region" aria-label="Fresh start">
        <strong>${h(fresh)}</strong> ${info('freshStart')}
        <p class="small muted">Recent misses can stay in last week. Nothing is deleted —
        your run simply counts from today, if you want it to.</p>
        <div class="actions">
          <button class="btn-primary" id="fresh-yes">Begin a fresh start</button>
          <button class="btn-ghost" id="fresh-no">Not now</button>
        </div>
      </div>` : ''}
    <div id="item-list">
      ${items.map((item) => itemCard(item, today)).join('')}
    </div>
    <button class="fab" id="fab-add" aria-label="Add a new item">+</button>`;

  if (fresh) {
    main.querySelector('#fresh-yes').addEventListener('click', () => {
      for (const i of db.load().items) db.updateItem(i.id, { freshStartAt: db.todayStr() });
      localStorage.setItem('anchor:freshDismiss', db.todayStr());
      toast('Fresh start begun. Today is day one, and that is enough.');
      render();
    });
    main.querySelector('#fresh-no').addEventListener('click', () => {
      localStorage.setItem('anchor:freshDismiss', db.todayStr());
      render();
    });
  }
  main.querySelector('#fab-add').addEventListener('click', () => { location.hash = '#add'; });
  main.querySelectorAll('[data-open]').forEach((n) =>
    n.addEventListener('click', () => { location.hash = '#item/' + n.dataset.open; }));
  main.querySelectorAll('[data-done]').forEach((b) =>
    b.addEventListener('click', (e) => { e.stopPropagation(); toggleDone(b.dataset.done); }));
}

function itemCard(item, today) {
  const doneToday = db.getLog(item, today)?.done === true;
  const run = db.currentRun(item);
  const lapsed = db.inLapse(item);
  return `
  <div class="card item-card">
    <span class="ring-wrap" data-open="${item.id}" role="link" tabindex="0" aria-label="Open ${h(item.title)}">${miniRingSVG(item)}</span>
    <div class="item-main" data-open="${item.id}" role="link" tabindex="0">
      <span class="mode-chip">${h(MODES[item.mode]?.label || item.mode)}</span>
      <div class="title">${h(item.title)}</div>
      <div class="plan">${h(planSentence(item))}</div>
      ${item.why ? `<div class="why">“${h(item.why)}”</div>` : ''}
      <div class="run">${run > 0 ? `Current run: ${run} day${run === 1 ? '' : 's'}` : (lapsed ? 'Yesterday slipped past — today is open.' : 'Today is open.')}</div>
    </div>
    <button class="btn-done" data-done="${item.id}" aria-pressed="${doneToday}">
      ${doneToday ? 'Done ✓' : (lapsed ? 'Back on track' : 'Done today')}
    </button>
  </div>`;
}

function toggleDone(id) {
  const item = db.findItem(id);
  if (!item) return;
  const today = db.todayStr();
  const log = db.getLog(item, today);
  if (log?.done === true) {
    db.unlogDay(id, today);
    toast('Undone — no worries.');
  } else {
    const wasLapsed = db.inLapse(item);
    db.logDay(id, today, true);
    if (wasLapsed) {
      toast(pick(LAPSE_COPY));
      reflectDialog(item, pick(MI_PROMPTS_LAPSE));
    } else {
      toast('Logged. Small and steady.');
    }
  }
  render();
}

// ---------- mode picker + wizard ----------

function viewModePicker() {
  main.innerHTML = `
    <h1>What would you like to work on?</h1>
    ${modeCardsHTML()}
    <a class="backlink" href="#">← Back</a>`;
  wireModeCards();
}

function modeCardsHTML() {
  return `
    <div class="mode-grid">${Object.entries(MODES).map(([key, m]) => `
      <div class="card">
        <button class="mode-card" style="border:none;box-shadow:none;padding:0;background:transparent;width:100%" data-mode="${key}">
          <h3>${h(m.label)}</h3>
          <span class="blurb muted small">${h(m.blurb)} Or start from an example:</span>
        </button>
        <div class="tmpl-row" aria-label="Example templates for ${h(m.label)}">
          ${TEMPLATES[key].map((t, i) => `
            <button data-mode="${key}" data-tmpl="${i}">
              ${h(t.title)}
              <span class="t-goal">${h(`${t.goalTarget} ${t.goalUnit} per ${t.goalFrequency}`)}</span>
            </button>`).join('')}
        </div>
      </div>`).join('')}
    </div>`;
}

function wireModeCards() {
  main.querySelectorAll('[data-mode]').forEach((b) =>
    b.addEventListener('click', () => {
      if (b.dataset.tmpl !== undefined) {
        sessionStorage.setItem('anchor:tmpl', JSON.stringify({ mode: b.dataset.mode, idx: Number(b.dataset.tmpl) }));
      } else {
        sessionStorage.removeItem('anchor:tmpl');
      }
      wiz = null; // always start the wizard fresh from a picker
      location.hash = '#add/' + b.dataset.mode;
    }));
}

let wiz = null;

function viewWizard(mode, editId) {
  const editing = editId ? db.findItem(editId) : null;
  if (editId && !editing) { location.hash = ''; return; }
  if (editing) mode = editing.mode;
  if (!MODES[mode]) { location.hash = '#add'; return; }

  if (!wiz || wiz.mode !== mode || wiz.editId !== (editId || null)) {
    let seed = {};
    const tmplRaw = sessionStorage.getItem('anchor:tmpl');
    if (!editing && tmplRaw) {
      try {
        const { mode: tm, idx } = JSON.parse(tmplRaw);
        if (tm === mode) seed = { ...TEMPLATES[mode][idx] };
      } catch { /* ignore */ }
      sessionStorage.removeItem('anchor:tmpl');
    }
    wiz = {
      mode, editId: editId || null, step: 0,
      f: {
        title: '', goalTarget: '', goalUnit: '', goalFrequency: 'day', subGoal: '',
        ifCue: '', thenAction: '', anchor: '', trigger: '', substitute: '',
        why: '', reminderTime: '', treat: '', pledge: '',
        ...(editing ? {
          title: editing.title, goalTarget: editing.goalTarget ?? '', goalUnit: editing.goalUnit,
          goalFrequency: editing.goalFrequency, subGoal: editing.subGoal,
          ifCue: editing.ifCue, thenAction: editing.thenAction, anchor: editing.anchor,
          trigger: editing.trigger, substitute: editing.substitute,
          why: editing.why, reminderTime: editing.reminderTime, treat: editing.treat, pledge: editing.pledge,
        } : seed),
      },
    };
  }
  drawWizard();
}

function drawWizard() {
  const { mode, step, f, editId } = wiz;
  const isBreak = mode === 'break';
  const stepsHtml = `<div class="steps" aria-hidden="true">${[0, 1, 2].map((i) => `<span class="${i <= step ? 'on' : ''}"></span>`).join('')}</div>`;

  let body = '';
  if (step === 0) {
    body = `
      <h1>${editId ? 'Edit' : MODES[mode].label}</h1>
      ${stepsHtml}
      <label for="w-title">What are you working on?</label>
      <input id="w-title" value="${h(f.title)}" placeholder="${isBreak ? 'e.g. Less doomscrolling' : 'e.g. Daily walk'}" autocomplete="off" />
      <div id="neg-nudge"></div>

      <label>Make it measurable ${info('specificGoal')}</label>
      <div class="field-row">
        <input id="w-target" inputmode="decimal" placeholder="15" value="${h(f.goalTarget)}" aria-label="Target number" />
        <input id="w-unit" placeholder="minutes" value="${h(f.goalUnit)}" aria-label="Unit" />
        <select id="w-freq" aria-label="Frequency">
          <option value="day" ${f.goalFrequency === 'day' ? 'selected' : ''}>per day</option>
          <option value="week" ${f.goalFrequency === 'week' ? 'selected' : ''}>per week</option>
        </select>
      </div>
      <div id="goal-nudge"></div>

      <label for="w-subgoal">This week's sub-goal <span class="muted">(optional)</span></label>
      <input id="w-subgoal" value="${h(f.subGoal)}" placeholder="e.g. just 3 days this week" />`;
  } else if (step === 1 && !isBreak) {
    body = `
      <h1>Tie it to your day</h1>
      ${stepsHtml}
      <p class="muted small">An if-then plan decided now saves a decision later. ${info('ifThen')}</p>
      <label for="w-anchor">Anchor — something you already do daily ${info('anchor')}</label>
      <input id="w-anchor" value="${h(f.anchor)}" placeholder="after my morning coffee" autocomplete="off" />
      <div class="chip-row" role="group" aria-label="Anchor suggestions">
        ${ANCHOR_SUGGESTIONS.map((a) => `<button type="button" data-anchor="${h(a)}">${h(a)}</button>`).join('')}
      </div>
      <label for="w-cue">If… <span class="muted">(cue: time, place or the anchor above)</span></label>
      <input id="w-cue" value="${h(f.ifCue)}" placeholder="After I finish my morning coffee" />
      <label for="w-then">…then I will <span class="muted">(the specific action)</span></label>
      <input id="w-then" value="${h(f.thenAction)}" placeholder="put on my shoes and walk for 15 minutes" />
      <div class="preview-sentence" id="w-preview" aria-live="polite"></div>
      <div id="step2-nudge"></div>`;
  } else if (step === 1 && isBreak) {
    body = `
      <h1>Map the trigger</h1>
      ${stepsHtml}
      <p class="muted small">Unwanted behaviours run on cues. Naming the trigger and planning a
      substitute beats willpower alone. ${info('substitute')}</p>
      <label for="w-trigger">When does it happen? <span class="muted">(the trigger — time, place, feeling)</span></label>
      <input id="w-trigger" value="${h(f.trigger)}" placeholder="I get into bed and reach for my phone" />
      <label for="w-sub">Then instead, I will… <span class="muted">(the substitute action)</span></label>
      <input id="w-sub" value="${h(f.substitute)}" placeholder="plug the phone in across the room and pick up my book" />
      <p class="small muted">Where you can, remove the cue itself too — out of sight genuinely is out of mind.</p>
      <div class="preview-sentence" id="w-preview" aria-live="polite"></div>
      <div id="step2-nudge"></div>`;
  } else {
    body = `
      <h1>Make it yours</h1>
      ${stepsHtml}
      <label for="w-why">Why does this matter to you? ${info('why')}</label>
      <textarea id="w-why" placeholder="In your own words — you'll see this on tough days.">${h(f.why)}</textarea>
      <label for="w-remind">Daily reminder <span class="muted">(optional)</span> ${info('reminders')}</label>
      <input type="time" id="w-remind" value="${h(f.reminderTime)}" />
      ${(f.anchor || f.trigger) ? `<p class="hint">Tip: set it a few minutes before your cue — “${h(f.anchor || f.trigger)}”.</p>` : ''}
      <label for="w-treat">Pair a treat with it <span class="muted">(optional)</span> ${info('bundle')}</label>
      <input id="w-treat" value="${h(f.treat)}" placeholder="e.g. my favourite podcast, only during this" />
      <label for="w-pledge">A written pledge to yourself <span class="muted">(optional)</span> ${info('pledge')}</label>
      <textarea id="w-pledge" placeholder="Only if it helps — no one else sees it.">${h(f.pledge)}</textarea>`;
  }

  main.innerHTML = `${body}
    <div class="wizard-nav">
      <button class="btn-ghost" id="w-back">${step === 0 ? 'Cancel' : 'Back'}</button>
      <button class="btn-primary" id="w-next">${step === 2 ? (editId ? 'Save changes' : 'Start') : 'Next'}</button>
    </div>`;

  captureWizardFields();
  const preview = main.querySelector('#w-preview');
  if (preview) updatePreview();

  main.querySelectorAll('[data-anchor]').forEach((b) =>
    b.addEventListener('click', () => {
      const anchorInput = main.querySelector('#w-anchor');
      anchorInput.value = b.dataset.anchor;
      wiz.f.anchor = b.dataset.anchor;
      const cue = main.querySelector('#w-cue');
      if (!cue.value) { cue.value = 'After ' + b.dataset.anchor.replace(/^after\s+/i, ''); wiz.f.ifCue = cue.value; }
      updatePreview();
    }));

  main.querySelector('#w-back').addEventListener('click', () => {
    if (wiz.step === 0) {
      const backTo = wiz.editId ? '#item/' + wiz.editId : '';
      wiz = null;
      if (location.hash === backTo) render(); else location.hash = backTo;
      return;
    }
    wiz.step--;
    drawWizard();
  });
  main.querySelector('#w-next').addEventListener('click', nextStep);
}

function captureWizardFields() {
  const map = {
    'w-title': 'title', 'w-target': 'goalTarget', 'w-unit': 'goalUnit', 'w-freq': 'goalFrequency',
    'w-subgoal': 'subGoal', 'w-anchor': 'anchor', 'w-cue': 'ifCue', 'w-then': 'thenAction',
    'w-trigger': 'trigger', 'w-sub': 'substitute', 'w-why': 'why', 'w-remind': 'reminderTime',
    'w-treat': 'treat', 'w-pledge': 'pledge',
  };
  for (const [id, field] of Object.entries(map)) {
    const node = main.querySelector('#' + id);
    if (node) node.addEventListener('input', () => { wiz.f[field] = node.value; updatePreview(); });
  }
}

function updatePreview() {
  const preview = main.querySelector('#w-preview');
  if (!preview) return;
  const { f } = wiz;
  if (wiz.mode === 'break') {
    preview.textContent = (f.trigger || f.substitute) ? composePlan(f.trigger, f.substitute, true) : '';
  } else {
    preview.textContent = (f.ifCue || f.thenAction) ? composePlan(f.ifCue, f.thenAction, false) : '';
  }
}

function nudge(sel, msg) {
  const zone = main.querySelector(sel);
  if (zone) zone.innerHTML = msg ? `<div class="nudge">${msg}</div>` : '';
}

function nextStep() {
  const { mode, step, f } = wiz;
  const isBreak = mode === 'break';

  if (step === 0) {
    if (!f.title.trim()) { nudge('#goal-nudge', 'Give it a name first — short is fine.'); return; }
    const target = Number(f.goalTarget);
    if (f.goalTarget === '' || Number.isNaN(target) || !f.goalUnit.trim()) {
      nudge('#goal-nudge', 'Make it measurable — a number and a unit, like “15 minutes per day”. Vague goals underperform specific ones.');
      return;
    }
    if (!isBreak && NEGATIVE_WORDS.test(f.title)) {
      if (!wiz.negAcknowledged) {
        nudge('#neg-nudge', '“Stop/less/don’t…” goals are easier to reach when flipped into what you <em>will</em> do — or use <strong>Break a behaviour</strong> mode, which plans a substitute. Tap Next again to keep it as is.');
        wiz.negAcknowledged = true;
        return;
      }
    }
    wiz.step = 1;
  } else if (step === 1) {
    if (isBreak) {
      if (!f.trigger.trim() || !f.substitute.trim()) {
        nudge('#step2-nudge', 'Both halves matter: the trigger you’ll watch for, and what you’ll do instead.');
        return;
      }
    } else if (!f.ifCue.trim() || !f.thenAction.trim()) {
      nudge('#step2-nudge', 'Fill in both halves — the “if” and the “then” are the whole trick.');
      return;
    }
    wiz.step = 2;
  } else {
    if (!f.why.trim()) {
      toast('One line on why this matters — future-you will want it.');
      main.querySelector('#w-why').focus();
      return;
    }
    const fields = { ...f, goalTarget: Number(f.goalTarget), mode };
    let item;
    if (wiz.editId) {
      item = db.updateItem(wiz.editId, fields);
      toast('Saved.');
    } else {
      item = db.createItem(fields);
    }
    const isNew = !wiz.editId;
    wiz = null;
    if (item.reminderTime) syncPushQuietly();
    location.hash = '#item/' + item.id;
    if (isNew) setTimeout(() => reflectDialog(item, pick(MI_PROMPTS_SETUP)), 350);
    return;
  }
  drawWizard();
}

// ---------- reflective prompt dialog (rule-based, offline) ----------

function reflectDialog(item, prompt) {
  const dlg = document.createElement('dialog');
  dlg.innerHTML = `
    <h3>A moment's thought ${info('reflection')}</h3>
    <p>${h(prompt)}</p>
    <textarea id="reflect-text" aria-label="Your reflection" placeholder="A few words is plenty — or skip."></textarea>
    <div class="detail-actions">
      <button class="btn-primary" id="reflect-save">Keep this</button>
      <button class="btn-ghost" id="reflect-skip">Skip</button>
    </div>`;
  document.body.appendChild(dlg);
  dlg.showModal();
  const close = () => { dlg.close(); dlg.remove(); };
  dlg.querySelector('#reflect-skip').addEventListener('click', close);
  dlg.querySelector('#reflect-save').addEventListener('click', () => {
    const text = dlg.querySelector('#reflect-text').value.trim();
    if (text) {
      const it = db.findItem(item.id);
      if (it) { it.reflections = it.reflections || []; it.reflections.push({ date: db.todayStr(), prompt, text }); db.save(); }
    }
    close();
  });
}

// ---------- detail ----------

function viewDetail(id) {
  const item = db.findItem(id);
  if (!item) { location.hash = ''; return; }
  const today = db.todayStr();
  const cons = db.consistency(item);
  const run = db.currentRun(item);
  const done = db.totalDone(item);
  const doneToday = db.getLog(item, today)?.done === true;
  const restToday = db.getLog(item, today)?.done === 'rest';
  const lapsed = db.inLapse(item);
  const misses7 = db.missesInLast7(item);
  const autoPct = Math.min(100, Math.round((done / 66) * 100));
  const badgesOn = db.load().settings.badgesOptIn;

  main.innerHTML = `
    <a class="backlink" href="#">← All items</a>
    <span class="mode-chip" style="margin-left:0.6rem">${h(MODES[item.mode]?.label || item.mode)}</span>
    <h1>${h(item.title)}</h1>

    <div class="card ring-hero">
      ${ringSVG(cons.pct, { size: 168 })}
      <div class="headline">Consistency over the last 30 days ${info('consistency')}</div>
      <p class="small muted">${run > 0 ? `Current run: ${run} day${run === 1 ? '' : 's'} · ` : ''}${cons.rest ? `${cons.rest} rest day${cons.rest === 1 ? '' : 's'} protected · ` : ''}${done} day${done === 1 ? '' : 's'} logged in total</p>
      <div class="detail-actions">
        <button class="btn-done" id="d-done" aria-pressed="${doneToday}">${doneToday ? 'Done ✓' : (lapsed ? 'Back on track' : 'Done today')}</button>
        <button class="btn-calm" id="d-rest" ${restToday ? 'disabled' : ''}>${restToday ? 'Resting today ✓' : 'Take a rest day'}</button>
      </div>
      <p class="small muted" style="margin-top:0.4rem">Rest days are free and never count against you. ${info('rest')}</p>
    </div>

    ${lapsed ? `
    <div class="banner banner-calm" role="region" aria-label="Getting back on track">
      <strong>${h(pick(LAPSE_COPY))}</strong> ${info('lapse')}
      <p class="small">A lapse is not quitting — quitting is a decision, and you haven't made it. A simple way back:</p>
      <ul class="plain recovery">${RECOVERY_PLAN.map((r) => `<li>${h(r)}</li>`).join('')}</ul>
    </div>` : ''}

    ${misses7 >= 3 ? barrierCard(item) : ''}

    <div class="card">
      <h2 style="margin-top:0">The plan ${info('ifThen')}</h2>
      <p class="preview-sentence">${h(planSentence(item))}</p>
      ${item.anchor ? `<p class="small muted">Anchored to: ${h(item.anchor)}</p>` : ''}
      <p class="small">Target: <strong>${h(goal(item))}</strong>${item.subGoal ? ` · This week: ${h(item.subGoal)}` : ''}</p>
      ${item.why ? `<p class="small why" style="color:var(--calm);font-style:italic">“${h(item.why)}”</p>` : ''}
      ${item.treat ? `<p class="small muted">Paired treat: ${h(item.treat)}</p>` : ''}
      ${item.pledge ? `<p class="small muted">Your pledge: “${h(item.pledge)}”</p>` : ''}
    </div>

    <div class="card">
      <h2 style="margin-top:0">Last 30 days ${info('logging')}</h2>
      ${heatmapHTML(item)}
      <label for="d-note" class="small">Note for today (optional)</label>
      <input id="d-note" value="${h(db.getLog(item, today)?.note || '')}" placeholder="How did it go?" />
    </div>

    <div class="card">
      <h2 style="margin-top:0">Becoming automatic ${info('timeline')}</h2>
      <div class="progress" role="progressbar" aria-valuenow="${autoPct}" aria-valuemin="0" aria-valuemax="100" aria-label="Progress towards automaticity">
        <span style="width:${autoPct}%"></span>
      </div>
      <p class="small muted">Habits typically take about two to three months to feel automatic —
      the median in Lally's study was 66 days, with a range of 18 to 254. You have shown up
      <strong>${done}</strong> day${done === 1 ? '' : 's'} so far. No rush; the curve bends slowly, then all at once.</p>
    </div>

    ${badgesOn ? `
    <div class="card">
      <h2 style="margin-top:0">Milestones ${info('badges')}</h2>
      <div class="badge-row">
        ${BADGES.map((b) => `<span class="badge ${done >= b.days ? 'earned' : ''}">${b.days} days — ${h(b.label)}</span>`).join('')}
      </div>
    </div>` : ''}

    <div class="card">
      <h2 style="margin-top:0">Reminder ${info('reminders')}</h2>
      <label for="d-remind" class="small">Daily nudge time (blank for none)</label>
      <input type="time" id="d-remind" value="${h(item.reminderTime)}" />
      ${(item.anchor || item.trigger) ? `<p class="hint">Best set just before your cue — “${h(item.anchor || item.trigger)}”.</p>` : ''}
      <p class="small muted">Reminders arrive as push notifications if you have opted in under Settings.</p>
    </div>

    <div class="detail-actions">
      <button class="btn-ghost" id="d-edit">Edit</button>
      <button class="btn-danger-quiet" id="d-delete">Remove this item</button>
    </div>`;

  main.querySelector('#d-done').addEventListener('click', () => { toggleDone(id); });
  main.querySelector('#d-rest').addEventListener('click', () => {
    db.logDay(id, today, 'rest');
    toast('Rest day taken. That is part of the plan, not a break from it.');
    render();
  });
  main.querySelector('#d-note').addEventListener('change', (e) => {
    const log = db.getLog(db.findItem(id), today);
    if (log) { db.logDay(id, today, log.done, e.target.value); }
    else if (e.target.value.trim()) { toast('Notes attach to a logged day — tap “Done today” or “rest” first.'); }
  });
  main.querySelector('#d-remind').addEventListener('change', (e) => {
    db.updateItem(id, { reminderTime: e.target.value });
    toast(e.target.value ? 'Reminder set.' : 'Reminder removed.');
    syncPushQuietly();
  });
  main.querySelector('#d-edit').addEventListener('click', () => { location.hash = '#edit/' + id; });
  main.querySelector('#d-delete').addEventListener('click', () => {
    if (confirm(`Remove “${item.title}” and its history? Export a backup first if unsure.`)) {
      db.deleteItem(id);
      syncPushQuietly();
      location.hash = '';
    }
  });
  wireBarrierCard(item);
}

function barrierCard(item) {
  const answered = item.barrier && item.barrier.date === db.todayStr();
  return `
  <div class="card" id="barrier-card">
    <h2 style="margin-top:0">A quick check-in ${info('barrier')}</h2>
    ${answered ? `
      <p class="small"><strong>${h(COMB_TIPS[item.barrier.choice].label.split(' — ')[0])}:</strong>
      ${h(COMB_TIPS[item.barrier.choice].tip)}</p>
      <button class="btn-ghost small" id="barrier-again">Ask me again</button>` : `
      <p class="small">This one has been hard lately — that is information, not failure.
      What's making it hard right now?</p>
      <div class="mode-grid">
        ${Object.entries(COMB_TIPS).map(([k, v]) => `
          <button class="mode-card" data-barrier="${k}"><span class="blurb">${h(v.label)}</span></button>`).join('')}
      </div>`}
  </div>`;
}

function wireBarrierCard(item) {
  main.querySelectorAll('[data-barrier]').forEach((b) =>
    b.addEventListener('click', () => {
      db.updateItem(item.id, { barrier: { date: db.todayStr(), choice: b.dataset.barrier } });
      render();
    }));
  const again = main.querySelector('#barrier-again');
  if (again) again.addEventListener('click', () => {
    db.updateItem(item.id, { barrier: null });
    render();
  });
}

// ---------- settings ----------

function viewSettings() {
  const s = db.load().settings;
  const supported = push.pushSupported();

  main.innerHTML = `
    <a class="backlink" href="#">← Back</a>
    <h1>Settings</h1>

    <div class="card">
      <h2 style="margin-top:0">Appearance</h2>
      ${settingSwitch('set-theme', 'Light theme', 'Dark is the default — easier on evening eyes.', s.theme === 'light')}
    </div>

    <div class="card">
      <h2 style="margin-top:0">Reminders ${info('reminders')}</h2>
      ${supported ? '' : `<p class="nudge small">This browser does not support push notifications. On iPhone you need iOS 16.4+ and the app added to your Home Screen first.</p>`}
      ${settingSwitch('set-push', 'Push notifications', 'Opt in on this device. Reminder times and item titles are stored server-side; nothing else leaves your phone.', s.notificationsOptIn, !supported)}
      ${settingSwitch('set-generic', 'Generic notification text', 'Notifications say only “Your reminder is due” with no item name.', s.genericPayloads)}
      ${settingSwitch('set-mute', 'Mute all reminders', 'Keeps your schedule but sends nothing until unmuted. Enforced on the server too.', s.muted)}
      <div class="setting-row">
        <div class="lbl">Quiet hours <span class="small muted">No reminders between these times (server-enforced).</span></div>
        <input type="time" id="set-quiet-start" value="${h(s.quietHours.start)}" aria-label="Quiet hours start" />
        <input type="time" id="set-quiet-end" value="${h(s.quietHours.end)}" aria-label="Quiet hours end" />
      </div>
      <div class="setting-row">
        <div class="lbl">Timezone <span class="small muted">Detected automatically; reminders fire in this zone.</span></div>
        <span class="small">${h(s.timezone)}</span>
      </div>
      <button class="btn-calm btn-block" id="set-test" ${s.notificationsOptIn ? '' : 'disabled'}>Send a test notification</button>
      <p class="small muted" style="margin-top:0.4rem">Close the app fully after tapping — the test should still arrive.</p>
    </div>

    <div class="card">
      <h2 style="margin-top:0">Motivation</h2>
      ${settingSwitch('set-badges', 'Milestone badges', 'Quiet markers at 7, 30 and 66 days. No leaderboards, no points.', s.badgesOptIn)}
      <div class="setting-row">
        <div class="lbl">Personal fresh-start date ${info('freshStart')} <span class="small muted">Mondays and month-starts are offered automatically; add one date of your own.</span></div>
        <input type="date" id="set-fresh" value="${h(s.freshStartDate || '')}" aria-label="Fresh start date" style="width:auto" />
      </div>
    </div>

    <div class="card">
      <h2 style="margin-top:0">Your data</h2>
      <p class="small muted">Everything lives on this device. Export a backup any time; import restores it exactly.</p>
      <div class="detail-actions">
        <button class="btn-primary" id="set-export">Export JSON</button>
        <button id="set-import-btn">Import JSON</button>
        <input type="file" id="set-import" accept="application/json,.json" hidden />
        <button class="btn-danger-quiet" id="set-wipe">Wipe everything</button>
      </div>
    </div>

    <p class="small muted">${h(APP_NAME)} implements methods from peer-reviewed behaviour research —
    tap any ⓘ through the app to see the study behind a feature. It is a companion, not a clinical tool.</p>`;

  wireSwitch('set-theme', (on) => {
    db.load().settings.theme = on ? 'light' : 'dark';
    db.save();
    applyTheme();
  });
  wireSwitch('set-push', async (on, input) => {
    const settings = db.load().settings;
    if (on) {
      try {
        settings.notificationsOptIn = true;
        db.save();
        await push.enablePush();
        toast('Reminders on. Try a test notification below.');
        viewSettings();
      } catch (err) {
        settings.notificationsOptIn = false;
        db.save();
        input.checked = false;
        toast(err.message || 'Could not enable notifications.');
      }
    } else {
      settings.notificationsOptIn = false;
      db.save();
      try { await push.disablePush(); } catch { /* offline is fine */ }
      toast('Reminders off. Your schedule was removed from the server.');
      viewSettings();
    }
  });
  wireSwitch('set-generic', (on) => { db.load().settings.genericPayloads = on; db.save(); syncPushQuietly(); });
  wireSwitch('set-mute', (on) => { db.load().settings.muted = on; db.save(); syncPushQuietly(); });
  wireSwitch('set-badges', (on) => { db.load().settings.badgesOptIn = on; db.save(); });

  main.querySelector('#set-quiet-start').addEventListener('change', (e) => {
    db.load().settings.quietHours.start = e.target.value || '21:00'; db.save(); syncPushQuietly();
  });
  main.querySelector('#set-quiet-end').addEventListener('change', (e) => {
    db.load().settings.quietHours.end = e.target.value || '07:00'; db.save(); syncPushQuietly();
  });
  main.querySelector('#set-fresh').addEventListener('change', (e) => {
    db.load().settings.freshStartDate = e.target.value || null; db.save();
  });
  main.querySelector('#set-test').addEventListener('click', async (e) => {
    e.target.disabled = true;
    try {
      await push.sendTestPush();
      toast('Sent — close the app and watch for it.');
    } catch (err) {
      toast(err.message || 'Test failed.');
    } finally {
      e.target.disabled = false;
    }
  });
  main.querySelector('#set-export').addEventListener('click', () => {
    const blob = new Blob([db.exportJSON()], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `${APP_NAME.toLowerCase()}-export-${db.todayStr()}.json`;
    a.click();
    URL.revokeObjectURL(a.href);
  });
  main.querySelector('#set-import-btn').addEventListener('click', () => main.querySelector('#set-import').click());
  main.querySelector('#set-import').addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    try {
      db.importJSON(await file.text());
      applyTheme();
      toast('Import complete.');
      syncPushQuietly();
      location.hash = '';
      render();
    } catch (err) {
      toast(err.message);
    }
  });
  main.querySelector('#set-wipe').addEventListener('click', () => {
    if (confirm('Wipe all items, logs and settings from this device? Export first if you want a backup.')) {
      db.wipe();
      applyTheme();
      location.hash = '';
      render();
    }
  });
}

function settingSwitch(id, label, sub, checked, disabled = false) {
  return `
  <div class="setting-row">
    <div class="lbl"><label for="${id}" style="margin:0;color:var(--text)">${h(label)}</label>
      <span class="small muted">${sub}</span></div>
    <span class="switch">
      <input type="checkbox" id="${id}" ${checked ? 'checked' : ''} ${disabled ? 'disabled' : ''} />
      <span class="track" aria-hidden="true"></span>
    </span>
  </div>`;
}

function wireSwitch(id, fn) {
  const input = main.querySelector('#' + id);
  if (input) input.addEventListener('change', () => fn(input.checked, input));
}

// ---------- evidence tooltips ----------

let tipNode = null;

document.addEventListener('click', (e) => {
  const btn = e.target.closest('.info');
  if (btn) {
    e.preventDefault();
    const key = btn.dataset.evidence;
    if (tipNode && tipNode.dataset.for === key) { closeTooltip(); return; }
    showTooltip(btn, key);
    return;
  }
  if (tipNode && !e.target.closest('.tooltip-pop')) closeTooltip();
});
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeTooltip(); });

// Keyboard activation for card regions that act as links.
document.addEventListener('keydown', (e) => {
  if ((e.key === 'Enter' || e.key === ' ') && e.target.dataset && e.target.dataset.open) {
    e.preventDefault();
    location.hash = '#item/' + e.target.dataset.open;
  }
});

function showTooltip(btn, key) {
  closeTooltip();
  const text = EVIDENCE[key];
  if (!text) return;
  tipNode = document.createElement('div');
  tipNode.className = 'tooltip-pop';
  tipNode.dataset.for = key;
  tipNode.setAttribute('role', 'note');
  tipNode.tabIndex = -1;
  tipNode.textContent = text;
  document.body.appendChild(tipNode);
  const r = btn.getBoundingClientRect();
  const w = tipNode.offsetWidth;
  let x = Math.min(Math.max(8, r.left - w / 2 + r.width / 2), window.innerWidth - w - 8);
  let y = r.bottom + 8;
  if (y + tipNode.offsetHeight > window.innerHeight - 8) y = r.top - tipNode.offsetHeight - 8;
  tipNode.style.left = x + 'px';
  tipNode.style.top = Math.max(8, y) + 'px';
  tipNode.focus();
}

function closeTooltip() {
  if (tipNode) { tipNode.remove(); tipNode = null; }
}

// ---------- theme + boot ----------

function applyTheme() {
  const theme = db.load().settings.theme;
  document.documentElement.dataset.theme = theme;
  const meta = document.querySelector('meta[name=theme-color]');
  if (meta) meta.content = theme === 'light' ? '#F3F0E8' : '#0E1412';
}

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(() => { /* offline first load */ });
}

applyTheme();
render();
