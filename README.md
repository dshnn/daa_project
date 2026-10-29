# SENTINEL - Network Attack Simulator

## Prerequisites
- macOS (tested)
- Python 3.9+ 
- Modern web browser (Chrome/Firefox/Safari)

## Installation on macOS

1. Navigate to the project directory:
```bash
cd sentinel
```

2. Create and activate a virtual environment:
```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
```

3. Install dependencies:
```bash
pip install -r requirements.txt
```

## Exact commands to run the application

### Start Backend (Terminal 1)
```bash
cd backend
source .venv/bin/activate
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

The API will be available at `http://localhost:8000`. API docs at `http://localhost:8000/docs`.

### Start Frontend (Terminal 2)
```bash
cd frontend
python3 -m http.server 8080
```

Open your browser and go to `http://localhost:8080`

## How the frontend communicates with the backend
The frontend (vanilla JavaScript) makes real HTTP requests to `http://localhost:8000/api/*` using `fetch()`. All data operations (networks, algorithms, simulations) go through the FastAPI backend. No fake API responses are used.

## How SQLite stores the data
SQLite database is stored at `backend/sentinel.db` (or as set by `SENTINEL_DB_PATH`). Tables:
- `networks` - Saved network metadata
- `devices` - Nodes in each network
- `connections` - Edges with weights
- `simulations` - Simulation configurations and results
- `simulation_events` - Round-by-round events

Database is initialized automatically on backend startup.

## How each DAA algorithm works

### Breadth-First Search (BFS)
Traverses level by level using a queue. Visits all neighbors at current depth before going deeper. Time: O(V + E).

### Depth-First Search (DFS)  
Traverses depth-first using recursion. Explores as deep as possible along each branch. Time: O(V + E).

### Dijkstra's Algorithm
Finds shortest path from source in graphs with non-negative weights. Uses distance array and relaxes edges. Time: O((V + E) log V) with binary heap (simple O(V^2) implementation here).

### Prim's Algorithm
Finds Minimum Spanning Tree for connected undirected weighted graphs. Grows tree from start vertex by adding minimum weight edge connecting tree to outside. For disconnected graphs, returns MST of the connected component. Time: O(V^2).

## How the attack simulation works
This is a **synthetic educational simulation** - no real network scanning, no real attacks.

- States: Safe, Exposed, Compromised, Isolated, Recovered
- Discrete rounds: Compromised devices (not isolated) may propagate to connected non-isolated devices
- Propagation probability controls chance of compromise vs exposure
- Random seed ensures reproducibility
- Isolated devices do not propagate
- Strategies:
  1. **Initial-node isolation**: Isolates the initial compromised device
  2. **Greedy containment**: Isolates compromised devices with highest degree (documented heuristic)
  3. **Manual containment**: User selects specific devices to isolate
- Strategies can be compared with same starting graph and seed

## How to demonstrate the project to faculty
1. Show Dashboard - stats overview
2. Network Lab - create nodes, add edges with weights, save/load network
3. Algorithm Lab - run BFS, DFS, Dijkstra, Prim on saved network showing traversal/order/path/MST
4. Attack Simulator - configure simulation, try different strategies with same seed, show round-by-round propagation
5. Simulation History - reopen previous simulations with full details

## Running Tests
```bash
cd backend
source .venv/bin/activate
pytest tests/ -v
```
