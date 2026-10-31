"""Proves from the JFR recording that a run generated its location in the canonical order.

`ChunkStep.apply` emits `minecraft.ChunkGeneration` for every status it actually generates. The JDK's `jfr` tool
turns the recording into JSON; the check requires that the events near the location are exactly the predicted chain
of every step (sweep.predicted_events), in that order, each step finished before the next began, all worldgen work on
one thread, and that nothing was generated elsewhere while the sweep ran.
"""
import json
import re
import subprocess
from datetime import datetime
from pathlib import Path
from typing import NamedTuple

import sweep

EVENT_NAME = "minecraft.ChunkGeneration"
CORE_STATUSES = ("structure_starts", "structure_references", "biomes", "terrain", "features")
MAIN_THREAD_STATUSES = ("full",)  # Mojang runs the full step on the server thread
_DURATION = re.compile(r"^PT(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?$")
_PROBLEMS_SHOWN = 12


class GenerationEvent(NamedTuple):
    start_ns: int
    duration_ns: int
    thread: str
    dimension: str
    status: str
    chunk: sweep.ChunkKey

    @property
    def end_ns(self) -> int:
        return self.start_ns + self.duration_ns


class OrderReport(NamedTuple):
    ok: bool
    problems: list[str]
    recorded_statuses: list[str]
    threads: dict[str, list[str]]
    events_near: int
    events_elsewhere_before: int
    sequence: list[tuple[str, sweep.ChunkKey]]

    def as_json(self) -> dict:
        return {**self._asdict(), "sequence": [[status, list(chunk)] for status, chunk in self.sequence]}


def read_events(jfr_tool: Path, recording: Path) -> list[GenerationEvent]:
    output = subprocess.run([str(jfr_tool), "print", "--json", "--events", EVENT_NAME, str(recording)],
                            check=True, capture_output=True, encoding="utf-8").stdout
    return parse_events(json.loads(output))


def parse_events(document: dict) -> list[GenerationEvent]:
    events = []
    for raw in document["recording"]["events"]:
        values = raw["values"]
        events.append(GenerationEvent(
            parse_instant_ns(values["startTime"]), parse_duration_ns(values["duration"]),
            values["eventThread"]["javaName"], values["level"], values["status"].removeprefix("minecraft:"),
            (values["chunkPosX"], values["chunkPosZ"])))
    return sorted(events)


def parse_instant_ns(text: str) -> int:
    """ISO-8601 with up to nanoseconds, e.g. 2026-10-30T13:39:48.264225900+01:00."""
    match = re.match(r"^(.*T\d\d:\d\d:\d\d)(?:\.(\d+))?(.*)$", text)
    whole, fraction, zone = match.groups()
    seconds = datetime.fromisoformat(whole + (zone or "")).timestamp()
    return int(seconds) * 1_000_000_000 + int((fraction or "0").ljust(9, "0")[:9])


def parse_duration_ns(text: str) -> int:
    match = _DURATION.match(text)
    if not match:
        raise ValueError(f"unexpected JFR duration {text!r}")
    hours, minutes, seconds = match.groups()
    whole, _, fraction = (seconds or "0").partition(".")
    total_seconds = int(hours or 0) * 3600 + int(minutes or 0) * 60 + int(whole)
    return total_seconds * 1_000_000_000 + int(fraction.ljust(9, "0")[:9])


def is_near(event: GenerationEvent, location: sweep.Location) -> bool:
    min_x, min_z, max_x, max_z = location.compare_box()
    x, z = event.chunk
    return event.dimension == location.dimension and min_x <= x <= max_x and min_z <= z <= max_z


def check_order(events: list[GenerationEvent], location: sweep.Location, sweep_start_ns: int) -> OrderReport:
    near = [event for event in events if is_near(event, location)]
    elsewhere = [event for event in events if not is_near(event, location)]
    recorded = [status for status in sweep.STATUS_ORDER if any(event.status == status for event in near)]
    problems = _missing_core_statuses(recorded)
    expected = [(e.status, e.chunk) for e in sweep.predicted_events(location) if e.status in recorded]
    actual = [(event.status, event.chunk) for event in near]
    problems += _sequence_problems(expected, actual)
    if not problems:
        problems += _overlapping_steps(near, location, recorded)
    problems += [f"generated outside the location during the sweep: {event.status} {event.chunk} {event.dimension}"
                 for event in elsewhere if event.start_ns >= sweep_start_ns]
    threads = _threads_by_status(near)
    problems += _thread_problems(threads)
    return OrderReport(not problems, problems, recorded, threads, len(near),
                       sum(1 for event in elsewhere if event.start_ns < sweep_start_ns), actual)


def _missing_core_statuses(recorded: list[str]) -> list[str]:
    return [f"no {status} event was recorded near the location" for status in CORE_STATUSES if status not in recorded]


def _sequence_problems(expected: list, actual: list) -> list[str]:
    if expected == actual:
        return []
    first = next((i for i, (a, b) in enumerate(zip(expected, actual)) if a != b), min(len(expected), len(actual)))
    problems = [f"event sequence differs from the canonical chain: {len(actual)} events, {len(expected)} predicted;"
                f" first difference at event {first}"]
    for index in range(first, min(first + _PROBLEMS_SHOWN, max(len(expected), len(actual)))):
        want = expected[index] if index < len(expected) else None
        got = actual[index] if index < len(actual) else None
        problems.append(f"  #{index}: predicted {want}, recorded {got}")
    return problems


def _overlapping_steps(near: list[GenerationEvent], location: sweep.Location, recorded: list[str]) -> list[str]:
    per_step = len(recorded)
    steps = [near[i:i + per_step] for i in range(0, len(near), per_step)]
    problems = []
    for index in range(1, len(steps)):
        previous_end = max(event.end_ns for event in steps[index - 1])
        if steps[index][0].start_ns < previous_end:
            problems.append(f"step {index} {location.steps()[index]} began before step {index - 1} had finished")
    return problems


def _threads_by_status(near: list[GenerationEvent]) -> dict[str, list[str]]:
    threads: dict[str, set[str]] = {}
    for event in near:
        threads.setdefault(event.status, set()).add(event.thread)
    return {status: sorted(names) for status, names in threads.items()}


def _thread_problems(threads: dict[str, list[str]]) -> list[str]:
    worker_threads = {name for status, names in threads.items() if status not in MAIN_THREAD_STATUSES for name in names}
    if len(worker_threads) <= 1:
        return []
    return [f"generation ran on {len(worker_threads)} threads: {sorted(worker_threads)}"]
