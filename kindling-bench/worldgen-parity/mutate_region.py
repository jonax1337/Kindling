"""Self-test 6: plants exactly one change of each kind into a copy of a saved world.

One block state, one biome cell, one structure reference, one scheduled tick and one entity field, each in its own
full chunk of the swept rectangle. The comparer must report exactly these five, one difference each.
"""
from pathlib import Path
from typing import NamedTuple

import paths  # noqa: F401
import chunkdiff
import nbt
import sweep
import worlddiff
from nbtwrite import Tag, compound, read_chunk, write_chunk

PLANTED_BIOME = "minecraft:the_void"
PLANTED_STRUCTURE = "minecraft:planted_by_self_test"
CELL = 0  # the first cell of a section: x, y, z all 0 inside it


class Planted(NamedTuple):
    category: str
    chunk: sweep.ChunkKey
    what: str


def plant_all(world: Path, location: sweep.Location) -> list[Planted]:
    folders = worlddiff.find_dimensions(world)[location.dimension]
    targets = location.steps()
    return [
        _edit(folders.region, targets[0], "block", plant_block),
        _edit(folders.region, targets[1], "biome", plant_biome),
        _edit(folders.region, targets[2], "structure reference", plant_structure_reference),
        _edit(folders.region, targets[3], "scheduled tick", plant_scheduled_tick),
        plant_entity_field(folders.entities, location),
    ]


def _edit(directory: Path, chunk: sweep.ChunkKey, category: str, change) -> Planted:
    name, root = read_chunk(directory, *chunk)
    what = change(root.value, chunk)
    write_chunk(directory, *chunk, name, root)
    return Planted(category, chunk, what)


def pack_indices(indices: list[int], bits: int) -> tuple[int, ...]:
    """The inverse of chunkdiff.unpack_indices: 64 // bits entries per long, never spanning two, as signed longs."""
    per_long = 64 // bits
    packed = []
    for start in range(0, len(indices), per_long):
        value = 0
        for offset, index in enumerate(indices[start:start + per_long]):
            value |= index << (offset * bits)
        packed.append(value - (1 << 64) if value >= 1 << 63 else value)
    return tuple(packed)


def _set_cell(container: dict, cell_count: int, min_bits: int, new_index: int) -> None:
    palette = container["palette"].value[1]
    data = container.get("data")
    bits_now = max(min_bits, (len(palette) - 1).bit_length())
    indices = chunkdiff.unpack_indices(data.value, bits_now, cell_count) if data else [0] * cell_count
    indices[CELL] = new_index
    bits_after = max(min_bits, (len(palette) - 1).bit_length())
    container["data"] = Tag(nbt.TAG_LONG_ARRAY, pack_indices(indices, bits_after))


def _sections_with(chunk: dict, key: str) -> list[dict]:
    return [section.value for section in chunk["sections"].value[1] if key in section.value]


def plant_block(chunk: dict, _position) -> str:
    section = next(s for s in _sections_with(chunk, "block_states") if len(s["block_states"].value["palette"].value[1]) > 1)
    states = section["block_states"].value
    palette = states["palette"].value[1]
    current = chunkdiff.unpack_indices(states["data"].value, max(4, (len(palette) - 1).bit_length()), 4096)[CELL]
    replacement = 1 if current == 0 else 0
    _set_cell(states, 4096, 4, replacement)
    return f"section {section['Y'].value} cell 0: palette entry {current} -> {replacement}"



def plant_biome(chunk: dict, _position) -> str:
    section = _sections_with(chunk, "biomes")[0]
    biomes = section["biomes"].value
    element_kind, palette = biomes["palette"].value
    palette.append(Tag(nbt.TAG_STRING, PLANTED_BIOME))
    biomes["palette"] = Tag(nbt.TAG_LIST, (element_kind, palette))
    _set_cell(biomes, 64, 1, len(palette) - 1)
    return f"section {section['Y'].value} quart cell 0 -> {PLANTED_BIOME}"



def plant_structure_reference(chunk: dict, position: sweep.ChunkKey) -> str:
    references = chunk["structures"].value["References"].value
    x, z = position
    packed = (x & 0xFFFFFFFF) | (z & 0xFFFFFFFF) << 32
    references[PLANTED_STRUCTURE] = Tag(nbt.TAG_LONG_ARRAY, (packed - (1 << 64) if packed >= 1 << 63 else packed,))
    return f"{PLANTED_STRUCTURE} -> {position}"



def plant_scheduled_tick(chunk: dict, position: sweep.ChunkKey) -> str:
    _, ticks = chunk.get("fluid_ticks", Tag(nbt.TAG_LIST, (nbt.TAG_END, []))).value
    x, z = position[0] * 16, position[1] * 16
    tick = compound(i=Tag(nbt.TAG_STRING, "minecraft:water"), x=Tag(nbt.TAG_INT, x), y=Tag(nbt.TAG_INT, 0),
                    z=Tag(nbt.TAG_INT, z), t=Tag(nbt.TAG_INT, 7), p=Tag(nbt.TAG_INT, 0))
    chunk["fluid_ticks"] = Tag(nbt.TAG_LIST, (nbt.TAG_COMPOUND, ticks + [tick]))
    return f"fluid tick minecraft:water at ({x}, 0, {z})"



def plant_entity_field(directory: Path, location: sweep.Location) -> Planted:
    for chunk in location.steps():
        try:
            name, root = read_chunk(directory, *chunk)
        except KeyError:
            continue
        for entity in root.value.get("Entities", Tag(nbt.TAG_LIST, (nbt.TAG_END, []))).value[1]:
            if "Health" in entity.value:
                health = entity.value["Health"]
                entity.value["Health"] = Tag(health.kind, health.value + 1.0)
                write_chunk(directory, *chunk, name, root)
                return Planted("entity", chunk, f"{entity.value['id'].value} Health {health.value} -> {health.value + 1}")
    raise LookupError(f"no entity with Health in the swept chunks of {location.name}")
