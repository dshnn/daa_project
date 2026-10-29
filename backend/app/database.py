from __future__ import annotations

import os
import sqlite3
from pathlib import Path
from typing import Any

DB_PATH = Path(os.environ.get("SENTINEL_DB_PATH", Path(__file__).resolve().parent / "sentinel.db"))


def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON;")
    return conn


def init_db() -> None:
    conn = get_connection()
    cur = conn.cursor()
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS networks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            description TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        """
    )
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS devices (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            network_id INTEGER NOT NULL,
            label TEXT NOT NULL,
            x REAL,
            y REAL,
            FOREIGN KEY(network_id) REFERENCES networks(id) ON DELETE CASCADE
        );
        """
    )
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS connections (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            network_id INTEGER NOT NULL,
            source_id INTEGER NOT NULL,
            target_id INTEGER NOT NULL,
            weight REAL DEFAULT 1.0,
            FOREIGN KEY(network_id) REFERENCES networks(id) ON DELETE CASCADE,
            FOREIGN KEY(source_id) REFERENCES devices(id) ON DELETE CASCADE,
            FOREIGN KEY(target_id) REFERENCES devices(id) ON DELETE CASCADE
        );
        """
    )
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS simulations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            network_id INTEGER NOT NULL,
            initial_compromised_device_id INTEGER,
            propagation_probability REAL DEFAULT 0.5,
            seed INTEGER,
            strategy TEXT,
            rounds INTEGER DEFAULT 0,
            num_compromised INTEGER DEFAULT 0,
            num_isolated INTEGER DEFAULT 0,
            execution_time_ms REAL DEFAULT 0,
            note TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY(network_id) REFERENCES networks(id) ON DELETE CASCADE
        );
        """
    )
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS simulation_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            simulation_id INTEGER NOT NULL,
            round_num INTEGER NOT NULL,
            device_id INTEGER NOT NULL,
            new_state TEXT NOT NULL,
            reason TEXT,
            FOREIGN KEY(simulation_id) REFERENCES simulations(id) ON DELETE CASCADE
        );
        """
    )
    conn.commit()
    conn.close()
