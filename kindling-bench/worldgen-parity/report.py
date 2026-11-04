"""Text of a comparison: what was compared, whether both runs proved the canonical order, and what differs."""
from collections import Counter, defaultdict

import compare_all

DIFFERENCES_PER_CHUNK = 12


def render_location(report: compare_all.LocationReport, orders: dict[str, dict], limit_chunks: int) -> list[str]:
    location = report.location
    lines = [f"== {location.name}: {location.dimension}, sweep chunks x {location.x0}..{location.x1}, "
             f"z {location.z0}..{location.z1}; compared every saved chunk in {location.compare_box()}"]
    lines += [render_order(side, order) for side, order in orders.items()]
    lines += render_coverage(report.coverage_a, report.coverage_b)
    lines += [f"SETUP PROBLEM: {problem}" for problem in report.setup_problems]
    lines += [f"PARTLY COMPARED: chunk {chunk}: {race.reason}" for chunk, race in sorted(report.excluded.items())]
    lines += _render_masked(report)
    if not report.differences:
        partly = (f" except the footprint of {len(report.excluded)} partly compared chunks (listed above)"
                  if report.excluded else "")
        lines.append("RESULT: identical in everything compared" + partly)
        return lines
    chunks = {(d.dimension, d.chunk) for d in report.differences}
    lines.append(f"RESULT: {len(report.differences)} differences in {len(chunks)} chunks")
    lines.append("by category: " + ", ".join(f"{category} {count}" for category, count
                                            in sorted(Counter(d.category for d in report.differences).items())))
    lines += _render_chunks(report, limit_chunks)
    return lines


def _render_masked(report: compare_all.LocationReport) -> list[str]:
    if not report.excluded:
        return []
    by_category = Counter(d.category.removeprefix("masked ") for d in report.masked)
    counts = ", ".join(f"{category} {count}" for category, count in sorted(by_category.items())) or "none"
    lines = [f"MASKED: {len(report.masked)} differences inside the footprint of the partly compared chunks ({counts}); "
             f"they are what post-processing changes there, and are not counted as equal or different"]
    lines += [f"  masked {d.category} in chunk {d.chunk}: {d.description[:160]}" for d in report.masked[:6]]
    if len(report.masked) > 6:
        lines.append(f"  ... {len(report.masked) - 6} more")
    return lines


def render_order(side: str, order: dict) -> str:
    verdict = "canonical order proven" if order["ok"] else "ORDER CHECK FAILED"
    text = (f"order {side}: {verdict}; {order['events_near']} generation events near the location, statuses recorded: "
            f"{','.join(order['recorded_statuses'])}; threads {order['threads']}")
    if not order["ok"]:
        text += "\n  " + "\n  ".join(order["problems"][:15])
    return text


def render_coverage(coverage_a: Counter, coverage_b: Counter) -> list[str]:
    lines = ["compared (A / B):"]
    for key in sorted(set(coverage_a) | set(coverage_b)):
        lines.append(f"  {key:<24} {coverage_a.get(key, 0):>8} / {coverage_b.get(key, 0)}")
    return lines


def _render_chunks(report: compare_all.LocationReport, limit_chunks: int) -> list[str]:
    by_chunk = defaultdict(list)
    for difference in report.differences:
        by_chunk[difference.chunk].append(difference)
    lines = []
    for index, (chunk, group) in enumerate(sorted(by_chunk.items(), key=lambda item: item[0] or (0, 0))):
        if index == limit_chunks:
            lines.append(f"... {len(by_chunk) - limit_chunks} more chunks (raise --limit)")
            break
        lines.append(f"  chunk {chunk} [{compare_all.origin_of(report.location, chunk)}]: {len(group)} differences")
        lines += [f"    {d.category}: {d.description}" for d in group[:DIFFERENCES_PER_CHUNK]]
        if len(group) > DIFFERENCES_PER_CHUNK:
            lines.append(f"    ... {len(group) - DIFFERENCES_PER_CHUNK} more")
    return lines


def render_ignored() -> list[str]:
    lines = ["not compared:"]
    lines += [f"  chunk {field}: {why}" for field, why in compare_all.IGNORED_CHUNK_FIELDS.items()]
    lines += [f"  section {field}: {why}" for field, why in compare_all.IGNORED_SECTION_FIELDS.items()]
    lines.append("  entity and block entity keys UUID, keepPacked, Leash, WorldUUIDLeast/Most and the Paper., Spigot.,"
                 " Bukkit, Purpur., Leaf., Kindling. prefixes (../vanilla-diff/ignored.py)")
    lines.append("  normalised: entity attribute lists sorted by id; an empty container's \"Items\": [] dropped")
    return lines
