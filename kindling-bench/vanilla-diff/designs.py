"""The hand-made contraptions. Each design takes a Cell and fills in its structure, its start and its follow-ups.

Local coordinates: x east, y up (0 = first block above the floor), z south, 0..9. Every design avoids sources of
randomness in the game itself: nothing ejects items into the air (a dropper's spread is random) and nothing destroys
blocks into item drops (the script also turns tile drops off).
"""
from cell import Cell

PLATE_ROW_Z = 3
# Stages are numbered by the tick step after which they run (see contraptions.STEP_PLAN).
STAGE_RETRACT = 4
STAGE_UNLOCK = 6
STAGE_SECOND_NOTE = 8


def stack(slot: int, item: str, count: int) -> str:
    return f'{{Slot:{slot}b,id:"minecraft:{item}",count:{count}}}'


def container_data(*stacks: str) -> str:
    return "{Items:[" + ",".join(stacks) + "]}"


def item_nbt(item: str, count: int) -> str:
    return f'Item:{{id:"minecraft:{item}",count:{count}}}'


def dust_comb(cell: Cell) -> None:
    """A dust line over supports, with pistons beside the supports and observers watching the dust."""
    cell.fill((1, 0, 0), (8, 0, 0), "stone")
    cell.fill((1, 1, 0), (8, 1, 0), "redstone_wire")
    for x in (2, 4, 6, 8):
        cell.block(x, 0, 1, "sticky_piston" if x % 4 == 0 else "piston", facing="south")
        cell.block(x, 0, 2, "stone" if x % 4 == 2 else "slime_block")
    for x in (3, 5, 7):
        cell.block(x, 1, 1, "observer", facing="north")
        cell.block(x, 1, 2, "redstone_lamp")
    cell.start_block(0, 1, 0, "redstone_block")


def observer_ring(cell: Cell) -> None:
    """Two observers that watch each other (a clock) and a chain of observers that a placed block sets off."""
    cell.block(2, 0, 4, "redstone_lamp")
    cell.block(5, 0, 4, "redstone_lamp")
    cell.block(3, 0, 4, "observer", facing="east")
    cell.block(4, 0, 4, "observer", facing="west")
    for x in range(4, 9):
        cell.block(x, 0, 7, "observer", facing="west")
    cell.block(9, 0, 7, "redstone_lamp")
    cell.start_block(3, 0, 7, "stone")


def piston_slime(cell: Cell, blocked: bool) -> None:
    """A sticky piston that pushes a slime block structure, optionally against an obsidian block, and pulls it back."""
    cell.block(1, 0, 4, "sticky_piston", facing="east")
    cell.block(2, 0, 4, "slime_block")
    cell.block(3, 0, 4, "slime_block")
    cell.block(3, 0, 5, "slime_block")
    cell.block(3, 0, 3, "stone")
    cell.block(4, 0, 5, "honey_block")
    cell.block(3, 0, 6, "obsidian" if blocked else "stone")
    cell.start_block(1, 1, 4, "redstone_block")
    cell.later_block(STAGE_RETRACT, 1, 1, 4, "air")


def hopper_chain(cell: Cell) -> None:
    """Hoppers in a line into a chest, a dropper feeding the third one, a lock that is released later."""
    stocked = container_data(stack(0, "cobblestone", 64), stack(1, "dirt", 32), stack(3, "oak_log", 7))
    cell.block(1, 0, 3, "hopper", stocked, facing="east")
    for x in (2, 3, 4):
        cell.block(x, 0, 3, "hopper", facing="east")
    cell.block(5, 0, 3, "chest", facing="west")
    cell.block(3, 0, 2, "dropper", container_data(stack(0, "iron_ingot", 5)), facing="south")
    cell.block(6, 0, 3, "comparator", facing="east")
    cell.fill((7, 0, 3), (8, 0, 3), "redstone_wire")
    cell.block(9, 0, 3, "redstone_lamp")
    cell.start_block(2, 0, 4, "redstone_block")
    cell.start_block(3, 1, 2, "redstone_block")
    cell.later_block(STAGE_UNLOCK, 2, 0, 4, "air")


def hopper_pile(cell: Cell, twin: bool) -> None:
    """Item entities falling onto a hopper (or onto the border of two), picked up in entity order."""
    cell.block(3, 0, 3, "chest", facing="north")
    cell.block(3, 1, 3, "hopper", facing="down")
    pile = [("cobblestone", 5), ("dirt", 3), ("oak_log", 9), ("sand", 1), ("cobblestone", 2), ("gravel", 7)]
    if twin:
        cell.block(4, 0, 3, "chest", facing="north")
        cell.block(4, 1, 3, "hopper", facing="down")
    for index, (item, count) in enumerate(pile):
        x = 3.8 + 0.08 * index if twin else 3.2 + 0.1 * index
        cell.start_entity("item", x, 3.0 + 0.3 * index, 3.5, item_nbt(item, count), motion=(0.0, 0.0, 0.0))


def dropper_dispenser(cell: Cell) -> None:
    """A dropper into a chest, a dispenser that fills a walled pool, a dispenser that places a shulker box."""
    cell.block(1, 0, 1, "dropper", container_data(stack(0, "iron_ingot", 5)), facing="east")
    cell.block(2, 0, 1, "chest", facing="west")
    cell.start_block(1, 1, 1, "redstone_block")
    cell.later_block(STAGE_RETRACT, 1, 1, 1, "air")
    cell.later_block(STAGE_UNLOCK, 1, 1, 1, "redstone_block")
    cell.fill((1, 0, 4), (5, 0, 8), "stone")
    cell.fill((2, 0, 5), (4, 0, 7), "air")
    cell.block(1, 0, 6, "dispenser", container_data(stack(0, "water_bucket", 1)), facing="east")
    cell.start_block(1, 1, 6, "redstone_block")
    cell.block(7, 0, 1, "dispenser", container_data(stack(0, "white_shulker_box", 1)), facing="east")
    cell.start_block(7, 1, 1, "redstone_block")


def comparator_containers(cell: Cell) -> None:
    """Comparators reading a chest, a barrel, a hopper and a furnace; the dust after each shows the signal."""
    sources = (
        ("chest", container_data(stack(0, "cobblestone", 64), stack(5, "dirt", 17), stack(26, "sand", 3)),
         {"facing": "east"}),
        ("barrel", container_data(stack(0, "oak_log", 33), stack(13, "gravel", 1)), {"facing": "up"}),
        ("hopper", container_data(stack(0, "stone", 64), stack(1, "stone", 64), stack(2, "dirt", 9)),
         {"facing": "down"}),
        ("furnace", container_data(stack(0, "iron_ore", 12), stack(1, "coal", 20)), {"facing": "east"}),
    )
    for row, (container, data, props) in enumerate(sources):
        z = 1 + 2 * row
        cell.block(1, 0, z, container, data, **props)
        cell.block(2, 0, z, "comparator", facing="east", mode="subtract" if row % 2 else "compare")
        cell.fill((3, 0, z), (4, 0, z), "redstone_wire")
        cell.block(5, 0, z, "redstone_lamp")
    cell.block(7, 0, 1, "composter", level=5)
    cell.block(8, 0, 1, "comparator", facing="east")
    cell.block(9, 0, 1, "redstone_wire")


def pressure_plates(cell: Cell, direction: int, plate: str) -> None:
    """Two plates that each drive a piston against the same block; an item crosses both, toward -x or +x."""
    z = PLATE_ROW_Z
    cell.block(1, 0, z - 1, "piston", facing="east")
    cell.block(2, 0, z - 1, "stone")
    cell.block(4, 0, z - 1, "piston", facing="west")
    cell.block(1, 0, z, plate)
    cell.block(4, 0, z, plate)
    _slide_item(cell, direction, z)


def tripwires(cell: Cell, direction: int) -> None:
    """A tripwire between two hooks; each hook powers the stone it sits on, and pistons sit against those stones."""
    z = PLATE_ROW_Z
    cell.block(0, 0, z, "stone")
    cell.block(1, 0, z, "tripwire_hook", facing="east", attached=True)
    cell.fill((2, 0, z), (5, 0, z), "tripwire", attached=True)
    cell.block(6, 0, z, "tripwire_hook", facing="west", attached=True)
    cell.block(7, 0, z, "stone")
    cell.block(0, 0, z - 1, "piston", facing="north")
    cell.block(7, 0, z - 1, "piston", facing="north")
    cell.block(0, 0, z - 2, "stone")
    cell.block(7, 0, z - 2, "stone")
    _slide_item(cell, direction, z, west_end=1.5)


def detector_rails(cell: Cell, direction: int) -> None:
    """A minecart on a rail line over two detector rails, each with a piston beside it; walls stop the cart."""
    z = PLATE_ROW_Z
    cell.fill((0, 0, z), (0, 1, z), "stone")
    cell.fill((9, 0, z), (9, 1, z), "stone")
    cell.fill((1, 0, z), (8, 0, z), "rail", shape="east_west")
    for x in (3, 6):
        cell.block(x, 0, z, "detector_rail", shape="east_west")
        cell.block(x, 0, z - 1, "piston", facing="north")
        cell.block(x, 0, z - 2, "stone")
    start_x = 8.5 if direction < 0 else 1.5
    cell.start_entity("chest_minecart", start_x, 0.1, z + 0.5,
                      "Items:[" + stack(0, "cobblestone", 3) + "]", motion=(0.6 * direction, 0.0, 0.0))


def note_sculk(cell: Cell) -> None:
    """Note blocks that start game events, sculk sensors around them (one behind wool), comparators on the sensors."""
    z = PLATE_ROW_Z
    cell.block(1, 0, z, "note_block", instrument="basedrum", note=7)
    cell.block(1, 0, 6, "note_block", instrument="basedrum", note=19)  # powered from the side: it needs air above
    for x in (3, 5, 8):
        cell.block(x, 0, z, "sculk_sensor")
        cell.block(x, 0, z + 1, "comparator", facing="south")
        cell.block(x, 0, z + 2, "redstone_wire")
        cell.block(x, 0, z + 3, "redstone_lamp")
    cell.block(7, 0, z, "white_wool")
    cell.start_block(0, 0, z, "redstone_block")
    cell.later_block(STAGE_SECOND_NOTE, 0, 0, 6, "redstone_block")


def _slide_item(cell: Cell, direction: int, z: int, west_end: float = 0.5, east_end: float = 6.5) -> None:
    start_x = east_end if direction < 0 else west_end
    cell.start_entity("item", start_x, 0.2, z + 0.5, item_nbt("cobblestone", 1), motion=(2.5 * direction, 0.0, 0.0))
