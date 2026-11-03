"""Draws Paper, Purpur, Leaf and Kindling from the all-forks runs as two charts, each in a light and a dark SVG:
headline tiles for the README (<out>-tiles-light.svg, <out>-tiles-dark.svg) and one dot line per figure for the
measurements page (<out>-light.svg, <out>-dark.svg).

    python -I kindling-bench/forks_chart.py kindling-bench/runs/final docs/media/all-forks

The folder holds af-<view distance>-<scenario>-<server>.json, each a list of runs. Kindling's number is coloured by
compare.py's rule against the best of the other forks. A tile that claims a lead stops the script when that lead is
not significant, so the README never states one the runs do not carry.
"""

import json
import sys
from dataclasses import dataclass
from html import escape
from pathlib import Path
from statistics import mean

# python -I leaves the script's own folder off the import path.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from chart import FONT, THEMES, text  # noqa: E402
from compare import figure, is_significant, welch_p_value  # noqa: E402

FORKS = ("paper", "purpur", "leaf", "kindling")
OTHERS = FORKS[:3]
FORK_NAMES = {"paper": "Paper", "purpur": "Purpur", "leaf": "Leaf", "kindling": "Kindling"}
FORK_COLORS = {
    "light": {"paper": "#A3A89C", "purpur": "#9D86B8", "leaf": "#3F8A52", "kindling": "#E0561A"},
    "dark": {"paper": "#6B7569", "purpur": "#8D76AA", "leaf": "#4E9E62", "kindling": "#E2601F"},
}
TILE_SURFACE = {"light": "#F1EDE1", "dark": "#18251C"}

NEW_CHUNKS = ("chunk_lead", "new_chunks_per_second")
VOID = ("chunk_lead", "hole_pct")
FLIGHT_P95 = ("travel", "mspt_p95")
FLIGHT_AVG = ("travel", "mspt_avg")
BASES_AVG = ("bases", "mspt_avg")
CPU = ("process_cpu_seconds",)
HEAP = ("heap_used_after_gc_mb",)


@dataclass(frozen=True)
class Figure:
    key: str
    section: str
    name: str
    view_distance: int
    scenario: str
    path: tuple[str, ...]
    unit: str
    higher_is_better: bool


FIGURES = [
    Figure("elytra-chunks", "New terrain", "Chunks per second, 5 elytras", 10, "elytra", NEW_CHUNKS, "", True),
    Figure("elytra-void", "New terrain", "Time over ungenerated void, 5 elytras", 10, "elytra", VOID, "%", False),
    Figure("elytra-p95", "New terrain", "Tick time while flying, p95", 10, "elytra", FLIGHT_P95, "ms", False),
    Figure("mixed-chunks", "New terrain", "Chunks per second, 8 players exploring", 10, "mixed", NEW_CHUNKS, "", True),
    Figure("far-chunks", "New terrain", "Chunks per second, view distance 32", 32, "elytra", NEW_CHUNKS, "", True),
    Figure("pregen", "New terrain", "Pregeneration, chunks per second", 10, "pregen", NEW_CHUNKS, "", True),
    Figure("bases", "Without new terrain", "Tick time in survival bases", 10, "bases", BASES_AVG, "ms", False),
    Figure("villagers", "Without new terrain", "Tick time among working villagers", 10, "villagers", BASES_AVG, "ms",
           False),
    Figure("loaded", "Without new terrain", "Flying over a pregenerated world", 10, "loaded", FLIGHT_AVG, "ms",
           False),
    Figure("far-tick", "What it costs", "Tick time, 8 players at view distance 32", 32, "mixed", FLIGHT_AVG, "ms",
           False),
    Figure("cpu", "What it costs", "CPU time, 8 players exploring", 10, "mixed", CPU, "s", False),
    Figure("heap", "What it costs", "Heap after GC, view distance 32", 32, "mixed", HEAP, "MB", False),
]

WIDTH = 860
LEFT = 24
LEGEND_Y = 96
DOT_TRACK_X = 290
DOT_TRACK_WIDTH = 300
DOT_ROW_HEIGHT = 34
TILE_COLUMNS = 3
TILE_GAP = 16
TILE_HEIGHT = 150
TILES_TOP = 92


@dataclass(frozen=True)
class Measured:
    figure: Figure
    means: dict[str, float]
    verdict: str  # Kindling against the best other fork: "better", "worse" or "even"

    @property
    def kindling(self) -> float:
        return self.means["kindling"]

    @property
    def best_other(self) -> float:
        others = [self.means[fork] for fork in OTHERS]
        return max(others) if self.figure.higher_is_better else min(others)


@dataclass(frozen=True)
class Tile:
    headline: str
    line: str
    note: str
    detail: str
    accent: str  # a colour key: "better", "leaf" or "muted"


def main(folder: str, out: str) -> None:
    runs = {(f.view_distance, f.scenario, fork): load(Path(folder), f, fork) for f in FIGURES for fork in FORKS}
    measured = {f.key: measure(f, runs) for f in FIGURES}
    run_count = min(len(side) for side in runs.values())
    tiles = headline_tiles(measured)
    for theme, base in THEMES.items():
        colors = base | FORK_COLORS[theme] | {"tile": TILE_SURFACE[theme]}
        Path(f"{out}-tiles-{theme}.svg").write_text(tiles_svg(tiles, run_count, colors), encoding="utf-8")
        Path(f"{out}-{theme}.svg").write_text(dots_svg(list(measured.values()), run_count, colors), encoding="utf-8")


def load(folder: Path, f: Figure, fork: str) -> list[dict]:
    path = folder / f"af-{f.view_distance}-{f.scenario}-{fork}.json"
    runs = json.loads(path.read_text(encoding="utf-8"))
    if not runs:
        raise SystemExit(f"{path} holds no runs")
    return runs


def measure(f: Figure, runs: dict) -> Measured:
    values = {fork: [figure(run, f.path) for run in runs[(f.view_distance, f.scenario, fork)]] for fork in FORKS}
    means = {fork: mean(v) for fork, v in values.items()}
    best = max(OTHERS, key=lambda fork: means[fork] if f.higher_is_better else -means[fork])
    p_value = welch_p_value(values[best], values["kindling"])
    if p_value is None or not is_significant(values[best], values["kindling"], p_value):
        verdict = "even"
    else:
        ahead = means["kindling"] > means[best] if f.higher_is_better else means["kindling"] < means[best]
        verdict = "better" if ahead else "worse"
    return Measured(f, means, verdict)


def headline_tiles(m: dict[str, Measured]) -> list[Tile]:
    for key in ("elytra-chunks", "elytra-void", "elytra-p95", "pregen"):
        if m[key].verdict != "better":
            raise SystemExit(f"tile {key}: Kindling's lead over the best other fork is not significant")
    chunks, void, p95, pregen = m["elytra-chunks"], m["elytra-void"], m["elytra-p95"], m["pregen"]
    return [
        Tile(f"{chunks.kindling / chunks.best_other:.1f}×", "the chunks per second", "with 5 elytras flying",
             against_others(chunks), "better"),
        Tile(f"{void.kindling:.0f} %", "of the flight over void", "with 5 elytras flying", against_others(void),
             "better"),
        Tile(f"{p95.best_other / p95.kindling:.0f}×", "lower tick spikes in flight", "tick time p95, 5 elytras",
             against_others(p95), "better"),
        Tile(f"{pregen.kindling / pregen.best_other:.1f}×", "faster pregeneration", "Chunky on every fork",
             against_others(pregen), "better"),
        like_leaf_tile([m["bases"], m["villagers"], m["loaded"]]),
        cost_tile(m["cpu"], m["heap"]),
    ]


def like_leaf_tile(figures: list[Measured]) -> Tile:
    against_leaf = [abs(change(f.kindling, f.means["leaf"])) for f in figures]
    ahead_of_paper_purpur = [-change(f.kindling, f.means[fork]) for f in figures for fork in ("paper", "purpur")]
    behind_leaf = any(f.verdict == "worse" and f.best_other == f.means["leaf"] for f in figures)
    return Tile("Near Leaf" if behind_leaf else "Like Leaf", "without new terrain",
                f"within {max(against_leaf):.0f} % of Leaf in all three scenarios",
                f"{min(ahead_of_paper_purpur):.0f} to {max(ahead_of_paper_purpur):.0f} % ahead of Paper and Purpur",
                "leaf")


def cost_tile(cpu: Measured, heap: Measured) -> Tile:
    return Tile(f"+{change(cpu.kindling, cpu.means['leaf']):.0f} % CPU",
                f"and {heap.kindling / heap.means['leaf']:.1f}× the heap", "heap at view distance 32",
                f"{number(cpu.kindling, 's')} against Leaf's {number(cpu.means['leaf'], 's')} while exploring", "muted")


def change(value: float, baseline: float) -> float:
    return (value - baseline) / baseline * 100


def against_others(m: Measured) -> str:
    return f"Kindling {number(m.kindling, m.figure.unit)} · others {others_spread(m)}"


def others_spread(m: Measured) -> str:
    others = [m.means[fork] for fork in OTHERS]
    low, high = number(min(others), m.figure.unit), number(max(others), m.figure.unit)
    return low if low == high else f"{low} to {high}"


def number(value: float, unit: str) -> str:
    digits = f"{value:,.0f}".replace(",", " ") if value >= 100 else f"{value:.1f}"
    return f"{digits} {unit}".strip()


def tiles_svg(tiles: list[Tile], run_count: int, colors: dict) -> str:
    tile_width = (WIDTH - 2 * LEFT - (TILE_COLUMNS - 1) * TILE_GAP) / TILE_COLUMNS
    rows = -(-len(tiles) // TILE_COLUMNS)
    height = TILES_TOP + rows * TILE_HEIGHT + (rows - 1) * TILE_GAP + 44
    parts = frame(colors, height, "Kindling against Paper, Purpur and Leaf",
                  f"Same machine, each with its own defaults, view distance 10; means of {run_count} runs.",
                  "; ".join(f"{t.headline} {t.line} ({t.detail})" for t in tiles))
    for index, tile in enumerate(tiles):
        x = LEFT + (index % TILE_COLUMNS) * (tile_width + TILE_GAP)
        y = TILES_TOP + (index // TILE_COLUMNS) * (TILE_HEIGHT + TILE_GAP)
        accent = colors[tile.accent]
        parts += [
            f'<rect x="{x:.1f}" y="{y}" width="{tile_width:.1f}" height="{TILE_HEIGHT}" rx="10" fill="{colors["tile"]}"/>',
            f'<rect x="{x:.1f}" y="{y}" width="4" height="{TILE_HEIGHT}" rx="2" fill="{accent}"/>',
            text(x + 20, y + 50, tile.headline, accent, 32, weight="800"),
            text(x + 20, y + 76, tile.line, colors["text"], 14, weight="600"),
            text(x + 20, y + 96, tile.note, colors["muted"], 12),
            text(x + 20, y + 128, tile.detail, colors["muted"], 11),
        ]
    parts.append(text(LEFT, height - 18, "Orange: Kindling ahead of every fork · green: even with Leaf · "
                      "gray: what it costs.", colors["muted"], 12))
    parts.append("</svg>")
    return "\n".join(parts) + "\n"


def dots_svg(measured: list[Measured], run_count: int, colors: dict) -> str:
    rows, y, section = [], LEGEND_Y + 16, None
    for m in measured:
        if m.figure.section != section:
            section = m.figure.section
            rows += section_heading(section, y + 18, colors)
            y += 48
        rows += dot_row(m, y, colors)
        y += DOT_ROW_HEIGHT
    height = y + 52
    parts = frame(colors, height, "All forks compared",
                  f"Means of {run_count} runs per fork on the same machine; each line runs from zero to the "
                  "highest value.", "; ".join(dot_description(m) for m in measured))
    parts += legend(colors)
    parts += rows
    parts.append(text(LEFT, height - 22, "↑ higher is better, ↓ lower is better. Kindling's number: orange ahead of "
                      "every other fork, blue behind the best one, gray no significant difference.",
                      colors["muted"], 12))
    parts.append("</svg>")
    return "\n".join(parts) + "\n"


def section_heading(name: str, y: float, colors: dict) -> list[str]:
    return [text(LEFT, y, name.upper(), colors["muted"], 12, weight="700"),
            f'<line x1="{LEFT}" y1="{y + 8}" x2="{WIDTH - LEFT}" y2="{y + 8}" stroke="{colors["line"]}"/>']


def dot_row(m: Measured, y: float, colors: dict) -> list[str]:
    scale = DOT_TRACK_WIDTH / (max(m.means.values()) * 1.04)
    others = [m.means[fork] for fork in OTHERS]
    low, high = min(others), max(others)
    parts = [
        text(LEFT, y + 4, m.figure.name, colors["text"], 13, weight="600"),
        text(DOT_TRACK_X - 12, y + 4, "↑" if m.figure.higher_is_better else "↓", colors["muted"], 13, anchor="end"),
        f'<line x1="{DOT_TRACK_X}" y1="{y}" x2="{DOT_TRACK_X + DOT_TRACK_WIDTH}" y2="{y}" stroke="{colors["line"]}" '
        'stroke-width="2"/>',
        f'<line x1="{DOT_TRACK_X + low * scale:.1f}" y1="{y}" x2="{DOT_TRACK_X + high * scale:.1f}" y2="{y}" '
        f'stroke="{colors["muted"]}" stroke-width="4" stroke-linecap="round" opacity="0.5"/>',
    ]
    for fork in FORKS:
        radius = 7 if fork == "kindling" else 5.5
        parts.append(f'<circle cx="{DOT_TRACK_X + m.means[fork] * scale:.1f}" cy="{y}" r="{radius}" '
                     f'fill="{colors[fork]}" stroke="{colors["surface"]}" stroke-width="1.5"/>')
    value_x = DOT_TRACK_X + DOT_TRACK_WIDTH + 20
    parts.append(text(value_x, y + 4, number(m.kindling, m.figure.unit), colors[m.verdict], 13, weight="700"))
    parts.append(text(value_x + 72, y + 4, f"others {others_spread(m)}", colors["muted"], 12))
    return parts


def dot_description(m: Measured) -> str:
    return f"{m.figure.name}: " + ", ".join(f"{FORK_NAMES[fork]} {number(m.means[fork], m.figure.unit)}"
                                            for fork in FORKS)


def frame(colors: dict, height: float, title: str, subtitle: str, description: str) -> list[str]:
    return [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{height}" viewBox="0 0 {WIDTH} {height}" '
        f'role="img" aria-labelledby="title desc" font-family="{FONT}">',
        f'<title id="title">{escape(title)}</title>',
        f'<desc id="desc">{escape(description)}</desc>',
        f'<rect width="{WIDTH}" height="{height}" rx="12" fill="{colors["surface"]}"/>',
        text(LEFT, 38, title, colors["text"], 20, weight="700"),
        text(LEFT, 62, subtitle, colors["muted"], 13),
    ]


def legend(colors: dict) -> list[str]:
    parts, x = [], LEFT
    for fork in FORKS:
        name = FORK_NAMES[fork]
        parts.append(f'<circle cx="{x + 6}" cy="{LEGEND_Y - 4}" r="6" fill="{colors[fork]}"/>')
        parts.append(text(x + 18, LEGEND_Y + 1, name, colors["text"], 13))
        x += 18 + 8 * len(name) + 26
    return parts


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(*sys.argv[1:])
