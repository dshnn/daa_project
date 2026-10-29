from __future__ import annotations

from collections import deque
from typing import Dict, List, Tuple

from .models import Graph


class Algorithms:
    @staticmethod
    def bfs(graph: Graph, start: int) -> Dict[str, object]:
        if start not in graph.nodes:
            raise ValueError("Start node not in graph")
        visited = set([start])
        order = [start]
        queue = deque([start])
        while queue:
            cur = queue.popleft()
            for nb in sorted(graph.neighbors(cur)):  # deterministic-ish order
                if nb not in visited:
                    visited.add(nb)
                    order.append(nb)
                    queue.append(nb)
        return {
            "traversal_order": order,
            "explanation": "Breadth-First Search visits nodes level by level using a queue.",
            "time_complexity": "O(V + E)",
        }

    @staticmethod
    def dfs(graph: Graph, start: int) -> Dict[str, object]:
        if start not in graph.nodes:
            raise ValueError("Start node not in graph")
        visited = set()
        order: List[int] = []

        def _dfs(v: int):
            visited.add(v)
            order.append(v)
            for nb in sorted(graph.neighbors(v)):
                if nb not in visited:
                    _dfs(nb)

        _dfs(start)
        return {
            "traversal_order": order,
            "explanation": "Depth-First Search explores as deep as possible along each branch using recursion/stack.",
            "time_complexity": "O(V + E)",
        }

    @staticmethod
    def dijkstra(graph: Graph, start: int, end: int) -> Dict[str, object]:
        if start not in graph.nodes or end not in graph.nodes:
            raise ValueError("Start or end node not in graph")
        # Dijkstra
        dist: Dict[int, float] = {n: float("inf") for n in graph.nodes}
        prev: Dict[int, int | None] = {n: None for n in graph.nodes}
        dist[start] = 0.0
        unvisited = set(graph.nodes)
        while unvisited:
            # pick min
            cur = None
            curd = float("inf")
            for n in unvisited:
                if dist[n] < curd:
                    curd = dist[n]
                    cur = n
            if cur is None or curd == float("inf"):
                break
            if cur == end:
                break
            unvisited.remove(cur)
            for nb in graph.neighbors(cur):
                if nb in unvisited:
                    nd = curd + graph.weight(cur, nb)
                    if nd < dist[nb]:
                        dist[nb] = nd
                        prev[nb] = cur
        # reconstruct path
        if dist[end] == float("inf"):
            return {
                "path": [],
                "total_cost": float("inf"),
                "explanation": "No path found between start and end.",
                "time_complexity": "O((V + E) log V) with binary heap (simple O(V^2) implementation here)",
            }
        path_rev = []
        cur = end
        while cur is not None:
            path_rev.append(cur)
            if cur == start:
                break
            cur = prev[cur]
        path_rev.reverse()
        if path_rev[0] != start:
            return {
                "path": [],
                "total_cost": float("inf"),
                "explanation": "No path found between start and end.",
                "time_complexity": "O((V + E) log V) with binary heap (simple O(V^2) implementation here)",
            }
        return {
            "path": path_rev,
            "total_cost": dist[end],
            "explanation": "Dijkstra finds shortest paths from source in graphs with non-negative weights.",
            "time_complexity": "O((V + E) log V) with binary heap (simple O(V^2) implementation here)",
        }

    @staticmethod
    def prim(graph: Graph) -> Dict[str, object]:
        if not graph.nodes:
            return {
                "mst_edges": [],
                "total_weight": 0.0,
                "explanation": "Empty graph has no MST.",
                "time_complexity": "O((V^2)) for simple implementation",
            }
        # Check connectivity roughly? Prim gives MST on connected component; handle disconnected
        # Simple Prim starting from first node
        nodes_list = list(graph.nodes)
        start = nodes_list[0]
        visited = set([start])
        mst_edges: List[Tuple[int, int, float]] = []
        total = 0.0
        while len(visited) < len(nodes_list):
            best = None
            best_w = float("inf")
            best_e = None
            for u in visited:
                for v in graph.neighbors(u):
                    if v not in visited:
                        w = graph.weight(u, v)
                        key = (min(u, v), max(u, v))
                        if w < best_w:
                            best_w = w
                            best = (u, v)
                            best_e = key
            if best is None:
                break  # disconnected
            mst_edges.append((best[0], best[1], best_w))
            total += best_w
            visited.add(best[1])
        if len(visited) != len(nodes_list):
            return {
                "mst_edges": [(e[0], e[1], e[2]) for e in mst_edges],
                "total_weight": total,
                "explanation": "Graph is disconnected; Prim returned MST for the connected component of start.",
                "time_complexity": "O((V^2)) for simple implementation",
                "connected": False,
            }
        return {
            "mst_edges": [(e[0], e[1], e[2]) for e in mst_edges],
            "total_weight": total,
            "explanation": "Prim’s algorithm builds MST by growing tree from an arbitrary start vertex.",
            "time_complexity": "O((V^2)) for simple implementation",
            "connected": True,
        }
