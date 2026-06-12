"""Tier 2: LightGBM classifier on walk-forward feature differentials.

Walk-forward protocol:
  * Features are as-of fight date by construction (models/features.py).
  * The model is retrained at each calendar-year boundary on all eligible
    bouts strictly before Jan 1 of that year, then predicts that year's bouts.
    (Per-event retraining would be stricter but changes nothing materially at
    LightGBM's training cost; yearly is the standard compromise and is still
    100% leak-free -- training data always predates every predicted bout.)

Symmetrization: ufcstats lists winners as fighter1, so source-orientation
training would leak the label through ordering. Every training row is emitted
in BOTH orientations (label and antisymmetric features flipped), forcing the
learned function toward f(x) = 1 - f(-x). Predictions average the two
orientations, making the output exactly orientation-invariant.

Predictions land in the `predictions` table (model='tier2_gbm') so the
existing backtest harness evaluates Tier 2 the same way as Tier 1.
"""
from __future__ import annotations

import sqlite3

import lightgbm as lgb
import numpy as np
import pandas as pd

from .features import FEATURE_COLS, build_features, flip_orientation

MODEL_NAME = "tier2_gbm"
MIN_TRAIN_ROWS = 1000

LGB_PARAMS = {
    "objective": "binary",
    "learning_rate": 0.03,
    "num_leaves": 31,
    "min_child_samples": 60,
    "feature_fraction": 0.8,
    "bagging_fraction": 0.8,
    "bagging_freq": 1,
    "lambda_l2": 5.0,
    "verbosity": -1,
    "seed": 42,
}
N_TREES = 400


def _fit(train: pd.DataFrame) -> lgb.Booster:
    both = pd.concat([train, flip_orientation(train)], ignore_index=True)
    dset = lgb.Dataset(both[FEATURE_COLS].astype(float), label=both["y"].astype(int))
    return lgb.train(LGB_PARAMS, dset, num_boost_round=N_TREES)


def _predict(model: lgb.Booster, rows: pd.DataFrame) -> np.ndarray:
    """Orientation-averaged P(fighter1 wins)."""
    p_fwd = model.predict(rows[FEATURE_COLS].astype(float))
    flipped = flip_orientation(rows)
    p_rev = model.predict(flipped[FEATURE_COLS].astype(float))
    return 0.5 * (p_fwd + (1.0 - p_rev))


def run_walkforward(conn: sqlite3.Connection) -> dict:
    """Yearly-retrained walk-forward predictions for all eligible bouts."""
    df = build_features(conn)
    labeled = df[df["y"].notna()].reset_index(drop=True)
    labeled["year"] = labeled["date"].str[:4].astype(int)

    n_prior = {
        (r["bout_id"]): (r["n_prior1"], r["n_prior2"]) for r in conn.execute(
            "SELECT bout_id, n_prior1, n_prior2 FROM predictions WHERE model='tier1_glicko'")
    }

    conn.execute("DELETE FROM predictions WHERE model=?", (MODEL_NAME,))
    preds_out = []
    years = sorted(labeled["year"].unique())
    n_trained_years = 0
    feature_importance = None
    for year in years:
        train = labeled[labeled["year"] < year]
        test = labeled[labeled["year"] == year]
        if len(train) < MIN_TRAIN_ROWS or test.empty:
            continue
        model = _fit(train)
        p = _predict(model, test)
        for bout_id, pi in zip(test["bout_id"], p):
            np1, np2 = n_prior.get(bout_id, (None, None))
            preds_out.append((bout_id, MODEL_NAME, float(pi), np1, np2))
        n_trained_years += 1
        # keep the final year's importances as representative
        feature_importance = sorted(
            zip(FEATURE_COLS, model.feature_importance("gain")),
            key=lambda t: -t[1],
        )

    conn.executemany(
        "INSERT OR REPLACE INTO predictions"
        "(bout_id,model,p_fighter1,n_prior1,n_prior2) VALUES(?,?,?,?,?)",
        preds_out,
    )
    conn.commit()
    return {
        "eligible_bouts": len(labeled),
        "years_trained": n_trained_years,
        "predictions": len(preds_out),
        "first_pred_year": next(
            (y for y in years if len(labeled[labeled["year"] < y]) >= MIN_TRAIN_ROWS),
            None),
        "feature_importance": feature_importance,
    }
