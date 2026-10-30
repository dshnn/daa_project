package com.sentinel.simulation;

import com.sentinel.algorithm.Graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Synthetic network-attack simulation. Port of the original Python implementation.
 */
public final class Simulation {

    public static final List<String> STATES =
            List.of("Safe", "Exposed", "Compromised", "Isolated", "Recovered");

    public record SimEvent(int roundNum, int deviceId, String newState, String reason) {
    }

    private final Graph graph;
    private final Random rng;
    private final double probability;
    private final int initial;
    private final Map<Integer, String> states = new LinkedHashMap<>();
    private final List<SimEvent> events = new ArrayList<>();
    private final List<Integer> compromisedHistory = new ArrayList<>();
    private int rounds;

    public Simulation(Graph graph, int initialCompromised, double propagationProbability, Long seed) {
        this.graph = graph;
        this.rng = seed == null ? new Random() : new Random(seed);
        this.probability = Math.max(0.0, Math.min(1.0, propagationProbability));
        this.initial = initialCompromised;

        for (int node : graph.sortedNodes()) {
            states.put(node, "Safe");
        }
        if (states.containsKey(initialCompromised)) {
            states.put(initialCompromised, "Compromised");
        }
    }

    public void applyContainment(String strategy, List<Integer> manual) {
        String normalized = strategy == null ? "none" : strategy.toLowerCase(Locale.ROOT);

        if (normalized.equals("initial-node isolation") || normalized.equals("isolate_initial")) {
            if (states.containsKey(initial) && !"Isolated".equals(states.get(initial))) {
                states.put(initial, "Isolated");
            }
            return;
        }

        if (normalized.equals("greedy containment") || normalized.equals("greedy")) {
            record Candidate(int degree, int node) {
            }
            List<Candidate> candidates = new ArrayList<>();
            for (int node : graph.nodes()) {
                if ("Compromised".equals(states.get(node))) {
                    candidates.add(new Candidate(graph.neighbors(node).size(), node));
                }
            }
            candidates.sort(Comparator.comparingInt(Candidate::degree)
                    .thenComparingInt(Candidate::node)
                    .reversed());
            for (Candidate candidate : candidates) {
                if (!"Isolated".equals(states.get(candidate.node()))) {
                    states.put(candidate.node(), "Isolated");
                }
            }
            return;
        }

        if (normalized.equals("manual containment") || normalized.equals("manual")) {
            if (manual != null) {
                for (int node : manual) {
                    if (states.containsKey(node) && !"Isolated".equals(states.get(node))) {
                        states.put(node, "Isolated");
                    }
                }
            }
        }
    }

    public boolean step() {
        List<Change> changes = new ArrayList<>();
        List<Integer> currentCompromised = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : states.entrySet()) {
            if ("Compromised".equals(entry.getValue())) {
                currentCompromised.add(entry.getKey());
            }
        }

        for (int compromised : currentCompromised) {
            if ("Isolated".equals(states.get(compromised))) {
                continue;
            }
            for (int neighbor : graph.neighbors(compromised)) {
                if ("Isolated".equals(states.get(neighbor))) {
                    continue;
                }
                if ("Safe".equals(states.get(neighbor))) {
                    if (rng.nextDouble() < probability) {
                        states.put(neighbor, "Compromised");
                        changes.add(new Change(neighbor, "Compromised", "Propagated from " + compromised));
                    } else {
                        states.put(neighbor, "Exposed");
                        changes.add(new Change(neighbor, "Exposed", "Exposed by " + compromised));
                    }
                }
            }
        }

        if (changes.isEmpty()) {
            return false;
        }

        rounds++;
        for (Change change : changes) {
            events.add(new SimEvent(rounds, change.deviceId(), change.newState(), change.reason()));
        }
        compromisedHistory.add(countCompromised());
        return true;
    }

    public Map<String, Object> run() {
        return run(50);
    }

    public Map<String, Object> run(int maxRounds) {
        long start = System.nanoTime();
        for (int i = 0; i < maxRounds; i++) {
            if (!step()) {
                break;
            }
        }
        long end = System.nanoTime();

        double elapsedMs = (end - start) / 1_000_000.0;
        elapsedMs = Math.round(elapsedMs * 100.0) / 100.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rounds", rounds);
        result.put("num_compromised", countCompromised());
        result.put("num_isolated", countIsolated());
        result.put("execution_time_ms", elapsedMs);
        result.put("states", states);
        result.put("events", events);
        return result;
    }

    public int countCompromised() {
        int count = 0;
        for (String state : states.values()) {
            if ("Compromised".equals(state)) {
                count++;
            }
        }
        return count;
    }

    public int countIsolated() {
        int count = 0;
        for (String state : states.values()) {
            if ("Isolated".equals(state)) {
                count++;
            }
        }
        return count;
    }

    public List<Integer> compromisedHistory() {
        return compromisedHistory;
    }

    private record Change(int deviceId, String newState, String reason) {
    }
}
