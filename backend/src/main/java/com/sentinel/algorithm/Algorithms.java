package com.sentinel.algorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Port of the original Python DAA algorithms with the same result payloads.
 */
public final class Algorithms {

    private static final String DIJKSTRA_COMPLEXITY =
            "O((V + E) log V) with binary heap (simple O(V^2) implementation here)";

    private Algorithms() {
    }

    public static Map<String, Object> bfs(Graph graph, int start) {
        if (!graph.nodes().contains(start)) {
            throw new IllegalArgumentException("Start node not in graph");
        }
        Set<Integer> visited = new HashSet<>();
        visited.add(start);
        List<Integer> order = new ArrayList<>();
        order.add(start);
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int neighbor : graph.neighbors(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    order.add(neighbor);
                    queue.addLast(neighbor);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traversal_order", order);
        result.put("explanation", "Breadth-First Search visits nodes level by level using a queue.");
        result.put("time_complexity", "O(V + E)");
        return result;
    }

    public static Map<String, Object> dfs(Graph graph, int start) {
        if (!graph.nodes().contains(start)) {
            throw new IllegalArgumentException("Start node not in graph");
        }
        Set<Integer> visited = new HashSet<>();
        List<Integer> order = new ArrayList<>();
        dfsVisit(graph, start, visited, order);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traversal_order", order);
        result.put("explanation",
                "Depth-First Search explores as deep as possible along each branch using recursion/stack.");
        result.put("time_complexity", "O(V + E)");
        return result;
    }

    private static void dfsVisit(Graph graph, int node, Set<Integer> visited, List<Integer> order) {
        visited.add(node);
        order.add(node);
        for (int neighbor : graph.neighbors(node)) {
            if (!visited.contains(neighbor)) {
                dfsVisit(graph, neighbor, visited, order);
            }
        }
    }

    public static Map<String, Object> dijkstra(Graph graph, int start, int end) {
        if (!graph.nodes().contains(start) || !graph.nodes().contains(end)) {
            throw new IllegalArgumentException("Start or end node not in graph");
        }

        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> prev = new HashMap<>();
        for (int node : graph.nodes()) {
            dist.put(node, Double.POSITIVE_INFINITY);
            prev.put(node, null);
        }
        dist.put(start, 0.0);

        TreeSet<Integer> unvisited = new TreeSet<>(graph.nodes());
        while (!unvisited.isEmpty()) {
            Integer current = null;
            double currentDistance = Double.POSITIVE_INFINITY;
            for (int node : unvisited) {
                double distance = dist.get(node);
                if (distance < currentDistance) {
                    currentDistance = distance;
                    current = node;
                }
            }
            if (current == null || currentDistance == Double.POSITIVE_INFINITY) {
                break;
            }
            if (current == end) {
                break;
            }
            unvisited.remove(current);
            for (int neighbor : graph.neighbors(current)) {
                if (unvisited.contains(neighbor)) {
                    double nextDistance = currentDistance + graph.weight(current, neighbor);
                    if (nextDistance < dist.get(neighbor)) {
                        dist.put(neighbor, nextDistance);
                        prev.put(neighbor, current);
                    }
                }
            }
        }

        if (dist.get(end) == Double.POSITIVE_INFINITY) {
            return noPath();
        }

        LinkedList<Integer> path = new LinkedList<>();
        Integer current = end;
        while (current != null) {
            path.addFirst(current);
            if (current == start) {
                break;
            }
            current = prev.get(current);
        }
        if (path.isEmpty() || path.getFirst() != start) {
            return noPath();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        result.put("total_cost", dist.get(end));
        result.put("explanation",
                "Dijkstra finds shortest paths from source in graphs with non-negative weights.");
        result.put("time_complexity", DIJKSTRA_COMPLEXITY);
        return result;
    }

    private static Map<String, Object> noPath() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", List.of());
        result.put("total_cost", Double.POSITIVE_INFINITY);
        result.put("explanation", "No path found between start and end.");
        result.put("time_complexity", DIJKSTRA_COMPLEXITY);
        return result;
    }

    public static Map<String, Object> prim(Graph graph) {
        if (graph.nodes().isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mst_edges", List.of());
            result.put("total_weight", 0.0);
            result.put("explanation", "Empty graph has no MST.");
            result.put("time_complexity", "O((V^2)) for simple implementation");
            return result;
        }

        List<Integer> nodes = graph.sortedNodes();
        int start = nodes.get(0);
        Set<Integer> visited = new HashSet<>();
        visited.add(start);
        List<List<Object>> mstEdges = new ArrayList<>();
        double total = 0.0;

        while (visited.size() < nodes.size()) {
            Integer bestSource = null;
            Integer bestTarget = null;
            double bestWeight = Double.POSITIVE_INFINITY;

            for (int source : visited) {
                for (int target : graph.neighbors(source)) {
                    if (!visited.contains(target)) {
                        double weight = graph.weight(source, target);
                        if (weight < bestWeight) {
                            bestWeight = weight;
                            bestSource = source;
                            bestTarget = target;
                        }
                    }
                }
            }

            if (bestSource == null) {
                break;
            }
            mstEdges.add(List.of(bestSource, bestTarget, bestWeight));
            total += bestWeight;
            visited.add(bestTarget);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mst_edges", mstEdges);
        result.put("total_weight", total);
        if (visited.size() != nodes.size()) {
            result.put("explanation",
                    "Graph is disconnected; Prim returned MST for the connected component of start.");
            result.put("time_complexity", "O((V^2)) for simple implementation");
            result.put("connected", false);
        } else {
            result.put("explanation",
                    "Prim\u2019s algorithm builds MST by growing tree from an arbitrary start vertex.");
            result.put("time_complexity", "O((V^2)) for simple implementation");
            result.put("connected", true);
        }
        return result;
    }
}
