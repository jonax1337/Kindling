"""Runs one reproducible server benchmark and writes its result next to the run folder.

A run starts the given server jar in a fresh folder on loopback, with the KindlingBench plugin and a fixed scenario.
The plugin joins fake players, drives the load, measures tick times and the heap, writes bench-result.json and stops
the server. This script adds what only the operating system sees: the resident memory of the process and the CPU time.

    python -I run_bench.py --jar <server.jar> --plugin <kindling-bench.jar> --label leaf-stock [--jvm "-XX:+Flag ..."]
                            [--overlay <folder with config files>] [--world <world folder to copy>] [--repeat 3]
"""

import argparse
import json
import shlex
import shutil
import subprocess
import sys
import time
from pathlib import Path

import psutil

RUNS = Path(__file__).resolve().parent / "runs"
PORT = 25999
SEED = "-4172144997902289642"
SAMPLE_SECONDS = 1.0
TIMEOUT_SECONDS = 1800

SERVER_PROPERTIES = f"""server-ip=127.0.0.1
server-port={PORT}
online-mode=false
level-seed={SEED}
view-distance=10
simulation-distance=8
spawn-protection=0
max-players=100
enable-rcon=false
enable-query=false
enable-status=false
sync-chunk-writes=false
"""


def main() -> int:
    args = parse_arguments()
    results = [run_once(args, attempt) for attempt in range(1, args.repeat + 1)]
    summary_file = RUNS / f"{args.label}.json"
    summary_file.write_text(json.dumps(results, indent=2), encoding="utf-8")
    print(f"wrote {summary_file}")
    return 0


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--plugin", type=Path, required=True)
    parser.add_argument("--label", required=True)
    parser.add_argument("--heap", default="3G", help="-Xmx; -Xms too unless --jvm sets -Xms itself")
    parser.add_argument("--jvm", default="", help="extra JVM arguments, one string")
    parser.add_argument("--overlay", type=Path, help="folder whose files are copied over the server folder")
    parser.add_argument("--world", type=Path, help="world folder copied in as 'world' before the start")
    parser.add_argument("--scenario", default="", help="key=value pairs for the plugin, comma separated")
    parser.add_argument("--repeat", type=int, default=1)
    parser.add_argument("--cpus", type=int,
                        help="pin the server to the first N logical CPUs, as a stand-in for a smaller machine "
                             "(taskset on Linux, psutil elsewhere)")
    return parser.parse_args()


def run_once(args: argparse.Namespace, attempt: int) -> dict:
    folder = prepare_folder(args, attempt)
    command = java_command(args)
    started = time.monotonic()
    with open(folder / "console.log", "w", encoding="utf-8") as console:
        process = subprocess.Popen(launch_command(args, command), cwd=folder, stdin=subprocess.DEVNULL,
                                   stdout=console, stderr=subprocess.STDOUT)
        if args.cpus and not sys.platform.startswith("linux"):
            psutil.Process(process.pid).cpu_affinity(list(range(args.cpus)))
        memory = watch(process, folder / "bench-result.json")
    result_file = folder / "bench-result.json"
    if not result_file.exists():
        raise RuntimeError(f"run {folder.name} ended (exit {process.returncode}) without bench-result.json; "
                           f"see {folder / 'console.log'}")
    result = json.loads(result_file.read_text(encoding="utf-8"))
    result.update(label=args.label, attempt=attempt, jvm=command[1:-3], cpus=args.cpus,
                  wall_seconds=round(time.monotonic() - started, 1), **memory)
    print(json.dumps(result, indent=2))
    return result


def prepare_folder(args: argparse.Namespace, attempt: int) -> Path:
    folder = RUNS / f"{args.label}-{attempt}"
    if folder.exists():
        shutil.rmtree(folder)
    (folder / "plugins" / "KindlingBench").mkdir(parents=True)
    (folder / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (folder / "server.properties").write_text(SERVER_PROPERTIES, encoding="utf-8")
    shutil.copy(args.plugin, folder / "plugins" / "kindling-bench.jar")
    (folder / "plugins" / "KindlingBench" / "scenario.properties").write_text(
        "\n".join(args.scenario.split(",")) + "\n", encoding="utf-8")
    if args.world:
        shutil.copytree(args.world, folder / "world")
    if args.overlay:
        shutil.copytree(args.overlay, folder, dirs_exist_ok=True)
    return folder


def java_command(args: argparse.Namespace) -> list[str]:
    extra = shlex.split(args.jvm)
    heap = [f"-Xmx{args.heap}"]
    if not any(flag.startswith("-Xms") for flag in extra):
        heap.append(f"-Xms{args.heap}")
    return ["java", *heap, *extra, "-jar", str(args.jar.resolve()), "--nogui"]


def launch_command(args: argparse.Namespace, java: list[str]) -> list[str]:
    """The command that starts the server. On Linux a pinned run starts under taskset, so that every thread of the JVM
    inherits the mask; setting the affinity after the start would only move the main thread. Elsewhere run_once() pins
    the process after the start."""
    if args.cpus and sys.platform.startswith("linux"):
        if shutil.which("taskset") is None:
            raise RuntimeError("--cpus needs taskset on Linux (util-linux)")
        return ["taskset", "-c", f"0-{args.cpus - 1}", *java]
    return java


def watch(process: subprocess.Popen, result_file: Path) -> dict:
    """Samples the resident memory until the process ends; returns the peak, the sample when the plugin wrote its
    result (the end of the idle phase, before the shutdown saves the world), the last sample and the CPU time."""
    handle = psutil.Process(process.pid)
    peak = last = 0
    idle = None
    cpu_seconds = 0.0
    deadline = time.monotonic() + TIMEOUT_SECONDS
    while process.poll() is None:
        if time.monotonic() > deadline:
            process.kill()
            raise RuntimeError("benchmark timed out")
        try:
            last = handle.memory_info().rss
            times = handle.cpu_times()
            cpu_seconds = times.user + times.system
        except psutil.NoSuchProcess:
            break
        peak = max(peak, last)
        if idle is None and result_file.exists():
            idle = last
        time.sleep(SAMPLE_SECONDS)
    return {"rss_peak_mb": peak // 2**20, "rss_idle_mb": (idle or 0) // 2**20, "rss_last_mb": last // 2**20,
            "process_cpu_seconds": round(cpu_seconds, 1)}


if __name__ == "__main__":
    sys.exit(main())
