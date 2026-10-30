package com.sentinel.algorithm;

import com.sentinel.model.ConnectionEntity;
import com.sentinel.model.DeviceEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple undirected graph representation used by the algorithms and simulation.
 * Mirrors the original Python Graph class.
 */
public final class Graph {

    private final Set<Integer> nodes = new HashSet<>();
    private final Map<Long, Double> edges = new HashMap<>();
    private final Map<Integer, Set<Integer>> adj = new HashMap<>();

    public static Graph fromDb(List<DeviceEntity> devices, List<ConnectionEntity> connections) {
        Graph graph = new Graph();
        for (DeviceEntity device : devices) {
            graph.addNode(device.getId().intValue());
        }
        for (ConnectionEntity connection : connections) {
            int source = connection.getSourceId().intValue();
            int target = connection.getTargetId().intValue();
            double weight = connection.getWeight() == null ? 1.0 : connection.getWeight();
            graph.addEdge(source, target, weight);
        }
        return graph;
    }

    public void addNode(int node) {
        nodes.add(node);
        adj.computeIfAbsent(node, ignored -> new HashSet<>());
    }

    public void addEdge(int a, int b, double weight) {
        addNode(a);
        addNode(b);
        edges.put(key(a, b), weight);
        adj.get(a).add(b);
        adj.get(b).add(a);
    }

    public Set<Integer> nodes() {
        return nodes;
    }

    public List<Integer> sortedNodes() {
        List<Integer> sorted = new ArrayList<>(nodes);
        Collections.sort(sorted);
        return sorted;
    }

    public List<Integer> neighbors(int node) {
        Set<Integer> neighbors = adj.get(node);
        if (neighbors == null) {
            return List.of();
        }
        List<Integer> sorted = new ArrayList<>(neighbors);
        Collections.sort(sorted);
        return sorted;
    }

    public double weight(int a, int b) {
        Double weight = edges.get(key(a, b));
        return weight == null ? 1.0 : weight;
    }

    private static long key(int a, int b) {
        int low = Math.min(a, b);
        int high = Math.max(a, b);
        return (((long) low) << 32) ^ (high & 0xffffffffL);
    }
}
