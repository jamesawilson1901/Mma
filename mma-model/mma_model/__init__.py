"""MMA multi-promotion fight prediction model.

Two-tier architecture:
  Tier 1 -- Glicko-2 global rating layer over every recorded pro MMA bout.
  Tier 2 -- gradient-boosted classifier on UFC-rich per-fight stat differentials.

See PROGRESS.md for current build state and the project spec for design rationale.
"""

__version__ = "0.1.0"
