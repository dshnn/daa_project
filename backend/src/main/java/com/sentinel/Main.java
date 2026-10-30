package com.sentinel;

import com.sentinel.db.Database;
import com.sentinel.server.ApiHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String host = env("HOST", "127.0.0.1");
        int port = intEnv("PORT", 8000);

        System.setProperty("org.jboss.logging.provider", "slf4j");
        Database.init();

        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/api", new ApiHandler());

        ExecutorService executor = Executors.newFixedThreadPool(8);
        server.setExecutor(executor);
        server.start();

        System.out.println("SENTINEL backend (JDBC + Hibernate) listening on http://" + host + ":" + port);
        System.out.println("API base: http://" + host + ":" + port + "/api");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            executor.shutdownNow();
            Database.reset();
        }));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }
}
