"""What the tool types into a running server: freezing, game rules, the bootstrap L and the canonical sweep.

Waiting for a step needs care. `execute if loaded` passes as soon as the chunk is a full chunk whose ticket level says
entity ticking (LevelChunk.getFullStatus follows the ticket level, not the promotion futures), so for a chunk that was
already full it passes before the step's generation chain has run (seen in the first dev run: the next step started
before spawn and full of (x+2, z+2)). The barrier is therefore (x+2, z+2), the last chunk of the chain: `execute if
block` answers "That position is not loaded" until that chunk is a full chunk (BlockPosArgument.getLoadedBlockPos),
and it never loads or generates anything itself.
"""
import time
from typing import NamedTuple

import sweep
from server import Server, ServerError

# 26.3 renamed the game rules to snake case; the pre-26 names (doMobSpawning, randomTickSpeed) are rejected with
# "Incorrect argument for command" (seen in the first dev run, IMPL-NOTES.md).
GAME_RULES = (
    ("random_tick_speed", "0"), ("spawn_mobs", "false"), ("spawn_monsters", "false"), ("spawn_patrols", "false"),
    ("spawn_phantoms", "false"), ("spawn_wandering_traders", "false"), ("spawn_wardens", "false"),
    ("advance_time", "false"), ("advance_weather", "false"), ("spread_vines", "false"),
)
STEP_TIMEOUT = 300
POLL_SECONDS = 0.02
# The block-ticking promotion (LevelChunk.postProcessGeneration) runs as a main-thread task after a step's chain and is
# not a generation event, so no barrier sees it. Without a pause its outcome depended on timing: in 4 of 14 fast
# vanilla runs chunk (187, 140) of the dev sweep was post-processed, in the others not, and the promotion of
# (x1 + 1, z1 + 1) after the last step raced the save. With these pauses 5 of 5 vanilla runs agreed (IMPL-NOTES.md).
STEP_SETTLE_SECONDS = 0.25
END_SETTLE_SECONDS = 2.0


class Settle(NamedTuple):
    """The two pauses; options of `run` so that a result can be shown not to depend on them (DESIGN.md, open points)."""
    step: float = STEP_SETTLE_SECONDS
    end: float = END_SETTLE_SECONDS


_TEST_ANSWER = r"Test (passed|failed)"
_NOT_LOADED = "That position is not loaded"
_BLOCK_ANSWER = rf"Test (passed|failed)|{_NOT_LOADED}"


def prepare(server: Server) -> None:
    """Freeze first (before anything is loaded), then the game rules, each answer checked."""
    server.command("tick freeze", r"The game is frozen")
    for rule, value in GAME_RULES:
        answer = server.command(f"gamerule {rule} {value}",
                                rf"Game rule {rule} is (now|already) set to {value}|Incorrect argument|Unknown")
        if not answer.group(1):
            raise ServerError(f"game rule {rule} was not accepted: {answer.string}")


def _run_in(dimension: str, command: str) -> str:
    return f"execute in {dimension} run {command}"


def _test_in(dimension: str, condition: str) -> str:
    return f"execute in {dimension} {condition}"


def forceload_chunk(server: Server, dimension: str, chunk: sweep.ChunkKey) -> None:
    x, z = chunk
    server.command(_run_in(dimension, f"forceload add {x * 16} {z * 16}"), r"Marked chunk .* to be force loaded")


def forceload_rectangle(server: Server, location: sweep.Location) -> None:
    command = f"forceload add {location.x0 * 16} {location.z0 * 16} {location.x1 * 16} {location.z1 * 16}"
    server.command(_run_in(location.dimension, command), r"Marked .* to be force loaded")


def is_loaded(server: Server, dimension: str, chunk: sweep.ChunkKey) -> bool:
    x, z = chunk
    return server.command(_test_in(dimension, f"if loaded {x * 16} 0 {z * 16}"), _TEST_ANSWER).group(1) == "passed"


def wait_until_loaded(server: Server, dimension: str, chunk: sweep.ChunkKey) -> None:
    _poll(lambda: is_loaded(server, dimension, chunk), f"chunk {chunk} in {dimension} was not entity-ticking")


def _poll(condition, failure: str) -> None:
    deadline = time.monotonic() + STEP_TIMEOUT
    while not condition():
        if time.monotonic() > deadline:
            raise ServerError(f"{failure} after {STEP_TIMEOUT} s")
        time.sleep(POLL_SECONDS)


def is_full(server: Server, dimension: str, chunk: sweep.ChunkKey) -> bool:
    x, z = chunk
    answer = server.command(_test_in(dimension, f"if block {x * 16} 0 {z * 16} minecraft:air"), _BLOCK_ANSWER)
    return answer.group(0) != _NOT_LOADED


def wait_until_full(server: Server, dimension: str, chunk: sweep.ChunkKey) -> None:
    _poll(lambda: is_full(server, dimension, chunk), f"chunk {chunk} in {dimension} was not full")


def wait_for_step(server: Server, dimension: str, chunk: sweep.ChunkKey) -> None:
    radius = sweep.RADIUS_OF["full"]
    wait_until_full(server, dimension, (chunk[0] + radius, chunk[1] + radius))
    wait_until_loaded(server, dimension, chunk)


def generate_bootstrap(server: Server, location: sweep.Location) -> None:
    """The L, in any order (it is copied, not reproduced), then the forceloads are removed so that a later start
    loads nothing before `tick freeze`."""
    for chunk in location.bootstrap_chunks():
        forceload_chunk(server, location.dimension, chunk)
    for chunk in location.bootstrap_chunks():
        wait_until_loaded(server, location.dimension, chunk)
    server.command(_run_in(location.dimension, "forceload remove all"), r"Unmarked all force loaded chunks")


def canonical_sweep(server: Server, location: sweep.Location, settle: Settle = Settle()) -> list[float]:
    """Raster order, one chunk at a time, each step settled; returns the seconds each step took."""
    durations = []
    for chunk in location.steps():
        started = time.monotonic()
        forceload_chunk(server, location.dimension, chunk)
        wait_for_step(server, location.dimension, chunk)
        time.sleep(settle.step)
        durations.append(time.monotonic() - started)
    time.sleep(settle.end)
    return durations


def whole_rectangle_at_once(server: Server, location: sweep.Location, settle: Settle = Settle()) -> list[float]:
    """Self-test 2: the order a scheduler picks; the order check must reject it."""
    started = time.monotonic()
    forceload_rectangle(server, location)
    for chunk in location.steps():
        wait_for_step(server, location.dimension, chunk)
    time.sleep(settle.end)
    return [time.monotonic() - started]


def save(server: Server) -> None:
    server.command("save-all flush", r"Saved the game", timeout=STEP_TIMEOUT)
