"""Deterministic worldgen parity against Mojang's server jar (DESIGN.md). Standard library only.

    python -I parity.py bootstrap --vanilla-jar <mojang.jar> --profile dev --out runs/boot
    python -I parity.py run --jar <jar> --vanilla-jar <mojang.jar> --bootstrap runs/boot --profile dev --out runs/v1
    python -I parity.py compare runs/v1 runs/v2
    python -I parity.py plant runs/v1 --out runs/v1-planted     (self-test 6)

compare takes its locations and rectangles from the runs' run.json (a --profile or --locations that disagrees is
refused), and refuses runs that are the same folder, hold identical region files, started from different bootstraps or
differ in variant.

Exit codes: 0 identical (or the self-test passed), 1 differences, 2 a setup or order failure or refused runs (no
verdict), 3 identical except the footprint of Mojang's promotion race in some chunks (not "identical").
Every server listens on 127.0.0.1:25999 only; one runs at a time.
"""
import argparse
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # python -I does not add the script directory

import paths  # noqa: E402,F401
import compare_all  # noqa: E402
import mutate_region  # noqa: E402
import report  # noqa: E402
import runinfo  # noqa: E402
import runs  # noqa: E402
import driver  # noqa: E402
import server  # noqa: E402
import sweep  # noqa: E402
import worldcopy  # noqa: E402

EXIT_SAME, EXIT_DIFFERENT, EXIT_NO_VERDICT, EXIT_PARTIAL = 0, 1, 2, 3
_SEVERITY = (EXIT_SAME, EXIT_PARTIAL, EXIT_DIFFERENT, EXIT_NO_VERDICT)  # least to most severe
ADOPTIUM_25 = Path(r"C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot\bin\java.exe")
DEFAULT_LOCATIONS = paths.HERE / "locations.json"


def default_java() -> Path:
    """$JAVA, else this PC's Adoptium 25 if it exists, else `java` on the PATH (the bench host)."""
    if os.environ.get("JAVA"):
        return Path(os.environ["JAVA"])
    return ADOPTIUM_25 if ADOPTIUM_25.is_file() else Path("java")


def worse(first: int, second: int) -> int:
    return max(first, second, key=_SEVERITY.index)


def main() -> int:
    args = parse_arguments()
    try:
        return args.command(args)
    except (server.ServerError, runinfo.Refused, FileNotFoundError, FileExistsError) as error:
        print(f"error: {error}", file=sys.stderr)
        return EXIT_NO_VERDICT


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(required=True)
    bootstrap = commands.add_parser("bootstrap", help="Mojang's jar generates the L of every location")
    _add_common(bootstrap)
    bootstrap.add_argument("--out", type=Path, required=True)
    bootstrap.set_defaults(command=command_bootstrap)
    run = commands.add_parser("run", help="one jar sweeps every location of the profile")
    _add_common(run)
    run.add_argument("--jar", type=Path, required=True)
    run.add_argument("--bootstrap", type=Path, required=True)
    run.add_argument("--out", type=Path, required=True)
    run.add_argument("--order", choices=(runs.CANONICAL, runs.WHOLE_RECTANGLE), default=runs.CANONICAL)
    run.add_argument("--without-structures", action="store_true",
                     help="self-test 4: switch structures off in the copied world's world_gen_settings.dat")
    run.add_argument("--overlay", type=Path, help="folder copied over the server folder (self-test 5); must not "
                                                  "contain server.properties")
    run.add_argument("--step-settle", type=float, default=driver.STEP_SETTLE_SECONDS,
                     help="seconds to wait after each step (the promotion race is held down by this pause)")
    run.add_argument("--end-settle", type=float, default=driver.END_SETTLE_SECONDS,
                     help="seconds to wait before saving")
    run.set_defaults(command=command_run)
    compare = commands.add_parser("compare", help="compare two run folders location by location")
    compare.add_argument("run_a", type=Path)
    compare.add_argument("run_b", type=Path)
    compare.add_argument("--locations", type=Path, help="only to check against the runs; they carry their own")
    compare.add_argument("--profile", help="only to check against the runs; they carry their own")
    compare.add_argument("--allow-variant-mismatch", action="store_true",
                         help="compare runs that differ in order, structures or overlay (self-tests 2, 4, 5)")
    compare.add_argument("--allow-identical-runs", action="store_true",
                         help="compare a run with itself or with a byte-identical copy (a deliberate self-check)")
    compare.add_argument("--limit", type=int, default=20, help="differing chunks printed per location")
    compare.add_argument("--json", type=Path, help="also write the differences as JSON")
    compare.set_defaults(command=command_compare)
    plant = commands.add_parser("plant", help="self-test 6: plant five changes in a copy and require exactly them")
    plant.add_argument("run", type=Path)
    plant.add_argument("--out", type=Path, required=True)
    plant.add_argument("--locations", type=Path, help="only to check against the run; it carries its own")
    plant.add_argument("--profile", help="only to check against the run; it carries its own")
    plant.set_defaults(command=command_plant)
    return parser.parse_args()


def _add_common(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--vanilla-jar", type=Path, required=True, help="Mojang's server jar (bootstrap, Paperclip cache)")
    parser.add_argument("--locations", type=Path, default=DEFAULT_LOCATIONS)
    parser.add_argument("--profile", default="dev")
    parser.add_argument("--java", type=Path, default=default_java(), help="Java 25 ($JAVA, else `java` on the PATH)")
    parser.add_argument("--jfr", type=Path, help="the jfr tool (default: next to the resolved java)")
    parser.add_argument("--heap", default="3G")
    parser.add_argument("--keep-unpacked", action="store_true", help="keep libraries/, versions/, cache/")


def _setup(args) -> runs.Setup:
    if server.jar_family(args.vanilla_jar) != server.MOJANG:
        raise server.ServerError(f"{args.vanilla_jar} is not Mojang's bundler jar")
    java = runs.resolve_java(args.java)
    jfr = args.jfr or runs.jfr_tool(java)
    if not jfr.is_file():
        raise server.ServerError(f"no jfr tool at {jfr}; pass --jfr (checked before any server starts)")
    return runs.Setup(java, args.heap, args.vanilla_jar.resolve(), args.keep_unpacked, jfr)


def command_bootstrap(args) -> int:
    setup = _setup(args)
    for location in sweep.load_locations(args.locations, args.profile):
        print(f"bootstrap {location.name}: L of {len(location.bootstrap_chunks())} chunks", flush=True)
        runs.bootstrap_location(setup, location, args.out.resolve() / location.name)
    return EXIT_SAME


def command_run(args) -> int:
    setup = _setup(args)
    variant = runs.Variant(args.order, args.without_structures, args.overlay,
                           driver.Settle(args.step_settle, args.end_settle))
    worst = EXIT_SAME
    for location in sweep.load_locations(args.locations, args.profile):
        folder = args.out.resolve() / location.name
        print(f"run {args.jar.name} {location.name} ({args.order}) in {folder}", flush=True)
        order = runs.measured_run(setup, args.jar.resolve(), location, args.bootstrap / location.name / "world",
                                  folder, variant)
        timing = json.loads((folder / "run.json").read_text(encoding="utf-8"))
        print(f"  start {timing['start_seconds']} s, sweep {timing['sweep_seconds']} s over "
              f"{len(timing['step_seconds'])} steps; " + report.render_order("", order.as_json()), flush=True)
        worst = worse(worst, EXIT_SAME if order.ok else EXIT_NO_VERDICT)
    return worst


def command_compare(args) -> int:
    side_a, side_b = runinfo.read_run(args.run_a), runinfo.read_run(args.run_b)
    if args.profile or args.locations:
        expected = sweep.load_locations(args.locations or DEFAULT_LOCATIONS, args.profile or "dev")
        runinfo.check_expected(side_a, expected)
        runinfo.check_expected(side_b, expected)
    warnings = runinfo.check_pair(side_a, side_b, args.allow_variant_mismatch, args.allow_identical_runs)
    worst, everything = EXIT_SAME, []
    for warning in warnings:
        print(f"WARNING: {warning}")
    for location in runinfo.locations_of(side_a):
        folder_a, folder_b = side_a.folder / location.name, side_b.folder / location.name
        orders = {"A": _read_json(folder_a / "order.json"), "B": _read_json(folder_b / "order.json")}
        bootstrap = Path(side_a.info[location.name]["bootstrap_world"])
        result = compare_all.compare_location(folder_a / "world", folder_b / "world", location, bootstrap,
                                              runinfo.mojang_sides(side_a, side_b))
        print("\n".join(runinfo.describe("A", side_a, location.name) + runinfo.describe("B", side_b, location.name)))
        print("\n".join(report.render_location(result, orders, args.limit)))
        worst = worse(worst, _verdict(result, orders))
        everything += [{**d._asdict(), "location": location.name} for d in result.differences]
    print("\n".join(report.render_ignored()))
    if args.json:
        args.json.write_text(json.dumps(everything, indent=1, default=list) + "\n", encoding="utf-8")
    print({EXIT_SAME: "VERDICT: identical", EXIT_DIFFERENT: "VERDICT: different",
           EXIT_PARTIAL: "VERDICT: identical except the post-processing footprint of Mojang's promotion race "
                         "(see PARTLY COMPARED above); not plain identical",
           EXIT_NO_VERDICT: "VERDICT: none (setup or order failure)"}[worst])
    return worst


def _verdict(result: compare_all.LocationReport, orders: dict) -> int:
    """Each run must have proven its own order. The recorded statuses may differ between jar families (Moonrise runs
    light and full outside ChunkStep, so no event); each run matched the prediction for the statuses it recorded."""
    if result.setup_problems or not all(order["ok"] for order in orders.values()):
        return EXIT_NO_VERDICT
    if result.differences:
        return EXIT_DIFFERENT
    return EXIT_PARTIAL if result.excluded else EXIT_SAME


def command_plant(args) -> int:
    """Copies each location's world, plants one change of each kind and requires the comparer to report exactly
    those (one difference each, at the planted chunk) and nothing else."""
    failures = 0
    side = runinfo.read_run(args.run)
    if args.profile or args.locations:
        runinfo.check_expected(side, sweep.load_locations(args.locations or DEFAULT_LOCATIONS, args.profile or "dev"))
    for location in runinfo.locations_of(side):
        source, target = args.run / location.name / "world", args.out / location.name / "world"
        worldcopy.copy_world(source, target)
        planted = mutate_region.plant_all(target, location)
        result = compare_all.compare_location(source, target, location)
        found = sorted((d.category, d.chunk) for d in result.differences)
        expected = sorted((p.category, p.chunk) for p in planted)
        print(f"== {location.name}: planted {len(planted)}:")
        print("\n".join(f"  {p.category} in chunk {p.chunk}: {p.what}" for p in planted))
        print(f"reported {len(result.differences)}:")
        print("\n".join(f"  {d.category} in chunk {d.chunk}: {d.description}" for d in result.differences))
        passed = found == expected
        failures += not passed
        print("SELF-TEST 6: " + ("passed, exactly the planted changes were reported" if passed else "FAILED"))
    return EXIT_SAME if failures == 0 else EXIT_DIFFERENT


def _read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    sys.exit(main())
