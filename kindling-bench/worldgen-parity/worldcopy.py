"""Copies a saved world into a fresh server folder.

26.3 keeps every dimension under world/dimensions/<namespace>/<path>/ and Paper 26.3 reads the same layout (its
LegacyCraftBukkitWorldMigration only converts old world_nether/world_the_end folders), so one copy serves every jar.
"""
import gzip
import hashlib
import shutil
from pathlib import Path

import paths  # noqa: F401
import nbt
import nbtwrite

WORLD_GEN_SETTINGS = Path("data/minecraft/world_gen_settings.dat")

_NOT_COPIED = ("session.lock",)


def digest(world: Path, suffix: str = "") -> str:
    """sha256 over the relative paths and bytes of every file of a world (session.lock excluded), optionally only the
    files with this suffix (".mca" for the region files)."""
    hasher = hashlib.sha256()
    for path in sorted(p for p in world.rglob("*") if p.is_file() and p.name not in _NOT_COPIED
                       and p.name.endswith(suffix)):
        hasher.update(path.relative_to(world).as_posix().encode("utf-8") + b"\0")
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(1 << 20), b""):
                hasher.update(block)
        hasher.update(b"\0")
    return hasher.hexdigest()


def copy_world(source: Path, target: Path) -> None:
    if target.exists():
        raise FileExistsError(f"{target} exists; a run always starts from a fresh copy")
    shutil.copytree(source, target, ignore=shutil.ignore_patterns(*_NOT_COPIED))


def disable_structures(world: Path) -> None:
    """Self-test 4. `generate-structures` in server.properties only applies when a world is created; an existing world
    keeps its own world_gen_settings.dat (seen in the first try: the property changed nothing). So the copy's saved
    setting is switched off instead."""
    path = world / WORLD_GEN_SETTINGS
    name, root = nbtwrite.parse(gzip.decompress(path.read_bytes()))
    settings = root.value["data"].value
    settings["generate_structures"] = nbtwrite.Tag(nbt.TAG_BYTE, 0)
    path.write_bytes(gzip.compress(nbtwrite.serialize(name, root)))
