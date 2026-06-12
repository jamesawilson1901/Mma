"""Hand-rolled Glicko-2 rating system (Glickman, 2013).

Chosen over plain Elo because the rating-deviation (RD) and volatility terms
explicitly model uncertainty from long layoffs and thin fight records -- both
endemic in MMA.

The implementation follows the reference paper step-for-step and is validated
against the worked example in the paper (see tests/test_glicko2.py):
    player r=1500, RD=200, vol=0.06, tau=0.5 vs
    [(1400,30,W), (1550,100,L), (1700,300,L)]
  -> r' ~= 1464.06, RD' ~= 151.52, vol' ~= 0.05999

Two usage modes:
  * rate_period(rating, opponents)  -- batch a rating period's games (paper form)
  * the streaming driver in rate_fighters.py treats each bout as a 1-game period
    and inflates RD across inactivity via did_not_compete().
"""
from __future__ import annotations

import math
from dataclasses import dataclass

from ..config import (
    GLICKO_DEFAULT_RATING,
    GLICKO_DEFAULT_RD,
    GLICKO_DEFAULT_VOL,
    GLICKO_SCALE,
    GLICKO_TAU,
)

_EPS = 1e-6


@dataclass
class Rating:
    """A fighter's rating on the public (Glicko) scale."""

    rating: float = GLICKO_DEFAULT_RATING
    rd: float = GLICKO_DEFAULT_RD
    vol: float = GLICKO_DEFAULT_VOL

    # --- conversions to/from the internal Glicko-2 scale -------------------
    @property
    def mu(self) -> float:
        return (self.rating - GLICKO_DEFAULT_RATING) / GLICKO_SCALE

    @property
    def phi(self) -> float:
        return self.rd / GLICKO_SCALE

    @classmethod
    def from_internal(cls, mu: float, phi: float, vol: float) -> "Rating":
        return cls(
            rating=mu * GLICKO_SCALE + GLICKO_DEFAULT_RATING,
            rd=phi * GLICKO_SCALE,
            vol=vol,
        )


class Glicko2:
    """Glicko-2 engine. One instance encapsulates the system constant tau."""

    def __init__(
        self,
        tau: float = GLICKO_TAU,
        default_rating: float = GLICKO_DEFAULT_RATING,
        default_rd: float = GLICKO_DEFAULT_RD,
        default_vol: float = GLICKO_DEFAULT_VOL,
        max_rd: float = GLICKO_DEFAULT_RD,
    ):
        self.tau = tau
        self.default_rating = default_rating
        self.default_rd = default_rd
        self.default_vol = default_vol
        self.max_rd = max_rd

    def create_rating(self) -> Rating:
        return Rating(self.default_rating, self.default_rd, self.default_vol)

    # --- core helpers (operate on the internal mu/phi scale) --------------
    @staticmethod
    def _g(phi: float) -> float:
        return 1.0 / math.sqrt(1.0 + 3.0 * phi * phi / (math.pi * math.pi))

    @staticmethod
    def _E(mu: float, mu_j: float, phi_j: float) -> float:
        return 1.0 / (1.0 + math.exp(-Glicko2._g(phi_j) * (mu - mu_j)))

    def _new_volatility(self, phi: float, v: float, delta: float, sigma: float) -> float:
        """Illinois-algorithm root find for the updated volatility (paper step 5)."""
        a = math.log(sigma * sigma)
        tau2 = self.tau * self.tau
        phi2 = phi * phi
        delta2 = delta * delta

        def f(x: float) -> float:
            ex = math.exp(x)
            num = ex * (delta2 - phi2 - v - ex)
            den = 2.0 * (phi2 + v + ex) ** 2
            return num / den - (x - a) / tau2

        A = a
        if delta2 > phi2 + v:
            B = math.log(delta2 - phi2 - v)
        else:
            k = 1
            while f(a - k * self.tau) < 0:
                k += 1
            B = a - k * self.tau

        fA, fB = f(A), f(B)
        while abs(B - A) > _EPS:
            C = A + (A - B) * fA / (fB - fA)
            fC = f(C)
            if fC * fB <= 0:
                A, fA = B, fB
            else:
                fA /= 2.0
            B, fB = C, fC
        return math.exp(A / 2.0)

    # --- public API -------------------------------------------------------
    def rate_period(self, rating: Rating, opponents: list[tuple[Rating, float]]) -> Rating:
        """Update `rating` after a rating period of games.

        opponents: list of (opponent_rating, score) where score is
        1.0 win / 0.5 draw / 0.0 loss for `rating`'s player.
        """
        if not opponents:
            return self.did_not_compete(rating)

        mu, phi = rating.mu, rating.phi
        v_inv = 0.0
        delta_sum = 0.0
        for opp, score in opponents:
            g = self._g(opp.phi)
            E = self._E(mu, opp.mu, opp.phi)
            v_inv += g * g * E * (1.0 - E)
            delta_sum += g * (score - E)
        v = 1.0 / v_inv
        delta = v * delta_sum

        sigma_p = self._new_volatility(phi, v, delta, rating.vol)
        phi_star = math.sqrt(phi * phi + sigma_p * sigma_p)
        phi_p = 1.0 / math.sqrt(1.0 / (phi_star * phi_star) + 1.0 / v)
        mu_p = mu + phi_p * phi_p * delta_sum

        new = Rating.from_internal(mu_p, phi_p, sigma_p)
        if new.rd > self.max_rd:
            new.rd = self.max_rd
        return new

    def did_not_compete(self, rating: Rating, periods: float = 1.0) -> Rating:
        """Inflate RD across `periods` of inactivity; rating/vol unchanged.

        periods may be fractional (e.g. days_elapsed / RATING_PERIOD_DAYS).
        """
        phi = rating.phi
        phi_star = math.sqrt(phi * phi + rating.vol * rating.vol * max(periods, 0.0))
        new = Rating.from_internal(rating.mu, phi_star, rating.vol)
        if new.rd > self.max_rd:
            new.rd = self.max_rd
        return new

    def expected_score(self, a: Rating, b: Rating) -> float:
        """P(a beats b), accounting for both fighters' RD (uncertainty)."""
        # Combine deviations so a high-RD opponent pulls the estimate toward 0.5.
        phi = math.sqrt(a.phi ** 2 + b.phi ** 2)
        return 1.0 / (1.0 + math.exp(-self._g(phi) * (a.mu - b.mu)))
