"""Promotion + ruleset inference tests."""
from mma_model.ingest.promotions import infer_promotion, infer_ruleset


def test_promotion_codes():
    assert infer_promotion("UFC 300 - Pereira vs Hill") == "UFC"
    assert infer_promotion("UFC Fight Night: Allen vs. Costa") == "UFC"
    assert infer_promotion("ONE 165 - Superlek vs Takeru") == "ONE"
    assert infer_promotion("ONE Championship - A New Tomorrow") == "ONE"
    assert infer_promotion("PFL 3 - 2023 Regular Season") == "PFL"
    assert infer_promotion("World Series of Fighting 9") == "PFL"
    assert infer_promotion("Bellator 300") == "Bellator"
    assert infer_promotion("RIZIN 45") == "RIZIN_PRIDE"
    assert infer_promotion("PRIDE 33 - Second Coming") == "RIZIN_PRIDE"
    assert infer_promotion("Strikeforce - Diaz vs Cyborg") == "Strikeforce"
    assert infer_promotion("Some Tiny Regional Show 4") == "Other"
    assert infer_promotion("") == "Other"


def test_ruleset_excludes_non_mma():
    assert infer_ruleset("UFC 300") == "MMA"
    assert infer_ruleset("ONE 165 - Superlek vs Takeru") == "MMA"
    # ONE's non-MMA divisions / events:
    assert infer_ruleset("ONE Friday Fights 50", division="Muay Thai") == "non-MMA"
    assert infer_ruleset("ONE Championship - Kickboxing") == "non-MMA"
    assert infer_ruleset("ONE - Submission Grappling", division="Grappling") == "non-MMA"
    assert infer_ruleset("ADCC 2022") == "non-MMA"
    # method-only grappling tell
    assert infer_ruleset("Local Show", method="Decision (Points)") == "non-MMA"
    assert infer_ruleset("UFC 200", method="Decision (Unanimous)") == "MMA"
