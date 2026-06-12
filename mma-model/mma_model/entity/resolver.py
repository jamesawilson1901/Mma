"""Canonical fighter ids + fuzzy name resolution across sources.

The hard problem in this project: the same fighter appears under slightly
different names across ufcstats, betmma.tips, Tapology and Sherdog. We keep a
single canonical `fighters` row per person and an alias index that maps every
observed name variant (normalized) to a canonical id.

Resolution order for an incoming name:
  1. exact normalized-name hit in the alias index   -> canonical id
  2. unique fuzzy match above threshold              -> canonical id (+ learn alias)
  3. otherwise                                       -> unresolved (caller logs it)
"""
from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass, field

from rapidfuzz import fuzz, process

# Tokens that carry no identity signal and only add noise to matching.
_SUFFIXES = {"jr", "sr", "ii", "iii", "iv"}


def normalize_name(name: str) -> str:
    """Lowercase, strip accents/punctuation, collapse whitespace, drop suffixes.

    'Jose Aldo Jr.' -> 'jose aldo'  ;  'Khabib Nurmagomedov' unchanged form.
    """
    if not name:
        return ""
    # Strip accents.
    name = unicodedata.normalize("NFKD", name)
    name = "".join(c for c in name if not unicodedata.combining(c))
    name = name.lower()
    # Drop the common "nickname" in quotes if present.
    name = re.sub(r'["“”‘’].*?["“”‘’]', " ", name)
    # Non-alphanumeric -> space.
    name = re.sub(r"[^a-z0-9 ]", " ", name)
    tokens = [t for t in name.split() if t and t not in _SUFFIXES]
    return " ".join(tokens)


@dataclass
class EntityResolver:
    """In-memory alias index, backed by the `fighters`/`fighter_aliases` tables.

    Build it from canonical fighters (with stable ids), then call resolve() for
    every name encountered in fight records.
    """

    threshold: int = 90  # rapidfuzz token_sort_ratio cutoff for fuzzy accept
    # norm_name -> set of candidate canonical ids (a name can be ambiguous)
    _index: dict[str, set[str]] = field(default_factory=dict)
    # canonical id -> display name (for diagnostics)
    _names: dict[str, str] = field(default_factory=dict)
    unmatched: list[tuple[str, str]] = field(default_factory=list)  # (raw, context)
    ambiguous: list[tuple[str, str]] = field(default_factory=list)

    def add_canonical(self, fighter_id: str, name: str) -> None:
        self._names[fighter_id] = name
        self._add_alias(fighter_id, name)

    def _add_alias(self, fighter_id: str, alias: str) -> None:
        norm = normalize_name(alias)
        if not norm:
            return
        self._index.setdefault(norm, set()).add(fighter_id)

    def resolve(self, name: str, context: str = "") -> str | None:
        """Return canonical fighter_id for `name`, or None if unresolved.

        Side effect: learns the alias on a successful fuzzy match, and records
        unmatched / ambiguous names for later auditing.
        """
        norm = normalize_name(name)
        if not norm:
            return None
        # 1. exact normalized hit
        hit = self._index.get(norm)
        if hit:
            if len(hit) == 1:
                return next(iter(hit))
            self.ambiguous.append((name, context))
            return None  # ambiguous exact -- needs manual disambiguation
        # 2. fuzzy
        match = process.extractOne(
            norm, self._index.keys(), scorer=fuzz.token_sort_ratio
        )
        if match and match[1] >= self.threshold:
            cands = self._index[match[0]]
            if len(cands) == 1:
                fid = next(iter(cands))
                self._add_alias(fid, name)  # learn the variant
                return fid
            self.ambiguous.append((name, context))
            return None
        # 3. unmatched
        self.unmatched.append((name, context))
        return None

    def get_or_create(self, name: str, context: str = "") -> str:
        """Resolve, or mint a deterministic synthetic id and register it.

        Used so a fighter with no ufcstats tale-of-the-tape row still gets a
        stable canonical id derived from their normalized name.
        """
        fid = self.resolve(name, context)
        if fid is not None:
            return fid
        norm = normalize_name(name)
        syn = f"syn:{norm.replace(' ', '_')}"
        self.add_canonical(syn, name)
        return syn
