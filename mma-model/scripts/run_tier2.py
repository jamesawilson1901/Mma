#!/usr/bin/env python3
"""Step 4: train walk-forward Tier 2 GBM and compare against Tier 1 + baselines.

Assumes the DB is built and ratings/odds are loaded (scripts/run_backtest.py).

Usage:
    python -m scripts.run_tier2
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from mma_model.config import DB_PATH  # noqa: E402
from mma_model.db.database import connect, init_db  # noqa: E402
from mma_model.models import tier2  # noqa: E402
from mma_model.backtest import report  # noqa: E402


def main() -> None:
    conn = connect(DB_PATH)
    init_db(conn)

    print("== Tier 2 walk-forward training ==")
    out = tier2.run_walkforward(conn)
    for k, v in out.items():
        if k == "feature_importance":
            continue
        print(f"  {k:18s}: {v}")
    print("  top features by gain:")
    for name, gain in (out["feature_importance"] or [])[:12]:
        print(f"    {name:22s} {gain:12.1f}")

    print("== Comparison report (common sample) ==")
    cmp = report.write_comparison(
        conn,
        models={"tier2_gbm": "Tier 2 (GBM)", "tier1_glicko": "Tier 1 (Glicko-2)"},
        out_name="tier2_vs_tier1",
    )
    print(f"  common bouts      : {cmp['common_bouts']}")
    for r in cmp["results"]:
        print(f"  [{r.label}] log_loss={r.model['log_loss']:.4f} "
              f"brier={r.model['brier']:.4f} acc={r.model['accuracy']:.1%}")
    print(f"  implied           : log_loss={cmp['results'][0].implied['log_loss']:.4f}")
    print(f"  report written    : {cmp['report']}")
    conn.close()


if __name__ == "__main__":
    main()
