// All user-facing copy and evidence notes in one place. Australian English.
// Tone: autonomy-supportive — invitations, never commands; no shame states.

export const EVIDENCE = {
  ifThen: 'People who decide in advance exactly when, where and how they will act follow through far more often — a large review found a medium-to-large effect (Gollwitzer & Sheeran, 2006).',
  anchor: 'Habits form fastest when the new action is tied to something you already do every day; repetition in a stable context is what builds automaticity (Lally et al., 2010; Wood et al., 2005).',
  substitute: 'Planning what you will do instead of an unwanted behaviour works better than simply resolving not to do it (Adriaanse et al., 2011).',
  specificGoal: 'Specific, measurable goals reliably beat vague "do your best" intentions (Locke & Latham, 2002).',
  logging: 'Monitoring your own progress is itself an effective change technique — reviews show tracking meaningfully lifts goal attainment (Harkin et al., 2016).',
  consistency: 'Overall consistency predicts habit formation better than unbroken streaks, and missing a single day makes little difference (Lally et al., 2010).',
  lapse: 'Treating a slip with self-compassion, rather than criticism, predicts getting back on track sooner (Adams & Leary, 2007; Marlatt & Gordon, 1985).',
  timeline: 'In real-world data, habits took a median of 66 days to become automatic, with a range of 18 to 254 — around two to three months is typical (Lally et al., 2010).',
  why: 'Change sticks better when it is driven by your own reasons rather than outside pressure — self-determination theory, backed by a large 2021 meta-analysis (Ntoumanis et al., 2021).',
  reminders: 'Cue-based prompts help early on, while the habit is still forming; they are training wheels, not the engine.',
  freshStart: 'People are measurably more likely to start and stick with changes at temporal landmarks — new weeks, new months, meaningful dates (Dai, Milkman & Riis, 2014).',
  bundle: 'Pairing something you enjoy with the behaviour ("temptation bundling") increased gym attendance in a field experiment (Milkman, Minson & Volpp, 2014).',
  pledge: 'A written commitment raises follow-through for some people; it is optional because pressure that does not come from you can backfire.',
  barrier: 'When change stalls, the useful question is what is blocking it — capability, opportunity or motivation (the COM-B model, Michie et al., 2011) — not which "stage" you are in.',
  badges: 'Small milestone markers can support motivation, but leaderboards and points that punish misses tend to backfire — so badges here are quiet and opt-in (Mazeas et al., 2022).',
  reflection: 'Brief reflective questions in the style of motivational interviewing help people find their own next step (Singh et al., 2023).',
  rest: 'Planned rest is not a miss. Consistency over months, not perfection over weeks, is what forms habits (Lally et al., 2010).',
};

export const MODES = {
  build: { label: 'Build a habit', blurb: 'Add something small and repeatable to your day.' },
  learn: { label: 'Learn a skill', blurb: 'A steady practice routine, tied to your day.' },
  break: { label: 'Break a behaviour', blurb: 'Map the trigger, plan a substitute.' },
};

export const TEMPLATES = {
  build: [
    {
      title: 'Daily walk', goalTarget: 15, goalUnit: 'minutes', goalFrequency: 'day',
      anchor: 'after breakfast', ifCue: 'After I finish breakfast', thenAction: 'put on my shoes and walk for 15 minutes',
      why: 'I want more energy and a clearer head.',
    },
    {
      title: 'Read before bed', goalTarget: 10, goalUnit: 'pages', goalFrequency: 'day',
      anchor: 'when I get into bed', ifCue: 'When I get into bed', thenAction: 'read 10 pages instead of opening my phone',
      why: 'I miss reading and sleep better without a screen.',
    },
    {
      title: 'Morning stretch', goalTarget: 5, goalUnit: 'minutes', goalFrequency: 'day',
      anchor: 'after my morning coffee', ifCue: 'After my morning coffee', thenAction: 'stretch on the mat for 5 minutes',
      why: 'My back feels better on the days I stretch.',
    },
  ],
  learn: [
    {
      title: 'Guitar practice', goalTarget: 20, goalUnit: 'minutes', goalFrequency: 'day',
      anchor: 'after dinner', ifCue: 'After I clear the dinner table', thenAction: 'practise guitar for 20 minutes',
      why: 'I have always wanted to play properly.',
    },
    {
      title: 'Spanish practice', goalTarget: 15, goalUnit: 'minutes', goalFrequency: 'day',
      anchor: 'on my morning commute', ifCue: 'When I sit down on the train', thenAction: 'do 15 minutes of Spanish',
      why: 'I want to hold a real conversation on my next trip.',
    },
    {
      title: 'Sketching', goalTarget: 3, goalUnit: 'sessions', goalFrequency: 'week',
      anchor: 'Saturday morning coffee', ifCue: 'When I sit down with Saturday coffee', thenAction: 'sketch for 30 minutes',
      why: 'Drawing switches my brain off in the best way.',
    },
  ],
  break: [
    {
      title: 'Less doomscrolling', goalTarget: 0, goalUnit: 'late-night scrolls', goalFrequency: 'day',
      trigger: 'I get into bed and reach for my phone', substitute: 'plug the phone in across the room and pick up my book',
      why: 'I want my evenings and my sleep back.',
    },
    {
      title: 'Cut back fizzy drinks', goalTarget: 1, goalUnit: 'glass of water first', goalFrequency: 'day',
      trigger: 'I feel like a soft drink mid-afternoon', substitute: 'drink a full glass of cold water first, then decide',
      why: 'Small change, big difference to how I feel by evening.',
    },
    {
      title: 'No phone at breakfast', goalTarget: 0, goalUnit: 'phone pickups at the table', goalFrequency: 'day',
      trigger: 'I sit down to eat and my phone is next to me', substitute: 'leave the phone on the bench and just eat',
      why: 'I want to start the day on my own terms.',
    },
  ],
};

// Common daily anchors offered as suggestions.
export const ANCHOR_SUGGESTIONS = [
  'after my morning coffee',
  'after I brush my teeth',
  'after breakfast',
  'when I get home from work',
  'after dinner',
  'when I get into bed',
];

export const NEGATIVE_WORDS = /\b(stop|quit|don't|dont|never|no more|less|avoid|give up|cut out)\b/i;

export const MI_PROMPTS_SETUP = [
  'What would make the first time you do this almost embarrassingly easy?',
  'When this is part of your life in three months, what will be different?',
  'What is one thing you could set up tonight so tomorrow needs no willpower?',
];

export const MI_PROMPTS_LAPSE = [
  'What is one small thing that would make tomorrow easier?',
  'What got in the way — and is there a way to move it before next time?',
  'If a good mate were in your shoes, what would you tell them?',
];

export const LAPSE_COPY = [
  'One missed day barely affects habit formation — Lally’s data shows it. Pick it up today.',
  'A lapse is a data point, not a verdict. The plan still stands.',
  'Missing once changes almost nothing. Missing the restart is the only real risk.',
];

export const RECOVERY_PLAN = [
  'Do the smallest possible version today — even one minute counts.',
  'Re-read your "why" below; it has not changed.',
  'Put the cue back in place tonight so tomorrow starts itself.',
];

export const COMB_TIPS = {
  ability: {
    label: 'Ability — it feels too hard or I don’t know how',
    tip: 'Shrink it until it is almost trivial: halve the target, or do just the first step. A version you actually do beats a version you admire.',
  },
  opportunity: {
    label: 'Opportunity — my day or environment gets in the way',
    tip: 'Change the environment, not yourself: move the cue somewhere unmissable, pick a steadier time, or attach it to an anchor that happens every single day.',
  },
  motivation: {
    label: 'Motivation — I just don’t feel like it',
    tip: 'Re-read your "why", then make it more enjoyable: pair it with something you like (music, a podcast, a nicer setting). Enjoyment predicts sticking with it.',
  },
};

export const BADGES = [
  { days: 7, label: 'One steady week' },
  { days: 30, label: 'A month of showing up' },
  { days: 66, label: 'The long haul — median habit territory' },
];

export const FRESH_START_COPY = {
  monday: 'New week. If the last one got messy, it stays there.',
  month: 'New month, clean page.',
  custom: 'Your fresh-start date is here. Clean slate from today.',
};

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}
