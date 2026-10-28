"""A contraption cell: a 10 x 10 block footprint inside one chunk that a design is built in with local coordinates.

A design uses local coordinates (x east, y up, z south, all 0..9, y = 0 is the first block above the floor) and
the cell turns them into world commands, rotated by a quarter turn per step of `rotation`, so that one design covers
four orientations (and four different BlockPos hash patterns) without being written four times.
"""
import math
from collections import defaultdict

SIZE = 10
HORIZONTALS = ("north", "east", "south", "west")
_RAIL_SHAPE_SWAPS = {"east_west": "north_south", "north_south": "east_west"}


class Cell:
    def __init__(self, origin: tuple[int, int, int], rotation: int = 0):
        self.origin = origin
        self.rotation = rotation % 4
        self.setup: list[str] = []        # building the structure, before anything can run
        self.start: list[str] = []        # the things that set it going: power sources, moving items
        self.later: dict[int, list[str]] = defaultdict(list)  # stage -> commands after that many tick steps

    def block(self, x: int, y: int, z: int, block_id: str, data: str = "", **props) -> None:
        self.setup.append(self._setblock(x, y, z, block_id, data, props))

    def fill(self, first: tuple[int, int, int], last: tuple[int, int, int], block_id: str, **props) -> None:
        (x1, y1, z1), (x2, y2, z2) = self._world(*first), self._world(*last)
        self.setup.append(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {_block(block_id, self._rotated(props))}")

    def start_block(self, x: int, y: int, z: int, block_id: str, data: str = "", **props) -> None:
        self.start.append(self._setblock(x, y, z, block_id, data, props))

    def later_block(self, stage: int, x: int, y: int, z: int, block_id: str, data: str = "", **props) -> None:
        self.later[stage].append(self._setblock(x, y, z, block_id, data, props))

    def start_entity(self, entity_id: str, x: float, y: float, z: float, nbt: str = "",
                     motion: tuple[float, float, float] | None = None) -> None:
        """x and z are local coordinates of the point (a block centre is n + 0.5); motion is rotated like a direction."""
        world_x, world_z = self._world_point(x, z)
        parts = [nbt] if nbt else []
        if motion is not None:
            motion_x, motion_z = self._rotate_direction(motion[0], motion[2])
            parts.append(f"Motion:[{motion_x!r}d,{motion[1]!r}d,{motion_z!r}d]")
        origin_y = self.origin[1]
        self.start.append(f"summon minecraft:{entity_id} {world_x!r} {origin_y + y!r} {world_z!r} {{{','.join(parts)}}}")

    def _setblock(self, x: int, y: int, z: int, block_id: str, data: str, props: dict) -> str:
        world_x, world_y, world_z = self._world(x, y, z)
        return f"setblock {world_x} {world_y} {world_z} {_block(block_id, self._rotated(props))}{data}"

    def _world(self, x: int, y: int, z: int) -> tuple[int, int, int]:
        world_x, world_z = self._world_point(x + 0.5, z + 0.5)
        return math.floor(world_x), self.origin[1] + y, math.floor(world_z)

    def _world_point(self, x: float, z: float) -> tuple[float, float]:
        for _ in range(self.rotation):  # a quarter turn clockwise inside the footprint: east becomes south
            x, z = SIZE - z, x
        return self.origin[0] + x, self.origin[2] + z

    def _rotate_direction(self, x: float, z: float) -> tuple[float, float]:
        for _ in range(self.rotation):
            x, z = -z, x
        return x, z

    def _rotated(self, props: dict) -> dict:
        return {self._rotated_key(key): self._rotated_value(key, value) for key, value in props.items()}

    def _rotated_key(self, key: str) -> str:
        return _turn(key, self.rotation) if key in HORIZONTALS else key  # redstone dust: east=side, north=none, ...

    def _rotated_value(self, key: str, value):
        odd = self.rotation % 2 == 1
        if key == "facing" and value in HORIZONTALS:
            return _turn(value, self.rotation)
        if key == "axis" and odd and value in ("x", "z"):
            return "z" if value == "x" else "x"
        if key == "shape" and odd:
            return _RAIL_SHAPE_SWAPS.get(value, value)
        return value


def _turn(direction: str, quarter_turns: int) -> str:
    return HORIZONTALS[(HORIZONTALS.index(direction) + quarter_turns) % 4]


def _block(block_id: str, props: dict) -> str:
    name = block_id if ":" in block_id else f"minecraft:{block_id}"
    if not props:
        return name
    inner = ",".join(f"{key}={str(value).lower() if isinstance(value, bool) else value}"
                     for key, value in sorted(props.items()))
    return f"{name}[{inner}]"
