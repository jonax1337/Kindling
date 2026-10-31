"""Compares every saved chunk of a swept location in two worlds, whatever its status (DESIGN.md 3.3).

Built on the block, biome, block entity and scheduled tick diff of ../vanilla-diff/chunkdiff.py, and adds chunk
status, structure starts and references, post-processing lists, heightmaps, entities (in proto chunks and in the
entity storage, matched without UUIDs) and every other chunk or section field. Nothing is left out unless it is listed
in IGNORED_CHUNK_FIELDS or IGNORED_SECTION_FIELDS with its reason, so a field a jar adds shows up instead of hiding.

One effect is handled position by position instead of field by field: Mojang's block-ticking promotion sometimes
never post-processes a chunk that was loaded full but unprocessed from the bootstrap (IMPL-NOTES.md, "promotion
race"). A chunk gets this treatment only on exactly that signature, and only when the unprocessed side is Mojang's jar
and the other side is not: full on both sides, Mojang's PostProcessing list is still the bootstrap's list, the other
side's is empty. The chunk is then still compared, except the positions its marks name and their six neighbours (what
post-processing can change), the heightmap columns above them, and the marks themselves. Differences inside that
footprint are listed as masked; the verdict can then be at best "identical except ..." and never plain "identical".
A jar under test that is the unprocessed side is never excused: its missing post-processing is a difference.

A chunk that differs in a way no category explains is reported as "unexplained", so nothing passes because a
category function happened to look away.
"""
import json
import re
from collections import Counter
from pathlib import Path
from typing import Iterator, NamedTuple

import paths  # noqa: F401
import anvil
import chunkdiff
import nbt
import sweep
import worlddiff

IGNORED_CHUNK_FIELDS = {
    "LastUpdate": "game time of the last save; depends on how long the server ran",
    "InhabitedTime": "player presence counter",
    "isLightOn": "light engine bookkeeping (Starlight in the Paper family)",
    "starlight.light_version": "Starlight's light format version, written only by the Paper family",
}
IGNORED_SECTION_FIELDS = {
    "BlockLight": "light is not compared (different light engines)",
    "SkyLight": "light is not compared (different light engines)",
    "starlight.blocklight_state": "Starlight's light bookkeeping, written only by the Paper family",
    "starlight.skylight_state": "Starlight's light bookkeeping, written only by the Paper family",
}
_HANDLED_CHUNK_FIELDS = {"Status", "sections", "block_entities", "block_ticks", "fluid_ticks", "structures",
                         "Heightmaps", "PostProcessing", "entities"}
_HANDLED_SECTION_FIELDS = {"Y", "block_states", "biomes"}
HEIGHTMAP_COLUMNS = 256
_ENTITY_MATCH_KEYS = ("id", "Pos")


class Difference(NamedTuple):
    dimension: str
    chunk: sweep.ChunkKey | None
    category: str
    description: str


class LocationReport(NamedTuple):
    location: sweep.Location
    differences: list[Difference]
    coverage_a: Counter
    coverage_b: Counter
    setup_problems: list[str]
    excluded: dict[sweep.ChunkKey, "Race"]
    masked: tuple = ()


class Footprint:
    """What post-processing of an unprocessed chunk can change: the marked positions and their six neighbours."""

    def __init__(self, positions=frozenset()):
        self.positions = frozenset(positions)
        self.columns = frozenset((x, z) for x, _, z in self.positions)

    def __bool__(self) -> bool:
        return bool(self.positions)

    def union(self, other: "Footprint") -> "Footprint":
        return Footprint(self.positions | other.positions)

    def local_columns(self, key: sweep.ChunkKey) -> set[int]:
        """Heightmap column indices (x + 16 z) of this chunk that lie under the footprint."""
        return {(x - key[0] * 16) + 16 * (z - key[1] * 16) for x, z in self.columns
                if x >> 4 == key[0] and z >> 4 == key[1]}

    def covers(self, category: str, description: str) -> bool:
        if category.startswith("masked "):
            return True
        position = _position_of(category, description)
        return position in self.positions if position else False


NO_FOOTPRINT = Footprint()
_POSITION = re.compile(r"^\((-?\d+), (-?\d+), (-?\d+)\)")
_NEIGHBOURS = ((0, 0, 0), (1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


def _position_of(category: str, description: str) -> tuple[int, int, int] | None:
    """Where a block, block entity, container or scheduled tick difference is; None when it cannot be told (a
    difference whose position is unknown is never masked)."""
    if category in ("block", "block entity", "container"):
        match = _POSITION.match(description)
        return tuple(int(value) for value in match.groups()) if match else None
    if category == "scheduled tick":
        try:
            tick = json.loads(description.partition(": ")[2])
            return int(tick["x"]), int(tick["y"]), int(tick["z"])
        except (ValueError, KeyError, TypeError):
            return None
    return None


class Race(NamedTuple):
    """A chunk Mojang's block-ticking promotion left unprocessed while the jar under test processed it."""
    reason: str
    footprint: Footprint


def compare_location(world_a: Path, world_b: Path, location: sweep.Location, bootstrap: Path | None = None,
                     mojang_sides: frozenset[str] = frozenset()) -> LocationReport:
    """mojang_sides names which of "A" and "B" are Mojang's jar; without it no chunk is ever excused."""
    folders_a, folders_b = _dimension_folders(world_a, location), _dimension_folders(world_b, location)
    box = location.compare_box()
    chunks_a, chunks_b = load_chunks(folders_a.region, box), load_chunks(folders_b.region, box)
    races = promotion_races(chunks_a, chunks_b, _bootstrap_chunks(bootstrap, location, box), mojang_sides)
    footprint = NO_FOOTPRINT
    for race in races.values():
        footprint = footprint.union(race.footprint)
    entities_a, entities_b = load_chunks(folders_a.entities, box), load_chunks(folders_b.entities, box)
    found = list(diff_chunk_maps(location.dimension, chunks_a, chunks_b, frozenset(races), footprint))
    found += diff_entity_storage(location.dimension, entities_a, entities_b)
    differences = [d for d in found if not footprint.covers(d.category, d.description)]
    masked = tuple(d for d in found if footprint.covers(d.category, d.description))
    cover_a, cover_b = coverage(chunks_a, entities_a), coverage(chunks_b, entities_b)
    if not differences:  # a safety net: counts that differ although no difference was found
        differences = list(coverage_mismatches(location.dimension, cover_a, cover_b, masking=bool(races)))
    problems = _vacuity_problems(location, "A", chunks_a) + _vacuity_problems(location, "B", chunks_b)
    return LocationReport(location, differences, cover_a, cover_b, problems, races, masked)


# What masked positions can change in a count; these counts are not compared as the safety net when something is masked.
_MASK_SENSITIVE_COUNTS = ("scheduled ticks", "post-processing marks", "sections with blocks", "block entities")


def coverage_mismatches(dimension: str, cover_a: Counter, cover_b: Counter, masking: bool) -> Iterator[Difference]:
    for key in sorted(set(cover_a) | set(cover_b)):
        if masking and key in _MASK_SENSITIVE_COUNTS:
            continue
        if cover_a.get(key, 0) != cover_b.get(key, 0):
            yield Difference(dimension, None, "unexplained",
                             f"counts differ although no difference was found: {key} A {cover_a.get(key, 0)}"
                             f" B {cover_b.get(key, 0)}")


def _bootstrap_chunks(bootstrap: Path | None, location: sweep.Location, box) -> dict:
    return {} if bootstrap is None else load_chunks(_dimension_folders(bootstrap, location).region, box)


def promotion_races(chunks_a: dict, chunks_b: dict, bootstrap: dict,
                    mojang_sides: frozenset[str] = frozenset()) -> dict[sweep.ChunkKey, Race]:
    """Chunks where Mojang's jar never post-processed what it loaded from the bootstrap and the jar under test did.
    Never the other way round, and never between two Mojang runs (a self-check shows such a chunk as a difference)."""
    found = {}
    for key, original in bootstrap.items():
        marks = _post_processing(original)
        a, b = chunks_a.get(key), chunks_b.get(key)
        footprint = post_processing_footprint(key, original) if marks else None
        if footprint is None or not (_is_full(a) and _is_full(b)):
            continue
        for side, pending, done in (("A", a, b), ("B", b, a)):
            other = "B" if side == "A" else "A"
            if side in mojang_sides and other not in mojang_sides \
                    and _post_processing(pending) == marks and not _post_processing(done):
                found[key] = Race(f"Mojang's block-ticking promotion never post-processed it in {side} (its bootstrap "
                                  f"list is unchanged); compared except {len(footprint.positions)} positions that "
                                  f"its {sum(map(len, marks))} marks and their neighbours cover", footprint)
    return found


def post_processing_footprint(key: sweep.ChunkKey, chunk: dict) -> Footprint | None:
    """The marked positions and their neighbours, or None when the section list does not line up with the marks."""
    sections, lists = chunk.get("sections", []), chunk.get("PostProcessing", [])
    if not sections or len(lists) != len(sections):
        return None
    lowest = min(section["Y"] for section in sections)
    positions = set()
    for index, marks in enumerate(lists):
        for packed in marks:
            x, y, z = unpack_post_processing(key, lowest + index, packed)
            positions.update((x + dx, y + dy, z + dz) for dx, dy, dz in _NEIGHBOURS)
    return Footprint(positions)


def unpack_post_processing(key: sweep.ChunkKey, section_y: int, packed: int) -> tuple[int, int, int]:
    """ProtoChunk.packOffsetCoordinates: x | y << 4 | z << 8 inside the section (read in ProtoChunk.java)."""
    packed &= 0xFFFF
    return key[0] * 16 + (packed & 15), section_y * 16 + ((packed >> 4) & 15), key[1] * 16 + ((packed >> 8) & 15)


def _post_processing(chunk) -> list:
    return [marks for marks in chunk.get("PostProcessing", []) if marks] if isinstance(chunk, dict) else []


def _is_full(chunk) -> bool:
    return isinstance(chunk, dict) and str(chunk.get("Status", "")).endswith("full")


def _dimension_folders(world: Path, location: sweep.Location) -> worlddiff.DimensionFolders:
    found = worlddiff.find_dimensions(world)
    if location.dimension not in found:
        raise FileNotFoundError(f"{world}: no region folder for {location.dimension} (found {sorted(found)})")
    return found[location.dimension]


def load_chunks(directory: Path, box: tuple[int, int, int, int]) -> dict[sweep.ChunkKey, dict | str]:
    """Chunk -> decoded NBT, or the error text when it could not be read."""
    min_x, min_z, max_x, max_z = box
    chunks = {}
    if not directory.is_dir():
        return chunks
    for region_x, region_z, path in anvil.region_files(directory):
        for record in anvil.read_chunks(path, region_x, region_z):
            if min_x <= record.chunk_x <= max_x and min_z <= record.chunk_z <= max_z:
                chunks[(record.chunk_x, record.chunk_z)] = _decode(record)
    return chunks


def _decode(record: anvil.ChunkRecord) -> dict | str:
    if record.payload is None:
        return f"unreadable: {record.error}"
    try:
        return anvil.parse_chunk(record.payload)
    except nbt.NbtError as error:
        return f"unreadable: {error}"


def diff_chunk_maps(dimension: str, chunks_a: dict, chunks_b: dict, excluded: frozenset = frozenset(),
                    footprint: Footprint = NO_FOOTPRINT) -> Iterator[Difference]:
    for key in sorted(set(chunks_a) | set(chunks_b)):
        a, b = chunks_a.get(key), chunks_b.get(key)
        for category, text in _diff_present_chunk(key, a, b, key in excluded, footprint):
            yield Difference(dimension, key, category, text)


def _diff_present_chunk(key: sweep.ChunkKey, a, b, excluded: bool = False,
                        footprint: Footprint = NO_FOOTPRINT) -> Iterator[tuple[str, str]]:
    if a is None or b is None:
        present, side = (a, "A") if a is not None else (b, "B")
        yield "chunk", f"only in {side} (status {_status(present)})"
    elif isinstance(a, str) or isinstance(b, str):
        yield "chunk", f"A {a if isinstance(a, str) else 'ok'}; B {b if isinstance(b, str) else 'ok'}"
    else:
        try:
            yield from diff_chunk(key, a, b, excluded, footprint)
        except (chunkdiff.ChunkFormatError, KeyError, TypeError, ValueError) as error:
            yield "chunk", f"could not compare: {type(error).__name__}: {error}"


def _status(chunk) -> str:
    return chunk.get("Status", "?") if isinstance(chunk, dict) else "unreadable"


def diff_chunk(key: sweep.ChunkKey, a: dict, b: dict, excluded: bool = False,
               footprint: Footprint = NO_FOOTPRINT) -> Iterator[tuple[str, str]]:
    """Every difference of two present chunks. An excluded chunk (Mojang's promotion race) is compared like any
    other except for its post-processing list; the caller masks what lies inside the footprint."""
    a, b = _with_canonical_containers(a), _with_canonical_containers(b)
    if not excluded and _without_ignored(a) == _without_ignored(b):
        return
    found = 0
    for category, text in _diff_chunk_parts(key, a, b, excluded, footprint):
        found += 1
        yield category, text
    if found == 0 and not excluded:
        yield from _unexplained(a, b)


def _diff_chunk_parts(key: sweep.ChunkKey, a: dict, b: dict, excluded: bool,
                      footprint: Footprint) -> Iterator[tuple[str, str]]:
    if a.get("Status") != b.get("Status"):
        yield "status", f"A {a.get('Status')} B {b.get('Status')}"
    for difference in chunkdiff.diff_chunks(key[0], key[1], a, b):
        yield difference.category, difference.description
    yield from _diff_section_presence(a, b)
    yield from _diff_section_fields(a, b)
    yield from _diff_duplicate_block_entities(a, b)
    yield from _diff_structures(a.get("structures", {}), b.get("structures", {}))
    yield from _diff_heightmaps(key, a.get("Heightmaps", {}), b.get("Heightmaps", {}), footprint)
    if not excluded:
        yield from _diff_post_processing(key, a.get("PostProcessing", []), b.get("PostProcessing", []))
    yield from diff_entity_lists(a.get("entities", []), b.get("entities", []))
    skipped = _HANDLED_CHUNK_FIELDS | set(IGNORED_CHUNK_FIELDS)
    yield from _diff_other_fields(a, b, skipped, "chunk field")


def _unexplained(a: dict, b: dict) -> Iterator[tuple[str, str]]:
    """The raw chunks differ and no category said why: either only the order of lists or the palette layout differs
    (the normal forms are equal, so this is not a difference) or something is not covered, which must not pass."""
    normal_a, normal_b = _normalised(a), _normalised(b)
    if normal_a == normal_b:
        return
    changes = list(chunkdiff.value_changes(normal_a, normal_b, ""))
    first = "; ".join(f"{path}: A {chunkdiff.shorten(chunkdiff.canonical(x))[:60]} B "
                      f"{chunkdiff.shorten(chunkdiff.canonical(y))[:60]}" for path, x, y in changes[:3])
    yield "unexplained", f"the chunks differ but no comparison names it ({len(changes)} paths): {first}"


def _normalised(chunk: dict) -> dict:
    """The chunk as the comparison sees it: lists that are compared as multisets sorted, palettes decoded to cells."""
    kept = {key: value for key, value in _without_ignored(chunk).items() if key != "sections"}
    kept["sections"] = sorted((_normal_section(section) for section in chunk.get("sections", [])
                               if _has_content(section)), key=lambda section: str(section.get("Y")))
    for field in ("block_entities", "entities"):
        if field in kept:
            kept[field] = sorted(chunkdiff.canonical(normalized_entity(entity)) for entity in kept[field])
    for field in ("block_ticks", "fluid_ticks"):
        if field in kept:
            kept[field] = sorted(chunkdiff.canonical(tick) for tick in kept[field])
    if "PostProcessing" in kept:
        kept["PostProcessing"] = [sorted(marks) for marks in kept["PostProcessing"]]
    references = kept.get("structures", {}).get("References")
    if references:
        kept["structures"] = {**kept["structures"],
                              "References": {name: sorted(values) for name, values in references.items()}}
    return kept


def _has_content(section: dict) -> bool:
    """False for a section that holds nothing but its Y and ignored light data: the Paper family writes such sections
    above and below the world for its light engine, and Mojang does not."""
    return any(key != "Y" and key not in IGNORED_SECTION_FIELDS for key in section)


def _normal_section(section: dict) -> dict:
    normal = {key: value for key, value in section.items() if key not in IGNORED_SECTION_FIELDS}
    if "block_states" in section:
        normal["block_states"] = chunkdiff.cell_names(section["block_states"], chunkdiff.block_state_name,
                                                      chunkdiff.MIN_BITS_BLOCKS, chunkdiff.CELLS_PER_SECTION_BLOCKS)
    if "biomes" in section:
        normal["biomes"] = chunkdiff.cell_names(section["biomes"], str, chunkdiff.MIN_BITS_BIOMES,
                                                chunkdiff.CELLS_PER_SECTION_BIOMES)
    return normal


def _diff_section_presence(a: dict, b: dict) -> Iterator[tuple[str, str]]:
    """A section, or a block or biome container of one, that only one side has (chunkdiff reads a missing block
    container as air and skips a missing biome container, so a dropped section or container would pass)."""
    for side, chunk in (("A", a), ("B", b)):
        for y, count in sorted(Counter(section["Y"] for section in chunk.get("sections", [])).items()):
            if count > 1:
                yield "section", f"section {y} listed {count} times in {side}"
    sections_a = {section["Y"]: section for section in a.get("sections", []) if _has_content(section)}
    sections_b = {section["Y"]: section for section in b.get("sections", []) if _has_content(section)}
    for y in sorted(set(sections_a) | set(sections_b)):
        if y not in sections_b or y not in sections_a:
            yield "section", f"section {y} only in {'A' if y in sections_a else 'B'}"
            continue
        for container, category in (("biomes", "biome"), ("block_states", "section")):
            if (container in sections_a[y]) != (container in sections_b[y]):
                side = "A" if container in sections_a[y] else "B"
                yield category, f"section {y}: {container} container only in {side}"


def _diff_duplicate_block_entities(a: dict, b: dict) -> Iterator[tuple[str, str]]:
    """chunkdiff keys block entities by position, so a second entry at a position would hide the first."""
    for side, chunk in (("A", a), ("B", b)):
        counts = Counter((entity.get("x"), entity.get("y"), entity.get("z"))
                         for entity in chunk.get("block_entities", []))
        for (x, y, z), count in sorted(counts.items(), key=str):
            if count > 1:
                yield "block entity", f"({x}, {y}, {z}) listed {count} times in {side}"


def _with_canonical_containers(chunk: dict) -> dict:
    """An empty container is written without "Items" by Mojang and with "Items": [] by the Paper family; both mean
    the same (no items), so the empty list is dropped before comparing."""
    entities = chunk.get("block_entities")
    if not entities:
        return chunk
    cleaned = [{k: v for k, v in entity.items() if not (k == "Items" and v == [])} for entity in entities]
    return {**chunk, "block_entities": cleaned}


def _without_ignored(chunk: dict) -> dict:
    kept = {key: value for key, value in chunk.items() if key not in IGNORED_CHUNK_FIELDS and key != "sections"}
    kept["sections"] = [{key: value for key, value in section.items() if key not in IGNORED_SECTION_FIELDS}
                        for section in chunk.get("sections", [])]
    return kept


def _diff_other_fields(a: dict, b: dict, skipped: set, label: str) -> Iterator[tuple[str, str]]:
    for field in sorted((set(a) | set(b)) - skipped):
        if a.get(field) != b.get(field):
            yield label, (f"{field}: A {chunkdiff.shorten(chunkdiff.canonical(a.get(field)))}"
                          f" B {chunkdiff.shorten(chunkdiff.canonical(b.get(field)))}")


def _diff_section_fields(a: dict, b: dict) -> Iterator[tuple[str, str]]:
    sections_a = {section["Y"]: section for section in a.get("sections", [])}
    sections_b = {section["Y"]: section for section in b.get("sections", [])}
    skipped = _HANDLED_SECTION_FIELDS | set(IGNORED_SECTION_FIELDS)
    for y in sorted(set(sections_a) | set(sections_b)):
        for category, text in _diff_other_fields(sections_a.get(y, {}), sections_b.get(y, {}), skipped, "section field"):
            yield category, f"section {y} {text}"


def _diff_structures(a: dict, b: dict) -> Iterator[tuple[str, str]]:
    starts_a, starts_b = a.get("starts", {}), b.get("starts", {})
    for structure in sorted(set(starts_a) | set(starts_b)):
        start_a = chunkdiff.without_volatile(starts_a.get(structure))
        start_b = chunkdiff.without_volatile(starts_b.get(structure))
        if start_a != start_b:
            changes = list(chunkdiff.value_changes(start_a, start_b, ""))
            first = ", ".join(path for path, _, _ in changes[:4])
            yield "structure start", f"{structure}: {len(changes)} fields differ ({first})"
    references_a, references_b = a.get("References", {}), b.get("References", {})
    for structure in sorted(set(references_a) | set(references_b)):
        positions_a = {unpack_chunk_pos(value) for value in references_a.get(structure, ())}
        positions_b = {unpack_chunk_pos(value) for value in references_b.get(structure, ())}
        for position in sorted(positions_a - positions_b):
            yield "structure reference", f"{structure} -> start chunk {position} only in A"
        for position in sorted(positions_b - positions_a):
            yield "structure reference", f"{structure} -> start chunk {position} only in B"
    yield from _diff_other_fields(a, b, {"starts", "References"}, "structure field")


def unpack_chunk_pos(packed: int) -> sweep.ChunkKey:
    """ChunkPos.pack: x in the low 32 bits, z in the high 32 bits, both signed."""
    packed &= 0xFFFFFFFFFFFFFFFF
    x, z = packed & 0xFFFFFFFF, packed >> 32
    return x - (1 << 32) if x >= 1 << 31 else x, z - (1 << 32) if z >= 1 << 31 else z


def _diff_heightmaps(key: sweep.ChunkKey, a: dict, b: dict, footprint: Footprint = NO_FOOTPRINT) -> Iterator[tuple[str, str]]:
    """Columns that lie under the footprint of an excused chunk are counted apart ("masked heightmap")."""
    under = footprint.local_columns(key)
    for name in sorted(set(a) | set(b)):
        if a.get(name) == b.get(name):
            continue
        if name not in a or name not in b:
            yield "heightmap", f"{name} only in {'A' if name in a else 'B'}"
            continue
        differing = [index for index, (x, y) in enumerate(zip(heightmap_values(a[name]), heightmap_values(b[name])))
                     if x != y]
        outside = [index for index in differing if index not in under]
        if outside or not differing:
            yield "heightmap", f"{name}: {len(outside)} of {HEIGHTMAP_COLUMNS} columns differ"
        if len(outside) != len(differing):
            yield "masked heightmap", f"{name}: {len(differing) - len(outside)} columns under the footprint"


def heightmap_values(data: tuple) -> list[int]:
    per_long = -(-HEIGHTMAP_COLUMNS // len(data))
    return chunkdiff.unpack_indices(data, 64 // per_long, HEIGHTMAP_COLUMNS)


def _diff_post_processing(key: sweep.ChunkKey, a: list, b: list) -> Iterator[tuple[str, str]]:
    for index in range(max(len(a), len(b))):
        marks_a = Counter(a[index]) if index < len(a) else Counter()
        marks_b = Counter(b[index]) if index < len(b) else Counter()
        for side, extra in (("A", marks_a - marks_b), ("B", marks_b - marks_a)):
            for packed in sorted(extra.elements()):
                yield "post-processing", f"{_post_processing_position(key, index, packed)} marked only in {side}"


def _post_processing_position(key: sweep.ChunkKey, section_index: int, packed: int) -> str:
    """ProtoChunk.packOffsetCoordinates: x | y << 4 | z << 8 inside the section; the section index counts from the
    lowest section, which the chunk alone does not name, so y is given relative to it."""
    x, y, z = unpack_post_processing(key, 0, packed)
    return f"({x}, section #{section_index} y+{y}, {z})"


def diff_entity_storage(dimension: str, chunks_a: dict, chunks_b: dict) -> list[Difference]:
    found = []
    for key in sorted(set(chunks_a) | set(chunks_b)):
        a, b = chunks_a.get(key, {}), chunks_b.get(key, {})
        if isinstance(a, str) or isinstance(b, str):
            found.append(Difference(dimension, key, "entity chunk", f"A {a!r:.80} B {b!r:.80}"))
            continue
        for category, text in diff_entity_lists(a.get("Entities", []), b.get("Entities", [])):
            found.append(Difference(dimension, key, category, text))
    return found


def diff_entity_lists(a: list, b: list) -> Iterator[tuple[str, str]]:
    """Entities as a multiset without UUIDs; an entity left over on both sides with the same id and position is
    reported once, with the fields that differ, instead of as one missing and one extra entity."""
    remaining_a = Counter(chunkdiff.canonical(normalized_entity(entity)) for entity in a)
    remaining_b = Counter(chunkdiff.canonical(normalized_entity(entity)) for entity in b)
    only_a = [json.loads(text) for text in sorted((remaining_a - remaining_b).elements())]
    only_b = [json.loads(text) for text in sorted((remaining_b - remaining_a).elements())]
    for entity in only_a:
        partner = next((other for other in only_b if _entity_identity(other) == _entity_identity(entity)), None)
        if partner is None:
            yield "entity", f"only in A: {chunkdiff.shorten(chunkdiff.canonical(entity))}"
            continue
        only_b.remove(partner)
        yield "entity", f"{entity.get('id')} at {entity.get('Pos')}: " + "; ".join(_entity_changes(entity, partner)[:6])
    for entity in only_b:
        yield "entity", f"only in B: {chunkdiff.shorten(chunkdiff.canonical(entity))}"


def _entity_changes(a: dict, b: dict) -> list[str]:
    """Field changes; the attribute list is compared by attribute id, so an attribute written by one side only reads
    as that, e.g. "attribute minecraft:scale only in B (base 1.0, no modifiers)"."""
    rest_a = {key: value for key, value in a.items() if key != "attributes"}
    rest_b = {key: value for key, value in b.items() if key != "attributes"}
    changes = [f"{path}: {chunkdiff.shorten(chunkdiff.canonical(x))} -> {chunkdiff.shorten(chunkdiff.canonical(y))}"
               for path, x, y in chunkdiff.value_changes(rest_a, rest_b, "")]
    return changes + list(_attribute_changes(a.get("attributes", []), b.get("attributes", [])))


def _attribute_changes(a: list, b: list) -> Iterator[str]:
    by_id_a = {attribute.get("id"): attribute for attribute in a}
    by_id_b = {attribute.get("id"): attribute for attribute in b}
    for attribute_id in sorted(set(by_id_a) | set(by_id_b), key=str):
        x, y = by_id_a.get(attribute_id), by_id_b.get(attribute_id)
        if x is None or y is None:
            side, present = ("B", y) if x is None else ("A", x)
            modifiers = "no modifiers" if not present.get("modifiers") else f"{len(present['modifiers'])} modifiers"
            yield f"attribute {attribute_id} only in {side} (base {present.get('base')}, {modifiers})"
        elif x != y:
            yield f"attribute {attribute_id}: {chunkdiff.shorten(chunkdiff.canonical(x))} -> "                   f"{chunkdiff.shorten(chunkdiff.canonical(y))}"


def normalized_entity(entity: dict) -> dict:
    """Without volatile keys, and with the attribute list in id order: Mojang writes it from a hash map keyed by
    attribute holders (identity hash codes), so its order changes from one JVM start to the next (seen in the first
    vanilla self-check: same attributes, different order)."""
    clean = chunkdiff.without_volatile(entity)
    if isinstance(clean.get("attributes"), list):
        clean["attributes"] = sorted(clean["attributes"], key=lambda attribute: str(attribute.get("id")))
    return clean


def _entity_identity(entity: dict) -> tuple:
    return tuple(chunkdiff.canonical(entity.get(key)) for key in _ENTITY_MATCH_KEYS)


def coverage(chunks: dict, entity_chunks: dict) -> Counter:
    """What a comparison looked at, so that "0 differences" over nothing cannot pass unnoticed."""
    counts, biomes = Counter(), set()
    counts["stored entities"] = sum(len(chunk.get("Entities", [])) for chunk in entity_chunks.values()
                                    if isinstance(chunk, dict))
    for chunk in chunks.values():
        if isinstance(chunk, str):
            counts["unreadable chunks"] += 1
            continue
        counts[f"status {str(chunk.get('Status', '?')).removeprefix('minecraft:')}"] += 1
        _count_chunk(chunk, counts, biomes)
    counts["distinct biomes"] = len(biomes)
    return counts


def _count_chunk(chunk: dict, counts: Counter, biomes: set) -> None:
    for section in chunk.get("sections", []):
        palette = [chunkdiff.block_state_name(entry) for entry in section.get("block_states", {}).get("palette", [])]
        if any(name != chunkdiff.AIR for name in palette):
            counts["sections with blocks"] += 1
        biomes.update(section.get("biomes", {}).get("palette", []))
    counts["block entities"] += len(chunk.get("block_entities", []))
    counts["scheduled ticks"] += len(chunk.get("block_ticks", [])) + len(chunk.get("fluid_ticks", []))
    counts["proto-chunk entities"] += len(chunk.get("entities", []))
    structures = chunk.get("structures", {})
    counts["structure starts"] += sum(1 for start in structures.get("starts", {}).values()
                                      if start.get("id") != "INVALID")
    counts["structure references"] += sum(len(values) for values in structures.get("References", {}).values())
    counts["post-processing marks"] += sum(len(marks) for marks in chunk.get("PostProcessing", []))


def _vacuity_problems(location: sweep.Location, side: str, chunks: dict) -> list[str]:
    not_full = [key for key in sweep.must_be_full(location)
                if not isinstance(chunks.get(key), dict) or not str(chunks[key].get("Status", "")).endswith("full")]
    if not_full:
        return [f"{side}: {len(not_full)} chunks that the sweep makes full are missing or not full, e.g. {not_full[:4]}"]
    return []


def origin_of(location: sweep.Location, chunk: sweep.ChunkKey | None) -> str:
    """Which statuses of this chunk the jar under test computed itself; the rest it only loaded and saved again."""
    computed = sweep.generated_by_sweep(location).get(chunk) if chunk else None
    return "computed: " + ",".join(computed) if computed else "loaded from the bootstrap only"
