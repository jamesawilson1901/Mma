"""Assemble the walk-forward backtest report (UFC, Tier 1 vs baselines).

Sample = bouts that have (a) a pre-fight model prediction, (b) matched
historical odds for both fighters, (c) a decisive result (draws and NCs are
excluded from binary metrics; counts reported).

Baselines:
  (a) pick-favourite  -- accuracy + ROI of flat-staking every favourite.
  (b) implied-prob    -- margin-removed bookmaker probability as the prediction
                          (this is the line to beat on log loss / Brier).

Outputs reports/backtest_tier1.md and reports/calibration_tier1.png.
CLV is N/A for this dataset: betmma.tips historical odds are a single
closing-ish snapshot, so there is no open->close movement to measure. Own
closing-line capture starts in build step 6.
"""
from __future__ import annotations

import hashlib
import sqlite3
from dataclasses import dataclass, field

import numpy as np

from ..config import PROJECT_DIR
from . import metrics as M

REPORT_DIR = PROJECT_DIR / "reports"
MODEL = "tier1_glicko"
EDGE_THRESHOLDS = (0.0, 0.05, 0.10)
MIN_EXPERIENCE = 3  # prior bouts each, for the "established fighters" subsample


@dataclass
class SampleRow:
    bout_id: str
    date: str
    y: int          # 1 if fighter1 won
    p: float        # model P(fighter1)
    d1: float       # decimal odds, fighter1
    d2: float       # decimal odds, fighter2
    n_prior1: int
    n_prior2: int

    @property
    def p_implied(self) -> float:
        return M.implied_prob_two_way(self.d1, self.d2)


@dataclass
class EvalResult:
    label: str
    n: int = 0
    model: dict = field(default_factory=dict)
    implied: dict = field(default_factory=dict)
    favourite: dict = field(default_factory=dict)
    staking: list = field(default_factory=list)
    calib: list = field(default_factory=list)


def _flip(bout_id: str) -> bool:
    """Deterministic coin flip per bout for evaluation orientation.

    ufcstats lists the winner as fighter1 (~64% of rows after draws), so
    evaluating in source orientation skews the calibration display upward even
    though the model never sees ordering. Flipping half the sample restores a
    ~50% base rate. Log loss / Brier / accuracy / staking are invariant under
    this flip (y, p, d1, d2 all flip together); only the calibration plot's
    presentation changes.
    """
    return hashlib.sha256(bout_id.encode()).digest()[0] % 2 == 1


def load_sample(conn: sqlite3.Connection, model: str = MODEL) -> tuple[list[SampleRow], dict]:
    """Join predictions x odds x decisive results into backtest rows."""
    rows = conn.execute(
        """
        SELECT b.bout_id, b.date, b.fighter1_id, b.fighter2_id, b.winner_id, b.result,
               p.p_fighter1, p.n_prior1, p.n_prior2,
               o1.decimal_odds AS d1, o2.decimal_odds AS d2
        FROM bouts b
        JOIN predictions p ON p.bout_id = b.bout_id AND p.model = ?
        JOIN odds o1 ON o1.bout_id = b.bout_id AND o1.fighter_id = b.fighter1_id
        JOIN odds o2 ON o2.bout_id = b.bout_id AND o2.fighter_id = b.fighter2_id
        ORDER BY b.date
        """,
        (model,),
    ).fetchall()
    sample, n_draw = [], 0
    for r in rows:
        if r["result"] != "win" or r["winner_id"] is None:
            n_draw += 1
            continue
        y = 1 if r["winner_id"] == r["fighter1_id"] else 0
        p, d1, d2 = r["p_fighter1"], r["d1"], r["d2"]
        n1, n2 = r["n_prior1"], r["n_prior2"]
        if _flip(r["bout_id"]):
            y, p, d1, d2, n1, n2 = 1 - y, 1.0 - p, d2, d1, n2, n1
        sample.append(SampleRow(
            bout_id=r["bout_id"], date=r["date"],
            y=y, p=p, d1=d1, d2=d2, n_prior1=n1, n_prior2=n2,
        ))
    meta = {"joined": len(rows), "excluded_draw_nc": n_draw, "decisive": len(sample)}
    return sample, meta


def evaluate(sample: list[SampleRow], label: str) -> EvalResult:
    res = EvalResult(label=label, n=len(sample))
    if not sample:
        return res
    y = np.array([s.y for s in sample])
    p = np.array([s.p for s in sample])
    pi = np.array([s.p_implied for s in sample])

    res.model = {"log_loss": M.log_loss(y, p), "brier": M.brier(y, p),
                 "accuracy": M.accuracy(y, p)}
    res.implied = {"log_loss": M.log_loss(y, pi), "brier": M.brier(y, pi),
                   "accuracy": M.accuracy(y, pi)}

    # Baseline (a): flat-stake every favourite at offered odds.
    fav_bets = [
        M.Bet(won=bool((s.d1 < s.d2) == (s.y == 1)) if s.d1 != s.d2 else bool(s.y == 1),
              odds=min(s.d1, s.d2), p=max(s.p_implied, 1 - s.p_implied), edge=0.0)
        for s in sample
    ]
    res.favourite = {
        "accuracy": float(np.mean([b.won for b in fav_bets])),
        "roi_flat": M.roi_flat(fav_bets),
        "roi_ci": M.bootstrap_roi_ci(fav_bets),
    }

    # Model staking at each edge threshold.
    rows = [(s.y, s.p, s.d1, s.d2) for s in sample]
    for thr in EDGE_THRESHOLDS:
        bets = M.select_bets(rows, threshold=thr)
        entry = {"threshold": thr, "n_bets": len(bets)}
        if bets:
            entry.update({
                "win_rate": float(np.mean([b.won for b in bets])),
                "roi_flat": M.roi_flat(bets),
                "roi_flat_ci": M.bootstrap_roi_ci(bets, M.roi_flat),
                "roi_kelly": M.roi_kelly(bets),
                "roi_kelly_ci": M.bootstrap_roi_ci(bets, M.roi_kelly),
            })
        res.staking.append(entry)

    res.calib = M.calibration_table(y, p)
    return res


def calibration_plot(results: list[EvalResult], path) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    fig, axes = plt.subplots(1, len(results), figsize=(6 * len(results), 5.5), squeeze=False)
    for ax, res in zip(axes[0], results):
        xs = [b["mean_pred"] for b in res.calib if b["n"]]
        ys = [b["frac_won"] for b in res.calib if b["n"]]
        ns = [b["n"] for b in res.calib if b["n"]]
        ax.plot([0, 1], [0, 1], "k--", lw=1, label="perfect")
        ax.scatter(xs, ys, s=[max(10, n / 3) for n in ns], alpha=0.8, label="model bins")
        for x, yy, n in zip(xs, ys, ns):
            ax.annotate(str(n), (x, yy), fontsize=7, xytext=(4, 4),
                        textcoords="offset points")
        ax.set_xlabel("predicted P(win), randomized orientation")
        ax.set_ylabel("observed frequency")
        ax.set_title(f"{res.label} (n={res.n})")
        ax.set_xlim(0, 1)
        ax.set_ylim(0, 1)
        ax.legend(loc="upper left", fontsize=8)
        ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(path, dpi=130)
    plt.close(fig)


def _fmt_pct(x: float) -> str:
    return f"{x * 100:+.1f}%"


def _staking_table(res: EvalResult) -> list[str]:
    lines = [
        "| edge > | bets | win% | flat ROI | flat 95% CI | 1/4-Kelly ROI | Kelly 95% CI |",
        "|---|---|---|---|---|---|---|",
    ]
    for s in res.staking:
        if s["n_bets"] == 0:
            lines.append(f"| {s['threshold']:.0%} | 0 | – | – | – | – | – |")
            continue
        fci, kci = s["roi_flat_ci"], s["roi_kelly_ci"]
        lines.append(
            f"| {s['threshold']:.0%} | {s['n_bets']} | {s['win_rate']:.1%} "
            f"| {_fmt_pct(s['roi_flat'])} | [{_fmt_pct(fci[0])}, {_fmt_pct(fci[1])}] "
            f"| {_fmt_pct(s['roi_kelly'])} | [{_fmt_pct(kci[0])}, {_fmt_pct(kci[1])}] |"
        )
    return lines


def write_report(conn: sqlite3.Connection, model: str = MODEL) -> dict:
    """Run the full evaluation and write markdown + calibration PNG."""
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    sample, meta = load_sample(conn, model)
    established = [s for s in sample
                   if s.n_prior1 >= MIN_EXPERIENCE and s.n_prior2 >= MIN_EXPERIENCE]
    res_all = evaluate(sample, "All bouts with odds")
    res_est = evaluate(established, f"Both fighters ≥{MIN_EXPERIENCE} prior UFC bouts")

    png = REPORT_DIR / f"calibration_{model.replace('_glicko','')}.png"
    calibration_plot([res_all, res_est], png)

    dates = [s.date for s in sample]
    lines = [
        f"# Walk-forward backtest — {model}",
        "",
        f"Sample: **{meta['decisive']}** decisive UFC bouts with matched odds "
        f"({min(dates)} → {max(dates)}); {meta['excluded_draw_nc']} draws/NCs excluded.",
        "",
        "Strict walk-forward: every prediction was computed from ratings as they "
        "stood before the bout, in one chronological pass (leakage-tested).",
        "",
    ]
    for res in (res_all, res_est):
        better_ll = res.model["log_loss"] < res.implied["log_loss"]
        lines += [
            f"## {res.label} (n={res.n})",
            "",
            "| metric | Tier 1 (Glicko-2) | implied prob (margin-removed) | pick favourite |",
            "|---|---|---|---|",
            f"| log loss | {res.model['log_loss']:.4f} | {res.implied['log_loss']:.4f} | – |",
            f"| Brier | {res.model['brier']:.4f} | {res.implied['brier']:.4f} | – |",
            f"| accuracy | {res.model['accuracy']:.1%} | {res.implied['accuracy']:.1%} "
            f"| {res.favourite['accuracy']:.1%} |",
            "",
            f"Always-bet-favourite flat ROI: {_fmt_pct(res.favourite['roi_flat'])} "
            f"(95% CI [{_fmt_pct(res.favourite['roi_ci'][0])}, "
            f"{_fmt_pct(res.favourite['roi_ci'][1])}]).",
            "",
            f"**Tier 1 {'beats' if better_ll else 'does NOT beat'} the bookmaker "
            f"implied probability on log loss** in this subsample.",
            "",
            "### Staking simulation (bet model-edge sides)",
            "",
            *_staking_table(res),
            "",
        ]
    lines += [
        f"![calibration]({png.name})",
        "",
        "## Honest readout",
        "",
        "- The bookmaker implied probability is a very strong predictor; Tier 1 uses "
        "results-level data only (no per-fight stats, no odds), so matching it is hard.",
        "- A positive ROI whose 95% bootstrap CI includes 0 **is noise, not edge** — "
        "treat it as such. A few hundred bets is far too small a sample to claim an edge.",
        "- CLV is N/A here: the historical dataset is a single closing-ish snapshot. "
        "Own open/close capture starts in build step 6.",
        "- Tier 1 currently sees UFC bouts only; debutants enter at the prior (1500). "
        "Cross-promotion records (step 5) and Tier 2 stats (step 4) address this.",
        "",
    ]
    md = REPORT_DIR / f"backtest_{model.replace('_glicko','')}.md"
    md.write_text("\n".join(lines))
    return {"sample": meta, "report": str(md), "plot": str(png),
            "all": res_all, "established": res_est}
