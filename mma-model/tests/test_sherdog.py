"""Sherdog parser + cross-promotion loader tests (fixtures, no network)."""
import sqlite3
from pathlib import Path

import pytest

from mma_model.config import SCHEMA_PATH
from mma_model.ingest import crosspromo, sherdog_scraper as sd
from mma_model.ratings import rate_fighters

FIXTURE = Path(__file__).parent / "fixtures" / "sherdog_fighter.html"


# --- parser -----------------------------------------------------------------

def test_parse_name_and_history():
    html = FIXTURE.read_text()
    assert sd.parse_fighter_name(html) == "Sample Fighter"
    fights = sd.parse_fight_history(html)
    assert len(fights) == 3
    f0 = fights[0]
    assert f0.result == "win" and f0.opponent == "Alpha Opponent"
    assert f0.date == "2024-01-28" and f0.method == "Decision"
    assert f0.method_detail == "Unanimous" and f0.rnd == 5


def test_parse_sherdog_date_formats():
    assert sd._parse_sherdog_date("Mar / 20 / 2005") == "2005-03-20"
    assert sd._parse_sherdog_date("June 8, 2023") == "2023-06-08"
    assert sd._parse_sherdog_date("") is None


def test_robots_check_is_called(monkeypatch):
    # can_fetch must gate live requests; simulate a disallow.
    monkeypatch.setattr(sd, "can_fetch", lambda url: False)
    with pytest.raises(PermissionError):
        sd.fetch("https://www.sherdog.com/fighter/x", use_cache=False)


# --- loader -----------------------------------------------------------------

def make_db() -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA_PATH.read_text())
    conn.execute("INSERT INTO sources(source) VALUES('ufc_mirror')")
    # canonical UFC fighter that a Sherdog record should link to
    conn.execute(
        "INSERT INTO fighters(fighter_id, name, source) VALUES('ufc:mcg','Conor McGregor','ufc_mirror')")
    conn.commit()
    return conn


def test_loader_excludes_nonmma_and_ufc():
    conn = make_db()
    recs = sd.parse_fight_history(FIXTURE.read_text())  # 1 ONE MMA, 1 PFL, 1 ONE grappling
    recs.append(sd.SherdogFight(  # a UFC bout that must be skipped (ufcstats owns it)
        fighter="Sample Fighter", opponent="Someone", result="win",
        event="UFC 250 - Nunes vs Spencer", date="2020-06-06",
        method="KO", method_detail="", rnd=1, time="2:00"))
    summary = crosspromo.load(conn, recs)
    assert summary["non_mma_excluded"] == 1   # the submission-grappling bout
    assert summary["ufc_skipped"] == 1
    assert summary["bouts_inserted"] == 2     # ONE + PFL only
    promos = {r["promotion"] for r in conn.execute("SELECT promotion FROM bouts")}
    assert promos == {"ONE", "PFL"}
    assert all(r["ruleset"] == "MMA" for r in conn.execute("SELECT ruleset FROM bouts"))


def test_loader_links_to_existing_ufc_fighter():
    conn = make_db()
    recs = [sd.SherdogFight(
        fighter="Conor McGregor", opponent="Some Cage Warriors Foe", result="win",
        event="Cage Warriors 51", date="2012-12-31",
        method="TKO", method_detail="Punches", rnd=1, time="4:10")]
    crosspromo.load(conn, recs)
    row = conn.execute("SELECT fighter1_id, fighter2_id, winner_id FROM bouts").fetchone()
    # the Sherdog 'Conor McGregor' resolved to the canonical UFC id, not a synthetic
    assert "ufc:mcg" in (row["fighter1_id"], row["fighter2_id"])
    assert row["winner_id"] == "ufc:mcg"


def test_loader_dedups_mirrored_rows():
    conn = make_db()
    recs = [
        sd.SherdogFight("Fighter A", "Fighter B", "win", "ONE 100", "2022-01-01",
                        "KO", "", 1, "1:00"),
        sd.SherdogFight("Fighter B", "Fighter A", "loss", "ONE 100", "2022-01-01",
                        "KO", "", 1, "1:00"),  # same bout from B's page
    ]
    summary = crosspromo.load(conn, recs)
    assert summary["bouts_inserted"] == 1
    win = conn.execute("SELECT winner_id, fighter1_id, fighter2_id FROM bouts").fetchone()
    a = conn.execute("SELECT fighter_id FROM fighters WHERE name='Fighter A'").fetchone()[0]
    assert win["winner_id"] == a


def test_crosspromotion_gives_debutant_a_real_prior():
    """A fighter with prior non-UFC wins must enter their UFC debut above 1500."""
    conn = make_db()
    # build a small non-UFC history: prospect beats two opponents in PFL
    recs = [
        sd.SherdogFight("Hot Prospect", "Jobber One", "win", "PFL 1", "2021-01-01",
                        "KO", "", 1, "1:00"),
        sd.SherdogFight("Hot Prospect", "Jobber Two", "win", "PFL 2", "2021-06-01",
                        "Submission", "", 1, "2:00"),
    ]
    crosspromo.load(conn, recs)
    # now add a UFC debut bout for the prospect (as ufcstats would)
    pid = conn.execute("SELECT fighter_id FROM fighters WHERE name='Hot Prospect'").fetchone()[0]
    conn.execute("INSERT INTO fighters(fighter_id,name,source) VALUES('ufc:dbt','UFC Debutant','ufc_mirror')")
    conn.execute(
        "INSERT INTO bouts(bout_id,date,promotion,fighter1_id,fighter2_id,winner_id,result)"
        " VALUES('u1','2022-01-01','UFC',?, 'ufc:dbt', ?, 'win')", (pid, pid))
    conn.commit()
    rate_fighters.run(conn)
    # the prediction for the UFC debut should reflect the prospect's prior wins
    p = conn.execute(
        "SELECT p_fighter1 FROM predictions WHERE bout_id='u1'").fetchone()[0]
    # fighter1 is the prospect (2-0 in PFL) vs a true 1500 debutant -> > 0.5
    assert p > 0.5
