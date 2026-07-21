#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "calculate_e3.py"
SPEC = importlib.util.spec_from_file_location("calculate_e3", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class CalculationTests(unittest.TestCase):
    def test_nearest_rank_percentiles(self):
        values = [42, 44, 45, 47, 49, 50, 52, 53, 55, 58, 61, 63, 66, 70, 75, 81, 89, 96, 110, 135]
        self.assertEqual(MODULE.percentile_nearest_rank(values, 0.50), 58)
        self.assertEqual(MODULE.percentile_nearest_rank(values, 0.95), 110)

    def test_fixture_summary(self):
        root = Path(__file__).resolve().parents[1]
        result = MODULE.calculate(root / "e3" / "calculation_fixture.jsonl")
        standard = result["groups"]["CALC-RUN-01/CALC-STANDARD/c10"]
        self.assertEqual(standard["count"], 20)
        self.assertEqual(standard["p50_ms"], 58)
        self.assertEqual(standard["p95_ms"], 110)
        self.assertEqual(standard["success_rate"], 1.0)
        self.assertEqual(result["cache"]["miss_ms"], 620)
        self.assertEqual(result["cache"]["hit_median_ms"], 15)


if __name__ == "__main__":
    unittest.main()
