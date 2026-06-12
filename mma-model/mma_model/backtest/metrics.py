"""Scoring rules, calibration, staking ROI and bootstrap CIs.

All functions take plain numpy arrays / lists of simple tuples so they are
trivially unit-testable and model-agnostic (Tier 1 today, Tier 2 in step 4).
Conventions: y is 1 if the predicted-on fighter won, p is the model's
probability for that fighter, d is the offered decimal odds for that fighter.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np

_EPS = 1e-12


# --- scoring rules ----------------------------------------------------------

def log_loss(y, p) -> float:
    y = np.asarray(y, dtype=float)
    p = np.clip(np.asarray(p, dtype=float), _EPS, 1 - _EPS)
    return float(-np.mean(y * np.log(p) + (1 - y) * np.log(1 - p)))


def brier(y, p) -> float:
    y = np.asarray(y, dtype=float)
    p = np.asarray(p, dtype=float)
    return float(np.mean((p - y) ** 2))


def accuracy(y, p) -> float:
    """Fraction of bouts where the >0.5 side won (0.5 predictions count half)."""
    y = np.asarray(y, dtype=float)
    p = np.asarray(p, dtype=float)
    picks = np.where(p > 0.5, 1.0, np.where(p < 0.5, 0.0, 0.5))
    return float(np.mean(np.where(picks == 0.5, 0.5, (picks == y).astype(float))))


# --- calibration ------------------------------------------------------------

def calibration_table(y, p, n_bins: int = 10) -> list[dict]:
    """Equal-width probability bins: predicted mean vs observed frequency."""
    y = np.asarray(y, dtype=float)
    p = np.asarray(p, dtype=float)
    edges = np.linspace(0.0, 1.0, n_bins + 1)
    out = []
    for lo, hi in zip(edges[:-1], edges[1:]):
        mask = (p >= lo) & (p < hi) if hi < 1.0 else (p >= lo) & (p <= hi)
        n = int(mask.sum())
        out.append({
            "lo": float(lo), "hi": float(hi), "n": n,
            "mean_pred": float(p[mask].mean()) if n else None,
            "frac_won": float(y[mask].mean()) if n else None,
        })
    return out


# --- odds helpers -----------------------------------------------------------

def implied_prob_two_way(d_a: float, d_b: float) -> float:
    """Margin-removed implied probability for side A of a two-way market."""
    ia, ib = 1.0 / d_a, 1.0 / d_b
    return ia / (ia + ib)


def kelly_fraction(p: float, d: float) -> float:
    """Full-Kelly stake fraction for prob p at decimal odds d (0 if no edge)."""
    if d <= 1.0:
        return 0.0
    return max(0.0, (p * d - 1.0) / (d - 1.0))


# --- staking simulation -------------------------------------------------------

@dataclass
class Bet:
    won: bool
    odds: float       # decimal odds taken
    p: float          # model probability for the side bet on
    edge: float       # p * odds - 1

    @property
    def flat_profit(self) -> float:
        """Profit on a 1-unit flat stake."""
        return self.odds - 1.0 if self.won else -1.0


def select_bets(rows, threshold: float = 0.0) -> list[Bet]:
    """From (y, p, d_fighter, d_opponent) rows, bet any side with edge > threshold.

    y/p/d_fighter refer to fighter1 of the prediction; the opponent side is
    evaluated with (1-p, d_opponent). At most one side can have positive edge.
    """
    bets = []
    for y, p, d1, d2 in rows:
        e1 = p * d1 - 1.0
        e2 = (1.0 - p) * d2 - 1.0
        if e1 > threshold and e1 >= e2:
            bets.append(Bet(won=bool(y == 1), odds=d1, p=p, edge=e1))
        elif e2 > threshold:
            bets.append(Bet(won=bool(y == 0), odds=d2, p=1.0 - p, edge=e2))
    return bets


def roi_flat(bets: list[Bet]) -> float:
    """Return on total staked with 1-unit flat stakes."""
    if not bets:
        return 0.0
    return float(sum(b.flat_profit for b in bets) / len(bets))


def roi_kelly(bets: list[Bet], fraction: float = 0.25) -> float:
    """Return on total staked with fractional-Kelly stakes (default 1/4)."""
    staked = profit = 0.0
    for b in bets:
        stake = fraction * kelly_fraction(b.p, b.odds)
        if stake <= 0:
            continue
        staked += stake
        profit += stake * (b.odds - 1.0) if b.won else -stake
    return float(profit / staked) if staked > 0 else 0.0


def bootstrap_roi_ci(
    bets: list[Bet],
    roi_fn=roi_flat,
    n_boot: int = 4000,
    alpha: float = 0.05,
    seed: int = 42,
) -> tuple[float, float]:
    """Percentile bootstrap CI for an ROI statistic over the bet sample."""
    if not bets:
        return (0.0, 0.0)
    rng = np.random.default_rng(seed)
    n = len(bets)
    stats = np.empty(n_boot)
    for i in range(n_boot):
        sample = [bets[j] for j in rng.integers(0, n, size=n)]
        stats[i] = roi_fn(sample)
    lo, hi = np.quantile(stats, [alpha / 2, 1 - alpha / 2])
    return (float(lo), float(hi))
