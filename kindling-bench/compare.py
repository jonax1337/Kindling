"""Prints the benchmark results of several labels side by side: per figure the mean of its runs, the half range of
the runs (half the gap between the highest and the lowest value) and how many runs there were (nN).

Every label after the first is also tested against the first: its cell ends in the p-value of Welch's t-test. A '*'
marks a difference as significant, which takes both p < 0.05 and a difference of the means larger than twice the
larger half range of the two labels. The test needs two runs per label; with fewer there is no p-value, so no
difference can be called significant. Rows that no run has are left out, so mixed and bases runs can be compared
with the same script. The counters of a bases run must be identical in every run of every label; a row marked '!'
differs, which means behaviour changed.

    python -I compare.py up-stock up-coh up-elastic ...
"""

import json
import math
import sys
from pathlib import Path
from statistics import mean, variance

RUNS = Path(__file__).resolve().parent / "runs"
FIGURES = [
    ("travel mspt avg", ("travel", "mspt_avg")),
    ("travel mspt p95", ("travel", "mspt_p95")),
    ("travel GC count", ("travel", "gc_count")),
    ("travel GC pause ms", ("travel", "gc_pause_millis")),
    ("travel main run ms", ("travel", "main_run_ms")),
    ("travel main wait ms", ("travel", "main_wait_ms")),
    ("travel CPU steal %", ("travel", "cpu_steal_pct")),
    ("travel CPU idle %", ("travel", "cpu_idle_pct")),
    ("steady mspt avg", ("steady", "mspt_avg")),
    ("steady mspt p95", ("steady", "mspt_p95")),
    ("steady GC pause ms", ("steady", "gc_pause_millis")),
    ("steady main wait ms", ("steady", "main_wait_ms")),
    ("bases mspt avg", ("bases", "mspt_avg")),
    ("bases mspt p95", ("bases", "mspt_p95")),
    ("bases mspt p99", ("bases", "mspt_p99")),
    ("bases GC pause ms", ("bases", "gc_pause_millis")),
    ("bases main wait ms", ("bases", "main_wait_ms")),
    ("warm-up s", ("warmup_seconds",)),
    ("join full view ms", ("join_full_view_ms",)),
    ("chunk packets warm-up", ("chunk_packets_warmup",)),
    ("chunk packets travel", ("chunk_packets_travel",)),
    ("flown blocks", ("flown_blocks_avg",)),
    ("lead avg chunks", ("chunk_lead", "lead_avg")),
    ("lead min chunks", ("chunk_lead", "lead_min")),
    ("lead <= 2 %", ("chunk_lead", "close_pct")),
    ("lead 0 (void) %", ("chunk_lead", "hole_pct")),
    ("own chunk missing %", ("chunk_lead", "own_chunk_missing_pct")),
    ("worst ray lead avg", ("chunk_lead", "worst_ray_lead_avg")),
    ("worst ray void %", ("chunk_lead", "worst_ray_hole_pct")),
    ("new chunks / s", ("chunk_lead", "new_chunks_per_second")),
    ("entities", ("entities",)),
    ("heap after GC MB", ("heap_used_after_gc_mb",)),
    ("heap committed MB", ("heap_committed_mb",)),
    ("RSS peak MB", ("rss_peak_mb",)),
    ("RSS end MB", ("rss_last_mb",)),
    ("idle heap commit MB", ("idle", "heap_committed_mb")),
    ("idle RSS MB", ("rss_idle_mb",)),
    ("GC count", ("gc_count",)),
    ("GC ms (all beans)", ("gc_millis",)),
    ("GC pause ms", ("gc_pause_millis",)),
    ("CPU s", ("process_cpu_seconds",)),
]
NAME_WIDTH = 26
SIGNIFICANCE_LEVEL = 0.05
HALF_RANGES_APART = 2
LEGEND = ("cell: mean ±half range, nN runs; every label but the first adds p of Welch's t-test against the first "
          "label, '*' = p < 0.05 and difference > 2 x the larger half range")


def main(labels: list[str]) -> None:
    results = {label: json.loads((RUNS / f"{label}.json").read_text(encoding="utf-8")) for label in labels}
    rows = []
    for name, path in FIGURES:
        values_per_label = [[figure(run, path) for run in results[label]] for label in labels]
        if any(value is not None for values in values_per_label for value in values):
            rows.append((name, row_cells(values_per_label)))
    counters = counter_rows(results)
    width = max(len(label) for label in labels) + 2
    width = max([width] + [len(cell) + 2 for _, cells in rows + counters for cell in cells])
    print(f"{'':{NAME_WIDTH}}" + "".join(f"{label:>{width}}" for label in labels))
    for name, cells in rows:
        print_row(name, cells, width)
    if counters:
        print("\ncounters (identical in every run, or marked '!')")
    for name, cells in counters:
        print_row(name, cells, width)
    warn_unsettled(results)
    print(f"\n{LEGEND}")


def print_row(name: str, cells: list[str], width: int) -> None:
    print(f"{name:{NAME_WIDTH}}" + "".join(f"{cell:>{width}}" for cell in cells))


def warn_unsettled(results: dict) -> None:
    """A bases run that built before its plots ticked measured something else; its numbers do not compare."""
    for label, runs in results.items():
        for run in runs:
            if run.get("settled") is False:
                print(f"\nWARNING: {label} run {run.get('attempt')} built its bases before world generation settled")


def counter_rows(results: dict) -> list[tuple[str, list[str]]]:
    names = sorted({name for runs in results.values() for run in runs for name in run.get("counters", {})})
    rows = []
    for name in names:
        values = [[run.get("counters", {}).get(name) for run in runs] for runs in results.values()]
        differs = len({value for runs in values for value in runs if value is not None}) > 1
        rows.append((("! " if differs else "  ") + name,
                     [summary([value for value in runs if value is not None]) for runs in values]))
    return rows


def row_cells(values_per_label: list[list]) -> list[str]:
    """One cell per label: its summary, and for every label but the first the test against the first one."""
    samples = [[value for value in values if value is not None] for values in values_per_label]
    return [summary(values) + ("" if index == 0 else comparison(samples[0], values))
            for index, values in enumerate(samples)]


def comparison(baseline: list[float], values: list[float]) -> str:
    p_value = welch_p_value(baseline, values)
    if p_value is None:
        return " p=n/a"
    return f" p={p_value:.3f}" + ("*" if is_significant(baseline, values, p_value) else "")


def is_significant(baseline: list[float], values: list[float], p_value: float) -> bool:
    gap = abs(mean(values) - mean(baseline))
    return p_value < SIGNIFICANCE_LEVEL and gap > HALF_RANGES_APART * max(half_range(baseline), half_range(values))


def welch_p_value(first: list[float], second: list[float]) -> float | None:
    """Two-sided p-value of Welch's t-test, or None where a sample has fewer than two values."""
    if len(first) < 2 or len(second) < 2:
        return None
    spread_first, spread_second = variance(first) / len(first), variance(second) / len(second)
    if spread_first + spread_second == 0:
        return 1.0 if mean(first) == mean(second) else 0.0
    t = (mean(first) - mean(second)) / math.sqrt(spread_first + spread_second)
    degrees = (spread_first + spread_second) ** 2 / (
        spread_first ** 2 / (len(first) - 1) + spread_second ** 2 / (len(second) - 1))
    return incomplete_beta(degrees / 2, 0.5, degrees / (degrees + t * t))


def incomplete_beta(a: float, b: float, x: float) -> float:
    """The regularised incomplete beta function I_x(a, b), by the continued fraction of Numerical Recipes."""
    if x <= 0:
        return 0.0
    if x >= 1:
        return 1.0
    front = math.exp(math.lgamma(a + b) - math.lgamma(a) - math.lgamma(b) + a * math.log(x) + b * math.log1p(-x))
    if x < (a + 1) / (a + b + 2):
        return front * beta_fraction(a, b, x) / a
    return 1 - front * beta_fraction(b, a, 1 - x) / b


def beta_fraction(a: float, b: float, x: float) -> float:
    """Lentz's evaluation of the continued fraction behind the incomplete beta function."""
    tiny, precision = 1e-300, 3e-14
    c = 1.0
    d = 1 / nonzero(1 - (a + b) * x / (a + 1), tiny)
    fraction = d
    for step in range(1, 300):
        even = step * (b - step) * x / ((a - 1 + 2 * step) * (a + 2 * step))
        odd = -(a + step) * (a + b + step) * x / ((a + 2 * step) * (a + 1 + 2 * step))
        for term in (even, odd):
            d = 1 / nonzero(1 + term * d, tiny)
            c = nonzero(1 + term / c, tiny)
            fraction *= d * c
        if abs(d * c - 1) < precision:
            break
    return fraction


def nonzero(value: float, tiny: float) -> float:
    return value if abs(value) > tiny else tiny


def figure(run: dict, path: tuple[str, ...]):
    """The value at the key path, or None where the run does not have it."""
    value = run
    for key in path:
        if not isinstance(value, dict) or key not in value:
            return None
        value = value[key]
    return value


def half_range(values: list[float]) -> float:
    return (max(values) - min(values)) / 2


def summary(values: list[float]) -> str:
    if not values:
        return "-"
    return f"{mean(values):.1f} ±{half_range(values):.1f} n{len(values)}"


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(sys.argv[1:])
