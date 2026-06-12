"""Paper-ledger, live-odds, predict, and dashboard tests (in-memory DB)."""
import sqlite3

import pytest
from fastapi.testclient import TestClient

from mma_model.app import create_app
from mma_model.config import SCHEMA_PATH
from mma_model.ingest.live_odds import OddsAPIClient, OddsSnapshot, record_snapshot
from mma_model.models.predict import current_rating, predict_pair
from mma_model.paper import ledger as L
from mma_model.ratings import rate_fighters


def make_db(tmp_path) -> str:
    path = str(tmp_path / "t.sqlite")
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA_PATH.read_text())
    conn.execute("INSERT INTO sources(source) VALUES('demo')")
    fighters = [("fa", "Fighter A"), ("fb", "Fighter B"), ("fc", "Fighter C")]
    conn.executemany("INSERT INTO fighters(fighter_id,name,source) VALUES(?,?,'demo')", fighters)
    # historical decisive bouts so ratings + a settled bet exist
    hist = [
        ("h1", "2022-01-01", "fa", "fb", "fa"),
        ("h2", "2022-02-01", "fa", "fc", "fa"),
        ("h3", "2022-03-01", "fb", "fc", "fc"),
    ]
    conn.executemany(
        "INSERT INTO bouts(bout_id,date,promotion,fighter1_id,fighter2_id,winner_id,"
        "result,ruleset,source) VALUES(?,?, 'UFC', ?,?,?, 'win','MMA','demo')", hist)
    # an upcoming bout (no result) for the dashboard
    conn.execute(
        "INSERT INTO bouts(bout_id,date,promotion,fighter1_id,fighter2_id,result,"
        "ruleset,source) VALUES('up1','2030-01-01','UFC','fa','fb',NULL,'MMA','demo')")
    conn.commit()
    rate_fighters.run(conn)
    conn.close()
    return path


# --- predict ----------------------------------------------------------------

def test_predict_uses_current_rating(tmp_path):
    path = make_db(tmp_path)
    conn = sqlite3.connect(path); conn.row_factory = sqlite3.Row
    # fa won twice -> higher rating than fb
    assert current_rating(conn, "fa").rating > current_rating(conn, "fb").rating
    pred = predict_pair(conn, "fa", "fb")
    assert pred.p_fighter1 > 0.5 and pred.model == "tier1_glicko"
    # unknown fighter falls back to the prior (1500) -> ~0.5 vs another debutant
    assert predict_pair(conn, "ghost1", "ghost2").p_fighter1 == pytest.approx(0.5, abs=1e-9)
    conn.close()


def test_resultless_bouts_not_rated(tmp_path):
    path = make_db(tmp_path)
    conn = sqlite3.connect(path); conn.row_factory = sqlite3.Row
    # the upcoming bout must not appear in ratings_history or predictions
    assert conn.execute(
        "SELECT COUNT(*) FROM ratings_history WHERE bout_id='up1'").fetchone()[0] == 0
    assert conn.execute(
        "SELECT COUNT(*) FROM predictions WHERE bout_id='up1'").fetchone()[0] == 0
    conn.close()


# --- ledger -----------------------------------------------------------------

def test_place_settle_win_and_loss(tmp_path):
    path = make_db(tmp_path)
    conn = sqlite3.connect(path); conn.row_factory = sqlite3.Row
    # bet on the winner (fa) of h1 at 2.0 and on the loser (fb) at 2.5
    L.place_bet(conn, "h1", "fa", stake=10, odds_decimal=2.0, p_model=0.6)
    L.place_bet(conn, "h1", "fb", stake=10, odds_decimal=2.5, p_model=0.4)
    out = L.settle(conn)
    assert out == {"settled": 2, "won": 1, "lost": 1, "void": 0}
    s = L.summary(conn)
    # +10 on the winner, -10 on the loser -> net 0, ROI 0 over 20 staked
    assert s["total_pnl"] == pytest.approx(0.0)
    assert s["roi"] == pytest.approx(0.0)
    conn.close()


def test_suggest_only_bets_positive_edge(tmp_path):
    path = make_db(tmp_path)
    conn = sqlite3.connect(path); conn.row_factory = sqlite3.Row
    # p=0.6 at 2.0 -> edge +0.2 -> bet placed
    assert L.suggest_and_place(conn, "up1", "fa", 0.6, 2.0) is not None
    # p=0.4 at 2.0 -> edge -0.2 -> no bet
    assert L.suggest_and_place(conn, "up1", "fb", 0.4, 2.0) is None
    conn.close()


def test_clv_computation(tmp_path):
    path = make_db(tmp_path)
    conn = sqlite3.connect(path); conn.row_factory = sqlite3.Row
    L.place_bet(conn, "h1", "fa", stake=10, odds_decimal=2.10, p_model=0.55)
    L.record_closing(conn, "h1", "fa", closing_odds=2.00)
    row = conn.execute("SELECT clv FROM paper_bets").fetchone()
    # took 2.10, closed 2.00 -> beat the close by 5%
    assert row["clv"] == pytest.approx(2.10 / 2.00 - 1.0)
    conn.close()


# --- live odds --------------------------------------------------------------

def test_record_snapshot_and_api_client(tmp_path):
    path = make_db(tmp_path)
    conn = sqlite3.connect(path); conn.row_factory = sqlite3.Row
    n = record_snapshot(conn, [
        OddsSnapshot("up1", "fa", "book", 1.8, "2030-01-01T00:00:00Z"),
        OddsSnapshot("up1", "fb", "book", 2.1, "2030-01-01T00:00:00Z", is_closing=True),
    ])
    assert n == 2
    assert conn.execute("SELECT COUNT(*) FROM odds WHERE bout_id='up1'").fetchone()[0] == 2
    # API client with no key is unavailable and refuses to fetch
    client = OddsAPIClient(api_key=None)
    assert not client.available()
    with pytest.raises(RuntimeError):
        client.fetch_mma()
    conn.close()


# --- dashboard --------------------------------------------------------------

def test_dashboard_endpoints(tmp_path):
    path = make_db(tmp_path)
    conn = sqlite3.connect(path); conn.row_factory = sqlite3.Row
    record_snapshot(conn, [
        OddsSnapshot("up1", "fa", "book", 3.0, "2030-01-01T00:00:00Z"),  # juicy odds
        OddsSnapshot("up1", "fb", "book", 1.4, "2030-01-01T00:00:00Z"),
    ])
    L.place_bet(conn, "h1", "fa", 10, 2.0, 0.6)
    L.settle(conn)
    conn.close()

    client = TestClient(create_app(path))
    assert client.get("/").status_code == 200
    up = client.get("/api/upcoming").json()["bouts"]
    assert len(up) == 1 and up[0]["bout_id"] == "up1"
    # fa is the favourite by rating and is offered 3.0 -> big edge -> flagged
    fa_side = next(s for s in up[0]["sides"] if s["fighter_id"] == "fa")
    assert fa_side["bet"] is True and fa_side["edge"] > 0
    prof = client.get("/api/fighter/fa").json()
    assert prof["fighter"]["name"] == "Fighter A" and len(prof["history"]) == 2
    led = client.get("/api/ledger").json()
    assert led["summary"]["n_bets"] == 1
    assert client.get("/fighter/fa").status_code == 200
    assert client.get("/ledger").status_code == 200
