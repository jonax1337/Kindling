"""What a run folder says about itself (run.json), and the checks that two runs may be compared at all.

A comparison takes its locations, rectangles and bootstrap from the runs, never from a profile: a profile that is
smaller or different would silently skip part of the swept area. Folders that cannot be compared are refused with the
reason, instead of producing a verdict.
"""
import json
import re
from pathlib import Path
from typing import NamedTuple

import server
import sweep
import worldcopy

_BRAND_LINES = (re.compile(r"This server is running (.+)"), re.compile(r"Starting minecraft server version (.+)"))
_BRAND_SCAN_LINES = 300


class Refused(RuntimeError):
    """The two runs cannot be compared; the text says why."""


class RunSide(NamedTuple):
    folder: Path
    info: dict[str, dict]  # location name -> run.json

    @property
    def family(self) -> str:
        return next(iter(self.info.values()))["family"]


def read_run(folder: Path) -> RunSide:
    if not folder.is_dir():
        raise Refused(f"{folder} is not a folder")
    info = {child.name: json.loads((child / "run.json").read_text(encoding="utf-8"))
            for child in sorted(folder.iterdir()) if (child / "run.json").is_file()}
    if not info:
        raise Refused(f"{folder} has no location folder with a run.json")
    if len({entry["family"] for entry in info.values()}) != 1:
        raise Refused(f"{folder}: its locations were run by different jar families")
    return RunSide(folder, info)


def location_of(entry: dict) -> sweep.Location:
    return sweep.Location(**entry["location"])


def locations_of(side: RunSide) -> list[sweep.Location]:
    return [location_of(entry) for entry in side.info.values()]


def mojang_sides(a: RunSide, b: RunSide) -> frozenset[str]:
    return frozenset(label for label, side in (("A", a), ("B", b)) if side.family == server.MOJANG)


def check_expected(side: RunSide, expected: list[sweep.Location]) -> None:
    """A given --profile or --locations must describe exactly the swept rectangles of the run."""
    actual = {location.name: location for location in locations_of(side)}
    wanted = {location.name: location for location in expected}
    if actual != wanted:
        raise Refused(f"{side.folder}: the runs swept {_names(actual)}, the given profile or locations file says "
                      f"{_names(wanted)}; leave --profile and --locations out to use what the runs recorded")


def _names(locations: dict) -> str:
    return "; ".join(f"{name} {loc.dimension} x {loc.x0}..{loc.x1} z {loc.z0}..{loc.z1}"
                     for name, loc in sorted(locations.items()))


def check_pair(a: RunSide, b: RunSide, allow_variant_mismatch: bool = False,
               allow_identical: bool = False) -> list[str]:
    """Raises Refused when A and B must not be compared; returns warnings for what could not be verified."""
    warnings = []
    if a.folder.resolve() == b.folder.resolve() and not allow_identical:
        raise Refused(f"A and B are the same folder {a.folder.resolve()}")
    if set(a.info) != set(b.info):
        raise Refused(f"A has locations {sorted(a.info)}, B has {sorted(b.info)}")
    for name in sorted(a.info):
        entry_a, entry_b = a.info[name], b.info[name]
        if entry_a["location"] != entry_b["location"]:
            raise Refused(f"{name}: A swept {entry_a['location']}, B swept {entry_b['location']}")
        warnings += _check_bootstrap(name, entry_a, entry_b)
        if entry_a["variant"] != entry_b["variant"] and not allow_variant_mismatch:
            raise Refused(f"{name}: the runs differ on purpose in their variant (A {entry_a['variant']}, "
                          f"B {entry_b['variant']}); pass --allow-variant-mismatch for a self-test")
        if not allow_identical and _same_regions(a, b, name):
            raise Refused(f"{name}: the region files of A and B are byte-identical; this is one run (or a copy of it) "
                          f"compared with itself; pass --allow-identical-runs to do that on purpose")
    return warnings


def _check_bootstrap(name: str, entry_a: dict, entry_b: dict) -> list[str]:
    """Both runs must have started from the same bootstrap, and it must be the bootstrap on disk now."""
    sha_a, sha_b = entry_a.get("bootstrap_sha256"), entry_b.get("bootstrap_sha256")
    if sha_a and sha_b:
        if sha_a != sha_b:
            raise Refused(f"{name}: A and B started from different bootstraps (sha256 {sha_a[:16]} / {sha_b[:16]})")
        for label, entry in (("A", entry_a), ("B", entry_b)):
            world = Path(entry["bootstrap_world"])
            if not world.is_dir():
                raise Refused(f"{name}: the bootstrap of {label} is gone: {world}")
            if worldcopy.digest(world) != entry["bootstrap_sha256"]:
                raise Refused(f"{name}: the bootstrap {world} changed since {label} ran")
        return []
    if Path(entry_a["bootstrap_world"]).resolve() != Path(entry_b["bootstrap_world"]).resolve():
        raise Refused(f"{name}: A and B recorded different bootstrap folders and no hash to compare")
    return [f"{name}: no bootstrap hash recorded (run made before it was recorded); the bootstrap is the same folder "
            f"but its content could not be verified"]


def _same_regions(a: RunSide, b: RunSide, name: str) -> bool:
    world_a, world_b = a.folder / name / "world", b.folder / name / "world"
    return worldcopy.digest(world_a, ".mca") == worldcopy.digest(world_b, ".mca")


def describe(label: str, side: RunSide, name: str) -> list[str]:
    entry = side.info[name]
    variant = entry["variant"]
    changes = [f"{key}={value}" for key, value in variant.items() if value not in (None, False, "canonical")]
    return [f"run {label}: {entry['jar']} (sha256 {entry['jar_sha256'][:16]}, family {entry['family']}), "
            f"{brand_of(side.folder / name)}",
            f"       java {entry['java']}; variant {', '.join(changes) or 'canonical'}; settle {entry.get('settle', 'default')};"
            f" bootstrap sha256 {(entry.get('bootstrap_sha256') or 'not recorded')[:16]}"]


def brand_of(folder: Path) -> str:
    """The version line the server printed, e.g. "Kindling version 26.3-DEV-work/kindling-hardening@23a2629"."""
    console = folder / "console.log"
    if not console.is_file():
        return "no console.log"
    with console.open(encoding="utf-8", errors="replace") as stream:
        lines = [line.rstrip("\n") for _, line in zip(range(_BRAND_SCAN_LINES), stream)]
    for pattern in _BRAND_LINES:
        for line in lines:
            match = pattern.search(line)
            if match:
                return match.group(1)
    return "no version line in console.log"
