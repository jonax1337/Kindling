"""Compare two saved Minecraft levels and print what differs. Standard library only.

    python -I compare_worlds.py <level A> <level B> [--areas areas.json] [--limit 20]

A is the reference (the vanilla server), B the server under test. Exit code 0: no differences, 1: differences,
2: a level could not be read. Run `python -I compare_worlds.py --list-ignored` for what is not compared.
"""
import argparse
import os
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # python -I does not add the script directory

import anvil  # noqa: E402
import ignored  # noqa: E402
import nbt  # noqa: E402
import worlddiff  # noqa: E402

EXIT_SAME, EXIT_DIFFERENT, EXIT_UNREADABLE = 0, 1, 2


def main() -> int:
    args = parse_arguments()
    if args.list_ignored:
        print(render_ignored())
        return EXIT_SAME
    areas = worlddiff.load_areas(args.areas) if args.areas else None
    try:
        report = worlddiff.compare_levels(args.level_a, args.level_b, areas, not args.skip_entities)
    except (anvil.AnvilError, nbt.NbtError, OSError) as error:
        print(f"error: could not read a level: {type(error).__name__}: {error}", file=sys.stderr)
        return EXIT_UNREADABLE
    print(render_report(report, args))
    return EXIT_DIFFERENT if report.differences else EXIT_SAME


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("level_a", type=Path, nargs="?", help="level folder of the reference (vanilla) server")
    parser.add_argument("level_b", type=Path, nargs="?", help="level folder of the server under test")
    parser.add_argument("--areas", type=Path, help="areas.json from gen_commands.py; only these chunks are compared")
    parser.add_argument("--limit", type=int, default=20, help="differences printed per chunk (default 20)")
    parser.add_argument("--skip-entities", action="store_true",
                        help="do not compare entities (for worldgen runs, where mobs walk around while the world generates)")
    parser.add_argument("--list-ignored", action="store_true", help="print what is not compared and exit")
    args = parser.parse_args()
    if not args.list_ignored and (args.level_a is None or args.level_b is None):
        parser.error("two level folders are required")
    return args


def render_ignored() -> str:
    lines = ["Compared: " + ", ".join(ignored.ONLY_COMPARED_FOR_CHUNKS), "", "Not compared:"]
    lines += [f"  - {what}: {why}" for what, why in ignored.IGNORED_FIELDS]
    return "\n".join(lines)


def render_report(report: worlddiff.WorldReport, args: argparse.Namespace) -> str:
    lines = [
        f"A (reference): {args.level_a}",
        f"B (under test): {args.level_b}",
        f"compared {report.chunks_compared} full chunks; skipped {report.chunks_skipped} chunks that were not full in both",
        "compared: " + ", ".join(ignored.ONLY_COMPARED_FOR_CHUNKS) + "   (run --list-ignored for what is left out)",
    ]
    if not report.differences:
        lines.append("RESULT: identical")
        return "\n".join(lines)
    lines.append(f"RESULT: {len(report.differences)} differences in {_count_chunks(report)} chunks")
    lines.append("by category: " + _category_summary(report))
    lines += _render_by_dimension(report, args.limit)
    return "\n".join(lines)


def _count_chunks(report: worlddiff.WorldReport) -> int:
    return len({(d.dimension, d.chunk) for d in report.differences})


def _category_summary(report: worlddiff.WorldReport) -> str:
    counts = Counter(d.category for d in report.differences)
    return ", ".join(f"{category} {count}" for category, count in sorted(counts.items()))


def _render_by_dimension(report: worlddiff.WorldReport, limit: int) -> list[str]:
    by_chunk = defaultdict(list)
    for difference in report.differences:
        by_chunk[(difference.dimension, difference.chunk)].append(difference)
    lines, current_dimension = [], None
    for (dimension, chunk), group in sorted(by_chunk.items(), key=lambda item: (item[0][0], item[0][1] or (0, 0))):
        if dimension != current_dimension:
            lines.append(f"\n{dimension}")
            current_dimension = dimension
        lines += _render_chunk(chunk, group, limit)
    return lines


def _render_chunk(chunk, group: list, limit: int) -> list[str]:
    where = "dimension" if chunk is None else f"chunk {chunk} (blocks x {chunk[0] * 16}..{chunk[0] * 16 + 15}, " \
                                              f"z {chunk[1] * 16}..{chunk[1] * 16 + 15})"
    lines = [f"  {where}: {len(group)} differences"]
    lines += [f"    {d.category}: {d.description}" for d in group[:limit]]
    if len(group) > limit:
        lines.append(f"    ... {len(group) - limit} more (raise --limit)")
    return lines


if __name__ == "__main__":
    sys.exit(main())
