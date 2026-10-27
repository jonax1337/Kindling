"""Prints the benchmark results of several labels side by side, each figure as the mean of its runs plus their spread.

Rows that no run has are left out, so mixed and bases runs can be compared with the same script. The counters of a
bases run must be identical in every run of every label; a row marked '!' differs, which means behaviour changed.

    python -I compare.py up-stock up-coh up-elastic ...
"""

import json
import sys
from pathlib import Path
from statistics import mean

RUNS = Path(__file__).resolve().parent / "runs"
FIGURES = [
    ("travel mspt avg", ("travel", "mspt_avg")),
    ("travel mspt p95", ("travel", "mspt_p95")),
    ("steady mspt avg", ("steady", "mspt_avg")),
    ("steady mspt p95", ("steady", "mspt_p95")),
    ("bases mspt avg", ("bases", "mspt_avg")),
    ("bases mspt p95", ("bases", "mspt_p95")),
    ("bases mspt p99", ("bases", "mspt_p99")),
    ("flown blocks", ("flown_blocks_avg",)),
    ("lead avg chunks", ("chunk_lead", "lead_avg")),
    ("lead min chunks", ("chunk_lead", "lead_min")),
    ("lead <= 2 %", ("chunk_lead", "close_pct")),
    ("lead 0 (void) %", ("chunk_lead", "hole_pct")),
    ("new chunks / s", ("chunk_lead", "new_chunks_per_second")),
    ("entities", ("entities",)),
    ("heap after GC MB", ("heap_used_after_gc_mb",)),
    ("heap committed MB", ("heap_committed_mb",)),
    ("RSS peak MB", ("rss_peak_mb",)),
    ("RSS end MB", ("rss_last_mb",)),
    ("idle heap commit MB", ("idle", "heap_committed_mb")),
    ("idle RSS MB", ("rss_idle_mb",)),
    ("GC count", ("gc_count",)),
    ("GC ms", ("gc_millis",)),
    ("CPU s", ("process_cpu_seconds",)),
]
NAME_WIDTH = 24


def main(labels: list[str]) -> None:
    results = {label: json.loads((RUNS / f"{label}.json").read_text(encoding="utf-8")) for label in labels}
    width = max(len(label) for label in labels) + 10
    print(f"{'':{NAME_WIDTH}}" + "".join(f"{label:>{width}}" for label in labels))
    for name, path in FIGURES:
        print_row(name, [[figure(run, path) for run in results[label]] for label in labels], width)
    print_counters(results, width)
    warn_unsettled(results)


def warn_unsettled(results: dict) -> None:
    """A bases run that built before its plots ticked measured something else; its numbers do not compare."""
    for label, runs in results.items():
        for run in runs:
            if run.get("settled") is False:
                print(f"\nWARNING: {label} run {run.get('attempt')} built its bases before world generation settled")


def print_counters(results: dict, width: int) -> None:
    names = sorted({name for runs in results.values() for run in runs for name in run.get("counters", {})})
    if names:
        print("\ncounters (identical in every run, or marked '!')")
    for name in names:
        values = [[run.get("counters", {}).get(name) for run in runs] for runs in results.values()]
        differs = len({value for runs in values for value in runs if value is not None}) > 1
        print_row(("! " if differs else "  ") + name, values, width)


def print_row(name: str, values_per_label: list[list], width: int) -> None:
    if all(value is None for values in values_per_label for value in values):
        return
    cells = [summary([value for value in values if value is not None]) for values in values_per_label]
    print(f"{name:{NAME_WIDTH}}" + "".join(f"{cell:>{width}}" for cell in cells))


def figure(run: dict, path: tuple[str, ...]):
    """The value at the key path, or None where the run does not have it."""
    value = run
    for key in path:
        if not isinstance(value, dict) or key not in value:
            return None
        value = value[key]
    return value


def summary(values: list[float]) -> str:
    if not values:
        return "-"
    return f"{mean(values):.1f} ±{(max(values) - min(values)) / 2:.1f}"


if __name__ == "__main__":
    main(sys.argv[1:])
