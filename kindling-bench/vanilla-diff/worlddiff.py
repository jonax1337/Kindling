"""Compares two saved levels (the folders that hold the region files of every dimension)."""
import json
from pathlib import Path
from typing import Iterator, NamedTuple

import anvil
import chunkdiff
import nbt

OVERWORLD = "minecraft:overworld"
NETHER = "minecraft:the_nether"
END = "minecraft:the_end"
LEGACY_DIMENSION_FOLDERS = {"DIM-1": NETHER, "DIM1": END}
LEGACY_SIBLING_SUFFIXES = ("_nether", "_the_end")  # Paper's older layout: world_nether next to world

ChunkKey = tuple[int, int]
Area = tuple[int, int, int, int]  # min chunk x, min chunk z, max chunk x, max chunk z (inclusive)


class DimensionFolders(NamedTuple):
    region: Path
    entities: Path


class ChunkDifference(NamedTuple):
    dimension: str
    chunk: ChunkKey | None
    category: str
    description: str


class WorldReport(NamedTuple):
    differences: list[ChunkDifference]
    chunks_compared: int
    chunks_skipped: int


def load_areas(path: Path) -> dict[str, list[Area]]:
    """The file the command generator writes: {"minecraft:overworld": [[min_x, min_z, max_x, max_z], ...]}."""
    raw = json.loads(path.read_text(encoding="utf-8"))
    return {dimension: [tuple(box) for box in boxes] for dimension, boxes in raw.items()}


def find_dimensions(level: Path) -> dict[str, DimensionFolders]:
    roots = [level] + [sibling for sibling in _legacy_siblings(level) if sibling.is_dir()]
    found = {}
    for root in roots:
        for region in sorted(root.rglob("region")):
            if region.is_dir():
                found[_dimension_id(region.parent.relative_to(root).parts)] = DimensionFolders(
                    region, region.parent / "entities")
    return found


def compare_levels(level_a: Path, level_b: Path, areas: dict[str, list[Area]] | None = None,
                   compare_entities: bool = True) -> WorldReport:
    dimensions_a, dimensions_b = find_dimensions(level_a), find_dimensions(level_b)
    differences, compared, skipped = [], 0, 0
    for dimension in sorted(set(dimensions_a) | set(dimensions_b)):
        if areas is not None and dimension not in areas:
            continue
        if dimension not in dimensions_a or dimension not in dimensions_b:
            side = "B" if dimension in dimensions_a else "A"
            differences.append(ChunkDifference(dimension, None, "dimension", f"missing in {side}"))
            continue
        boxes = None if areas is None else areas[dimension]
        outcome = _compare_dimension(dimension, dimensions_a[dimension], dimensions_b[dimension], boxes,
                                     compare_entities)
        differences += outcome.differences
        compared += outcome.chunks_compared
        skipped += outcome.chunks_skipped
    return WorldReport(differences, compared, skipped)


def _legacy_siblings(level: Path) -> Iterator[Path]:
    for suffix in LEGACY_SIBLING_SUFFIXES:
        yield level.parent / (level.name + suffix)


def _dimension_id(parts: tuple[str, ...]) -> str:
    if not parts:
        return OVERWORLD
    if len(parts) >= 3 and parts[-3] == "dimensions":
        return f"{parts[-2]}:{parts[-1]}"
    return LEGACY_DIMENSION_FOLDERS.get(parts[-1], "/".join(parts))


def _in_areas(key: ChunkKey, boxes: list[Area] | None) -> bool:
    if boxes is None:
        return True
    x, z = key
    return any(min_x <= x <= max_x and min_z <= z <= max_z for min_x, min_z, max_x, max_z in boxes)


def _load_records(directory: Path, boxes: list[Area] | None) -> dict[ChunkKey, anvil.ChunkRecord]:
    records = {}
    if not directory.is_dir():
        return records
    for region_x, region_z, path in anvil.region_files(directory):
        for record in anvil.read_chunks(path, region_x, region_z):
            key = (record.chunk_x, record.chunk_z)
            if _in_areas(key, boxes):
                records[key] = record
    return records


def _parse(record: anvil.ChunkRecord | None) -> dict | None:
    return None if record is None or record.payload is None else anvil.parse_chunk(record.payload)


def _is_full(chunk: dict) -> bool:
    return str(chunk.get("Status", "")).endswith("full")


def _compare_dimension(dimension: str, folders_a: DimensionFolders, folders_b: DimensionFolders,
                       boxes: list[Area] | None, compare_entities: bool) -> WorldReport:
    differences = []
    compared = skipped = 0
    records_a, records_b = _load_records(folders_a.region, boxes), _load_records(folders_b.region, boxes)
    for key in sorted(set(records_a) | set(records_b)):
        outcome = _compare_chunk(dimension, key, records_a.get(key), records_b.get(key), boxes is not None)
        differences += outcome.differences
        compared += outcome.chunks_compared
        skipped += outcome.chunks_skipped
    entity_differences = (_compare_entities(dimension, folders_a.entities, folders_b.entities, boxes)
                          if compare_entities else [])
    return WorldReport(differences + entity_differences, compared, skipped)


def _compare_chunk(dimension: str, key: ChunkKey, record_a, record_b, require_full: bool) -> WorldReport:
    unreadable = [f"unreadable in {side}: {record.error}"
                  for side, record in (("A", record_a), ("B", record_b)) if record is not None and record.payload is None]
    if unreadable:
        return WorldReport([ChunkDifference(dimension, key, "chunk", text) for text in unreadable], 0, 0)
    try:
        return _compare_readable_chunk(dimension, key, _parse(record_a), _parse(record_b), require_full)
    except (nbt.NbtError, chunkdiff.ChunkFormatError, KeyError) as error:
        text = f"could not compare: {type(error).__name__}: {error}"
        return WorldReport([ChunkDifference(dimension, key, "chunk", text)], 0, 0)


def _compare_readable_chunk(dimension: str, key: ChunkKey, chunk_a, chunk_b, require_full: bool) -> WorldReport:
    if chunk_a is None or chunk_b is None:
        return _one_sided_chunk(dimension, key, chunk_a, chunk_b, require_full)
    if not (_is_full(chunk_a) and _is_full(chunk_b)):
        return _status_mismatch(dimension, key, chunk_a, chunk_b)
    found = [ChunkDifference(dimension, key, d.category, d.description)
             for d in chunkdiff.diff_chunks(key[0], key[1], chunk_a, chunk_b)]
    return WorldReport(found, 1, 0)


def _one_sided_chunk(dimension: str, key: ChunkKey, chunk_a, chunk_b, require_full: bool) -> WorldReport:
    present, side = (chunk_a, "A") if chunk_a is not None else (chunk_b, "B")
    other = "B" if side == "A" else "A"
    if _is_full(present) or require_full:
        text = f"{'full ' if _is_full(present) else ''}chunk only in {side}, missing in {other}"
        return WorldReport([ChunkDifference(dimension, key, "chunk", text)], 0, 0)
    return WorldReport([], 0, 1)


def _status_mismatch(dimension: str, key: ChunkKey, chunk_a: dict, chunk_b: dict) -> WorldReport:
    if _is_full(chunk_a) != _is_full(chunk_b):
        text = f"status differs: A {chunk_a.get('Status')} B {chunk_b.get('Status')}"
        return WorldReport([ChunkDifference(dimension, key, "chunk", text)], 0, 0)
    return WorldReport([], 0, 1)


def _entity_lists(directory: Path, boxes: list[Area] | None) -> dict[ChunkKey, list]:
    lists = {}
    for key, record in _load_records(directory, boxes).items():
        if record.payload is not None:
            lists[key] = anvil.parse_chunk(record.payload).get("Entities", [])
    return lists


def _compare_entities(dimension: str, directory_a: Path, directory_b: Path,
                      boxes: list[Area] | None) -> list[ChunkDifference]:
    lists_a, lists_b = _entity_lists(directory_a, boxes), _entity_lists(directory_b, boxes)
    found = []
    for key in sorted(set(lists_a) | set(lists_b)):
        for difference in chunkdiff.diff_entities(lists_a.get(key, []), lists_b.get(key, [])):
            found.append(ChunkDifference(dimension, key, difference.category, difference.description))
    return found
