"""The worldgen mode: generate fixed chunk areas from the same seed and save them."""
from commandscript import CommandScript

SERVER_PROPERTIES = {"level-type": "normal", "generate-structures": "true"}
# 26.3 names; the pre-26 names were rejected, so these rules were never set before (../worldgen-parity/IMPL-NOTES.md)
GAME_RULES = (("spawn_mobs", False), ("spawn_patrols", False), ("spawn_wandering_traders", False),
              ("spawn_wardens", False), ("advance_time", False), ("advance_weather", False))
SQUARE_CHUNKS = 6
DISTANCES_BLOCKS = (1000, 2000, 6000)


def squares_around(distance_blocks: int) -> list[tuple[int, int, int, int]]:
    """Four squares at the corners (or edges) of a ring of the given distance from the origin."""
    centre = distance_blocks // 16
    half = SQUARE_CHUNKS // 2
    return [(x - half, z - half, x + half - 1, z + half - 1)
            for x, z in ((centre, centre), (-centre, centre), (centre, -centre), (-centre, -centre))]


def chunk_areas() -> list[tuple[int, int, int, int]]:
    """Chunk rectangles (min x, min z, max x, max z). Spread over the map, so that oceans, mountains, caves, rivers and
    biome borders are all inside by sheer coverage: around spawn, four squares on three rings, and a long strip."""
    spawn = [(-4, -4, 3, 3)]
    rings = [area for distance in DISTANCES_BLOCKS for area in squares_around(distance)]
    strip = [(100, 0, 163, 1)]
    return spawn + rings + strip


def build_script() -> tuple[CommandScript, list[tuple[int, int, int, int]]]:
    areas = chunk_areas()
    script = CommandScript()
    script.comment(f"worldgen: {len(areas)} areas, {sum((x2 - x1 + 1) * (z2 - z1 + 1) for x1, z1, x2, z2 in areas)} chunks")
    for rule, value in GAME_RULES:
        script.command(f"gamerule {rule} {str(value).lower()}")
    for min_x, min_z, max_x, max_z in areas:
        script.command(f"forceload add {min_x * 16} {min_z * 16} {max_x * 16 + 15} {max_z * 16 + 15}")
    for area in areas:
        script.await_loaded(*area)
    script.command("save-all flush")
    script.command("stop")
    return script, areas
