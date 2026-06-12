"""Parsing-helper tests (dates, tale-of-the-tape, fight fields, stat strings)."""
from mma_model.ingest import parse


def test_id_from_url():
    assert parse.id_from_url("http://ufcstats.com/fighter-details/93fe7332d16c6ad9") == "93fe7332d16c6ad9"
    assert parse.id_from_url("") is None


def test_parse_date():
    assert parse.parse_date("May 16, 2026") == "2026-05-16"
    assert parse.parse_date("Jul 13, 1978") == "1978-07-13"
    assert parse.parse_date("garbage") is None


def test_tale_of_the_tape():
    assert parse.parse_height_cm("5' 11\"") == 180.3
    assert parse.parse_height_cm("--") is None
    assert parse.parse_reach_cm('76"') == 193.0
    assert parse.parse_weight_lbs("155 lbs.") == 155.0


def test_outcome_and_winner():
    assert parse.parse_outcome("W/L") == "win"
    assert parse.parse_outcome("D/D") == "draw"
    assert parse.parse_outcome("NC/NC") == "nc"
    assert parse.winner_index("W/L") == 0
    assert parse.winner_index("L/W") == 1
    assert parse.winner_index("D/D") is None


def test_split_bout():
    assert parse.split_bout("Arnold Allen vs. Melquizael Costa") == (
        "Arnold Allen", "Melquizael Costa")
    assert parse.split_bout("A vs B") == ("A", "B")
    assert parse.split_bout("no separator") is None


def test_method_and_rounds():
    assert parse.parse_method("Decision - Unanimous") == ("Decision", "Unanimous")
    assert parse.parse_method("KO/TKO") == ("KO/TKO", "")
    assert parse.scheduled_rounds("5 Rnd (5-5-5-5-5)") == 5
    assert parse.scheduled_rounds("3 Rnd (5-5-5)") == 3
    assert parse.is_title_bout("UFC Lightweight Title Bout") == 1
    assert parse.is_title_bout("Featherweight Bout") == 0


def test_stat_strings():
    assert parse.parse_of("9 of 20") == (9, 20)
    assert parse.parse_of("") == (0, 0)
    assert parse.parse_ctrl_seconds("1:44") == 104
    assert parse.parse_ctrl_seconds("--") == 0
    assert parse.parse_int("Round 3") == 3
