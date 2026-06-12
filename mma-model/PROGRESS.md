# PROGRESS

Living build log. Update before ending any session so the next one can resume
without re-explanation.

## Current state
- **Build step:** 6 of 7 complete (live-odds capture + paper-bet ledger + dashboard). Only step 7 (run forward for months) remains — that is operational, not a build step.
- **Branch:** `claude/mma-model-imaz57` (harness-designated). See "Decisions" re: spec's `mma-model` name.
- **Folder:** `mma-model/` at repo root, as specified. No files outside it touched.

## Environment notes (important for next session)
- Running in a sandboxed cloud container. **`ufcstats.com`, `sherdog.com`,
  `tapology.com` and `wikipedia.org` are all firewalled** (`host_not_allowed`);
  live scraping is impossible here. PyPI + GitHub raw ARE reachable.
- **Step 5 data gap (self-recovery rule applied):** no cross-promotion data
  could be ingested in this sandbox. Sherdog/Tapology are blocked, and a GitHub
  search turned up only UFC-family datasets (Greco1899, grappler185) plus
  Sherdog *scraper* repos (Montanaz0r) that commit code, not data. Per the
  spec's "skip it, note it, continue" rule the cross-promotion pipeline is
  built and fully tested, but the live DB stays UFC-only. To populate it where
  Sherdog is reachable:
  `python -m scripts.run_crosspromo --crawl seeds.txt`  (seeds = Sherdog fighter
  URLs, one per line) or `--csv records.csv` (Montanaz0r column layout).
- Data therefore sourced from the **Greco1899/scrape_ufc_stats CSV mirror**
  (regularly-updated export of ufcstats.com), cached in `data/raw/` and
  committed for reproducibility. Snapshot current through **May 2026** events.
- The live scraper (`mma_model/ingest/ufc_scraper.py`) is written and targets
  ufcstats.com directly; it will work in an environment with network access. Its
  parsers operate on HTML strings and are independent of the network.
- Python 3.11 in this container (spec asked 3.12; no 3.12-only features used).

## What's built

### Step 1 — UFC scraper + DB schema + entity resolution ✅
- **Schema** (`mma_model/db/schema.sql`): `sources`, `fighters`,
  `fighter_aliases`, `events`, `bouts`, `bout_stats`, `odds` (placeholder for
  step 3), `ratings_history`, `unmatched_log`. Multi-promotion ready
  (`promotion`, `ruleset` columns; `ruleset` lets us exclude ONE's non-MMA bouts
  later).
- **Live scraper** (`ingest/ufc_scraper.py`): BeautifulSoup, HTML cache, rate
  limit, one parser per page type (events index / event / fighter).
- **Mirror loader** (`ingest/ufc_dataset.py`): loads the CSV mirror into SQLite,
  resolving every fight-record name to a canonical fighter id.
- **Entity resolution** (`entity/resolver.py`): canonical ids = ufcstats fighter
  hash; `normalize_name` strips accents/suffixes/nicknames; exact → fuzzy
  (rapidfuzz) → synthetic-id fallback; learns aliases; logs unmatched/ambiguous.
- **Build result:** 4503 fighters, 774 events, 8701 bouts, 40836 stat rows.
  52 synthetic ids minted (fighters with no tale-of-the-tape row).
  170 ambiguous names (same normalized name → multiple fighters) logged for
  later disambiguation — acceptable for v1, see Known issues.

### Step 2 — Glicko-2 engine + validation ✅
- **Engine** (`ratings/glicko2.py`): hand-rolled Glicko-2 (Glickman 2013),
  Illinois-method volatility solve. `did_not_compete()` inflates RD across
  layoffs (RATING_PERIOD_DAYS = 180). `expected_score()` folds both fighters' RD
  into the win probability.
- **Streaming driver** (`ratings/rate_fighters.py`): processes bouts
  chronologically, each as a 1-game period; inflates RD for inactivity before
  scoring; writes post-bout snapshots to `ratings_history` (history powers
  as-of-date, leak-free feature lookups in step 3).
- **Validation gate 1 — paper worked example** (`tests/test_glicko2.py`):
  matches Glickman's r'≈1464.06, RD'≈151.52, vol'≈0.05999.
- **Validation gate 2 — known rankings sanity check:** top current ratings are
  Jon Jones, Islam Makhachev, GSP, Volkanovski, Usman, Stipe, Merab, Max
  Holloway… (Valentina Shevchenko top woman). Matches consensus elite tier.
- **Tests:** 20 passing (glicko2, entity, parse).

### Step 3 — Backtest harness + baselines + calibration report ✅
- **Odds ingest** (`ingest/odds_dataset.py`): jansen88/ufc-data
  `complete_ufc_data.csv` (betmma.tips odds, Nov 2014 → Sep 2023), cached in
  `data/raw/`. Matcher: exact normalized-name-pair + date(±3d) → fuzzy resolver
  fallback → unmatched_log. **3394/3448 odds bouts matched (98.4%)**; 54
  unmatched logged. Guards against literal `inf` odds strings in the source.
- **Pre-fight predictions** captured inside the chronological rating replay
  (`predictions` table, model='tier1_glicko') — walk-forward by construction,
  enforced by `tests/test_leakage.py` (prediction for bout at T is bit-identical
  when all bouts after T are deleted).
- **Metrics** (`backtest/metrics.py`): log loss, Brier, accuracy, calibration
  bins, margin-removed implied prob, Kelly fraction, flat & 1/4-Kelly staking
  ROI, percentile-bootstrap CIs. Hand-computed-value unit tests.
- **Report** (`backtest/report.py` → `reports/backtest_tier1.md` + calibration
  PNG): n=3335 decisive bouts with odds. **Evaluation orientation randomized**
  per bout (ufcstats lists winners first — 64% base rate would distort the
  calibration display; scoring metrics are invariant under the flip).
- **Step 3 results (honest):** Tier 1 log loss 0.6985 vs implied 0.6174 — the
  results-only Glicko does NOT beat the market (expected; the market embeds far
  more information). Pick-favourite accuracy 65%; model 56%. Staking ROI ≈ −7%
  (CIs mostly below 0). Calibration plot shows Tier 1 is **overconfident**
  (flattened reliability slope) → a shrinkage/calibration layer is an easy step
  4 win, alongside the Tier 2 feature model.

### Step 4 — Tier 2 gradient-boosted model ✅
- **Pre-fight Glicko state capture** (`prefight_ratings` table): the replay now
  stores each fighter's layoff-inflated rating/RD per bout — Tier 2's strongest
  feature with zero as-of-date reconstruction risk.
- **Feature builder** (`models/features.py`): single chronological accumulator
  pass (same leak-free pattern as the replay; leakage-tested). Eligibility =
  both fighters have prior UFC stat history. 25 features: rating diff + RDs,
  age/height/reach diffs, SLpM/SApM, TD acc/def, sub & KD rates per 15,
  win-method profile (Laplace-smoothed), KO-loss rate, win rate, streak,
  weighted last-3 form, log-layoff diff, experience, title/5-round flags.
  Short-notice flag omitted: announcement dates aren't in any source (spec
  feature, documented limitation).
- **Symmetrization:** training emits every row in BOTH orientations (label +
  antisymmetric features flipped, rd1/rd2 swapped); prediction averages the two
  orientations → exactly orientation-invariant. This kills the
  winner-listed-first label leak.
- **Walk-forward training** (`models/tier2.py`): LightGBM retrained at each
  calendar-year boundary on all bouts strictly before Jan 1 (training always
  predates every predicted bout); 15 yearly models, 5246 predictions from 2012.
- **Comparison report** (`backtest/report.py::write_comparison` →
  `reports/backtest_tier2_vs_tier1.md`): evaluates models on the INTERSECTION
  sample (n=2650 decisive bouts with odds + both models' predictions).
- **Step 4 results (honest):**
  | | log loss | Brier | accuracy |
  |---|---|---|---|
  | Tier 2 (GBM) | 0.6762 | 0.2409 | 59.4% |
  | Tier 1 (Glicko-2) | 0.7102 | 0.2563 | 55.1% |
  | implied prob | 0.6269 | – | 65.3% |
  Tier 2 clearly beats Tier 1 and is far better calibrated (see plot), but
  **neither beats the closing line** — consistent with published results on
  this data. Top features by gain: age_diff, rating_diff, sapm_diff,
  reach_diff, td_acc/def_diff. Staking ROI remains negative; the report says so
  plainly.

### Step 5 — Cross-promotion ingest + promotion-aware global ratings ✅
- **Sherdog scraper** (`ingest/sherdog_scraper.py`): BeautifulSoup parser for
  fighter records (results-level, all promotions). robots.txt-gated via
  `can_fetch()`, 2s rate limit, per-page HTML cache. Pure parsers tested
  against a committed fixture (`tests/fixtures/sherdog_fighter.html`).
- **Promotion + ruleset inference** (`ingest/promotions.py`): event-name →
  promotion code (UFC/ONE/PFL/Bellator/RIZIN/Strikeforce/...); `infer_ruleset`
  flags ONE's non-MMA bouts (muay thai / kickboxing / submission grappling) via
  event+division+method text so they are **excluded** (spec requirement).
- **Cross-promotion loader** (`ingest/crosspromo.py`): resolves fighter &
  opponent names through the alias-aware `EntityResolver` seeded from canonical
  UFC fighters → a Sherdog "Conor McGregor" links to his ufcstats id; genuinely
  new non-UFC fighters get stable synthetic ids + fighters rows. Skips UFC bouts
  (ufcstats is authoritative — no double counting), excludes non-MMA, de-dups
  the mirrored rows Sherdog emits on both fighters' pages, tags `promotion`/
  `ruleset`/`source`, persists learned aliases to `fighter_aliases`.
- **Global ratings span promotions automatically**: `rate_fighters.run` already
  replays ALL bouts chronologically, so once non-UFC bouts are present a
  fighter's pre-UFC ONE/PFL record builds their rating before their UFC debut.
  Proven end-to-end by `test_crosspromotion_gives_debutant_a_real_prior`.
- **Note on the `promotion` Tier-2 feature:** Tier 2 only fires where per-fight
  UFC stats exist, so the bouts it sees are all UFC and a promotion feature
  would be constant — the cross-promotion signal reaches Tier 2 through
  `rating_diff` instead. Promotion-specific decision tendencies (the ONE
  caveat) matter for Tier 1 / multi-promotion prediction and are a step-6/7
  enhancement.
- **Tests:** `test_promotions.py`, `test_sherdog.py` (parser, robots gate,
  loader exclusions, cross-source linking, dedup, debutant-prior). 48 total.

### Step 6 — Live odds capture + paper-bet ledger + dashboard ✅
- **Live odds** (`ingest/live_odds.py`): pluggable capture into the `odds`
  table with `captured_at` + `is_closing`. The Odds API client (`OddsAPIClient`,
  reads ODDS_API_KEY) **plus** an always-available manual CSV path
  (`snapshot_from_csv`). NOTE in code: verify the free-tier quota and (thin)
  non-UFC coverage before relying on the API. The Odds API is firewalled in this
  sandbox (403), so production-only — the CSV path is the offline fallback.
- **Paper-bet ledger** (`paper/ledger.py`): `place_bet` /
  `suggest_and_place` (1/4-Kelly, edge-gated) / `settle` (auto from results) /
  `record_closing` (CLV) / `summary` (record, ROI with bootstrap CI, avg & %
  positive CLV). No real money (v1 non-goal). CLV = taken/closing − 1.
- **Model serving** (`models/predict.py`): live Tier 1 (Glicko-2) win prob for
  any fighter pair from current ratings; Tier 2 serving flagged as a future
  enhancement. Used by the dashboard and the Kelly suggester.
- **Dashboard** (`app/`, FastAPI): `/` upcoming cards (model prob vs current
  odds, edges flagged at >5%), `/fighter/{id}` profile (record + rating-history
  sparkline), `/ledger` (running ROI + CI + CLV). JSON mirrors at `/api/*` make
  it headless-testable via TestClient. Launch: `python -m scripts.run_dashboard`.
- **Robustness fix:** the rating replay and feature builder now skip any bout
  without a decisive/draw result, so scheduled future cards can live in the
  `bouts` table (for the dashboard) without polluting ratings or training.
- **Demo:** `scripts/seed_demo_card.py` stages a fantasy upcoming bout (top two
  rated fighters — currently Jon Jones vs Islam Makhachev) with an edge + open
  Kelly bet, and settles 6 paper bets on real historical bouts so the ledger
  shows realized P/L + CLV. Static HTML snapshots in `reports/dashboard_*.html`.
- **Tests:** `test_paper.py` (predict, resultless-bout guard, ledger
  place/settle/CLV, Kelly gating, live-odds snapshot + API guard, dashboard
  endpoints). 55 total.

## How to reproduce
```bash
cd mma-model
pip install -r requirements.txt
python -m pytest                 # 39 tests
python -m scripts.build_db       # builds data/mma.sqlite from cached CSVs
python -m scripts.run_ratings    # runs Glicko-2, prints top-25 sanity ranking
python -m scripts.run_backtest   # odds ingest + replay + reports/backtest_tier1.md
python -m scripts.run_tier2      # Tier 2 GBM + reports/backtest_tier2_vs_tier1.md
python -m scripts.run_crosspromo --csv <records.csv>   # cross-promotion ingest (needs data)
python -m scripts.seed_demo_card # stage a demo upcoming card + paper bets
python -m scripts.run_dashboard  # FastAPI dashboard on http://127.0.0.1:8000
```

## Decisions made
- **Data source:** CSV mirror instead of live scrape (ufcstats blocked here).
  No information loss — same underlying ufcstats data.
- **Canonical id = ufcstats fighter hash.** Stable, source-of-truth, survives
  name changes. Synthetic `syn:<norm-name>` ids for fighters lacking a hash.
- **Streaming Glicko-2 (1 game/period)** rather than fixed calendar periods —
  standard for MMA where bouts are sparse and irregular; layoff RD-inflation
  handled explicitly via elapsed-days → fractional periods.
- **Branch name:** Spec requested `mma-model`; the harness pins this session to
  `claude/mma-model-imaz57` and forbids pushing elsewhere. Developing on the
  designated branch. Folder name is `mma-model/` as requested.

## Known issues / to revisit
- 170 ambiguous exact-name collisions (e.g. multiple "Bruno Silva") resolve to
  None in stats join → those stat rows are dropped. Fix later with
  event/date-scoped disambiguation. Does not affect bout ingestion (bout names
  use `get_or_create`, so both fighters always get an id).
- RD cap currently = default RD (350). Fine for v1.

## Next: Step 7 — run it forward (operational, not a build step)
- Capture daily + closing odds snapshots for upcoming cards (CSV or The Odds
  API where reachable), let the ledger settle automatically, and watch ROI +
  **CLV** accumulate over a few months before any real-money decision.
- Open enhancement backlog (none blocking):
  * load real cross-promotion data where Sherdog/Tapology are reachable (step 5
    pipeline is ready) so non-UFC fighters and UFC debutants get real priors;
  * serve Tier 2 (not just Tier 1) on the dashboard by building an as-of-today
    feature row + the trained GBM;
  * calibration/shrinkage layer on Tier 1 (it is overconfident);
  * promotion-specific decision tendencies as a feature once multi-promotion
    data exists (the ONE judging caveat).
