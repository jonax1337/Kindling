"""Prints the benchmark results of several labels side by side, each figure as the mean of its runs plus their spread.

    python -I compare.py up-stock up-coh up-elastic ...
"""

import json
import sys
from pathlib import Path
from statistics import mean

RUNS = Path(__file__).resolve().parent / "runs"
FIGURES = [
    ("travel mspt avg", lambda r: r["travel"]["mspt_avg"]),
    ("travel mspt p95", lambda r: r["travel"]["mspt_p95"]),
    ("steady mspt avg", lambda r: r["steady"]["mspt_avg"]),
    ("steady mspt p95", lambda r: r["steady"]["mspt_p95"]),
    ("flown blocks", lambda r: r["flown_blocks_avg"]),
    ("lead avg chunks", lambda r: r.get("chunk_lead", {}).get("lead_avg", 0)),
    ("lead min chunks", lambda r: r.get("chunk_lead", {}).get("lead_min", 0)),
    ("lead <= 2 %", lambda r: r.get("chunk_lead", {}).get("close_pct", 0)),
    ("lead 0 (void) %", lambda r: r.get("chunk_lead", {}).get("hole_pct", 0)),
    ("new chunks / s", lambda r: r.get("chunk_lead", {}).get("new_chunks_per_second", 0)),
    ("entities", lambda r: r["entities"]),
    ("heap after GC MB", lambda r: r["heap_used_after_gc_mb"]),
    ("heap committed MB", lambda r: r["heap_committed_mb"]),
    ("RSS peak MB", lambda r: r["rss_peak_mb"]),
    ("RSS end MB", lambda r: r["rss_last_mb"]),
    ("idle heap commit MB", lambda r: r.get("idle", {}).get("heap_committed_mb", 0)),
    ("idle RSS MB", lambda r: r.get("rss_idle_mb", 0)),
    ("GC count", lambda r: r["gc_count"]),
    ("GC ms", lambda r: r["gc_millis"]),
    ("CPU s", lambda r: r["process_cpu_seconds"]),
]


def main(labels: list[str]) -> None:
    results = {label: json.loads((RUNS / f"{label}.json").read_text(encoding="utf-8")) for label in labels}
    width = max(len(label) for label in labels) + 2
    print(f"{'':20}" + "".join(f"{label:>{width + 8}}" for label in labels))
    for name, figure in FIGURES:
        cells = [summary([figure(run) for run in results[label]]) for label in labels]
        print(f"{name:20}" + "".join(f"{cell:>{width + 8}}" for cell in cells))


def summary(values: list[float]) -> str:
    return f"{mean(values):.1f} ±{(max(values) - min(values)) / 2:.1f}"


if __name__ == "__main__":
    main(sys.argv[1:])
