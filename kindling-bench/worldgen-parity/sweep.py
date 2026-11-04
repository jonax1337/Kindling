"""The geometry of the canonical sweep: the bootstrap L, the raster order and the generation events each step causes.

A location is a rectangle of chunks [x0, x1] x [z0, z1] in one dimension. Mojang's jar generates the L once (the row
above the rectangle and the column to its left, see DESIGN.md 3.1); every measured run then forceloads the rectangle
one chunk at a time in raster order. With the L in place, step (x, z) raises exactly one chunk to every status: the
chunk (x + r, z + r), where r is the status's radius below.
"""
import json
from pathlib import Path
from typing import Iterator, NamedTuple

ChunkKey = tuple[int, int]

# Chessboard radius around a forced chunk (ticket level 31) out to which each status is needed: the ticket levels of
# ChunkLevel and the accumulated dependencies of ChunkPyramid.GENERATION_PYRAMID give the same numbers.
STATUS_RADII = (
    ("structure_starts", 13),
    ("structure_references", 5),
    ("biomes", 5),
    ("terrain", 4),
    ("features", 3),
    ("initialize_light", 3),
    ("light", 2),
    ("spawn", 2),
    ("full", 2),
)
RADIUS_OF = dict(STATUS_RADII)
STATUS_ORDER = tuple(status for status, _ in STATUS_RADII)
LARGEST_RADIUS = max(RADIUS_OF.values())
COMPARE_MARGIN = LARGEST_RADIUS + 1  # chunks this far outside the rectangle may have been written to disk

DIMENSIONS = ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")


class Location(NamedTuple):
    name: str
    dimension: str
    x0: int
    z0: int
    x1: int
    z1: int

    def steps(self) -> list[ChunkKey]:
        """Raster order: z ascending, then x ascending."""
        return [(x, z) for z in range(self.z0, self.z1 + 1) for x in range(self.x0, self.x1 + 1)]

    def bootstrap_chunks(self) -> list[ChunkKey]:
        column = [(self.x0 - 1, z) for z in range(self.z0 - 1, self.z1 + 1)]
        row = [(x, self.z0 - 1) for x in range(self.x0, self.x1 + 1)]
        return column + row

    def compare_box(self) -> tuple[int, int, int, int]:
        return (self.x0 - COMPARE_MARGIN, self.z0 - COMPARE_MARGIN,
                self.x1 + COMPARE_MARGIN, self.z1 + COMPARE_MARGIN)


class PredictedEvent(NamedTuple):
    step: int
    status: str
    chunk: ChunkKey


def predicted_events(location: Location) -> list[PredictedEvent]:
    """Every generation event the sweep must cause, in order: per step one chain in status order."""
    events = []
    for index, (x, z) in enumerate(location.steps()):
        for status, radius in STATUS_RADII:
            events.append(PredictedEvent(index, status, (x + radius, z + radius)))
    return events


def generated_by_sweep(location: Location) -> dict[ChunkKey, list[str]]:
    """Chunk -> the statuses the sweep computes for it (the rest of the chunk came from the bootstrap)."""
    found: dict[ChunkKey, list[str]] = {}
    for event in predicted_events(location):
        found.setdefault(event.chunk, []).append(event.status)
    return found


def must_be_full(location: Location) -> list[ChunkKey]:
    """Chunks that are full after the sweep whatever happened in the bootstrap: every step's radius-2 square."""
    radius = RADIUS_OF["full"]
    return sorted({(x + dx, z + dz) for x, z in location.steps()
                   for dx in range(-radius, radius + 1) for dz in range(-radius, radius + 1)})


def squares_reached(sources: list[ChunkKey], radius: int) -> set[ChunkKey]:
    return {(x + dx, z + dz) for x, z in sources for dx in range(-radius, radius + 1) for dz in range(-radius, radius + 1)}


def simulate_new_chunks(location: Location, radius: int) -> Iterator[set[ChunkKey]]:
    """For each step, the chunks newly inside some radius-r square: what a scheduler could have to order. Used by the
    tests to check the one-chunk-per-step claim against brute force instead of against the proof."""
    covered = squares_reached(location.bootstrap_chunks(), radius)
    for step in location.steps():
        reached = squares_reached([step], radius)
        yield reached - covered
        covered |= reached


def load_locations(path: Path, profile: str) -> list[Location]:
    """locations.json: {"profiles": {name: {"size": n, "locations": [...]}}, "locations": {name: {...}}}."""
    raw = json.loads(path.read_text(encoding="utf-8"))
    chosen = raw["profiles"][profile]
    size = chosen["size"]
    found = []
    for name in chosen["locations"]:
        spec = raw["locations"][name]
        if spec["dimension"] not in DIMENSIONS:
            raise ValueError(f"location {name}: unknown dimension {spec['dimension']}")
        x0, z0 = spec["chunk"]
        found.append(Location(name, spec["dimension"], x0, z0, x0 + size - 1, z0 + size - 1))
    return found
