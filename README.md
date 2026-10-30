# SENTINEL - Network Attack Simulator (JDBC + Hibernate Edition)

SENTINEL is an educational network-security simulator with a Java backend and a
vanilla JavaScript frontend. It lets you build a network graph, run classic
Design and Analysis of Algorithms (DAA) algorithms on it, and simulate how a
synthetic attack could spread through the network under different containment
strategies.

This repository is the **Java port** of the original Python/FastAPI SENTINEL
project. The frontend, API routes, JSON payloads, and SQLite schema are kept
compatible with the original, while the backend has been rewritten in Java 17
using:

- **JDBC** for schema creation and the bulk graph-save operation
- **Hibernate ORM** for all normal entity persistence and queries
- **`com.sun.net.httpserver`** for the embedded HTTP API
- **Jackson** for JSON serialization/deserialization
- **SQLite** as the database

> **Educational simulation only.** SENTINEL does not scan, probe, or attack real
> networks. All "attacks" are random-number-driven state changes on an
> in-memory graph.

---

## Table of Contents

1. [What the Project Does](#what-the-project-does)
2. [Features](#features)
3. [Technology Stack](#technology-stack)
4. [Project Structure](#project-structure)
5. [How the System Works](#how-the-system-works)
6. [Data Model and SQLite Schema](#data-model-and-sqlite-schema)
7. [How JDBC Is Used](#how-jdbc-is-used)
8. [How Hibernate Is Used](#how-hibernate-is-used)
9. [JDBC vs Hibernate: Division of Responsibility](#jdbc-vs-hibernate-division-of-responsibility)
10. [DAA Algorithms](#daa-algorithms)
11. [How the Attack Simulation Works](#how-the-attack-simulation-works)
12. [API Endpoints](#api-endpoints)
13. [Build and Run](#build-and-run)
14. [Configuration](#configuration)
15. [Running the Tests](#running-the-tests)
16. [Demonstration Walkthrough](#demonstration-walkthrough)
17. [Limitations](#limitations)

---

## What the Project Does

The application has two main parts:

1. **Network modeling and algorithms**
   - Create devices (graph nodes) and weighted connections (graph edges).
   - Save and reload networks from SQLite.
   - Run BFS, DFS, Dijkstra, and Prim on a saved network.

2. **Synthetic attack simulation**
   - Choose a starting compromised device.
   - Configure a propagation probability and optional random seed.
   - Apply a containment strategy.
   - Watch the infection spread round by round.
   - Persist the simulation and every event so it can be reviewed later.

The frontend is a single-page application with five pages: Dashboard, Network
Lab, Attack Simulator, Algorithm Lab, and Simulation History.

---

## Features

- Visual graph editor built with **Cytoscape.js**
- Saved networks with devices, positions, connections, and weights
- BFS and DFS traversal order
- Dijkstra shortest path with total cost
- Prim minimum spanning tree, including disconnected-graph handling
- Reproducible simulations through an optional random seed
- Containment strategies:
  - none
  - initial-node isolation
  - greedy containment (isolate highest-degree compromised nodes)
  - manual containment
- Round-by-round event log persisted in the database
- Simulation history and detail view
- CORS-enabled API so the static frontend can call the backend directly

---

## Technology Stack

| Layer | Technology |
| --- | --- |
| Language | Java 17 |
| HTTP server | JDK `com.sun.net.httpserver.HttpServer` |
| Persistence | SQLite via JDBC and Hibernate ORM 6.6 |
| SQLite driver | `org.xerial:sqlite-jdbc` |
| Hibernate dialect | `org.hibernate.community.dialect.SQLiteDialect` |
| JSON | Jackson (`jackson-databind`) |
| Logging | SLF4J Simple |
| Build | Maven (wrapper included) |
| Tests | JUnit 5 |
| Frontend | HTML, CSS, vanilla JavaScript, Cytoscape.js |

---

## Project Structure

```text
daa_project_jdbc_hibernate/
├── backend/
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   └── src/
│       ├── main/java/com/sentinel/
│       │   ├── Main.java                     # Entry point, embedded HTTP server
│       │   ├── db/Database.java              # JDBC schema + Hibernate SessionFactory
│       │   ├── model/
│       │   │   ├── NetworkEntity.java
│       │   │   ├── DeviceEntity.java
│       │   │   ├── ConnectionEntity.java
│       │   │   ├── SimulationEntity.java
│       │   │   └── SimulationEventEntity.java
│       │   ├── algorithm/
│       │   │   ├── Graph.java                # In-memory undirected weighted graph
│       │   │   └── Algorithms.java           # BFS, DFS, Dijkstra, Prim
│       │   ├── simulation/Simulation.java    # Attack propagation engine
│       │   └── server/
│       │       ├── ApiHandler.java           # Routing, validation, JDBC/Hibernate calls
│       │       ├── ApiException.java         # HTTP error mapping
│       │       └── Json.java                 # Jackson ObjectMapper (snake_case)
│       └── test/java/com/sentinel/
│           ├── SentinelTest.java             # Algorithm and simulation tests
│           └── DatabasePersistenceTest.java  # JDBC schema and Hibernate tests
├── frontend/
│   ├── index.html
│   ├── app.js
│   └── style.css
├── structure.txt
├── LICENSE
└── README.md
```

---

## How the System Works

### High-level request flow

```text
Browser (frontend/app.js)
        |
        |  fetch() JSON over HTTP, CORS
        v
Main.java -> HttpServer -> ApiHandler
                              |
              +---------------+----------------+
              |                                |
              v                                v
       Hibernate ORM                      Plain JDBC
   (networks, devices,                 (schema creation,
    connections, simulations,          bulk graph save,
    simulation events)                 prepared statements)
              |                                |
              +---------------+----------------+
                              |
                              v
                     SQLite: sentinel.db
```

### Startup sequence

1. `Main.main()` reads `HOST` and `PORT` (defaults `127.0.0.1:8000`).
2. `Database.init()` runs.
   - Resolves the database path (`SENTINEL_DB_PATH` or `sentinel.db`).
   - Creates the parent directory if needed.
   - Builds the JDBC URL.
   - Creates all tables with **JDBC DDL** if they do not already exist.
   - Configures and builds a Hibernate `SessionFactory`.
3. An `HttpServer` is created and `/api` is bound to `ApiHandler`.
4. A fixed thread pool of 8 handles requests.
5. A shutdown hook stops the server and closes the Hibernate factory.

### Network Lab flow

1. The user adds devices and connections in Cytoscape.js.
2. On save, the frontend:
   - `POST /api/networks` to create the network metadata.
   - `POST /api/networks/{id}/graph` to replace the graph contents.
3. The graph endpoint is handled with **plain JDBC**:
   - Deletes existing connections and devices for that network.
   - Inserts devices one by one, capturing generated IDs.
   - Maps temporary frontend IDs to real database IDs.
   - Inserts connections using the mapped IDs.
   - Commits the whole operation as one transaction, rolling back on error.

### Algorithm Lab flow

1. The frontend sends `POST /api/algorithms/{name}` with `network_id` and the
   required node IDs.
2. `ApiHandler.loadGraph()` uses **Hibernate** to fetch devices and connections.
3. `Graph.fromDb()` converts the entities into an in-memory adjacency structure.
4. `Algorithms` runs the requested algorithm and returns a JSON result.
5. Results are returned to the browser for display.

### Attack Simulator flow

1. The frontend sends `POST /api/simulations` with:
   - `network_id`
   - `initial_compromised_device_id`
   - `propagation_probability`
   - `seed`
   - `strategy`
   - `manual_isolated`
2. The backend loads the graph with Hibernate and creates a `Simulation`.
3. The containment strategy is applied.
4. `Simulation.run()` advances discrete rounds until no new state changes occur
   or the 50-round limit is reached.
5. A `SimulationEntity` and its `SimulationEventEntity` records are persisted
   with **Hibernate** in a single transaction.
6. The response contains the simulation ID, counts, final states, and events.

### Simulation History flow

- `GET /api/simulations` lists saved simulations with Hibernate HQL.
- `GET /api/simulations/{id}` loads the simulation and its ordered events.
- The frontend reopens the saved details without rerunning the simulation.

### JSON handling

`Json.java` configures Jackson with `SNAKE_CASE` property naming. This is why
Java fields such as `networkId` are exposed as `network_id` and
`executionTimeMs` as `execution_time_ms`, matching the original FastAPI
contract.

---

## Data Model and SQLite Schema

The database is initialized automatically. The default file is
`backend/sentinel.db` when the backend is started from the `backend` directory.

### `networks`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER | Primary key, autoincrement |
| `name` | TEXT | Required |
| `description` | TEXT | Optional |
| `created_at` | TIMESTAMP | Defaults to current time |

### `devices`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER | Primary key, autoincrement |
| `network_id` | INTEGER | FK to `networks`, cascade delete |
| `label` | TEXT | Required |
| `x`, `y` | REAL | Optional canvas position |

### `connections`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER | Primary key, autoincrement |
| `network_id` | INTEGER | FK to `networks`, cascade delete |
| `source_id` | INTEGER | FK to `devices`, cascade delete |
| `target_id` | INTEGER | FK to `devices`, cascade delete |
| `weight` | REAL | Defaults to `1.0` |

### `simulations`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER | Primary key, autoincrement |
| `network_id` | INTEGER | FK to `networks`, cascade delete |
| `initial_compromised_device_id` | INTEGER | Starting device |
| `propagation_probability` | REAL | Defaults to `0.5` |
| `seed` | INTEGER | Optional reproducibility seed |
| `strategy` | TEXT | Containment strategy name |
| `rounds` | INTEGER | Number of rounds executed |
| `num_compromised` | INTEGER | Final compromised count |
| `num_isolated` | INTEGER | Final isolated count |
| `execution_time_ms` | REAL | Measured run time |
| `note` | TEXT | Optional note |
| `created_at` | TIMESTAMP | Defaults to current time |

### `simulation_events`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER | Primary key, autoincrement |
| `simulation_id` | INTEGER | FK to `simulations`, cascade delete |
| `round_num` | INTEGER | Round in which the event happened |
| `device_id` | INTEGER | Affected device |
| `new_state` | TEXT | New state of the device |
| `reason` | TEXT | Human-readable explanation |

Foreign keys are enabled explicitly with `PRAGMA foreign_keys = ON`, so deleting
a network cascades to its devices, connections, simulations, and events.

---

## How JDBC Is Used

JDBC is intentionally used for the operations where explicit SQL control is
most valuable. All JDBC code lives in `db/Database.java` and
`server/ApiHandler.java`.

### 1. Database path and connection URL

`Database.init()` builds a JDBC URL like:

```text
jdbc:sqlite:/absolute/path/sentinel.db?foreign_keys=on&busy_timeout=5000
```

- `foreign_keys=on` enables SQLite foreign-key enforcement.
- `busy_timeout=5000` makes SQLite wait up to 5 seconds for locks instead of
  failing immediately.

### 2. Schema creation with `Statement`

Before Hibernate is configured, JDBC creates the schema:

```java
try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
     Statement statement = connection.createStatement()) {
    statement.execute("PRAGMA foreign_keys = ON;");
    statement.execute("CREATE TABLE IF NOT EXISTS networks (...)");
    // devices, connections, simulations, simulation_events
}
```

This keeps the schema identical to the original Python backend and makes the
application self-initializing. Because Hibernate is configured with
`hibernate.hbm2ddl.auto=none`, Hibernate never creates or alters the tables.

### 3. Plain JDBC connections

`Database.getConnection()` opens a new JDBC connection and enables foreign keys:

```java
Connection connection = DriverManager.getConnection(jdbcUrl);
try (Statement statement = connection.createStatement()) {
    statement.execute("PRAGMA foreign_keys = ON;");
}
```

### 4. Bulk graph save with prepared statements

`ApiHandler.saveGraph()` uses JDBC because it replaces an entire graph in one
atomic operation and needs generated keys:

```java
try (Connection connection = Database.getConnection()) {
    connection.setAutoCommit(false);
    try {
        // DELETE FROM connections WHERE network_id = ?
        // DELETE FROM devices WHERE network_id = ?
        // INSERT INTO devices (...) VALUES (?, ?, ?, ?)
        // INSERT INTO connections (...) VALUES (?, ?, ?, ?)
        connection.commit();
    } catch (Exception e) {
        connection.rollback();
        throw e;
    }
}
```

Key points:

- **Prepared statements** are used for every insert and delete, preventing SQL
  injection and allowing parameter binding.
- **Transactions** guarantee that a failed graph save does not leave a partially
  replaced network.
- **Generated keys** are read with `Statement.RETURN_GENERATED_KEYS`.
- If the driver does not return a generated key, the code falls back to
  SQLite's `SELECT last_insert_rowid()`.
- A `Map<String, Long>` maps temporary frontend node IDs to real database IDs so
  connections can be rewritten correctly after devices are inserted.
- Nullable coordinates are written with `setNull(index, Types.REAL)`.

### 5. Why JDBC here?

The graph-save endpoint is essentially a batch ETL operation:

- Delete old rows.
- Insert many rows.
- Capture database-generated identities.
- Rewrite edge references.
- Commit or roll back as a unit.

Doing this directly with JDBC keeps the logic explicit, fast, and easy to reason
about, without needing JPA relationship mappings or cascade configuration.

---

## How Hibernate Is Used

Hibernate is the ORM for the application's normal entity lifecycle: creating
networks, reading networks and graphs, saving simulations, querying history, and
deleting networks.

### 1. Programmatic `SessionFactory` configuration

Hibernate is configured in Java rather than through `persistence.xml`:

```java
Configuration configuration = new Configuration();
configuration.addAnnotatedClass(NetworkEntity.class);
configuration.addAnnotatedClass(DeviceEntity.class);
configuration.addAnnotatedClass(ConnectionEntity.class);
configuration.addAnnotatedClass(SimulationEntity.class);
configuration.addAnnotatedClass(SimulationEventEntity.class);

configuration.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC");
configuration.setProperty("hibernate.connection.url", jdbcUrl);
configuration.setProperty("hibernate.dialect",
        "org.hibernate.community.dialect.SQLiteDialect");
configuration.setProperty("hibernate.hbm2ddl.auto", "none");
configuration.setProperty("hibernate.connection.pool_size", "1");
configuration.setProperty("hibernate.jdbc.time_zone", "UTC");

sessionFactory = configuration.buildSessionFactory();
```

Important settings:

| Setting | Purpose |
| --- | --- |
| `driver_class` | Uses the Xerial SQLite JDBC driver |
| `connection.url` | Reuses the same SQLite URL as the JDBC layer |
| `hibernate.dialect` | SQLite-specific SQL generation |
| `hbm2ddl.auto=none` | Hibernate does not create or modify the schema |
| `connection.pool_size=1` | Single connection, appropriate for a small SQLite app |
| `jdbc.time_zone=UTC` | Consistent timestamp handling |

### 2. Annotated entities

Each table has a matching JPA entity in `com.sentinel.model`:

```java
@Entity
@Table(name = "devices")
public class DeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "network_id", nullable = false)
    private Long networkId;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "x")
    private Double x;

    @Column(name = "y")
    private Double y;

    // getters and setters
}
```

Notes on the mapping style:

- Entities are **flat**: relationships are stored as scalar foreign-key columns
  (`networkId`, `sourceId`, `simulationId`) rather than JPA `@ManyToOne` or
  `@OneToMany` associations.
- This keeps loading predictable and avoids lazy-loading/serialization issues.
- `created_at` is mapped with `insertable = false, updatable = false`, so SQLite
  fills it with its `CURRENT_TIMESTAMP` default.
- `@GeneratedValue(strategy = GenerationType.IDENTITY)` maps to SQLite's
  `INTEGER PRIMARY KEY AUTOINCREMENT`.

### 3. Session and transaction pattern

Every database operation uses the standard Hibernate pattern:

```java
try (Session session = Database.sessionFactory().openSession()) {
    Transaction transaction = session.beginTransaction();
    session.persist(entity);
    transaction.commit();
}
```

- `openSession()` obtains a session.
- `beginTransaction()` / `commit()` wrap writes.
- The try-with-resources block closes the session.
- `SessionFactory` is created once and reused; sessions are short-lived.

### 4. Persisting data with Hibernate

- **Create network:** `session.persist(network)` then read the generated ID.
- **Save simulation:** `session.persist(simulationEntity)` followed by one
  `persist(eventEntity)` per event, all in one transaction.
- **Delete network:** a bulk HQL mutation query:

  ```java
  session.createMutationQuery("delete from NetworkEntity where id = :id")
         .setParameter("id", networkId)
         .executeUpdate();
  ```

  Database-level `ON DELETE CASCADE` removes dependent rows.

### 5. Querying data with Hibernate

Hibernate HQL is used for reads:

```java
session.createQuery("from NetworkEntity order by id desc", NetworkEntity.class)
       .getResultList();

session.createQuery(
        "from DeviceEntity where networkId = :id order by id", DeviceEntity.class)
       .setParameter("id", networkId)
       .getResultList();

session.createQuery(
        "from SimulationEventEntity where simulationId = :id order by roundNum, id",
        SimulationEventEntity.class)
       .setParameter("id", simulationId)
       .getResultList();
```

Single rows are fetched with `session.get(Entity.class, id)`.

### 6. Serialization

Entities are returned directly from `ApiHandler` and serialized by Jackson.
Because the entities are simple POJOs with getters and no JPA associations,
serialization produces clean JSON without lazy-loading proxies.

### 7. Cascade behavior

There are no JPA cascade annotations. Referential actions are handled by SQLite:

```sql
FOREIGN KEY(network_id) REFERENCES networks(id) ON DELETE CASCADE
```

Hibernate executes the network delete; SQLite enforces the cascades. This works
because foreign keys are enabled in both the Hibernate connection URL and the
plain JDBC connections.

---

## JDBC vs Hibernate: Division of Responsibility

| Concern | JDBC | Hibernate |
| --- | --- | --- |
| Create schema | Yes | No (`hbm2ddl.auto=none`) |
| Enable foreign keys | Yes (`PRAGMA`) | Via connection URL |
| Create/read network metadata | No | Yes |
| Read devices and connections | No | Yes |
| Replace full graph | Yes (transactional prepared statements) | No |
| Capture generated device IDs | Yes (`getGeneratedKeys` / `last_insert_rowid`) | No |
| Save simulation + events | No | Yes |
| Query simulation history | No | Yes |
| Delete network and dependents | No | Yes (SQLite cascades) |

In short:

- **JDBC owns the schema and the bulk graph replacement.**
- **Hibernate owns ordinary CRUD and queries.**

This split demonstrates both approaches in one project while keeping each
operation in the tool that handles it most clearly.

---

## DAA Algorithms

### Breadth-First Search (BFS)

- Uses a FIFO queue.
- Visits all neighbors at the current depth before moving deeper.
- Time complexity: `O(V + E)`.
- Returns `traversal_order`, an explanation, and the complexity string.

### Depth-First Search (DFS)

- Recursive traversal.
- Explores as deep as possible before backtracking.
- Time complexity: `O(V + E)`.
- Returns `traversal_order`.

### Dijkstra's Algorithm

- Finds the shortest path between a start and end node using non-negative
  edge weights.
- Maintains distance and previous-node maps.
- Stops early when the destination is selected.
- Returns `path`, `total_cost`, an explanation, and the complexity.
- If no path exists, returns an empty path and infinite cost.
- Implementation here is the simple `O(V^2)` selection variant.

### Prim's Algorithm

- Builds a minimum spanning tree by repeatedly adding the cheapest edge that
  connects the current tree to an unvisited node.
- Starts from the smallest node ID.
- If the graph is disconnected, returns the MST of the connected component
  containing the start node and sets `connected` to `false`.
- Time complexity: `O(V^2)` for this simple implementation.

The in-memory `Graph` class is undirected and weighted. Edge weights are stored
with a normalized node-pair key, and neighbor lists are sorted so results are
deterministic.

---

## How the Attack Simulation Works

The simulation is a discrete, round-based state machine over the graph.

### Device states

| State | Meaning |
| --- | --- |
| `Safe` | Not affected |
| `Exposed` | Was targeted but not successfully compromised |
| `Compromised` | Successfully compromised |
| `Isolated` | Removed from propagation |
| `Recovered` | Reserved state in the model |

### One round

1. Collect all currently `Compromised` devices.
2. For each compromised device, inspect its neighbors.
3. Skip neighbors that are `Isolated`.
4. For each `Safe` neighbor:
   - With probability `p`, mark it `Compromised`.
   - Otherwise mark it `Exposed`.
5. If nothing changed, stop.
6. Otherwise increment the round counter, record an event per change, and
   continue.

The simulation stops when no state changes occur or after 50 rounds.

### Reproducibility

If a `seed` is supplied, `java.util.Random` is initialized with that seed. The
same graph, probability, strategy, and seed produce the same result, which makes
strategy comparisons fair.

### Containment strategies

1. **Initial-node isolation**
   - Immediately isolates the starting compromised device.
   - Prevents it from propagating.

2. **Greedy containment**
   - Finds all currently compromised devices.
   - Sorts them by degree (number of neighbors), descending.
   - Isolates them in that order.
   - This is a documented heuristic, not an optimal solution.

3. **Manual containment**
   - Isolates the device IDs supplied by the user.

### Persisted results

Each run stores:

- configuration (`network_id`, initial device, probability, seed, strategy)
- aggregate results (`rounds`, compromised count, isolated count, execution time)
- every event (`round_num`, `device_id`, `new_state`, `reason`)

---

## API Endpoints

Base URL: `http://localhost:8000/api`

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Health check |
| GET | `/api/networks` | List saved networks |
| POST | `/api/networks` | Create a network |
| GET | `/api/networks/{id}` | Get network with devices and connections |
| DELETE | `/api/networks/{id}` | Delete a network and its data |
| POST | `/api/networks/{id}/graph` | Replace a network's devices and connections |
| POST | `/api/algorithms/bfs` | Run BFS |
| POST | `/api/algorithms/dfs` | Run DFS |
| POST | `/api/algorithms/dijkstra` | Run Dijkstra |
| POST | `/api/algorithms/prim` | Run Prim |
| POST | `/api/simulations` | Run and save an attack simulation |
| GET | `/api/simulations` | List saved simulations |
| GET | `/api/simulations/{id}` | Get a simulation and its events |

### Example: create a network

```bash
curl -X POST http://localhost:8000/api/networks \
  -H 'Content-Type: application/json' \
  -d '{"name":"Campus Network","description":"Demo"}'
```

### Example: save a graph

```bash
curl -X POST http://localhost:8000/api/networks/1/graph \
  -H 'Content-Type: application/json' \
  -d '{
    "devices": [
      {"id": "a", "label": "Router", "x": 100, "y": 100},
      {"id": "b", "label": "Server", "x": 300, "y": 100}
    ],
    "connections": [
      {"source": "a", "target": "b", "weight": 2.5}
    ]
  }'
```

### Example: run Dijkstra

```bash
curl -X POST http://localhost:8000/api/algorithms/dijkstra \
  -H 'Content-Type: application/json' \
  -d '{"network_id":1,"start":1,"end":2}'
```

### Example: run a simulation

```bash
curl -X POST http://localhost:8000/api/simulations \
  -H 'Content-Type: application/json' \
  -d '{
    "network_id": 1,
    "initial_compromised_device_id": 1,
    "propagation_probability": 0.6,
    "seed": 12345,
    "strategy": "greedy containment",
    "manual_isolated": []
  }'
```

---

## Build and Run

### Prerequisites

- Java 17 or newer (JDK)
- A modern web browser
- Python 3 only if you want to serve the static frontend (optional; any static
  file server works)

### 1. Build and start the backend

```bash
cd backend
./mvnw package
java -jar target/sentinel-backend.jar
```

On Windows:

```bat
mvnw.cmd package
java -jar target\sentinel-backend.jar
```

The API starts at `http://localhost:8000/api`.

### 2. Serve the frontend

In a second terminal:

```bash
cd frontend
python3 -m http.server 8080
```

Open `http://localhost:8080`.

The frontend calls `http://localhost:8000/api` directly using `fetch()`. CORS
headers are added by `ApiHandler.applyCors()`, so the browser can make
cross-origin requests to the backend.

---

## Configuration

| Environment variable | Default | Description |
| --- | --- | --- |
| `HOST` | `127.0.0.1` | Address the HTTP server binds to |
| `PORT` | `8000` | HTTP port |
| `SENTINEL_DB_PATH` | `sentinel.db` | SQLite database file path |

Example:

```bash
HOST=0.0.0.0 PORT=8000 SENTINEL_DB_PATH=/var/lib/sentinel/sentinel.db \
  java -jar target/sentinel-backend.jar
```

The database directory is created automatically if it does not exist.

---

## Running the Tests

```bash
cd backend
./mvnw test
```

The test suite covers:

- Graph construction and weighted edges
- BFS and DFS traversal
- Dijkstra shortest path and invalid-node handling
- Prim MST, including disconnected graphs
- Seeded simulation reproducibility
- Isolation and greedy containment behavior
- JDBC schema creation for all five tables
- Hibernate persistence, generated IDs, and cascade deletion

`DatabasePersistenceTest` uses a temporary SQLite database via JUnit's
`@TempDir`, so tests do not touch `sentinel.db`.

---

## Demonstration Walkthrough

1. **Dashboard** - show saved-network, device, and simulation counts.
2. **Network Lab** - add devices, connect them with weights, save the network,
   then reload it from the saved list.
3. **Algorithm Lab** - run BFS, DFS, Dijkstra, and Prim and explain the returned
   traversal, path, cost, and MST.
4. **Attack Simulator** - run the same graph and seed with different containment
   strategies, then compare compromised/isolated counts and event logs.
5. **Simulation History** - reopen a previous run and show that the full
   configuration, results, and events were persisted.

To highlight the JDBC/Hibernate split, point out that:

- `Database.createSchema()` uses JDBC `Statement` DDL.
- `ApiHandler.saveGraph()` uses JDBC prepared statements and transactions.
- `ApiHandler` uses Hibernate `Session`/`Transaction` for networks,
  simulations, events, and history queries.

---

## Limitations

- SQLite is configured with a Hibernate connection pool size of 1; this is
  intentional for a small single-file database, not for high concurrency.
- Entities use scalar foreign keys rather than JPA associations; graph assembly
  is done manually in `Graph.fromDb()`.
- Deleting a network uses database-level cascades, so foreign keys must remain
  enabled.
- The simulation is synthetic and probabilistic. It does not model real
  protocols, vulnerabilities, authentication, or network traffic.
- Prim and Dijkstra use simple implementations intended for teaching, not
  large-scale performance.
- The frontend API base URL is hard-coded to `http://localhost:8000/api` in
  `frontend/app.js`.
