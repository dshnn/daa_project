package com.sentinel;

import com.sentinel.algorithm.Algorithms;
import com.sentinel.algorithm.Graph;
import com.sentinel.simulation.Simulation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentinelTest {

    @Test
    void graphAddsNodesAndWeightedEdges() {
        Graph graph = new Graph();
        graph.addNode(1);
        graph.addNode(2);
        graph.addEdge(1, 2, 3.5);

        assertTrue(graph.nodes().contains(1));
        assertEquals(3.5, graph.weight(1, 2), 0.0001);
    }

    @Test
    void bfsAndDfsVisitGraph() {
        Graph graph = new Graph();
        for (int i = 1; i <= 5; i++) {
            graph.addNode(i);
        }
        graph.addEdge(1, 2, 1);
        graph.addEdge(1, 3, 1);
        graph.addEdge(2, 4, 1);
        graph.addEdge(3, 5, 1);

        Map<String, Object> bfs = Algorithms.bfs(graph, 1);
        Map<String, Object> dfs = Algorithms.dfs(graph, 1);

        @SuppressWarnings("unchecked")
        List<Integer> bfsOrder = (List<Integer>) bfs.get("traversal_order");
        @SuppressWarnings("unchecked")
        List<Integer> dfsOrder = (List<Integer>) dfs.get("traversal_order");

        assertTrue(bfsOrder.contains(1));
        assertTrue(dfsOrder.contains(1));
        assertEquals("O(V + E)", bfs.get("time_complexity"));
    }

    @Test
    void dijkstraFindsShortestPath() {
        Graph graph = new Graph();
        for (int i = 1; i <= 4; i++) {
            graph.addNode(i);
        }
        graph.addEdge(1, 2, 1);
        graph.addEdge(2, 3, 2);
        graph.addEdge(1, 4, 5);

        Map<String, Object> result = Algorithms.dijkstra(graph, 1, 3);

        assertEquals(List.of(1, 2, 3), result.get("path"));
        assertEquals(3.0, ((Number) result.get("total_cost")).doubleValue(), 0.0001);
    }

    @Test
    void primBuildsMinimumSpanningTree() {
        Graph graph = new Graph();
        for (int i = 1; i <= 4; i++) {
            graph.addNode(i);
        }
        graph.addEdge(1, 2, 1);
        graph.addEdge(2, 3, 1);
        graph.addEdge(3, 4, 1);
        graph.addEdge(1, 4, 4);

        Map<String, Object> result = Algorithms.prim(graph);

        assertEquals(true, result.get("connected"));
        assertEquals(3.0, ((Number) result.get("total_weight")).doubleValue(), 0.0001);
    }

    @Test
    void primReportsDisconnectedGraph() {
        Graph graph = new Graph();
        graph.addNode(1);
        graph.addNode(2);
        graph.addNode(3);
        graph.addEdge(1, 2, 1);

        Map<String, Object> result = Algorithms.prim(graph);

        assertEquals(false, result.get("connected"));
    }

    @Test
    void dijkstraRejectsUnknownNode() {
        Graph graph = new Graph();
        graph.addNode(1);

        assertThrows(IllegalArgumentException.class, () -> Algorithms.dijkstra(graph, 1, 99));
    }

    @Test
    void seededSimulationIsReproducible() {
        Graph graph = new Graph();
        for (int i = 1; i <= 5; i++) {
            graph.addNode(i);
        }
        graph.addEdge(1, 2, 1);
        graph.addEdge(2, 3, 1);
        graph.addEdge(1, 4, 1);

        Simulation first = new Simulation(graph, 1, 0.8, 42L);
        Simulation second = new Simulation(graph, 1, 0.8, 42L);

        assertEquals(first.run().get("num_compromised"), second.run().get("num_compromised"));
    }

    @Test
    void isolationPreventsPropagation() {
        Graph graph = new Graph();
        for (int i = 1; i <= 3; i++) {
            graph.addNode(i);
        }
        graph.addEdge(1, 2, 1);
        graph.addEdge(2, 3, 1);

        Simulation simulation = new Simulation(graph, 1, 1.0, 1L);
        simulation.applyContainment("initial-node isolation", List.of());
        Map<String, Object> result = simulation.run();

        assertEquals(1, result.get("num_isolated"));
    }

    @Test
    void greedyContainmentIsolatesCompromisedNodes() {
        Graph graph = new Graph();
        for (int i = 1; i <= 3; i++) {
            graph.addNode(i);
        }
        graph.addEdge(1, 2, 1);
        graph.addEdge(1, 3, 1);

        Simulation simulation = new Simulation(graph, 1, 1.0, 7L);
        simulation.applyContainment("greedy containment", List.of());
        Map<String, Object> result = simulation.run();

        assertEquals(1, result.get("num_isolated"));
        assertEquals(0, result.get("num_compromised"));
    }
}
