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
Build steps 1–6 complete: DB + entity resolution, validated Glicko-2, walk-forward
backtest vs historical odds, the Tier 2 LightGBM model, the cross-promotion
ingest pipeline, and the live-odds capture + paper-bet ledger + FastAPI
dashboard. Only step 7 (run it forward for a few months) remains — operational,
not a build step.

Headline backtest (n=2650 UFC bouts with odds, strict walk-forward): Tier 2 log
loss 0.6762 vs Tier 1 0.7102 vs bookmaker implied 0.6269 — Tier 2 beats Tier 1
decisively and is well calibrated, but **does not yet beat the closing line**
(reported honestly in `reports/`). No real money in v1.

Sandbox note: every external source (ufcstats, Sherdog, Tapology, The Odds API)
is firewalled in the build environment, so UFC data comes from a committed CSV
mirror and the cross-promotion / live-odds paths run where their sources are
reachable. See [PROGRESS.md](PROGRESS.md) for the live build log.

## Quickstart
```bash
pip install -r requirements.txt
python -m pytest                 # unit tests (Glicko-2 paper validation, leakage, metrics)
python -m scripts.build_db       # build data/mma.sqlite from cached UFC data
python -m scripts.run_ratings    # Glicko-2 ratings + top-25 sanity ranking
python -m scripts.run_backtest   # odds ingest + Tier 1 backtest report
python -m scripts.run_tier2      # Tier 2 GBM walk-forward + comparison report
python -m scripts.seed_demo_card # stage a demo upcoming card + paper bets
python -m scripts.run_dashboard  # paper-trading dashboard on http://127.0.0.1:8000
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
    odds_dataset.py    historical odds loader + name-pair fuzzy matcher
    sherdog_scraper.py cross-promotion record scraper (robots-gated, cached)
    promotions.py      event-name -> promotion + MMA/non-MMA ruleset inference
    crosspromo.py      cross-promotion loader (resolve, exclude, dedup, link)
  entity/resolver.py   canonical ids + alias index + fuzzy matching
  ratings/
    glicko2.py         hand-rolled Glicko-2 engine
    rate_fighters.py   chronological replay: ratings, pre-fight states, predictions
  models/
    features.py        walk-forward feature accumulator (leak-free, symmetrizable)
    tier2.py           LightGBM, yearly walk-forward retrain, orientation-averaged
  backtest/
    metrics.py         log loss/Brier/calibration/Kelly/bootstrap CIs
    report.py          single-model + multi-model comparison reports
  paper/ledger.py      paper-bet ledger: place/settle/CLV/ROI
  app/                 FastAPI dashboard (upcoming edges, fighter pages, ledger)
scripts/               build_db, run_ratings, run_backtest, run_tier2,
                       run_crosspromo, seed_demo_card, run_dashboard
tests/                 paper validation, leakage, metrics, features, entity, parse
data/raw/              cached data snapshots (committed for reproducibility)
reports/               generated backtest reports + calibration plots
```

## Data sources
- **UFC stats/results:** ufcstats.com via the Greco1899/scrape_ufc_stats CSV
  mirror (`data/raw/`). Live scraper targets ufcstats.com directly.
- **UFC historical odds** (step 3): jansen88/ufc-data (betmma.tips).
- **Cross-promotion records** (step 5): Tapology/Sherdog, MMA-rules bouts only.

## Non-goals (v1)
Round/method props, live betting, parlays, non-MMA rulesets, automated bet
placement, deep learning.
