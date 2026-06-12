-- MMA model SQLite schema.
-- Entity-resolution-first design: one canonical `fighters` table keyed by a
-- stable id, with a `fighter_aliases` table mapping name variants across
-- sources to that canonical id. Multi-promotion ready via `promotion` columns.

PRAGMA foreign_keys = ON;

-- Source-of-truth registry so every row can be traced back to its origin and
-- ingest run (incremental updates compare against this).
CREATE TABLE IF NOT EXISTS sources (
    source        TEXT PRIMARY KEY,   -- e.g. 'ufcstats', 'ufc_mirror', 'tapology'
    description   TEXT,
    last_ingested TEXT                 -- ISO timestamp of last successful load
);

-- Canonical fighters. id is the ufcstats fighter hash where available,
-- otherwise a deterministic 'syn:<normalized-name>' synthetic id.
CREATE TABLE IF NOT EXISTS fighters (
    fighter_id  TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    nickname    TEXT,
    height_cm   REAL,
    reach_cm    REAL,
    weight_lbs  REAL,
    stance      TEXT,
    dob         TEXT,                 -- ISO date
    source      TEXT REFERENCES sources(source),
    source_url  TEXT
);
CREATE INDEX IF NOT EXISTS idx_fighters_name ON fighters(name);

-- Name variants across sources -> canonical fighter_id.
CREATE TABLE IF NOT EXISTS fighter_aliases (
    alias       TEXT NOT NULL,
    norm_alias  TEXT NOT NULL,        -- normalized form used for matching
    fighter_id  TEXT NOT NULL REFERENCES fighters(fighter_id),
    source      TEXT,
    PRIMARY KEY (norm_alias, fighter_id)
);
CREATE INDEX IF NOT EXISTS idx_aliases_norm ON fighter_aliases(norm_alias);

CREATE TABLE IF NOT EXISTS events (
    event_id    TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    date        TEXT,                 -- ISO date
    location    TEXT,
    promotion   TEXT DEFAULT 'UFC',
    source      TEXT REFERENCES sources(source),
    source_url  TEXT
);
CREATE INDEX IF NOT EXISTS idx_events_date ON events(date);

-- One row per bout. fighter1/fighter2 ordering follows the source BOUT string.
-- winner_id is NULL for draws / no-contests; `outcome` retains the raw code.
CREATE TABLE IF NOT EXISTS bouts (
    bout_id          TEXT PRIMARY KEY,
    event_id         TEXT REFERENCES events(event_id),
    date             TEXT,            -- denormalized from event for as-of queries
    promotion        TEXT DEFAULT 'UFC',
    fighter1_id      TEXT REFERENCES fighters(fighter_id),
    fighter2_id      TEXT REFERENCES fighters(fighter_id),
    winner_id        TEXT REFERENCES fighters(fighter_id),
    outcome          TEXT,            -- W/L, L/W, D/D, NC/NC
    result           TEXT,            -- normalized: win / draw / nc
    weight_class     TEXT,
    is_title         INTEGER DEFAULT 0,
    method           TEXT,            -- KO/TKO, Submission, Decision, ...
    method_detail    TEXT,
    round            INTEGER,
    time             TEXT,
    scheduled_rounds INTEGER,         -- 3 or 5
    referee          TEXT,
    ruleset          TEXT DEFAULT 'MMA',  -- ONE mixes MT/KB/grappling; ingest MMA only
    source           TEXT REFERENCES sources(source),
    source_url       TEXT
);
CREATE INDEX IF NOT EXISTS idx_bouts_date ON bouts(date);
CREATE INDEX IF NOT EXISTS idx_bouts_f1 ON bouts(fighter1_id);
CREATE INDEX IF NOT EXISTS idx_bouts_f2 ON bouts(fighter2_id);

-- Per-fighter, per-round performance stats (UFC only; Tier 2 feature source).
-- round 0 is reserved for a fight-total roll-up.
CREATE TABLE IF NOT EXISTS bout_stats (
    bout_id          TEXT REFERENCES bouts(bout_id),
    fighter_id       TEXT REFERENCES fighters(fighter_id),
    round            INTEGER,
    kd               INTEGER,
    sig_str_landed   INTEGER,
    sig_str_att      INTEGER,
    total_str_landed INTEGER,
    total_str_att    INTEGER,
    td_landed        INTEGER,
    td_att           INTEGER,
    sub_att          INTEGER,
    rev              INTEGER,
    ctrl_sec         INTEGER,
    head_landed      INTEGER,
    body_landed      INTEGER,
    leg_landed       INTEGER,
    PRIMARY KEY (bout_id, fighter_id, round)
);

-- Historical / live bookmaker odds (populated in build step 3+).
CREATE TABLE IF NOT EXISTS odds (
    bout_id      TEXT REFERENCES bouts(bout_id),
    fighter_id   TEXT REFERENCES fighters(fighter_id),
    book         TEXT,
    decimal_odds REAL,
    captured_at  TEXT,               -- ISO timestamp
    is_closing   INTEGER DEFAULT 0,
    source       TEXT,
    PRIMARY KEY (bout_id, fighter_id, book, captured_at)
);

-- Glicko-2 rating snapshot AFTER each bout (output of the rating engine).
CREATE TABLE IF NOT EXISTS ratings_history (
    fighter_id  TEXT REFERENCES fighters(fighter_id),
    bout_id     TEXT REFERENCES bouts(bout_id),
    date        TEXT,
    rating      REAL,
    rd          REAL,
    vol         REAL,
    PRIMARY KEY (fighter_id, bout_id)
);
CREATE INDEX IF NOT EXISTS idx_ratings_fighter ON ratings_history(fighter_id);
CREATE INDEX IF NOT EXISTS idx_ratings_date ON ratings_history(date);

-- Pre-fight model predictions, captured during the chronological rating replay
-- (strictly walk-forward: computed from ratings BEFORE the bout is scored).
CREATE TABLE IF NOT EXISTS predictions (
    bout_id     TEXT REFERENCES bouts(bout_id),
    model       TEXT,                -- 'tier1_glicko', later 'tier2_gbm'
    p_fighter1  REAL,                -- P(fighter1 wins)
    n_prior1    INTEGER,             -- fighter1's rated bouts before this one
    n_prior2    INTEGER,
    PRIMARY KEY (bout_id, model)
);

-- Rows that could not be resolved to a canonical fighter (audit / improvement).
CREATE TABLE IF NOT EXISTS unmatched_log (
    raw_name  TEXT,
    context   TEXT,
    source    TEXT,
    note      TEXT
);
