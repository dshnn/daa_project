package com.sentinel;

import com.sentinel.db.Database;
import com.sentinel.model.ConnectionEntity;
import com.sentinel.model.DeviceEntity;
import com.sentinel.model.NetworkEntity;
import com.sentinel.model.SimulationEntity;
import com.sentinel.model.SimulationEventEntity;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabasePersistenceTest {

    @TempDir
    static Path tempDir;

    private static Path databasePath;

    @BeforeAll
    static void setUp() {
        databasePath = tempDir.resolve("sentinel_test.db");
        Database.reset();
        Database.init(databasePath);
    }

    @AfterAll
    static void tearDown() {
        Database.reset();
    }

    @Test
    void jdbcSchemaContainsAllTables() throws Exception {
        try (Connection connection = Database.getConnection()) {
            for (String table : List.of(
                    "networks", "devices", "connections", "simulations", "simulation_events")) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
                    statement.setString(1, table);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next(), "Missing table: " + table);
                    }
                }
            }
        }
    }

    @Test
    void hibernatePersistsEntitiesAndCascadesDeletes() {
        long networkId;
        long deviceId;
        long simulationId;

        try (Session session = Database.sessionFactory().openSession()) {
            Transaction transaction = session.beginTransaction();

            NetworkEntity network = new NetworkEntity();
            network.setName("Test Network");
            network.setDescription("Persistence test");
            session.persist(network);

            DeviceEntity first = new DeviceEntity();
            first.setNetworkId(network.getId());
            first.setLabel("Router");
            first.setX(10.0);
            first.setY(20.0);
            session.persist(first);

            DeviceEntity second = new DeviceEntity();
            second.setNetworkId(network.getId());
            second.setLabel("Server");
            second.setX(30.0);
            second.setY(40.0);
            session.persist(second);

            ConnectionEntity connection = new ConnectionEntity();
            connection.setNetworkId(network.getId());
            connection.setSourceId(first.getId());
            connection.setTargetId(second.getId());
            connection.setWeight(2.5);
            session.persist(connection);

            SimulationEntity simulation = new SimulationEntity();
            simulation.setNetworkId(network.getId());
            simulation.setInitialCompromisedDeviceId(first.getId());
            simulation.setPropagationProbability(0.5);
            simulation.setSeed(42L);
            simulation.setStrategy("none");
            simulation.setRounds(1);
            simulation.setNumCompromised(1);
            simulation.setNumIsolated(0);
            simulation.setExecutionTimeMs(1.25);
            session.persist(simulation);

            SimulationEventEntity event = new SimulationEventEntity();
            event.setSimulationId(simulation.getId());
            event.setRoundNum(1);
            event.setDeviceId(second.getId());
            event.setNewState("Exposed");
            event.setReason("Exposed by " + first.getId());
            session.persist(event);

            transaction.commit();

            networkId = network.getId();
            deviceId = first.getId();
            simulationId = simulation.getId();
        }

        try (Session session = Database.sessionFactory().openSession()) {
            NetworkEntity network = session.get(NetworkEntity.class, networkId);
            assertNotNull(network);
            assertEquals("Test Network", network.getName());
            assertNotNull(network.getCreatedAt());

            DeviceEntity device = session.get(DeviceEntity.class, deviceId);
            assertNotNull(device);
            assertEquals(10.0, device.getX(), 0.0001);

            SimulationEntity simulation = session.get(SimulationEntity.class, simulationId);
            assertNotNull(simulation);
            assertEquals(0.5, simulation.getPropagationProbability(), 0.0001);
        }

        try (Session session = Database.sessionFactory().openSession()) {
            Transaction transaction = session.beginTransaction();
            session.createMutationQuery("delete from NetworkEntity where id = :id")
                    .setParameter("id", networkId)
                    .executeUpdate();
            transaction.commit();
        }

        try (Session session = Database.sessionFactory().openSession()) {
            assertNull(session.get(DeviceEntity.class, deviceId));
            assertNull(session.get(SimulationEntity.class, simulationId));
        }
    }
}
