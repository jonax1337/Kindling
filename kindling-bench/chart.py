"""Draws Kindling against plain Leaf as a bar chart, one SVG for light and one for dark backgrounds: per figure the
change of Kindling's mean against Leaf's, in percent. Every figure in the chart is better when lower, so a bar to the
left means Kindling is better. A bar is coloured only when compare.py would mark the difference as significant; the
others are gray.

    python -I kindling-bench/chart.py final-mixed-leaf final-mixed-rc final-bases-leaf final-bases-rc \
        docs/media/kindling-vs-leaf

The four labels are the mixed runs of Leaf and of Kindling, then the bases runs of both; the last argument is the
output path without the "-light.svg" / "-dark.svg" ending.
"""

import json
import sys
from html import escape
from pathlib import Path
from statistics import mean

# python -I leaves the script's own folder off the import path.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from compare import RUNS, figure, is_significant, welch_p_value  # noqa: E402

MIXED, BASES = "mixed", "bases"
# label, load, key path, unit, footnote mark
ROWS = [
    ("Time over the void in the flight", MIXED, ("chunk_lead", "hole_pct"), "%", ""),
    ("Tick time in the flight, p95", MIXED, ("travel", "mspt_p95"), "ms", ""),
    ("Join until the full view", MIXED, ("join_full_view_ms",), "s", ""),
    ("Tick time in the flight, average", MIXED, ("travel", "mspt_avg"), "ms", ""),
    ("Tick time standing among mobs", MIXED, ("steady", "mspt_avg"), "ms", "*"),
    ("Tick time in survival bases", BASES, ("bases", "mspt_avg"), "ms", ""),
    ("CPU time of the bases run", BASES, ("process_cpu_seconds",), "s", ""),
    ("CPU time of the flight-and-mobs run", MIXED, ("process_cpu_seconds",), "s", ""),
]
FOOTNOTE = "* Leaf was still generating the players' surroundings while they stood."
THEMES = {
    "light": {"surface": "#FAF7EF", "text": "#19291F", "muted": "#556457", "line": "#DDDCCF",
              "better": "#E0561A", "worse": "#2464C2", "even": "#8C948A"},
    "dark": {"surface": "#101A14", "text": "#FAF7EF", "muted": "#ACB9AA", "line": "#2C3E30",
             "better": "#E2601F", "worse": "#3F8EDB", "even": "#6B7569"},
}
FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif"

WIDTH = 760
LABEL_X = 24
ZERO_X = 500
PIXELS_PER_PERCENT = 2.0
TOP = 132
ROW_HEIGHT = 44
BAR_HEIGHT = 14
CORNER = 4


def main(mixed_leaf: str, mixed_kindling: str, bases_leaf: str, bases_kindling: str, out: str) -> None:
    runs = {MIXED: (load(mixed_leaf), load(mixed_kindling)), BASES: (load(bases_leaf), load(bases_kindling))}
    rows = [measure(row, *runs[row[1]]) for row in ROWS]
    run_count = min(len(side) for pair in runs.values() for side in pair)
    for theme, colors in THEMES.items():
        Path(f"{out}-{theme}.svg").write_text(svg(rows, run_count, colors), encoding="utf-8")


def load(label: str) -> list[dict]:
    return json.loads((RUNS / f"{label}.json").read_text(encoding="utf-8"))


def measure(row: tuple, leaf_runs: list[dict], kindling_runs: list[dict]) -> dict:
    name, _, path, unit, mark = row
    leaf = [scaled(figure(run, path), path) for run in leaf_runs]
    kindling = [scaled(figure(run, path), path) for run in kindling_runs]
    p_value = welch_p_value(leaf, kindling)
    significant = p_value is not None and is_significant(leaf, kindling, p_value)
    change = (mean(kindling) - mean(leaf)) / mean(leaf) * 100
    verdict = "even" if not significant else ("better" if change < 0 else "worse")
    return {"name": name + mark, "leaf": mean(leaf), "kindling": mean(kindling), "unit": unit,
            "change": change, "verdict": verdict}


def scaled(value: float, path: tuple[str, ...]) -> float:
    """Milliseconds of the join are shown in seconds."""
    return value / 1000 if path == ("join_full_view_ms",) else value


def svg(rows: list[dict], run_count: int, colors: dict) -> str:
    height = TOP + ROW_HEIGHT * len(rows) + 56
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{height}" viewBox="0 0 {WIDTH} {height}" '
        f'role="img" aria-labelledby="title desc" font-family="{FONT}">',
        '<title id="title">Kindling against plain Leaf</title>',
        f'<desc id="desc">{escape(description(rows))}</desc>',
        f'<rect width="{WIDTH}" height="{height}" rx="12" fill="{colors["surface"]}"/>',
        text(LABEL_X, 38, "Kindling against plain Leaf", colors["text"], 19, weight="700"),
        text(LABEL_X, 62, f"Change of Kindling's mean of {run_count} runs against Leaf's, same base and machine. "
             "Every figure is better when lower.", colors["muted"], 13),
    ]
    parts += legend(colors)
    parts += axis(rows, colors)
    for index, row in enumerate(rows):
        parts += bar_row(row, TOP + index * ROW_HEIGHT, colors)
    parts.append(text(LABEL_X, height - 22, FOOTNOTE, colors["muted"], 12))
    parts.append("</svg>")
    return "\n".join(parts) + "\n"


def description(rows: list[dict]) -> str:
    return "; ".join(f"{row['name']}: Leaf {value(row['leaf'], row['unit'])}, Kindling "
                     f"{value(row['kindling'], row['unit'])} ({percent(row['change'])})" for row in rows)


def legend(colors: dict) -> list[str]:
    parts, x = [], LABEL_X
    for verdict, label in (("better", "Kindling better"), ("worse", "Kindling worse"),
                           ("even", "No significant difference")):
        parts.append(f'<rect x="{x}" y="80" width="12" height="12" rx="3" fill="{colors[verdict]}"/>')
        parts.append(text(x + 18, 91, label, colors["text"], 13))
        x += 18 + 8 * len(label) + 28
    return parts


def axis(rows: list[dict], colors: dict) -> list[str]:
    bottom = TOP + ROW_HEIGHT * len(rows) - 12
    parts = []
    for grid in (-100, -50, 50):
        x = ZERO_X + grid * PIXELS_PER_PERCENT
        parts.append(f'<line x1="{x}" y1="{TOP - 14}" x2="{x}" y2="{bottom}" stroke="{colors["line"]}" stroke-width="1"/>')
        parts.append(text(x, TOP - 20, percent(grid), colors["muted"], 11, anchor="middle"))
    parts.append(f'<line x1="{ZERO_X}" y1="{TOP - 14}" x2="{ZERO_X}" y2="{bottom}" stroke="{colors["muted"]}" '
                 'stroke-width="1"/>')
    parts.append(text(ZERO_X, TOP - 20, "Leaf", colors["muted"], 11, anchor="middle", weight="700"))
    return parts


def bar_row(row: dict, top: int, colors: dict) -> list[str]:
    center = top + BAR_HEIGHT / 2
    length = max(min(row["change"], 100), -100) * PIXELS_PER_PERCENT
    parts = [
        text(LABEL_X, center + 1, row["name"], colors["text"], 13, weight="600"),
        text(LABEL_X, center + 17, f"Leaf {value(row['leaf'], row['unit'])} → Kindling "
             f"{value(row['kindling'], row['unit'])}", colors["muted"], 12),
        f'<path d="{bar_path(length, top)}" fill="{colors[row["verdict"]]}"/>',
    ]
    tip = ZERO_X + length + (-8 if length < 0 else 8)
    parts.append(text(tip, center + 5, percent(row["change"]), colors["text"], 13, weight="700",
                      anchor="end" if length < 0 else "start"))
    return parts


def percent(change: float) -> str:
    """A signed percentage with a real minus sign."""
    return f"{change:+.0f} %".replace("-", "−")


def bar_path(length: float, top: int) -> str:
    """A bar from the zero line, square there and rounded at its far end."""
    end, bottom = ZERO_X + length, top + BAR_HEIGHT
    corner = min(CORNER, abs(length) / 2)
    step = -corner if length < 0 else corner
    return (f"M{ZERO_X} {top} H{end - step} Q{end} {top} {end} {top + corner} V{bottom - corner} "
            f"Q{end} {bottom} {end - step} {bottom} H{ZERO_X} Z")


def value(number: float, unit: str) -> str:
    if unit == "s" and number >= 1000:
        return f"{number:,.0f} s".replace(",", " ")
    return f"{number:.1f} {unit}" if number < 100 else f"{number:.0f} {unit}"


def text(x: float, y: float, content: str, color: str, size: int, anchor: str = "start", weight: str = "400") -> str:
    return (f'<text x="{x:.1f}" y="{y:.1f}" fill="{color}" font-size="{size}" font-weight="{weight}" '
            f'text-anchor="{anchor}">{escape(content)}</text>')


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(*sys.argv[1:])
