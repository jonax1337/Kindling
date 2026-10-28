"""The contraptions mode: a library of designs, one per chunk, built and run while the server is frozen."""
import designs
import fuzz
from cell import Cell
from commandscript import CommandScript

PLATFORM_Y = (-60, -59)  # two layers of stone that every cell is built on, whatever the generator made below
CELL_Y = -58
GRID_COLUMNS = 9
FIRST_CHUNK = 1  # chunk 0 and the last row and column stay empty, so that every cell has loaded neighbours
FUZZ_CELLS = 22
STEP_PLAN = (1, 1, 2, 5, 11, 20, 60, 100, 200)  # tick steps; stage n of a design runs after step n
GAME_RULES = (
    ("randomTickSpeed", 0), ("doMobSpawning", False), ("doPatrolSpawning", False), ("doTraderSpawning", False),
    ("doWardenSpawning", False), ("doWeatherCycle", False), ("doDaylightCycle", False), ("doFireTick", False),
    ("doTileDrops", False), ("doEntityDrops", False), ("doMobLoot", False),
)
SERVER_PROPERTIES = {
    "level-type": "flat",
    "generate-structures": "false",
    "generator-settings": '{"biome":"minecraft:plains","features":false,"lakes":false,'
                          '"layers":[{"block":"minecraft:stone","height":1}],"structure_overrides":[]}',
}


def designs_in_order() -> list[tuple[str, int, callable]]:
    """(name, rotation, build function) for every hand-made cell, then the seeded random ones."""
    cells = []
    for rotation in range(4):
        cells += [
            ("dust_comb", rotation, designs.dust_comb),
            ("piston_slime", rotation, lambda c, blocked=rotation % 2 == 1: designs.piston_slime(c, blocked)),
            ("hopper_chain", rotation, designs.hopper_chain),
            ("note_sculk", rotation, designs.note_sculk),
        ]
        for direction in (-1, 1):
            plate = "oak_pressure_plate" if direction < 0 else "heavy_weighted_pressure_plate"
            cells += [
                (f"pressure_plates{direction:+d}", rotation, lambda c, d=direction, p=plate: designs.pressure_plates(c, d, p)),
                (f"tripwires{direction:+d}", rotation, lambda c, d=direction: designs.tripwires(c, d)),
                (f"detector_rails{direction:+d}", rotation, lambda c, d=direction: designs.detector_rails(c, d)),
            ]
    cells += [("observer_ring", rotation, designs.observer_ring) for rotation in (0, 2)]
    cells += [("hopper_pile", rotation, lambda c, twin=twin: designs.hopper_pile(c, twin))
              for rotation in (0, 2) for twin in (False, True)]
    cells += [("dropper_dispenser", rotation, designs.dropper_dispenser) for rotation in (0, 1)]
    cells += [("comparator_containers", rotation, designs.comparator_containers) for rotation in (0, 3)]
    cells += [(f"redstone_soup#{seed}", seed % 4, lambda c, s=seed: fuzz.redstone_soup(c, s)) for seed in range(FUZZ_CELLS)]
    return cells


def cell_origin(index: int) -> tuple[int, int, int]:
    """Where cell number `index` starts: its own chunk, shifted inside it so that the positions vary."""
    chunk_x, chunk_z = FIRST_CHUNK + index % GRID_COLUMNS, FIRST_CHUNK + index // GRID_COLUMNS
    return chunk_x * 16 + 2 + (index * 7) % 4, CELL_Y, chunk_z * 16 + 2 + (index * 5) % 4


def build_cells() -> list[tuple[str, Cell]]:
    built = []
    for index, (name, rotation, build) in enumerate(designs_in_order()):
        cell = Cell(cell_origin(index), rotation)
        build(cell)
        built.append((name, cell))
    return built


def chunk_area(cell_count: int) -> tuple[int, int, int, int]:
    """The forceloaded chunk rectangle (min x, min z, max x, max z): every cell plus one chunk around it."""
    rows = -(-cell_count // GRID_COLUMNS)
    return 0, 0, FIRST_CHUNK + GRID_COLUMNS, FIRST_CHUNK + rows


def build_script() -> tuple[CommandScript, list[tuple[int, int, int, int]]]:
    cells = build_cells()
    area = chunk_area(len(cells))
    script = CommandScript()
    script.comment(f"contraptions: {len(cells)} cells, forceloaded chunks {area}")
    for rule, value in GAME_RULES:
        script.command(f"gamerule {rule} {str(value).lower()}")
    script.command("weather clear")
    min_x, min_z, max_x, max_z = area
    script.command(f"forceload add {min_x * 16} {min_z * 16} {max_x * 16 + 15} {max_z * 16 + 15}")
    script.await_loaded(*area)
    script.command("tick freeze")
    for name, cell in cells:
        script.comment(name)
        chunk_x, chunk_z = cell.origin[0] // 16, cell.origin[2] // 16
        script.command(f"fill {chunk_x * 16} {PLATFORM_Y[0]} {chunk_z * 16} {chunk_x * 16 + 15} {PLATFORM_Y[1]} "
                       f"{chunk_z * 16 + 15} minecraft:stone")
        script.commands(cell.setup)
    script.comment("set everything going")
    for _, cell in cells:
        script.commands(cell.start)
    for stage, ticks in enumerate(STEP_PLAN, start=1):
        script.step(ticks)
        for _, cell in cells:
            script.commands(cell.later.get(stage, []))
    script.command("save-all flush")
    script.command("stop")
    return script, [area]
