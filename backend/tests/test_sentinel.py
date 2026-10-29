import os
import tempfile
import unittest

os.environ["SENTINEL_DB_PATH"] = os.path.join(tempfile.gettempdir(), "sentinel_test.db")

from app import database
from app.algorithms import Algorithms
from app.models import Graph
from app.simulator import Simulation


class TestNetwork(unittest.TestCase):
    def setUp(self):
        database.init_db()
    
    def test_graph_from_db(self):
        g = Graph()
        g.add_node(1)
        g.add_node(2)
        g.add_edge(1, 2, 3.5)
        self.assertIn(1, g.nodes)
        self.assertEqual(g.weight(1, 2), 3.5)
    
    def test_bfs_dfs(self):
        g = Graph()
        for i in range(1, 6):
            g.add_node(i)
        g.add_edge(1, 2)
        g.add_edge(1, 3)
        g.add_edge(2, 4)
        g.add_edge(3, 5)
        bfs_res = Algorithms.bfs(g, 1)
        dfs_res = Algorithms.dfs(g, 1)
        self.assertIn(1, bfs_res["traversal_order"])
        self.assertIn(1, dfs_res["traversal_order"])
        self.assertEqual(bfs_res["time_complexity"], "O(V + E)")
    
    def test_dijkstra(self):
        g = Graph()
        for i in range(1, 5):
            g.add_node(i)
        g.add_edge(1, 2, 1)
        g.add_edge(2, 3, 2)
        g.add_edge(1, 4, 5)
        res = Algorithms.dijkstra(g, 1, 3)
        self.assertEqual(res["path"], [1, 2, 3])
        self.assertEqual(res["total_cost"], 3)
    
    def test_prim(self):
        g = Graph()
        for i in range(1, 5):
            g.add_node(i)
        g.add_edge(1, 2, 1)
        g.add_edge(2, 3, 1)
        g.add_edge(3, 4, 1)
        g.add_edge(1, 4, 4)
        res = Algorithms.prim(g)
        self.assertTrue(res["connected"])
        self.assertEqual(res["total_weight"], 3)
    
    def test_disconnected_prim(self):
        g = Graph()
        g.add_node(1)
        g.add_node(2)
        g.add_node(3)
        g.add_edge(1, 2, 1)
        res = Algorithms.prim(g)
        self.assertFalse(res["connected"])
    
    def test_invalid_inputs_dijkstra(self):
        g = Graph()
        g.add_node(1)
        with self.assertRaises(ValueError):
            Algorithms.dijkstra(g, 1, 99)
    
    def test_seeded_sim_reproducible(self):
        g = Graph()
        for i in range(1, 6):
            g.add_node(i)
        g.add_edge(1, 2)
        g.add_edge(2, 3)
        g.add_edge(1, 4)
        sim1 = Simulation(g, 1, 0.8, seed=42)
        r1 = sim1.run()
        sim2 = Simulation(g, 1, 0.8, seed=42)
        r2 = sim2.run()
        self.assertEqual(r1["num_compromised"], r2["num_compromised"])
    
    def test_isolation_prevents_propagation(self):
        g = Graph()
        for i in range(1, 4):
            g.add_node(i)
        g.add_edge(1, 2)
        g.add_edge(2, 3)
        sim = Simulation(g, 1, 1.0, seed=1)
        sim.apply_containment("initial-node isolation")
        r = sim.run()
        # Initial isolated, should not propagate much
        self.assertEqual(r["num_isolated"], 1)


if __name__ == "__main__":
    unittest.main()
