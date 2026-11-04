"""One server start per location: the bootstrap run of Mojang's jar, and the measured run of any jar.

A measured run folder holds the server's files, the saved world, parity.jfr, order.json (the order check) and
run.json (what ran, how long each step took). Server-side caches (libraries/, versions/, cache/) are deleted after
the run unless kept, because every run unpacks them again.
"""
import json
import re
import shutil
import subprocess
import time
from pathlib import Path
from typing import NamedTuple

import driver
import jfr_order
import server
import sweep
import worldcopy

CANONICAL, WHOLE_RECTANGLE = "canonical", "whole-rectangle"
_UNPACKED = ("libraries", "versions", "cache")
_WORKER_LINE = re.compile(r"is using (\d+) worker threads")


class Setup(NamedTuple):
    java: Path
    heap: str
    mojang_jar: Path
    keep_unpacked: bool
    jfr: Path


def resolve_java(java: Path) -> Path:
    """A bare name is looked up on the PATH, and symlinks are followed (on the bench host /usr/local/bin/java points
    into /opt/jdk-25/bin, where jfr lives; /usr/local/bin has no jfr)."""
    found = shutil.which(str(java))
    if found is None:
        raise server.ServerError(f"java not found: {java}")
    return Path(found).resolve()


def jfr_tool(java: Path) -> Path:
    resolved = java.resolve()
    return resolved.with_name("jfr.exe" if resolved.suffix == ".exe" else "jfr")


def java_version(java: Path) -> str:
    result = subprocess.run([str(java), "-version"], capture_output=True, encoding="utf-8")
    return result.stderr.splitlines()[0] if result.stderr else "unknown"


def bootstrap_location(setup: Setup, location: sweep.Location, folder: Path) -> None:
    """Mojang's jar, a new world: the L of the location, saved with no forceloads left. An existing folder is
    refused: extending an old bootstrap in place would leave earlier runs pointing at a world they never used."""
    if folder.exists():
        raise FileExistsError(f"{folder} exists; a bootstrap always starts from an empty folder")
    server.write_server_files(folder, setup.mojang_jar, setup.mojang_jar, {})
    running = server.Server(setup.java, setup.mojang_jar, folder, setup.heap, [])
    running.start()
    try:
        driver.prepare(running)
        driver.generate_bootstrap(running, location)
        driver.save(running)
    finally:
        exit_code = running.stop()
    _write_json(folder / "bootstrap.json", {"location": location._asdict(), "exit_code": exit_code,
                                            "world_sha256": worldcopy.digest(folder / "world")})
    _remove_unpacked(folder, setup.keep_unpacked)


class Variant(NamedTuple):
    """What a run changes on purpose: the order (self-test 2), structures off (4), an overlay folder (5). The pauses
    are not part of what is compared: a result must not depend on them."""
    order: str = CANONICAL
    without_structures: bool = False
    overlay: Path | None = None
    settle: driver.Settle = driver.Settle()


def measured_run(setup: Setup, jar: Path, location: sweep.Location, bootstrap_world: Path, folder: Path,
                 variant: Variant) -> jfr_order.OrderReport:
    if variant.overlay and (variant.overlay / "server.properties").exists():
        raise server.ServerError(f"{variant.overlay} contains server.properties; an overlay must not set the bind address")
    bootstrap_sha256 = _checked_bootstrap_digest(bootstrap_world)
    folder.mkdir(parents=True)
    worldcopy.copy_world(bootstrap_world, folder / "world")
    if variant.without_structures:
        worldcopy.disable_structures(folder / "world")
    if variant.overlay:
        shutil.copytree(variant.overlay, folder, dirs_exist_ok=True)
    server.write_server_files(folder, jar, setup.mojang_jar, {})  # last, so that nothing can override the bind address
    family = server.jar_family(jar)
    arguments = server.single_worker_arguments(family) + server.jfr_arguments()
    running = server.Server(setup.java, jar, folder, setup.heap, arguments)
    timings = _drive(running, location, variant.order, family, variant.settle)
    report = jfr_order.check_order(jfr_order.read_events(setup.jfr, folder / server.JFR_FILE),
                                   location, timings["sweep_start_ns"])
    _write_json(folder / "order.json", report.as_json())
    _write_json(folder / "run.json", {
        "jar": str(jar), "jar_sha256": server.sha256_of(jar), "family": family, "java": java_version(setup.java),
        "location": location._asdict(), "bootstrap_world": str(bootstrap_world.resolve()),
        "bootstrap_sha256": bootstrap_sha256,
        "variant": {"order": variant.order, "without_structures": variant.without_structures,
                    "overlay": str(variant.overlay) if variant.overlay else None},
        "settle": variant.settle._asdict(), **timings})
    _remove_unpacked(folder, setup.keep_unpacked)
    return report


def _checked_bootstrap_digest(bootstrap_world: Path) -> str:
    """The digest of the bootstrap now, which must be the one it had when the bootstrap run saved it."""
    actual = worldcopy.digest(bootstrap_world)
    recorded_file = bootstrap_world.parent / "bootstrap.json"
    if recorded_file.is_file():
        recorded = json.loads(recorded_file.read_text(encoding="utf-8")).get("world_sha256")
        if recorded is not None and recorded != actual:
            raise server.ServerError(f"{bootstrap_world} changed since its bootstrap run saved it "
                                     f"(sha256 {actual}, recorded {recorded})")
    return actual


def _drive(running: server.Server, location: sweep.Location, order: str, family: str, settle: driver.Settle) -> dict:
    started = time.monotonic()
    running.start()
    try:
        start_seconds = time.monotonic() - started
        driver.prepare(running)
        workers = _check_single_worker(running, family)
        sweep_start_ns = time.time_ns()
        steps = (driver.canonical_sweep if order == CANONICAL else driver.whole_rectangle_at_once)(
            running, location, settle)
        driver.save(running)
    finally:
        exit_code = running.stop()
    return {"start_seconds": round(start_seconds, 2), "worker_threads": workers, "sweep_start_ns": sweep_start_ns,
            "step_seconds": [round(step, 3) for step in steps], "sweep_seconds": round(sum(steps), 2),
            "exit_code": exit_code}


def _check_single_worker(running: server.Server, family: str) -> int | None:
    """The Paper family logs its worker count (MoonriseCommon.adjustWorkerThreads); Mojang's jar does not, and its
    single background thread is checked from the JFR thread names instead."""
    if family == server.MOJANG:
        return None
    counts = [int(match.group(1)) for line in running.lines if (match := _WORKER_LINE.search(line))]
    if counts != [1] * len(counts) or not counts:
        raise server.ServerError(f"expected 1 chunk worker thread, the log says {counts or 'nothing'}")
    return 1


def _remove_unpacked(folder: Path, keep: bool) -> None:
    if keep:
        return
    for name in _UNPACKED:
        shutil.rmtree(folder / name, ignore_errors=True)


def _write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value, indent=2, default=str) + "\n", encoding="utf-8")
