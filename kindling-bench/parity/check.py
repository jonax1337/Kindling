"""Checks that a CI bases run did exactly what the golden file says: every contraption counter identical.

The counters of the bases scenario count what each contraption did in a fixed number of server ticks, so they do not
depend on how fast the machine is. A counter that differs means block entity or redstone behaviour changed. Exit code 1
and one line per difference when the run does not match.

    python -I check.py <runs/parity-ci.json>                  # compare every run in the file with bases-ci.json
    python -I check.py <runs/parity-ci.json> --update         # rewrite the golden file from these runs (see README)
"""

import argparse
import json
import sys
from pathlib import Path

GOLDEN = Path(__file__).resolve().parent / "bases-ci.json"


def main() -> int:
    args = parse_arguments()
    runs = json.loads(args.result.read_text(encoding="utf-8"))
    if not runs:
        print(f"no runs in {args.result}")
        return 1
    if args.update:
        return update_golden(args.golden, runs)
    golden = json.loads(args.golden.read_text(encoding="utf-8"))
    problems = [f"run {run.get('attempt')}: {problem}" for run in runs for problem in problems_of(run, golden)]
    if problems:
        print(f"PARITY FAILED against {args.golden.name}:")
        print("\n".join(f"  {problem}" for problem in problems))
        return 1
    print(f"parity ok: {len(runs)} run(s), {len(golden['counters'])} counters identical to {args.golden.name}")
    return 0


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("result", type=Path, help="the runs/<label>.json that run_bench.py wrote")
    parser.add_argument("--golden", type=Path, default=GOLDEN)
    parser.add_argument("--update", action="store_true", help="write the golden file from these runs")
    return parser.parse_args()


def problems_of(run: dict, golden: dict) -> list[str]:
    """Everything that makes this run not comparable with the golden file, or different from it."""
    if run.get("settled") is not True:
        return ["built its bases before world generation settled, so its counters mean nothing"]
    if run.get("scenario") != golden["scenario"]:
        differences = counter_differences(golden["scenario"], run.get("scenario", {}))
        return ["ran another scenario than the golden file"] + [f"  scenario {line}" for line in differences]
    return counter_differences(golden["counters"], run.get("counters", {}))


def counter_differences(expected: dict[str, int], actual: dict[str, int]) -> list[str]:
    lines = []
    for name in sorted(expected.keys() | actual.keys()):
        if name not in actual:
            lines.append(f"{name}: expected {expected[name]}, the run has no such counter")
        elif name not in expected:
            lines.append(f"{name}: the run reports {actual[name]}, the golden file has no such counter")
        elif expected[name] != actual[name]:
            lines.append(f"{name}: expected {expected[name]}, got {actual[name]} ({actual[name] - expected[name]:+d})")
    return lines


def update_golden(golden_file: Path, runs: list[dict]) -> int:
    """Writes the golden file, but only from runs that settled and agree with each other."""
    first = runs[0]
    problems = [f"run {run.get('attempt')}: {problem}" for run in runs for problem in unfit_for_golden(run, first)]
    if problems:
        print("not writing the golden file:")
        print("\n".join(f"  {problem}" for problem in problems))
        return 1
    golden = {"scenario": first["scenario"], "counters": first["counters"]}
    golden_file.write_text(json.dumps(golden, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {golden_file} from {len(runs)} run(s)")
    return 0


def unfit_for_golden(run: dict, first: dict) -> list[str]:
    if run.get("settled") is not True:
        return ["built its bases before world generation settled"]
    if run["scenario"] != first["scenario"]:
        return ["ran another scenario than run 1"]
    return counter_differences(first["counters"], run["counters"])


if __name__ == "__main__":
    sys.exit(main())
