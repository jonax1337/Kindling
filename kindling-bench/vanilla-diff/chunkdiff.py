"""Differences between two decoded chunks (and between two decoded entity chunks).

Every function yields (category, description) pairs; the position is in world coordinates.
"""
import json
from collections import Counter
from typing import Iterator, NamedTuple

from ignored import is_volatile_key

CELLS_PER_SECTION_BLOCKS = 4096
CELLS_PER_SECTION_BIOMES = 64
MIN_BITS_BLOCKS = 4
MIN_BITS_BIOMES = 1
AIR = "minecraft:air"
_AIR_CONTAINER = {"palette": [{"Name": AIR}]}
_SUMMARY_LIMIT = 240
_MAX_PATHS_PER_BLOCK_ENTITY = 6


class Difference(NamedTuple):
    category: str
    description: str


class ChunkFormatError(ValueError):
    pass


def diff_chunks(chunk_x: int, chunk_z: int, a: dict, b: dict) -> Iterator[Difference]:
    yield from _diff_sections(chunk_x, chunk_z, a, b)
    yield from _diff_block_entities(a, b)
    for key, label in (("block_ticks", "block tick"), ("fluid_ticks", "fluid tick")):
        yield from _diff_scheduled_ticks(a.get(key, []), b.get(key, []), label)


def diff_entities(a: list, b: list) -> Iterator[Difference]:
    """Entities are matched as a multiset (their UUIDs are random), so a moved item shows as one missing and one extra."""
    in_a = Counter(canonical(_without_volatile(entity)) for entity in a)
    in_b = Counter(canonical(_without_volatile(entity)) for entity in b)
    for text in sorted((in_a - in_b).elements()):
        yield Difference("entity", f"only in A: {_shorten(text)}")
    for text in sorted((in_b - in_a).elements()):
        yield Difference("entity", f"only in B: {_shorten(text)}")


def canonical(value) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def block_state_name(palette_entry: dict) -> str:
    properties = palette_entry.get("Properties")
    if not properties:
        return palette_entry["Name"]
    inner = ",".join(f"{key}={value}" for key, value in sorted(properties.items()))
    return f"{palette_entry['Name']}[{inner}]"


def unpack_indices(data: tuple, bits: int, count: int) -> list[int]:
    """Palette indices of a section. Since 1.16 an entry never spans two longs: each long holds 64 // bits of them."""
    per_long = 64 // bits
    if len(data) * per_long < count:
        raise ChunkFormatError(f"{len(data)} longs cannot hold {count} entries of {bits} bits")
    mask = (1 << bits) - 1
    indices = []
    for packed in data:
        packed &= 0xFFFFFFFFFFFFFFFF
        indices.extend((packed >> (i * bits)) & mask for i in range(per_long))
    return indices[:count]


class _Cells(NamedTuple):
    palette: list[str]
    indices: list[int] | None  # None: every cell holds palette[0]

    def values(self, count: int) -> list[str]:
        if self.indices is None:
            return self.palette[:1] * count
        return [self.palette[i] for i in self.indices]


def _decode(container: dict, name_of, min_bits: int, count: int) -> _Cells:
    palette = [name_of(entry) for entry in container["palette"]]
    data = container.get("data")
    if len(palette) == 1 or not data:
        return _Cells(palette, None)
    bits = max(min_bits, (len(palette) - 1).bit_length())
    return _Cells(palette, unpack_indices(data, bits, count))


def _differing_cells(a: dict, b: dict, name_of, min_bits: int, count: int) -> Iterator[tuple[int, str, str]]:
    if a == b:
        return
    values_a = _decode(a, name_of, min_bits, count).values(count)
    values_b = _decode(b, name_of, min_bits, count).values(count)
    for index in range(count):
        if values_a[index] != values_b[index]:
            yield index, values_a[index], values_b[index]


def _sections_by_y(chunk: dict) -> dict[int, dict]:
    return {section["Y"]: section for section in chunk.get("sections", [])}


def _diff_sections(chunk_x: int, chunk_z: int, a: dict, b: dict) -> Iterator[Difference]:
    sections_a, sections_b = _sections_by_y(a), _sections_by_y(b)
    for y in sorted(set(sections_a) | set(sections_b)):
        section_a, section_b = sections_a.get(y, {}), sections_b.get(y, {})
        yield from _diff_section_blocks(chunk_x, chunk_z, y, section_a, section_b)
        yield from _diff_section_biomes(chunk_x, chunk_z, y, section_a, section_b)


def _diff_section_blocks(chunk_x: int, chunk_z: int, section_y: int, a: dict, b: dict) -> Iterator[Difference]:
    states_a = a.get("block_states", _AIR_CONTAINER)
    states_b = b.get("block_states", _AIR_CONTAINER)
    for index, name_a, name_b in _differing_cells(
            states_a, states_b, block_state_name, MIN_BITS_BLOCKS, CELLS_PER_SECTION_BLOCKS):
        x = chunk_x * 16 + (index & 15)
        z = chunk_z * 16 + ((index >> 4) & 15)
        y = section_y * 16 + (index >> 8)
        yield Difference("block", f"({x}, {y}, {z}) {name_a} -> {name_b}")


def _diff_section_biomes(chunk_x: int, chunk_z: int, section_y: int, a: dict, b: dict) -> Iterator[Difference]:
    if "biomes" not in a or "biomes" not in b:
        return
    for index, name_a, name_b in _differing_cells(
            a["biomes"], b["biomes"], str, MIN_BITS_BIOMES, CELLS_PER_SECTION_BIOMES):
        x = chunk_x * 4 + (index & 3)
        z = chunk_z * 4 + ((index >> 2) & 3)
        y = section_y * 4 + (index >> 4)
        yield Difference("biome", f"quart ({x}, {y}, {z}) {name_a} -> {name_b}")


def _without_volatile(value):
    if isinstance(value, dict):
        return {key: _without_volatile(inner) for key, inner in value.items() if not is_volatile_key(key)}
    if isinstance(value, list):
        return [_without_volatile(inner) for inner in value]
    return value


def _block_entities_by_position(chunk: dict) -> dict[tuple, dict]:
    return {
        (entity["x"], entity["y"], entity["z"]): _without_volatile(entity)
        for entity in chunk.get("block_entities", [])
    }


def _diff_block_entities(a: dict, b: dict) -> Iterator[Difference]:
    entities_a, entities_b = _block_entities_by_position(a), _block_entities_by_position(b)
    for position in sorted(set(entities_a) | set(entities_b)):
        label = "({}, {}, {})".format(*position)
        if position not in entities_b:
            yield Difference("block entity", f"{label} {entities_a[position].get('id')} only in A")
        elif position not in entities_a:
            yield Difference("block entity", f"{label} {entities_b[position].get('id')} only in B")
        else:
            yield from _diff_block_entity(label, entities_a[position], entities_b[position])


def _diff_block_entity(label: str, a: dict, b: dict) -> Iterator[Difference]:
    changes = list(_value_changes(a, b, ""))
    for path, value_a, value_b in changes[:_MAX_PATHS_PER_BLOCK_ENTITY]:
        yield Difference("block entity", f"{label} {a.get('id')} {path}: {_shorten(canonical(value_a))}"
                                         f" -> {_shorten(canonical(value_b))}")
    if len(changes) > _MAX_PATHS_PER_BLOCK_ENTITY:
        yield Difference("block entity", f"{label} {a.get('id')} ... {len(changes) - _MAX_PATHS_PER_BLOCK_ENTITY} more")
    if changes and ("Items" in a or "Items" in b):
        totals_a, totals_b = _item_totals(a), _item_totals(b)
        verdict = "totals equal, slots differ" if totals_a == totals_b else "totals differ"
        yield Difference("container", f"{label} {a.get('id')} {verdict}: A {dict(totals_a)} B {dict(totals_b)}")


def _item_totals(block_entity: dict) -> Counter:
    totals = Counter()
    for stack in block_entity.get("Items", []):
        totals[stack.get("id")] += stack.get("count", stack.get("Count", 1))
    return totals


def _value_changes(a, b, path: str) -> Iterator[tuple[str, object, object]]:
    if isinstance(a, dict) and isinstance(b, dict):
        for key in sorted(set(a) | set(b)):
            inner = f"{path}.{key}" if path else key
            if key not in b or key not in a:
                yield inner, a.get(key), b.get(key)
            else:
                yield from _value_changes(a[key], b[key], inner)
    elif isinstance(a, list) and isinstance(b, list) and len(a) == len(b):
        for index, (inner_a, inner_b) in enumerate(zip(a, b)):
            yield from _value_changes(inner_a, inner_b, f"{path}[{index}]")
    elif a != b:
        yield path or "<root>", a, b


def _diff_scheduled_ticks(a: list, b: list, label: str) -> Iterator[Difference]:
    in_a, in_b = Counter(canonical(tick) for tick in a), Counter(canonical(tick) for tick in b)
    for text in sorted((in_a - in_b).elements()):
        yield Difference("scheduled tick", f"{label} only in A: {text}")
    for text in sorted((in_b - in_a).elements()):
        yield Difference("scheduled tick", f"{label} only in B: {text}")


def _shorten(text: str) -> str:
    return text if len(text) <= _SUMMARY_LIMIT else text[:_SUMMARY_LIMIT] + "..."
