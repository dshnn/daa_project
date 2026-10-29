from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Set, Tuple


@dataclass
class Graph:
    """Simple undirected/directed-agnostic graph representation for algorithms/simulation."""

    nodes: Set[int] = field(default_factory=set)
    edges: Dict[Tuple[int, int], float] = field(default_factory=dict)
    adj: Dict[int, Set[int]] = field(default_factory=dict)

    @classmethod
    def from_db(cls, devices: List[dict], connections: List[dict]) -> "Graph":
        g = cls()
        for d in devices:
            did = int(d["id"])
            g.add_node(did)
        for c in connections:
            s = int(c["source_id"])
            t = int(c["target_id"])
            w = float(c.get("weight", 1.0))
            g.add_edge(s, t, w)
        return g

    def add_node(self, node: int) -> None:
        self.nodes.add(node)
        if node not in self.adj:
            self.adj[node] = set()

    def add_edge(self, a: int, b: int, weight: float = 1.0) -> None:
        self.add_node(a)
        self.add_node(b)
        key = (min(a, b), max(a, b))
        self.edges[key] = weight
        self.adj[a].add(b)
        self.adj[b].add(a)

    def neighbors(self, node: int) -> List[int]:
        return list(self.adj.get(node, set()))

    def weight(self, a: int, b: int) -> float:
        key = (min(a, b), max(a, b))
        return self.edges.get(key, 1.0)
