from __future__ import annotations

import json
from typing import Any, Dict, List

from fastapi import APIRouter, HTTPException

from . import algorithms, database, simulator
from .models import Graph

router = APIRouter()


@router.get("/health")
def health():
    return {"status": "ok"}


# Networks
@router.get("/networks")
def list_networks():
    conn = database.get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM networks ORDER BY id DESC")
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows


@router.post("/networks")
def create_network(payload: Dict[str, Any]):
    name = payload.get("name") or "Untitled Network"
    description = payload.get("description")
    conn = database.get_connection()
    cur = conn.cursor()
    cur.execute("INSERT INTO networks (name, description) VALUES (?, ?)", (name, description))
    nid = cur.lastrowid
    conn.commit()
    conn.close()
    return {"id": nid, "name": name, "description": description}


@router.get("/networks/{network_id}")
def get_network(network_id: int):
    conn = database.get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM networks WHERE id = ?", (network_id,))
    net = cur.fetchone()
    if not net:
        conn.close()
        raise HTTPException(status_code=404, detail="Network not found")
    netd = dict(net)
    cur.execute("SELECT * FROM devices WHERE network_id = ?", (network_id,))
    devs = [dict(r) for r in cur.fetchall()]
    cur.execute("SELECT * FROM connections WHERE network_id = ?", (network_id,))
    conns = [dict(r) for r in cur.fetchall()]
    conn.close()
    return {"network": netd, "devices": devs, "connections": conns}


@router.delete("/networks/{network_id}")
def delete_network(network_id: int):
    conn = database.get_connection()
    cur = conn.cursor()
    cur.execute("DELETE FROM networks WHERE id = ?", (network_id,))
    conn.commit()
    conn.close()
    return {"status": "deleted"}


@router.post("/networks/{network_id}/graph")
def save_graph(network_id: int, payload: Dict[str, Any]):
    devices = payload.get("devices", [])
    connections = payload.get("connections", [])
    conn = database.get_connection()
    cur = conn.cursor()
    # clear existing
    cur.execute("DELETE FROM devices WHERE network_id = ?", (network_id,))
    cur.execute("DELETE FROM connections WHERE network_id = ?", (network_id,))
    # insert devices
    id_map: Dict[str, int] = {}
    for d in devices:
        cur.execute("INSERT INTO devices (network_id, label, x, y) VALUES (?, ?, ?, ?)", (network_id, d.get("label"), d.get("x"), d.get("y")))
        new_id = cur.lastrowid
        if d.get("id") is not None:
            id_map[str(d["id"])] = new_id
        else:
            id_map[str(len(id_map))] = new_id
    # insert connections
    for c in connections:
        s = c.get("source")
        t = c.get("target")
        # try map
        if isinstance(s, str) and s in id_map:
            s_id = id_map[s]
        elif isinstance(s, int):
            s_id = s
        else:
            s_id = None
        if isinstance(t, str) and t in id_map:
            t_id = id_map[t]
        elif isinstance(t, int):
            t_id = t
        else:
            t_id = None
        if s_id is None or t_id is None:
            continue
        cur.execute("INSERT INTO connections (network_id, source_id, target_id, weight) VALUES (?, ?, ?, ?)", (network_id, s_id, t_id, float(c.get("weight", 1.0))))
    conn.commit()
    conn.close()
    return {"status": "saved"}


# Algorithms
@router.post("/algorithms/{algo}")
def run_algo(algo: str, payload: Dict[str, Any]):
    network_id = payload.get("network_id")
    if network_id is None:
        raise HTTPException(status_code=400, detail="network_id required")
    conn = database.get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM devices WHERE network_id = ?", (network_id,))
    devs = [dict(r) for r in cur.fetchall()]
    cur.execute("SELECT * FROM connections WHERE network_id = ?", (network_id,))
    conns = [dict(r) for r in cur.fetchall()]
    conn.close()
    g = Graph.from_db(devs, conns)
    try:
        if algo == "bfs":
            start = int(payload.get("start"))
            res = algorithms.Algorithms.bfs(g, start)
            return res
        if algo == "dfs":
            start = int(payload.get("start"))
            res = algorithms.Algorithms.dfs(g, start)
            return res
        if algo == "dijkstra":
            start = int(payload.get("start"))
            end = int(payload.get("end"))
            res = algorithms.Algorithms.dijkstra(g, start, end)
            return res
        if algo == "prim":
            res = algorithms.Algorithms.prim(g)
            return res
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
    raise HTTPException(status_code=404, detail="Unknown algorithm")


# Simulations
@router.post("/simulations")
def create_simulation(payload: Dict[str, Any]):
    network_id = int(payload["network_id"])
    initial = int(payload.get("initial_compromised_device_id"))
    prob = float(payload.get("propagation_probability", 0.5))
    seed = payload.get("seed")
    if seed is not None:
        seed = int(seed)
    strategy = payload.get("strategy", "none")
    manual = payload.get("manual_isolated") or []
    conn = database.get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM devices WHERE network_id = ?", (network_id,))
    devs = [dict(r) for r in cur.fetchall()]
    cur.execute("SELECT * FROM connections WHERE network_id = ?", (network_id,))
    conns = [dict(r) for r in cur.fetchall()]
    conn.close()
    g = Graph.from_db(devs, conns)
    sim = simulator.Simulation(g, initial, prob, seed)
    sim.apply_containment(strategy, manual)
    res = sim.run()
    # save
    conn = database.get_connection()
    cur = conn.cursor()
    cur.execute(
        "INSERT INTO simulations (network_id, initial_compromised_device_id, propagation_probability, seed, strategy, rounds, num_compromised, num_isolated, execution_time_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (network_id, initial, prob, seed, strategy, res["rounds"], res["num_compromised"], res["num_isolated"], res["execution_time_ms"]),
    )
    sim_id = cur.lastrowid
    for ev in res["events"]:
        cur.execute(
            "INSERT INTO simulation_events (simulation_id, round_num, device_id, new_state, reason) VALUES (?, ?, ?, ?, ?)",
            (sim_id, ev["round_num"], ev["device_id"], ev["new_state"], ev["reason"]),
        )
    conn.commit()
    conn.close()
    return {"simulation_id": sim_id, **res}


@router.get("/simulations")
def list_simulations():
    conn = database.get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM simulations ORDER BY id DESC")
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows


@router.get("/simulations/{sim_id}")
def get_simulation(sim_id: int):
    conn = database.get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM simulations WHERE id = ?", (sim_id,))
    sim = cur.fetchone()
    if not sim:
        conn.close()
        raise HTTPException(status_code=404, detail="Simulation not found")
    simd = dict(sim)
    cur.execute("SELECT * FROM simulation_events WHERE simulation_id = ? ORDER BY round_num, id", (sim_id,))
    evs = [dict(r) for r in cur.fetchall()]
    conn.close()
    return {"simulation": simd, "events": evs}
