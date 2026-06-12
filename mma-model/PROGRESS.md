# PROGRESS

Living build log. Update before ending any session so the next one can resume
without re-explanation.

## Current state
- **Build step:** 2 of 7 complete (Glicko-2 engine validated). **Checkpoint reached — paused per spec before step 3.**
- **Branch:** `claude/mma-model-imaz57` (harness-designated). See "Decisions" re: spec's `mma-model` name.
- **Folder:** `mma-model/` at repo root, as specified. No files outside it touched.

## Environment notes (important for next session)
- Running in a sandboxed cloud container. **`ufcstats.com` is firewalled**
  (`host_not_allowed`); live scraping is impossible here. PyPI + GitHub raw ARE
  reachable.
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

## How to reproduce
```bash
cd mma-model
pip install -r requirements.txt
python -m pytest                 # 20 tests
python -m scripts.build_db       # builds data/mma.sqlite from cached CSVs
python -m scripts.run_ratings    # runs Glicko-2, prints top-25 sanity ranking
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

## Next: Step 3 (after switching to Fable per checkpoint)
Backtest harness + baselines (favourite, margin-removed implied prob) +
calibration report (log loss, Brier, calibration plot) over UFC historical
odds. Needs the jansen88/ufc-data odds dataset wired into the `odds` table with
the fuzzy name matcher. Strict walk-forward using `ratings_history` as-of date.
