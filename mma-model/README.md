# MMA Fight Prediction Model

Multi-promotion MMA outcome prediction with a walk-forward backtest against
historical bookmaker odds and a paper-trading tracker. **No real money — v1 is
research/paper-trading only.**

Success metric: does the model beat the closing line on a meaningful sample,
measured by calibration (Brier / log loss) and hypothetical Kelly-staked ROI
with bootstrap confidence intervals.

## Two-tier architecture
- **Tier 1 — global rating layer (all promotions):** Glicko-2 per fighter over
  every recorded pro MMA bout. Glicko-2 (not Elo) because its rating-deviation
  and volatility terms handle long layoffs and thin records — both endemic in
  MMA. Multi-promotion data links opponent pools as fighters cross orgs.
- **Tier 2 — UFC-rich model:** gradient-boosted classifier on per-fight stat
  differentials (ufcstats only). Prediction = Tier 2 where both fighters have
  UFC stat history, else Tier 1 with wider uncertainty.

## Status
Build steps 1–2 complete (DB + entity resolution + validated Glicko-2). See
[PROGRESS.md](PROGRESS.md) for the live build log, environment notes, and the
remaining steps (backtest, Tier 2 GBM, cross-promotion ingest, dashboard).

## Quickstart
```bash
pip install -r requirements.txt
python -m pytest                 # unit tests (Glicko-2 paper validation, parsing, entity)
python -m scripts.build_db       # build data/mma.sqlite from cached UFC data
python -m scripts.run_ratings    # Glicko-2 ratings + top-25 sanity ranking
```

## Layout
```
mma_model/
  config.py            paths + constants
  db/                  schema.sql + sqlite helpers
  ingest/
    parse.py           pure parsing helpers (dates, tale-of-tape, stat strings)
    ufc_scraper.py     live ufcstats.com scraper (BeautifulSoup, cached)
    ufc_dataset.py     CSV-mirror loader -> SQLite (used where ufcstats blocked)
  entity/resolver.py   canonical ids + alias index + fuzzy matching
  ratings/
    glicko2.py         hand-rolled Glicko-2 engine
    rate_fighters.py   chronological streaming driver + current-rating query
scripts/               build_db.py, run_ratings.py
tests/                 test_glicko2.py, test_entity.py, test_parse.py
data/raw/              cached UFC CSV snapshot (committed for reproducibility)
```

## Data sources
- **UFC stats/results:** ufcstats.com via the Greco1899/scrape_ufc_stats CSV
  mirror (`data/raw/`). Live scraper targets ufcstats.com directly.
- **UFC historical odds** (step 3): jansen88/ufc-data (betmma.tips).
- **Cross-promotion records** (step 5): Tapology/Sherdog, MMA-rules bouts only.

## Non-goals (v1)
Round/method props, live betting, parlays, non-MMA rulesets, automated bet
placement, deep learning.
