"""FastAPI paper-trading dashboard.

Pages: upcoming cards (model prob vs current odds, flagged edges), fighter
profile (rating history), and the paper-bet ledger (running ROI + CLV). JSON
endpoints mirror each page so the app is testable headless via TestClient.

Run:  python -m scripts.run_dashboard   (uvicorn on :8000)
"""
from __future__ import annotations

import html
import sqlite3
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import HTMLResponse

from ..config import DB_PATH
from ..paper import ledger as L
from . import queries as Q

_STYLE = """
<style>
 body{font-family:system-ui,sans-serif;margin:2rem;max-width:1000px;color:#1c1c1c}
 h1{font-size:1.4rem} a{color:#0b6} table{border-collapse:collapse;width:100%}
 th,td{border:1px solid #ddd;padding:.4rem .6rem;text-align:left;font-size:.9rem}
 th{background:#f4f4f4} .edge{background:#dff7e3;font-weight:600}
 .neg{color:#b00} .pos{color:#070} .muted{color:#888}
 nav a{margin-right:1rem} .pill{padding:.1rem .4rem;border-radius:.3rem;background:#eee}
</style>
"""


def _page(title: str, body: str) -> str:
    nav = ('<nav><a href="/">Upcoming</a><a href="/ledger">Paper ledger</a>'
           '<span class="muted">paper-trading only — no real money</span></nav>')
    return f"<!doctype html><meta charset=utf-8><title>{html.escape(title)}</title>" \
           f"{_STYLE}<h1>{html.escape(title)}</h1>{nav}<hr>{body}"


def create_app(db_path: str | Path = DB_PATH) -> FastAPI:
    app = FastAPI(title="MMA model — paper trading")
    db_path = str(db_path)

    def conn() -> sqlite3.Connection:
        c = sqlite3.connect(db_path)
        c.row_factory = sqlite3.Row
        return c

    # --- JSON API (the testable surface) --------------------------------
    @app.get("/api/upcoming")
    def api_upcoming():
        c = conn()
        try:
            return {"bouts": Q.upcoming_bouts(c)}
        finally:
            c.close()

    @app.get("/api/fighter/{fighter_id}")
    def api_fighter(fighter_id: str):
        c = conn()
        try:
            return Q.fighter_profile(c, fighter_id)
        finally:
            c.close()

    @app.get("/api/ledger")
    def api_ledger():
        c = conn()
        try:
            return {"summary": L.summary(c), "bets": Q.ledger_view(c)}
        finally:
            c.close()

    # --- HTML pages -----------------------------------------------------
    @app.get("/", response_class=HTMLResponse)
    def index():
        c = conn()
        try:
            bouts = Q.upcoming_bouts(c)
        finally:
            c.close()
        if not bouts:
            return _page("Upcoming cards",
                         "<p class=muted>No upcoming bouts staged. Seed a demo "
                         "card with <code>python -m scripts.seed_demo_card</code> "
                         "or load real odds via the live-odds ingest.</p>")
        rows = []
        for b in bouts:
            for s in b["sides"]:
                cls = ' class="edge"' if s["bet"] else ""
                odds = f'{s["odds"]:.2f}' if s["odds"] else "—"
                edge = f'{s["edge"]*100:+.1f}%' if s["edge"] is not None else "—"
                rows.append(
                    f"<tr{cls}><td>{html.escape(b['date'] or '?')}</td>"
                    f"<td><span class=pill>{html.escape(b['promotion'] or '')}</span></td>"
                    f'<td><a href="/fighter/{s["fighter_id"]}">'
                    f"{html.escape(s['name'])}</a></td>"
                    f"<td>{s['p_model']*100:.1f}%</td><td>{odds}</td>"
                    f"<td>{edge}</td><td>{'BET' if s['bet'] else ''}</td></tr>")
        table = ("<table><tr><th>Date</th><th>Promo</th><th>Fighter</th>"
                 "<th>Model P</th><th>Odds</th><th>Edge</th><th></th></tr>"
                 + "".join(rows) + "</table>"
                 "<p class=muted>Model = Tier 1 (Glicko-2). Edge = model P × "
                 "odds − 1; rows flagged when edge &gt; 5%.</p>")
        return _page("Upcoming cards", table)

    @app.get("/fighter/{fighter_id}", response_class=HTMLResponse)
    def fighter(fighter_id: str):
        c = conn()
        try:
            prof = Q.fighter_profile(c, fighter_id)
        finally:
            c.close()
        if not prof["fighter"]:
            return _page("Unknown fighter", "<p>Not found.</p>")
        f = prof["fighter"]
        cur = prof["current"]
        rec = prof["record"]
        spark = _sparkline([h["rating"] for h in prof["history"]])
        cur_txt = (f"{cur['rating']:.0f} (RD {cur['rd']:.0f})" if cur else "unrated")
        body = (
            f"<p><b>Record (recorded):</b> {rec['wins']}–{rec['losses']} &nbsp; "
            f"<b>Current rating:</b> {cur_txt}</p>"
            f"<p><b>Rating history</b> ({len(prof['history'])} bouts):<br>{spark}</p>"
        )
        return _page(f["name"], body)

    @app.get("/ledger", response_class=HTMLResponse)
    def ledger():
        c = conn()
        try:
            s = L.summary(c)
            bets = Q.ledger_view(c)
        finally:
            c.close()
        roi_cls = "pos" if s["roi"] >= 0 else "neg"
        clv = (f"{s['avg_clv']*100:+.2f}% avg ({s['pct_positive_clv']*100:.0f}% "
               f"positive, n={s['n_clv']})" if s["avg_clv"] is not None else "n/a")
        head = (
            f"<p><b>Bets:</b> {s['n_bets']} {s['by_status']} &nbsp; "
            f"<b>Staked:</b> {s['total_staked']} &nbsp; "
            f"<b>P/L:</b> <span class={roi_cls}>{s['total_pnl']:+.2f}</span> &nbsp; "
            f"<b>ROI:</b> <span class={roi_cls}>{s['roi']*100:+.1f}%</span> "
            f"(95% CI [{s['roi_ci'][0]*100:+.1f}%, {s['roi_ci'][1]*100:+.1f}%]) &nbsp; "
            f"<b>CLV:</b> {clv}</p>"
            "<p class=muted>A positive ROI whose CI includes 0 is noise. CLV is "
            "the earlier signal of edge.</p>"
        )
        if not bets:
            return _page("Paper ledger", head + "<p class=muted>No bets yet.</p>")
        rows = []
        for b in bets:
            pnl = b["pnl"]
            pnl_txt = (f'<span class="{"pos" if pnl>=0 else "neg"}">{pnl:+.2f}</span>'
                       if pnl is not None else "—")
            clvv = f"{b['clv']*100:+.1f}%" if b["clv"] is not None else "—"
            rows.append(
                f"<tr><td>{html.escape((b['captured_at'] or '')[:10])}</td>"
                f"<td>{html.escape(b['fighter_name'] or b['fighter_id'] or '')}</td>"
                f"<td>{b['odds_decimal']:.2f}</td><td>{b['stake']:.2f}</td>"
                f"<td>{(b['edge'] or 0)*100:+.1f}%</td>"
                f"<td>{html.escape(b['status'])}</td><td>{pnl_txt}</td>"
                f"<td>{clvv}</td></tr>")
        table = ("<table><tr><th>Date</th><th>Side</th><th>Odds</th><th>Stake</th>"
                 "<th>Edge</th><th>Status</th><th>P/L</th><th>CLV</th></tr>"
                 + "".join(rows) + "</table>")
        return _page("Paper ledger", head + table)

    return app


def _sparkline(values: list[float]) -> str:
    """Tiny inline unicode sparkline of a rating series."""
    if not values:
        return "<span class=muted>no rated bouts</span>"
    blocks = "▁▂▃▄▅▆▇█"
    lo, hi = min(values), max(values)
    rng = (hi - lo) or 1.0
    chars = "".join(blocks[min(7, int((v - lo) / rng * 7))] for v in values)
    return (f"<span style='font-size:1.2rem;letter-spacing:1px'>{chars}</span> "
            f"<span class=muted>{lo:.0f}→{hi:.0f}</span>")
