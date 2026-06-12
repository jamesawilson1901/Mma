"""Entity-resolution tests: normalization, exact/fuzzy match, ambiguity."""
from mma_model.entity.resolver import EntityResolver, normalize_name


def test_normalize_strips_accents_suffixes_nicknames():
    assert normalize_name("José Aldo Jr.") == "jose aldo"
    assert normalize_name("Khabib  Nurmagomedov") == "khabib nurmagomedov"
    assert normalize_name('Israel "The Last Stylebender" Adesanya') == "israel adesanya"
    assert normalize_name("Anderson Silva III") == "anderson silva"


def test_exact_match():
    r = EntityResolver()
    r.add_canonical("f1", "Conor McGregor")
    assert r.resolve("conor mcgregor") == "f1"
    assert r.resolve("Conor McGregor") == "f1"


def test_fuzzy_match_and_alias_learning():
    r = EntityResolver(threshold=85)
    r.add_canonical("f1", "Aleksandar Rakic")
    # accented / spelling variant should fuzzy-match
    fid = r.resolve("Aleksandar Rakić")
    assert fid == "f1"
    # the variant is learned so a later lookup is an exact hit
    assert normalize_name("Aleksandar Rakić") in r._index


def test_unmatched_recorded():
    r = EntityResolver()
    r.add_canonical("f1", "Jon Jones")
    assert r.resolve("Totally Different Person", context="bout x") is None
    assert r.unmatched and r.unmatched[0][0] == "Totally Different Person"


def test_ambiguous_exact_returns_none():
    r = EntityResolver()
    # two different fighters with the same normalized name
    r.add_canonical("f1", "Bruno Silva")
    r.add_canonical("f2", "Bruno Silva")
    assert r.resolve("Bruno Silva") is None
    assert r.ambiguous


def test_get_or_create_mints_synthetic():
    r = EntityResolver()
    fid = r.get_or_create("Unknown Prospect")
    assert fid.startswith("syn:")
    # second call resolves to the same synthetic id
    assert r.get_or_create("Unknown Prospect") == fid
