#!/usr/bin/env python3
"""Calculate reproducible E3 summaries from sanitized or synthetic JSONL rows."""

from __future__ import annotations

import argparse
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path


def percentile_nearest_rank(values: list[float], probability: float) -> float:
    if not values:
        raise ValueError("values must not be empty")
    ordered = sorted(values)
    index = max(0, math.ceil(probability * len(ordered)) - 1)
    return ordered[index]


def summarize(rows: list[dict]) -> dict:
    latencies = [float(row["latency_ms"]) for row in rows]
    starts = [float(row["timestamp_offset_ms"]) for row in rows]
    ends = [start + latency for start, latency in zip(starts, latencies)]
    wall_ms = max(ends) - min(starts)
    success_count = sum(1 for row in rows if row["http_status"] == 200 and str(row["business_status"]) == "2000")
    return {
        "count": len(rows),
        "mean_ms": round(statistics.fmean(latencies), 3),
        "p50_ms": percentile_nearest_rank(latencies, 0.50),
        "p95_ms": percentile_nearest_rank(latencies, 0.95),
        "min_ms": min(latencies),
        "max_ms": max(latencies),
        "success_rate": round(success_count / len(rows), 6),
        "wall_ms": round(wall_ms, 3),
        "rps": round(len(rows) / (wall_ms / 1000), 3) if wall_ms > 0 else None,
    }


def calculate(path: Path) -> dict:
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]
    groups = defaultdict(list)
    for row in rows:
        groups[(row["run_id"], row["scenario_code"], int(row["concurrency"]))].append(row)
    summaries = {
        f"{run_id}/{scenario}/c{concurrency}": summarize(group)
        for (run_id, scenario, concurrency), group in sorted(groups.items())
    }
    cache_rows = [row for row in rows if row["scenario_code"] == "CALC-CACHE"]
    cache = None
    if cache_rows:
        misses = [float(row["latency_ms"]) for row in cache_rows if row["cache_status"] == "miss"]
        hits = [float(row["latency_ms"]) for row in cache_rows if row["cache_status"] == "hit"]
        cache = {
            "miss_ms": misses[0] if len(misses) == 1 else None,
            "hit_median_ms": statistics.median(hits) if hits else None,
            "miss_to_hit_median_ratio": round(misses[0] / statistics.median(hits), 3) if len(misses) == 1 and hits else None,
        }
    return {"source_type": "synthetic_calculation_fixture", "groups": summaries, "cache": cache}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("jsonl", nargs="?", default="e3/calculation_fixture.jsonl", type=Path)
    args = parser.parse_args()
    print(json.dumps(calculate(args.jsonl), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
