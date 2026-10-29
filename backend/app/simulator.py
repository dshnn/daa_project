from __future__ import annotations

import random
import time
from typing import Dict, List, Set

from .models import Graph


class Simulation:
    STATES = ["Safe", "Exposed", "Compromised", "Isolated", "Recovered"]

    def __init__(
        self,
        graph: Graph,
        initial_compromised: int,
        propagation_probability: float = 0.5,
        seed: int | None = None,
    ):
        self.graph = graph
        self.rng = random.Random(seed)
        self.prob = max(0.0, min(1.0, float(propagation_probability)))
        self.initial = initial_compromised
        self.states: Dict[int, str] = {n: "Safe" for n in graph.nodes}
        if initial_compromised in graph.nodes:
            self.states[initial_compromised] = "Compromised"
        self.events: List[Dict] = []
        self.rounds = 0
        self.compromised_history: List[int] = []

    def apply_containment(self, strategy: str, manual: List[int] | None = None) -> None:
        strategy = (strategy or "none").lower()
        if strategy == "initial-node isolation" or strategy == "isolate_initial":
            if self.initial in self.states and self.states[self.initial] != "Isolated":
                self.states[self.initial] = "Isolated"
            return
        if strategy == "greedy containment" or strategy == "greedy":
            # Greedy: isolate nodes with highest degree (documented heuristic)
            candidates = []
            for n in self.graph.nodes:
                if self.states.get(n) == "Compromised":
                    candidates.append((len(self.graph.neighbors(n)), n))
            candidates.sort(reverse=True)
            for _, n in candidates[: max(1, len(candidates))]:  # isolate top compromised by degree
                if self.states.get(n) != "Isolated":
                    self.states[n] = "Isolated"
            return
        if strategy == "manual containment" or strategy == "manual":
            if manual:
                for n in manual:
                    if n in self.states and self.states[n] != "Isolated":
                        self.states[n] = "Isolated"
            return
        return

    def step(self) -> bool:
        """Perform one round. Return True if any change occurred."""
        changes = []
        # Find newly exposed/compromised based on current compromised
        current_compromised = {n for n, s in self.states.items() if s == "Compromised"}
        for c in list(current_compromised):
            if self.states.get(c) == "Isolated":
                continue  # isolated do not propagate
            for nb in self.graph.neighbors(c):
                if self.states.get(nb) == "Isolated":
                    continue
                if self.states.get(nb) == "Safe":
                    # exposed with some prob? requirement says "Compromised devices may expose connected..." - model: exposure/compromise with prob
                    if self.rng.random() < self.prob:
                        self.states[nb] = "Compromised"
                        changes.append((nb, "Compromised", f"Propagated from {c}"))
                    else:
                        self.states[nb] = "Exposed"
                        changes.append((nb, "Exposed", f"Exposed by {c}"))
        if changes:
            self.rounds += 1
            for nb, ns, reason in changes:
                self.events.append({"round_num": self.rounds, "device_id": nb, "new_state": ns, "reason": reason})
            self.compromised_history.append(self.count_compromised())
            return True
        return False

    def run(self, max_rounds: int = 50) -> Dict:
        start = time.time()
        for _ in range(max_rounds):
            if not self.step():
                break
        end = time.time()
        return {
            "rounds": self.rounds,
            "num_compromised": self.count_compromised(),
            "num_isolated": self.count_isolated(),
            "execution_time_ms": round((end - start) * 1000, 2),
            "states": dict(self.states),
            "events": self.events,
        }

    def count_compromised(self) -> int:
        return sum(1 for s in self.states.values() if s == "Compromised")

    def count_isolated(self) -> int:
        return sum(1 for s in self.states.values() if s == "Isolated")
