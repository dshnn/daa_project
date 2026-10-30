package com.sentinel.server;

import com.sentinel.algorithm.Algorithms;
import com.sentinel.algorithm.Graph;
import com.sentinel.db.Database;
import com.sentinel.model.ConnectionEntity;
import com.sentinel.model.DeviceEntity;
import com.sentinel.model.NetworkEntity;
import com.sentinel.model.SimulationEntity;
import com.sentinel.model.SimulationEventEntity;
import com.sentinel.simulation.Simulation;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP API with the same routes and JSON payloads as the original FastAPI backend.
 */
public final class ApiHandler implements HttpHandler {

    private static final String JSON_TYPE = "application/json";

    @Override
    public void handle(HttpExchange exchange) {
        applyCors(exchange);
        try {
            String method = exchange.getRequestMethod().toUpperCase();
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith("/api")) {
                throw new ApiException(404, "Not Found");
            }

            List<String> segments = new ArrayList<>();
            for (String segment : path.substring(4).split("/")) {
                if (!segment.isEmpty()) {
                    segments.add(segment);
                }
            }

            Object result = route(method, segments, exchange);
            sendJson(exchange, 200, result);
        } catch (ApiException e) {
            sendJson(exchange, e.status(), detail(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, detail("Internal Server Error"));
        }
    }

    private Object route(String method, List<String> segments, HttpExchange exchange)
            throws IOException, SQLException {
        if (segments.isEmpty()) {
            throw new ApiException(404, "Not Found");
        }

        String resource = segments.get(0);

        if ("health".equals(resource) && segments.size() == 1) {
            requireMethod(method, "GET");
            return Map.of("status", "ok");
        }

        if ("networks".equals(resource)) {
            if (segments.size() == 1) {
                if ("GET".equals(method)) {
                    return listNetworks();
                }
                if ("POST".equals(method)) {
                    return createNetwork(readBody(exchange));
                }
                throw methodNotAllowed();
            }

            long networkId = parseId(segments.get(1));

            if (segments.size() == 2) {
                if ("GET".equals(method)) {
                    return getNetwork(networkId);
                }
                if ("DELETE".equals(method)) {
                    return deleteNetwork(networkId);
                }
                throw methodNotAllowed();
            }

            if (segments.size() == 3 && "graph".equals(segments.get(2))) {
                if ("POST".equals(method)) {
                    return saveGraph(networkId, readBody(exchange));
                }
                throw methodNotAllowed();
            }
        }

        if ("algorithms".equals(resource) && segments.size() == 2) {
            if ("POST".equals(method)) {
                return runAlgorithm(segments.get(1), readBody(exchange));
            }
            throw methodNotAllowed();
        }

        if ("simulations".equals(resource)) {
            if (segments.size() == 1) {
                if ("GET".equals(method)) {
                    return listSimulations();
                }
                if ("POST".equals(method)) {
                    return createSimulation(readBody(exchange));
                }
                throw methodNotAllowed();
            }
            if (segments.size() == 2) {
                if ("GET".equals(method)) {
                    return getSimulation(parseId(segments.get(1)));
                }
                throw methodNotAllowed();
            }
        }

        throw new ApiException(404, "Not Found");
    }

    private List<NetworkEntity> listNetworks() {
        try (Session session = Database.sessionFactory().openSession()) {
            return session.createQuery("from NetworkEntity order by id desc", NetworkEntity.class)
                    .getResultList();
        }
    }

    private Map<String, Object> createNetwork(Map<String, Object> payload) {
        Object rawName = payload.get("name");
        String name = rawName == null ? null : String.valueOf(rawName);
        if (name == null || name.isEmpty()) {
            name = "Untitled Network";
        }
        Object rawDescription = payload.get("description");
        String description = rawDescription == null ? null : String.valueOf(rawDescription);

        NetworkEntity network = new NetworkEntity();
        network.setName(name);
        network.setDescription(description);

        try (Session session = Database.sessionFactory().openSession()) {
            Transaction transaction = session.beginTransaction();
            session.persist(network);
            transaction.commit();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", network.getId());
        result.put("name", name);
        result.put("description", description);
        return result;
    }

    private Map<String, Object> getNetwork(long networkId) {
        NetworkEntity network;
        List<DeviceEntity> devices;
        List<ConnectionEntity> connections;

        try (Session session = Database.sessionFactory().openSession()) {
            network = session.get(NetworkEntity.class, networkId);
            if (network == null) {
                throw new ApiException(404, "Network not found");
            }
            devices = session.createQuery(
                            "from DeviceEntity where networkId = :id order by id", DeviceEntity.class)
                    .setParameter("id", networkId)
                    .getResultList();
            connections = session.createQuery(
                            "from ConnectionEntity where networkId = :id order by id", ConnectionEntity.class)
                    .setParameter("id", networkId)
                    .getResultList();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("network", network);
        result.put("devices", devices);
        result.put("connections", connections);
        return result;
    }

    private Map<String, Object> deleteNetwork(long networkId) {
        try (Session session = Database.sessionFactory().openSession()) {
            Transaction transaction = session.beginTransaction();
            session.createMutationQuery("delete from NetworkEntity where id = :id")
                    .setParameter("id", networkId)
                    .executeUpdate();
            transaction.commit();
        }
        return Map.of("status", "deleted");
    }

    private Map<String, Object> saveGraph(long networkId, Map<String, Object> payload) throws SQLException {
        List<?> devices = payload.get("devices") instanceof List<?> list ? list : List.of();
        List<?> connections = payload.get("connections") instanceof List<?> list ? list : List.of();

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM connections WHERE network_id = ?")) {
                    statement.setLong(1, networkId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM devices WHERE network_id = ?")) {
                    statement.setLong(1, networkId);
                    statement.executeUpdate();
                }

                Map<String, Long> idMap = new LinkedHashMap<>();
                for (Object item : devices) {
                    Map<String, Object> device = asMap(item);
                    String label = device.get("label") == null ? null : String.valueOf(device.get("label"));
                    Double x = asDouble(device.get("x"), "x");
                    Double y = asDouble(device.get("y"), "y");
                    long newId = insertDevice(connection, networkId, label, x, y);

                    Object originalId = device.get("id");
                    if (originalId != null) {
                        idMap.put(String.valueOf(originalId), newId);
                    } else {
                        idMap.put(String.valueOf(idMap.size()), newId);
                    }
                }

                for (Object item : connections) {
                    Map<String, Object> connectionData = asMap(item);
                    Long sourceId = resolveNode(connectionData.get("source"), idMap);
                    Long targetId = resolveNode(connectionData.get("target"), idMap);
                    if (sourceId == null || targetId == null) {
                        continue;
                    }
                    Double weight = asDouble(connectionData.get("weight"), "weight");
                    insertConnection(connection, networkId, sourceId, targetId, weight == null ? 1.0 : weight);
                }

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }

        return Map.of("status", "saved");
    }

    private static long insertDevice(Connection connection, long networkId, String label, Double x, Double y)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO devices (network_id, label, x, y) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, networkId);
            statement.setString(2, label);
            setNullableDouble(statement, 3, x);
            setNullableDouble(statement, 4, y);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys != null && keys.next()) {
                    long id = keys.getLong(1);
                    if (!keys.wasNull() && id != 0) {
                        return id;
                    }
                }
            } catch (SQLException ignored) {
                // Fall through to SQLite's last_insert_rowid().
            }

            try (Statement fallback = connection.createStatement();
                 ResultSet resultSet = fallback.executeQuery("SELECT last_insert_rowid()")) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }
        throw new SQLException("Unable to obtain generated device id");
    }

    private static void insertConnection(Connection connection, long networkId, long sourceId, long targetId,
                                         double weight) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO connections (network_id, source_id, target_id, weight) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, networkId);
            statement.setLong(2, sourceId);
            statement.setLong(3, targetId);
            statement.setDouble(4, weight);
            statement.executeUpdate();
        }
    }

    private static void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.REAL);
        } else {
            statement.setDouble(index, value);
        }
    }

    private static Long resolveNode(Object value, Map<String, Long> idMap) {
        if (value instanceof String string) {
            return idMap.get(string);
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Map<String, Object> runAlgorithm(String algorithm, Map<String, Object> payload) {
        Long networkId = asLong(payload.get("network_id"), "network_id");
        if (networkId == null) {
            throw new ApiException(400, "network_id required");
        }

        Graph graph = loadGraph(networkId);
        try {
            switch (algorithm) {
                case "bfs":
                    return Algorithms.bfs(graph, requireInt(payload.get("start"), "start"));
                case "dfs":
                    return Algorithms.dfs(graph, requireInt(payload.get("start"), "start"));
                case "dijkstra":
                    return Algorithms.dijkstra(
                            graph,
                            requireInt(payload.get("start"), "start"),
                            requireInt(payload.get("end"), "end"));
                case "prim":
                    return Algorithms.prim(graph);
                default:
                    throw new ApiException(404, "Unknown algorithm");
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e.getMessage());
        }
    }

    private Graph loadGraph(long networkId) {
        try (Session session = Database.sessionFactory().openSession()) {
            List<DeviceEntity> devices = session.createQuery(
                            "from DeviceEntity where networkId = :id order by id", DeviceEntity.class)
                    .setParameter("id", networkId)
                    .getResultList();
            List<ConnectionEntity> connections = session.createQuery(
                            "from ConnectionEntity where networkId = :id order by id", ConnectionEntity.class)
                    .setParameter("id", networkId)
                    .getResultList();
            return Graph.fromDb(devices, connections);
        }
    }

    private Map<String, Object> createSimulation(Map<String, Object> payload) {
        Long networkId = asLong(payload.get("network_id"), "network_id");
        if (networkId == null) {
            throw new ApiException(400, "network_id required");
        }
        Integer initial = asInteger(payload.get("initial_compromised_device_id"),
                "initial_compromised_device_id");
        if (initial == null) {
            throw new ApiException(400, "initial_compromised_device_id required");
        }

        Double rawProbability = asDouble(payload.get("propagation_probability"), "propagation_probability");
        double probability = rawProbability == null ? 0.5 : rawProbability;
        Long seed = asLong(payload.get("seed"), "seed");
        Object rawStrategy = payload.get("strategy");
        String strategy = rawStrategy == null ? "none" : String.valueOf(rawStrategy);
        List<Integer> manual = asIntList(payload.get("manual_isolated"));

        Graph graph = loadGraph(networkId);
        Simulation simulation = new Simulation(graph, initial, probability, seed);
        simulation.applyContainment(strategy, manual);
        Map<String, Object> result = simulation.run();

        @SuppressWarnings("unchecked")
        List<Simulation.SimEvent> events = (List<Simulation.SimEvent>) result.get("events");

        SimulationEntity entity = new SimulationEntity();
        entity.setNetworkId(networkId);
        entity.setInitialCompromisedDeviceId((long) initial);
        entity.setPropagationProbability(probability);
        entity.setSeed(seed);
        entity.setStrategy(strategy);
        entity.setRounds((Integer) result.get("rounds"));
        entity.setNumCompromised((Integer) result.get("num_compromised"));
        entity.setNumIsolated((Integer) result.get("num_isolated"));
        entity.setExecutionTimeMs((Double) result.get("execution_time_ms"));

        long simulationId;
        try (Session session = Database.sessionFactory().openSession()) {
            Transaction transaction = session.beginTransaction();
            session.persist(entity);
            simulationId = entity.getId();

            for (Simulation.SimEvent event : events) {
                SimulationEventEntity eventEntity = new SimulationEventEntity();
                eventEntity.setSimulationId(simulationId);
                eventEntity.setRoundNum(event.roundNum());
                eventEntity.setDeviceId((long) event.deviceId());
                eventEntity.setNewState(event.newState());
                eventEntity.setReason(event.reason());
                session.persist(eventEntity);
            }
            transaction.commit();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("simulation_id", simulationId);
        response.putAll(result);
        return response;
    }

    private List<SimulationEntity> listSimulations() {
        try (Session session = Database.sessionFactory().openSession()) {
            return session.createQuery("from SimulationEntity order by id desc", SimulationEntity.class)
                    .getResultList();
        }
    }

    private Map<String, Object> getSimulation(long simulationId) {
        SimulationEntity simulation;
        List<SimulationEventEntity> events;

        try (Session session = Database.sessionFactory().openSession()) {
            simulation = session.get(SimulationEntity.class, simulationId);
            if (simulation == null) {
                throw new ApiException(404, "Simulation not found");
            }
            events = session.createQuery(
                            "from SimulationEventEntity where simulationId = :id order by roundNum, id",
                            SimulationEventEntity.class)
                    .setParameter("id", simulationId)
                    .getResultList();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("simulation", simulation);
        result.put("events", events);
        return result;
    }

    private static Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        byte[] body;
        try (InputStream inputStream = exchange.getRequestBody()) {
            body = inputStream.readAllBytes();
        }
        if (body.length == 0) {
            return new LinkedHashMap<>();
        }
        Object parsed = Json.MAPPER.readValue(body, Object.class);
        return asMap(parsed);
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static Integer asInteger(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException e) {
                throw new ApiException(400, "Invalid integer for " + field);
            }
        }
        throw new ApiException(400, "Invalid integer for " + field);
    }

    private static Long asLong(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.parseLong(string.trim());
            } catch (NumberFormatException e) {
                throw new ApiException(400, "Invalid integer for " + field);
            }
        }
        throw new ApiException(400, "Invalid integer for " + field);
    }

    private static Double asDouble(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException e) {
                throw new ApiException(400, "Invalid number for " + field);
            }
        }
        throw new ApiException(400, "Invalid number for " + field);
    }

    private static List<Integer> asIntList(Object value) {
        List<Integer> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Integer parsed = asInteger(item, "manual_isolated");
                if (parsed != null) {
                    result.add(parsed);
                }
            }
        }
        return result;
    }

    private static int requireInt(Object value, String field) {
        Integer parsed = asInteger(value, field);
        if (parsed == null) {
            throw new ApiException(400, field + " required");
        }
        return parsed;
    }

    private static long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ApiException(404, "Not Found");
        }
    }

    private static void requireMethod(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw methodNotAllowed();
        }
    }

    private static ApiException methodNotAllowed() {
        return new ApiException(405, "Method Not Allowed");
    }

    private static Map<String, Object> detail(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detail", message == null ? "" : message);
        return result;
    }

    private static void applyCors(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin == null ? "*" : origin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Accept, Authorization");
        exchange.getResponseHeaders().set("Vary", "Origin");
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) {
        try {
            byte[] bytes = Json.MAPPER.writeValueAsBytes(body);
            exchange.getResponseHeaders().set("Content-Type", JSON_TYPE);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            exchange.close();
        }
    }
}
