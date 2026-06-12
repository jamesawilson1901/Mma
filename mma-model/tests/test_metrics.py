"""Metric / staking unit tests with hand-computed expected values."""
import math

import pytest

from mma_model.backtest import metrics as M


def test_log_loss_known_value():
    # -mean(ln 0.8, ln 0.6) for two correct-ish predictions
    expected = -(math.log(0.8) + math.log(0.6)) / 2
    assert M.log_loss([1, 0], [0.8, 0.4]) == pytest.approx(expected)


def test_brier_known_value():
    assert M.brier([1, 0], [0.8, 0.4]) == pytest.approx(((0.8 - 1) ** 2 + 0.4 ** 2) / 2)


def test_accuracy_with_half_credit_for_coin_flips():
    assert M.accuracy([1, 0, 1], [0.9, 0.2, 0.5]) == pytest.approx((1 + 1 + 0.5) / 3)


def test_implied_prob_removes_margin():
    # symmetric -110/-110 style market -> 0.5 each despite the vig
    assert M.implied_prob_two_way(1.91, 1.91) == pytest.approx(0.5)
    p = M.implied_prob_two_way(1.5, 2.5)
    assert p == pytest.approx((1 / 1.5) / (1 / 1.5 + 1 / 2.5))


def test_kelly_fraction():
    # p=0.6 at evens: f* = (0.6*2-1)/(2-1) = 0.2
    assert M.kelly_fraction(0.6, 2.0) == pytest.approx(0.2)
    assert M.kelly_fraction(0.4, 2.0) == 0.0  # no edge -> no bet


def test_select_bets_picks_positive_edge_side():
    rows = [
        (1, 0.60, 2.0, 1.9),   # edge f1 = 0.2 -> bet f1, wins
        (0, 0.30, 2.0, 1.9),   # edge f2 = 0.7*1.9-1 = 0.33 -> bet f2, wins
        (1, 0.50, 1.9, 1.9),   # both edges negative -> no bet
    ]
    bets = M.select_bets(rows, threshold=0.0)
    assert len(bets) == 2
    assert all(b.won for b in bets)
    assert bets[0].odds == 2.0 and bets[1].odds == 1.9


def test_roi_flat():
    bets = [M.Bet(True, 2.0, 0.6, 0.2), M.Bet(False, 2.0, 0.6, 0.2)]
    # +1 then -1 over 2 units staked
    assert M.roi_flat(bets) == pytest.approx(0.0)


def test_roi_kelly_single_bet():
    bets = [M.Bet(True, 2.0, 0.6, 0.2)]
    # stake = 0.25*0.2, profit = stake*(2-1) -> ROI = 100%
    assert M.roi_kelly(bets) == pytest.approx(1.0)


def test_bootstrap_ci_brackets_point_estimate():
    bets = [M.Bet(i % 2 == 0, 2.1, 0.55, 0.155) for i in range(200)]
    point = M.roi_flat(bets)
    lo, hi = M.bootstrap_roi_ci(bets, M.roi_flat, n_boot=500)
    assert lo <= point <= hi
    assert hi > lo


def test_calibration_table_bins():
    y = [1, 0, 1, 1]
    p = [0.95, 0.05, 0.55, 0.92]
    tab = M.calibration_table(y, p, n_bins=10)
    assert sum(b["n"] for b in tab) == 4
    top = tab[-1]  # [0.9, 1.0]
    assert top["n"] == 2 and top["frac_won"] == 1.0
