package com.sentinel.db;

import com.sentinel.model.ConnectionEntity;
import com.sentinel.model.DeviceEntity;
import com.sentinel.model.NetworkEntity;
import com.sentinel.model.SimulationEntity;
import com.sentinel.model.SimulationEventEntity;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the SQLite database file.
 *
 * <p>JDBC is used for the schema (the same DDL as the original Python backend)
 * and for the bulk graph-save operation. Hibernate is used as the ORM for all
 * entity persistence and queries.</p>
 */
public final class Database {

    private static final String DEFAULT_DB_FILE = "sentinel.db";

    private static SessionFactory sessionFactory;
    private static String jdbcUrl;

    private Database() {
    }

    public static synchronized void init() {
        init(defaultPath());
    }

    public static synchronized void init(Path path) {
        if (sessionFactory != null) {
            return;
        }
        Path absolute = path.toAbsolutePath().normalize();
        try {
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create database directory", e);
        }

        jdbcUrl = "jdbc:sqlite:" + absolute + "?foreign_keys=on&busy_timeout=5000";

        try {
            createSchema(absolute);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize database schema", e);
        }

        Configuration configuration = new Configuration();
        configuration.addAnnotatedClass(NetworkEntity.class);
        configuration.addAnnotatedClass(DeviceEntity.class);
        configuration.addAnnotatedClass(ConnectionEntity.class);
        configuration.addAnnotatedClass(SimulationEntity.class);
        configuration.addAnnotatedClass(SimulationEventEntity.class);

        configuration.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC");
        configuration.setProperty("hibernate.connection.url", jdbcUrl);
        configuration.setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        configuration.setProperty("hibernate.connection.pool_size", "1");
        configuration.setProperty("hibernate.show_sql", "false");
        configuration.setProperty("hibernate.format_sql", "false");
        configuration.setProperty("hibernate.jdbc.time_zone", "UTC");

        sessionFactory = configuration.buildSessionFactory();
    }

    public static synchronized void reset() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        sessionFactory = null;
        jdbcUrl = null;
    }

    public static synchronized SessionFactory sessionFactory() {
        if (sessionFactory == null) {
            init();
        }
        return sessionFactory;
    }

    /** A plain JDBC connection, used for the schema and bulk graph writes. */
    public static Connection getConnection() throws SQLException {
        if (jdbcUrl == null) {
            init();
        }
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");
        }
        return connection;
    }

    public static String jdbcUrl() {
        if (jdbcUrl == null) {
            init();
        }
        return jdbcUrl;
    }

    private static Path defaultPath() {
        String env = System.getenv("SENTINEL_DB_PATH");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        return Paths.get(DEFAULT_DB_FILE);
    }

    private static void createSchema(Path path) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement statement = connection.createStatement()) {

            statement.execute("PRAGMA foreign_keys = ON;");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS networks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        description TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS devices (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        network_id INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        x REAL,
                        y REAL,
                        FOREIGN KEY(network_id) REFERENCES networks(id) ON DELETE CASCADE
                    );
                    """);

            statement.execute("""
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
                    """);

            statement.execute("""
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
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS simulation_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        simulation_id INTEGER NOT NULL,
                        round_num INTEGER NOT NULL,
                        device_id INTEGER NOT NULL,
                        new_state TEXT NOT NULL,
                        reason TEXT,
                        FOREIGN KEY(simulation_id) REFERENCES simulations(id) ON DELETE CASCADE
                    );
                    """);
        }
    }
}
