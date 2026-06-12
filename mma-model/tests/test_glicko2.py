"""Validate the Glicko-2 engine against Glickman's worked example.

Glickman (2013), "Example of the Glicko-2 system": a player rated 1500 with
RD 200 and volatility 0.06 (tau = 0.5) plays three games in a rating period
against (1400, RD 30, W), (1550, RD 100, L), (1700, RD 300, L). The expected
post-period values are r' ~= 1464.06, RD' ~= 151.52, vol' ~= 0.05999.
"""
import math

import pytest

from mma_model.ratings.glicko2 import Glicko2, Rating


def test_paper_worked_example():
    g = Glicko2(tau=0.5)
    player = Rating(1500, 200, 0.06)
    opponents = [
        (Rating(1400, 30), 1.0),
        (Rating(1550, 100), 0.0),
        (Rating(1700, 300), 0.0),
    ]
    new = g.rate_period(player, opponents)
    assert new.rating == pytest.approx(1464.06, abs=0.1)
    assert new.rd == pytest.approx(151.52, abs=0.2)
    assert new.vol == pytest.approx(0.05999, abs=1e-4)


def test_internal_scale_roundtrip():
    r = Rating(1500, 200, 0.06)
    assert r.mu == pytest.approx(0.0, abs=1e-9)
    assert r.phi == pytest.approx(200 / 173.7178, abs=1e-9)
    back = Rating.from_internal(r.mu, r.phi, r.vol)
    assert back.rating == pytest.approx(1500)
    assert back.rd == pytest.approx(200)


def test_g_and_e_helpers():
    # E == 0.5 when ratings are equal regardless of phi.
    assert Glicko2._E(0.0, 0.0, 1.0) == pytest.approx(0.5)
    # g(phi) is decreasing in phi and equals 1 at phi=0.
    assert Glicko2._g(0.0) == pytest.approx(1.0)
    assert Glicko2._g(1.0) < 1.0


def test_did_not_compete_inflates_rd_only():
    g = Glicko2()
    r = Rating(1600, 80, 0.06)
    after = g.did_not_compete(r, periods=2.0)
    assert after.rating == pytest.approx(1600)
    assert after.rd > r.rd
    assert after.vol == pytest.approx(0.06)


def test_rd_capped_at_max():
    g = Glicko2(max_rd=350)
    r = Rating(1500, 340, 0.20)
    after = g.did_not_compete(r, periods=50.0)
    assert after.rd <= 350.0 + 1e-9


def test_winning_raises_losing_lowers():
    g = Glicko2()
    a, b = g.create_rating(), g.create_rating()
    a_win = g.rate_period(a, [(b, 1.0)])
    b_loss = g.rate_period(b, [(a, 0.0)])
    assert a_win.rating > 1500
    assert b_loss.rating < 1500
    # An upset (low-rated beats high-rated) moves ratings more than a chalk win.
    strong, weak = Rating(1800, 60), Rating(1200, 60)
    upset = g.rate_period(weak, [(strong, 1.0)])
    chalk = g.rate_period(strong, [(weak, 1.0)])
    assert (upset.rating - 1200) > (chalk.rating - 1800)


def test_expected_score_symmetry():
    g = Glicko2()
    a, b = Rating(1600, 50), Rating(1500, 50)
    assert g.expected_score(a, b) + g.expected_score(b, a) == pytest.approx(1.0)
    assert g.expected_score(a, b) > 0.5
