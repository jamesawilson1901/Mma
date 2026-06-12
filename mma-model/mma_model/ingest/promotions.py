"""Promotion + ruleset inference for cross-promotion fight records.

Sherdog/Tapology fight rows don't carry a clean `promotion` field -- the
promotion is encoded in the event name ("ONE 165 - Superlek vs Takeru",
"PFL 3 - 2023 Regular Season"). This module maps an event name to a canonical
promotion code and detects non-MMA rulesets so they can be excluded (ONE mixes
MMA, muay thai, kickboxing and submission grappling on the same card -- the
spec requires ingesting MMA-rules bouts only).
"""
from __future__ import annotations

import re

# Ordered (regex -> promotion code). First match wins, so put specific
# patterns before generic ones. Word boundaries avoid 'UFC' matching inside
# other tokens.
_PROMOTION_PATTERNS: list[tuple[re.Pattern, str]] = [
    (re.compile(r"\bUFC\b|Ultimate Fighting", re.I), "UFC"),
    (re.compile(r"\bONE\b|ONE Championship|ONE FC|ONE Fighting", re.I), "ONE"),
    (re.compile(r"\bPFL\b|Professional Fighters League|WSOF|World Series of Fighting", re.I), "PFL"),
    (re.compile(r"\bBellator\b", re.I), "Bellator"),
    (re.compile(r"\bRIZIN\b|\bDREAM\b|\bPRIDE\b|\bK-1\b", re.I), "RIZIN_PRIDE"),
    (re.compile(r"\bStrikeforce\b", re.I), "Strikeforce"),
    (re.compile(r"\bM-1\b|M-1 Global", re.I), "M-1"),
    (re.compile(r"\bKSW\b", re.I), "KSW"),
    (re.compile(r"\bACA\b|\bACB\b|Absolute Championship", re.I), "ACA"),
    (re.compile(r"\bLFA\b|Legacy Fighting|Resurrection Fighting|\bRFA\b", re.I), "LFA"),
    (re.compile(r"\bInvicta\b", re.I), "Invicta"),
    (re.compile(r"\bCage Warriors\b|\bCWFC\b", re.I), "CageWarriors"),
    (re.compile(r"\bBKFC\b|Bare Knuckle", re.I), "BKFC"),
]

# Non-MMA ruleset signals (ONE and some others run these). If a bout's event or
# method text matches, it is NOT MMA and must be excluded from ratings.
_NON_MMA = re.compile(
    r"muay\s*thai|kickbox|\bk-?1\b|submission\s*grappl|grappling|jiu[-\s]?jitsu|"
    r"\bibjjf\b|\badcc\b|boxing|lethwei|sanda|wrestling\s*match",
    re.I,
)
# Methods that only exist under non-MMA rules (grappling has no KO-by-strikes,
# but does have points/submission-only; muay thai/boxing have rounds decisions
# too, so we rely mainly on event/division text). A few method-only tells:
_NON_MMA_METHOD = re.compile(r"points|advantage|\bdq\b\s*\(grappl", re.I)


def infer_promotion(event_name: str) -> str:
    """Map an event name to a canonical promotion code; 'Other' if unknown."""
    if not event_name:
        return "Other"
    for pat, code in _PROMOTION_PATTERNS:
        if pat.search(event_name):
            return code
    return "Other"


def infer_ruleset(event_name: str = "", division: str = "", method: str = "") -> str:
    """Return 'MMA' or 'non-MMA' from event / division / method text.

    Division is the most reliable signal on ONE cards (e.g. the bout is listed
    under a "Muay Thai" or "Submission Grappling" division).
    """
    blob = " ".join(filter(None, (event_name, division, method)))
    if _NON_MMA.search(blob) or _NON_MMA_METHOD.search(method or ""):
        return "non-MMA"
    return "MMA"
