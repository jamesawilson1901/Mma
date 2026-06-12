"""Walk-forward Tier 2 feature builder.

Single chronological pass over all bouts, maintaining per-fighter career
accumulators. A feature row for a bout is emitted from the accumulators as
they stand BEFORE that bout, then the accumulators are updated with the bout's
result and stats -- the same leak-free-by-construction pattern as the rating
replay (covered by tests/test_features.py).

Eligibility: a bout gets a feature row only when BOTH fighters have at least
one prior UFC bout with per-fight stats (the Tier 2 contract: rich model only
where ufcstats history exists; everyone else rides on Tier 1).

Orientation: features are emitted in source order (fighter1/fighter2) along
with the label. ufcstats lists winners first, so consumers MUST symmetrize --
mma_model.models.tier2 trains on both orientations of every row.

Note: a short-notice flag is in the spec but fight-announcement dates are not
in any of our sources, so it is omitted (documented limitation).
"""
from __future__ import annotations

import sqlite3
from dataclasses import dataclass, field
from datetime import date

import numpy as np
import pandas as pd

from ..ingest.parse import parse_ctrl_seconds

ROUND_SECONDS = 300.0


def _fight_seconds(rnd: int | None, time_str: str | None) -> float:
    """Total fight duration: completed rounds + time into the final round."""
    if not rnd or rnd < 1:
        return 0.0
    return (rnd - 1) * ROUND_SECONDS + parse_ctrl_seconds(time_str or "")


def _method_class(method: str) -> str:
    m = (method or "").lower()
    if "ko" in m:           # KO/TKO, TKO - Doctor's Stoppage
        return "ko"
    if "submission" in m:
        return "sub"
    if "decision" in m:
        return "dec"
    return "other"


@dataclass
class Career:
    """Per-fighter accumulator of everything known strictly before 'now'."""

    n_bouts: int = 0
    wins: int = 0
    win_streak: int = 0
    last3: list = field(default_factory=list)   # most recent first, 1/0/0.5
    last_date: date | None = None
    wins_ko: int = 0
    wins_sub: int = 0
    wins_dec: int = 0
    losses_ko: int = 0
    # stat sums over bouts where per-fight stats exist
    stat_seconds: float = 0.0
    sig_landed: int = 0
    sig_absorbed: int = 0
    td_landed: int = 0
    td_att: int = 0
    td_faced_landed: int = 0
    td_faced_att: int = 0
    sub_att: int = 0
    kd_for: int = 0
    kd_against: int = 0

    # --- derived rates (None until enough data; LightGBM handles NaN) ------
    @property
    def slpm(self):
        return self.sig_landed / self.stat_seconds * 60 if self.stat_seconds else None

    @property
    def sapm(self):
        return self.sig_absorbed / self.stat_seconds * 60 if self.stat_seconds else None

    @property
    def td_acc(self):
        return self.td_landed / self.td_att if self.td_att else None

    @property
    def td_def(self):
        return 1 - self.td_faced_landed / self.td_faced_att if self.td_faced_att else None

    @property
    def sub_per15(self):
        return self.sub_att / self.stat_seconds * 900 if self.stat_seconds else None

    @property
    def kd_per15(self):
        return self.kd_for / self.stat_seconds * 900 if self.stat_seconds else None

    @property
    def kdd_per15(self):
        return self.kd_against / self.stat_seconds * 900 if self.stat_seconds else None

    def win_method_rates(self):
        """(ko, sub, dec) shares of wins, Laplace-smoothed toward uniform."""
        n = self.wins
        return ((self.wins_ko + 1) / (n + 3),
                (self.wins_sub + 1) / (n + 3),
                (self.wins_dec + 1) / (n + 3))

    @property
    def win_rate(self):
        return self.wins / self.n_bouts if self.n_bouts else None

    @property
    def ko_loss_rate(self):
        losses = self.n_bouts - self.wins
        return self.losses_ko / losses if losses else 0.0

    def form3(self):
        """Recency-weighted last-3 results (weights 3/2/1), None if no bouts."""
        if not self.last3:
            return None
        w = [3, 2, 1][: len(self.last3)]
        return float(np.dot(self.last3, w) / sum(w))

    def update(self, won: float, method: str, bout_date: date | None,
               stats: dict | None, opp_stats: dict | None) -> None:
        """Fold one completed bout into the accumulator. won: 1/0/0.5(draw)."""
        self.n_bouts += 1
        mc = _method_class(method)
        if won == 1.0:
            self.wins += 1
            self.win_streak = max(self.win_streak, 0) + 1
            if mc == "ko":
                self.wins_ko += 1
            elif mc == "sub":
                self.wins_sub += 1
            elif mc == "dec":
                self.wins_dec += 1
        elif won == 0.0:
            self.win_streak = min(self.win_streak, 0) - 1
            if mc == "ko":
                self.losses_ko += 1
        else:
            self.win_streak = 0
        self.last3 = ([won] + self.last3)[:3]
        if bout_date:
            self.last_date = bout_date
        if stats and stats.get("seconds", 0) > 0:
            self.stat_seconds += stats["seconds"]
            self.sig_landed += stats["sig_landed"]
            self.td_landed += stats["td_landed"]
            self.td_att += stats["td_att"]
            self.sub_att += stats["sub_att"]
            self.kd_for += stats["kd"]
            if opp_stats:
                self.sig_absorbed += opp_stats["sig_landed"]
                self.td_faced_landed += opp_stats["td_landed"]
                self.td_faced_att += opp_stats["td_att"]
                self.kd_against += opp_stats["kd"]


def _load_bout_stats(conn: sqlite3.Connection) -> dict:
    """bout_id -> {fighter_id: summed stat dict} from per-round rows."""
    out: dict[str, dict[str, dict]] = {}
    for r in conn.execute(
        "SELECT bout_id, fighter_id, SUM(kd) kd, SUM(sig_str_landed) sl, "
        "SUM(td_landed) tl, SUM(td_att) ta, SUM(sub_att) sa "
        "FROM bout_stats GROUP BY bout_id, fighter_id"
    ):
        out.setdefault(r["bout_id"], {})[r["fighter_id"]] = {
            "kd": r["kd"] or 0, "sig_landed": r["sl"] or 0,
            "td_landed": r["tl"] or 0, "td_att": r["ta"] or 0,
            "sub_att": r["sa"] or 0,
        }
    return out


def _diff(a, b):
    return None if a is None or b is None else a - b


def _age(dob: str | None, on: date | None):
    if not dob or not on:
        return None
    try:
        return (on - date.fromisoformat(dob)).days / 365.25
    except ValueError:
        return None


def build_features(conn: sqlite3.Connection) -> pd.DataFrame:
    """One feature row per eligible bout, in chronological order.

    Columns: bout_id, date, y (1 if fighter1 won, NaN for draws), and
    fighter1-minus-fighter2 differential features. Draw rows are emitted (for
    completeness) but training drops them.
    """
    fighters = {
        r["fighter_id"]: r for r in conn.execute(
            "SELECT fighter_id, height_cm, reach_cm, dob FROM fighters")
    }
    prefight = {
        (r["bout_id"], r["fighter_id"]): r for r in conn.execute(
            "SELECT bout_id, fighter_id, rating, rd FROM prefight_ratings")
    }
    stats_by_bout = _load_bout_stats(conn)
    bouts = conn.execute(
        "SELECT bout_id, date, fighter1_id, fighter2_id, winner_id, result, method, "
        "round, time, scheduled_rounds, is_title FROM bouts "
        "WHERE fighter1_id IS NOT NULL AND fighter2_id IS NOT NULL "
        "ORDER BY date ASC, bout_id ASC"
    ).fetchall()

    careers: dict[str, Career] = {}
    rows = []
    for b in bouts:
        # Skip no-contests and any non-completed bout (e.g. scheduled future
        # cards staged for the dashboard) -- they carry no result to learn from.
        if b["result"] not in ("win", "draw"):
            continue
        if b["result"] == "win" and b["winner_id"] is None:
            continue
        f1, f2 = b["fighter1_id"], b["fighter2_id"]
        bdate = date.fromisoformat(b["date"]) if b["date"] else None
        c1 = careers.setdefault(f1, Career())
        c2 = careers.setdefault(f2, Career())

        # ---- emit features from PRE-bout state, if eligible ----
        if c1.stat_seconds > 0 and c2.stat_seconds > 0 and bdate is not None:
            pf1 = prefight.get((b["bout_id"], f1))
            pf2 = prefight.get((b["bout_id"], f2))
            a1, a2 = fighters.get(f1), fighters.get(f2)
            ko1, sub1, dec1 = c1.win_method_rates()
            ko2, sub2, dec2 = c2.win_method_rates()
            lay1 = (bdate - c1.last_date).days if c1.last_date else None
            lay2 = (bdate - c2.last_date).days if c2.last_date else None
            if b["result"] == "win":
                y = 1.0 if b["winner_id"] == f1 else 0.0
            else:
                y = np.nan  # draw
            rows.append({
                "bout_id": b["bout_id"], "date": b["date"], "y": y,
                "rating_diff": _diff(pf1["rating"] if pf1 else None,
                                     pf2["rating"] if pf2 else None),
                "rd1": pf1["rd"] if pf1 else None,
                "rd2": pf2["rd"] if pf2 else None,
                "age_diff": _diff(_age(a1["dob"] if a1 else None, bdate),
                                  _age(a2["dob"] if a2 else None, bdate)),
                "height_diff": _diff(a1["height_cm"] if a1 else None,
                                     a2["height_cm"] if a2 else None),
                "reach_diff": _diff(a1["reach_cm"] if a1 else None,
                                    a2["reach_cm"] if a2 else None),
                "slpm_diff": _diff(c1.slpm, c2.slpm),
                "sapm_diff": _diff(c1.sapm, c2.sapm),
                "td_acc_diff": _diff(c1.td_acc, c2.td_acc),
                "td_def_diff": _diff(c1.td_def, c2.td_def),
                "sub_per15_diff": _diff(c1.sub_per15, c2.sub_per15),
                "kd_per15_diff": _diff(c1.kd_per15, c2.kd_per15),
                "kdd_per15_diff": _diff(c1.kdd_per15, c2.kdd_per15),
                "ko_rate_diff": ko1 - ko2,
                "sub_rate_diff": sub1 - sub2,
                "dec_rate_diff": dec1 - dec2,
                "ko_loss_rate_diff": c1.ko_loss_rate - c2.ko_loss_rate,
                "win_rate_diff": _diff(c1.win_rate, c2.win_rate),
                "streak_diff": c1.win_streak - c2.win_streak,
                "form3_diff": _diff(c1.form3(), c2.form3()),
                "log_layoff_diff": _diff(
                    np.log1p(lay1) if lay1 is not None else None,
                    np.log1p(lay2) if lay2 is not None else None),
                "n_prior_diff": c1.n_bouts - c2.n_bouts,
                "n_prior_min": min(c1.n_bouts, c2.n_bouts),
                "is_title": b["is_title"] or 0,
                "five_rounds": 1 if (b["scheduled_rounds"] or 3) >= 5 else 0,
            })

        # ---- update accumulators with this bout ----
        if b["result"] == "draw":
            s1 = s2 = 0.5
        else:
            s1 = 1.0 if b["winner_id"] == f1 else 0.0
            s2 = 1.0 - s1
        bstats = stats_by_bout.get(b["bout_id"], {})
        st1, st2 = bstats.get(f1), bstats.get(f2)
        secs = _fight_seconds(b["round"], b["time"])
        for st in (st1, st2):
            if st is not None:
                st["seconds"] = secs
        c1.update(s1, b["method"], bdate, st1, st2)
        c2.update(s2, b["method"], bdate, st2, st1)

    return pd.DataFrame(rows)


# Differential columns that flip sign when fighter order is swapped, and the
# paired columns that swap with each other. Used by tier2 to symmetrize.
ANTISYMMETRIC = [
    "rating_diff", "age_diff", "height_diff", "reach_diff", "slpm_diff",
    "sapm_diff", "td_acc_diff", "td_def_diff", "sub_per15_diff", "kd_per15_diff",
    "kdd_per15_diff", "ko_rate_diff", "sub_rate_diff", "dec_rate_diff",
    "ko_loss_rate_diff", "win_rate_diff", "streak_diff", "form3_diff",
    "log_layoff_diff", "n_prior_diff",
]
SWAP_PAIRS = [("rd1", "rd2")]
SYMMETRIC = ["n_prior_min", "is_title", "five_rounds"]
FEATURE_COLS = ANTISYMMETRIC + [c for p in SWAP_PAIRS for c in p] + SYMMETRIC


def flip_orientation(df: pd.DataFrame) -> pd.DataFrame:
    """Return the same rows seen from fighter2's perspective (label flipped)."""
    out = df.copy()
    out["y"] = 1.0 - out["y"]
    for c in ANTISYMMETRIC:
        out[c] = -out[c]
    for a, b in SWAP_PAIRS:
        out[a], out[b] = df[b], df[a]
    return out
