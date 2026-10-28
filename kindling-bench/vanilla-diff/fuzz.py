"""Seeded random redstone: a dense mix of dust, pistons, observers, repeaters, comparators and torches.

The hand-made designs test mechanisms somebody thought of; this tests the ones nobody did. The same seed always
gives the same commands, so both servers get the same soup. Nothing in it needs the game's own randomness.
"""
import random

from cell import Cell, HORIZONTALS

GRID = range(1, 9)
POWER_SOURCES = 2
FIRST_LAYER = (
    ("stone", 22), ("redstone_wire", 30), ("repeater", 6), ("comparator", 4), ("observer", 6),
    ("piston", 5), ("sticky_piston", 4), ("redstone_torch", 4), ("slime_block", 3), ("redstone_lamp", 3),
    (None, 13),
)
SECOND_LAYER_ON_STONE = (
    ("redstone_wire", 50), ("redstone_torch", 15), ("repeater", 10), ("observer", 10), (None, 15),
)


def redstone_soup(cell: Cell, seed: int) -> None:
    rng = random.Random(seed)
    for x in GRID:
        for z in GRID:
            first = _pick(rng, FIRST_LAYER)
            _place(cell, rng, x, 0, z, first)
            if first == "stone":
                _place(cell, rng, x, 1, z, _pick(rng, SECOND_LAYER_ON_STONE))
    for _ in range(POWER_SOURCES):
        cell.start_block(rng.choice(GRID), 0, rng.choice(GRID), "redstone_block")


def _pick(rng: random.Random, weighted):
    names, weights = zip(*weighted)
    return rng.choices(names, weights)[0]


def _place(cell: Cell, rng: random.Random, x: int, y: int, z: int, block_id: str | None) -> None:
    if block_id is None:
        return
    cell.block(x, y, z, block_id, **_properties(rng, block_id))


def _properties(rng: random.Random, block_id: str) -> dict:
    if block_id == "repeater":
        return {"facing": rng.choice(HORIZONTALS), "delay": rng.randint(1, 4)}
    if block_id == "comparator":
        return {"facing": rng.choice(HORIZONTALS), "mode": rng.choice(("compare", "subtract"))}
    if block_id == "observer":
        return {"facing": rng.choice(HORIZONTALS)}
    if block_id in ("piston", "sticky_piston"):
        return {"facing": rng.choice(HORIZONTALS + ("up",))}
    return {}
